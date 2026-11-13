// v7 · Pago en línea de un pedido (tarjeta al instante; Yape y Plin con un QR que vence en 60 s) y
// boleta imprimible. El servidor decide montos, vencimientos y resultados: aquí solo se presentan.
// La cuenta regresiva se sincroniza con el servidor (TTL de la clave en Redis) cada pocos segundos.

import { ApiError, paymentApi, shopApi, storeApi } from '../api.js';
import { emptyState, errorMessage, field, formError, icon, money, orderRef, showFormError, skeletons, submitting } from '../components.js';
import { link, route } from '../router.js';
import { el, formatDateTime, toast } from '../ui.js';
import qrcode from '../vendor/qrcode.js';
import { t } from '../i18n.js';

const view = (...children) => el('section', { class: 'view view--store' }, ...children);
const head = (eyebrow, title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }),
    lead ? el('p', { class: 'section__lead', text: lead }) : null),
  actions.filter(Boolean).length ? el('div', { class: 'row-actions no-print' }, actions) : null);

export const METHOD_LABELS = { CARD: t('Tarjeta de crédito o débito'), YAPE: 'Yape', PLIN: 'Plin' };
const DECLINE_TEXT = {
  INSUFFICIENT_FUNDS: t('Fondos insuficientes: prueba con otra tarjeta.'),
  CARD_BLOCKED: t('El banco bloqueó la tarjeta: comunícate con tu banco o usa otra.'),
  DO_NOT_HONOR: t('El banco rechazó la operación: usa otra tarjeta o método.'),
  WRONG_APPROVAL_CODE: t('El código de aprobación no es correcto: genera un QR nuevo e inténtalo otra vez.'),
};
const BRANDS = { VISA: 'Visa', MASTERCARD: 'Mastercard', AMEX: 'American Express', DINERS: 'Diners Club', OTHER: t('Tarjeta') };

/** Marca según los primeros dígitos (solo para mostrarla mientras se escribe; el servidor la vuelve a calcular). */
function brandOf(digits) {
  if (/^4/.test(digits)) return 'VISA';
  if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(digits)) return 'MASTERCARD';
  if (/^3[47]/.test(digits)) return 'AMEX';
  if (/^3(0[0-5]|[689])/.test(digits)) return 'DINERS';
  return null;
}

/** Código QR como SVG (sin bibliotecas externas en tiempo de ejecución ni imágenes remotas). */
export function qrSvg(text, size = 220) {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  const n = qr.getModuleCount();
  const margin = 2;
  let path = '';
  for (let r = 0; r < n; r += 1) for (let c = 0; c < n; c += 1) if (qr.isDark(r, c)) path += `M${c + margin},${r + margin}h1v1h-1z`;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${n + margin * 2} ${n + margin * 2}`);
  svg.setAttribute('width', String(size));
  svg.setAttribute('height', String(size));
  svg.setAttribute('role', 'img');
  svg.setAttribute('shape-rendering', 'crispEdges');
  const bg = document.createElementNS(svg.namespaceURI, 'rect');
  bg.setAttribute('width', '100%'); bg.setAttribute('height', '100%'); bg.setAttribute('fill', '#fff');
  const dark = document.createElementNS(svg.namespaceURI, 'path');
  dark.setAttribute('d', path); dark.setAttribute('fill', '#111');
  svg.append(bg, dark);
  return svg;
}

function sandboxNote(kind) {
  const text = kind === 'CARD'
    ? t('Modo de pruebas: no se cobra nada. Aprobadas: 4111 1111 1111 1111 (Visa), 5555 5555 5555 4444 (Mastercard). Rechazadas: 4000 0000 0000 0002 (fondos insuficientes) y 4000 0000 0000 0069 (tarjeta bloqueada). Cualquier fecha futura y CVV de 3 dígitos.')
    : t('Modo de pruebas: no se cobra nada. Usa cualquier celular que empiece con 9 y cualquier código de 6 dígitos; 000000 simula un rechazo.');
  return el('div', { class: 'notice-box notice-box--warn small', 'data-testid': 'payment-sandbox' }, el('strong', { text: t('Demostración. ') }), text);
}

function successPanel(orderId, status) {
  return el('div', { class: 'card pay-success', 'data-testid': 'payment-approved' },
    el('div', { class: 'pay-success__check', 'aria-hidden': 'true', text: '✓' }),
    el('h2', { text: t('¡Pago aprobado!') }),
    el('p', { class: 'muted' }, t('Boleta {0} · {1}', status.receipt, money(status.amount, status.currency))),
    el('p', { class: 'small muted', text: t('Te enviamos la boleta a tu correo. También puedes verla e imprimirla aquí.') }),
    el('dl', { class: 'pay-success__facts' },
      el('dt', { text: t('Método') }), el('dd', { text: status.cardLast4 ? `${BRANDS[status.cardBrand] || status.cardBrand} •••• ${status.cardLast4}` : METHOD_LABELS[status.method] }),
      el('dt', { text: t('N.º de operación') }), el('dd', { class: 'mono', text: status.reference || '—' }),
      el('dt', { text: t('Fecha') }), el('dd', { text: status.paidAt ? formatDateTime(status.paidAt) : '—' })),
    el('div', { class: 'row-actions' },
      el('a', { class: 'btn btn--primary', href: link(`/boleta/${orderId}`), 'data-testid': 'receipt-link' }, icon('file'), t(' Ver boleta')),
      el('a', { class: 'btn btn--ghost', href: link(`/seguimiento/${orderId}`), text: t('Seguir mi pedido') })));
}

route('/pago/:id', async (root, { id }) => {
  let details;
  let status;
  try {
    [details, status] = await Promise.all([shopApi.order(id), paymentApi.status(id)]);
  } catch (error) {
    root.append(view(head(t('Pago'), t('Pedido no disponible'), ''), el('div', { class: 'card' }, emptyState(t('No encontramos este pedido'), errorMessage(error)))));
    return;
  }
  const { order } = details;
  const amount = money(order.amount, order.currency);
  const panel = el('div', { class: 'card pay-panel' });
  const methods = ['CARD', 'YAPE', 'PLIN'];
  let current = methods.includes(status.method) ? status.method : (methods.includes(status.paymentMethod) ? status.paymentMethod : 'CARD');
  let countdown = null;
  let syncer = null;
  const stopTimers = () => { window.clearInterval(countdown); window.clearInterval(syncer); };

  const tabs = el('div', { class: 'pay-tabs', role: 'tablist', 'aria-label': t('Método de pago') }, methods.map((m) => el('button', {
    type: 'button', role: 'tab', class: 'pay-tab', 'data-method': m, 'aria-selected': String(m === current),
    onclick: () => { if (m !== current) { current = m; render(); } },
  }, el('span', { class: `pay-tab__logo pay-tab__logo--${m.toLowerCase()}`, 'aria-hidden': 'true', text: m === 'CARD' ? '💳' : m === 'YAPE' ? 'Y' : 'P' }),
  el('span', { text: METHOD_LABELS[m] }), el('small', { text: m === 'CARD' ? t('Inmediato') : t('QR · 60 s') }))));

  function render() {
    stopTimers();
    tabs.querySelectorAll('.pay-tab').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.method === current)));
    if (status.status === 'PAID') {
      tabs.hidden = true;
      panel.replaceChildren(successPanel(id, status));
      return;
    }
    if (['CANCELLED', 'REJECTED'].includes(order.status)) {
      tabs.hidden = true;
      panel.replaceChildren(emptyState(t('Este pedido no se puede pagar'), t('Está cancelado o no fue aprobado. No se realizó ningún cobro.'), 'box'));
      return;
    }
    panel.replaceChildren(current === 'CARD' ? cardForm() : walletPanel(current));
  }

  function declined(view) {
    return el('div', { class: 'notice-box notice-box--error', role: 'alert', 'data-testid': 'payment-declined' },
      el('strong', { text: t('Pago rechazado. ') }), DECLINE_TEXT[view.failureCode] || t('La operación no fue aprobada.'));
  }

  function cardForm() {
    const number = el('input', { inputmode: 'numeric', autocomplete: 'cc-number', maxlength: '23', placeholder: '1234 5678 9012 3456', 'aria-describedby': 'card-brand' });
    const holder = el('input', { autocomplete: 'cc-name', maxlength: '60', placeholder: t('Como figura en la tarjeta') });
    const expiry = el('input', { inputmode: 'numeric', autocomplete: 'cc-exp', maxlength: '5', placeholder: t('MM/AA') });
    const cvv = el('input', { inputmode: 'numeric', autocomplete: 'cc-csc', maxlength: '4', placeholder: 'CVV', type: 'password' });
    const brand = el('span', { class: 'card-brand', id: 'card-brand', 'aria-live': 'polite' });
    const errorNode = formError();
    const result = el('div', {});
    const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block btn--lg', 'data-testid': 'pay-card' }, icon('shield'), t(' Pagar {0}', amount));
    number.addEventListener('input', () => {
      const digits = number.value.replace(/\D/g, '').slice(0, 19);
      number.value = digits.replace(/(\d{4})(?=\d)/g, '$1 ');
      const b = brandOf(digits);
      brand.textContent = b ? BRANDS[b] : '';
      cvv.maxLength = b === 'AMEX' ? 4 : 3;
    });
    expiry.addEventListener('input', () => {
      const d = expiry.value.replace(/\D/g, '').slice(0, 4);
      expiry.value = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
    });
    cvv.addEventListener('input', () => { cvv.value = cvv.value.replace(/\D/g, ''); });
    const form = el('form', { class: 'form pay-form', novalidate: true, autocomplete: 'on' },
      el('div', { class: 'pay-form__card', 'aria-hidden': 'true' }, el('span', { text: 'POLYMER' }), brand),
      el('div', { class: 'form-grid' },
        (() => { const f = field(t('Número de tarjeta'), number); f.classList.add('field--full'); return f; })(),
        (() => { const f = field(t('Titular'), holder); f.classList.add('field--full'); return f; })(),
        field(t('Vencimiento'), expiry), field(t('CVV'), cvv, t('3 dígitos al reverso (4 al frente en American Express).'))),
      submit, errorNode, result,
      el('p', { class: 'small muted' }, icon('shield'), t(' Los datos de tu tarjeta solo se usan para esta operación: no los guardamos (solo la marca y los 4 últimos dígitos).')),
      sandboxNote('CARD'));
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      result.replaceChildren();
      const [mm, yy] = expiry.value.split('/');
      const card = { number: number.value, holder: holder.value.trim(), expMonth: Number(mm), expYear: Number(yy), cvv: cvv.value };
      if (number.value.replace(/\D/g, '').length < 13 || !holder.value.trim() || !mm || !yy || cvv.value.length < 3) {
        showFormError(errorNode, new Error(t('Completa el número, el titular, el vencimiento (MM/AA) y el CVV.')));
        return;
      }
      try {
        status = await submitting(submit, errorNode, () => paymentApi.card(id, card));
        toast(t('Pago aprobado'), 'ok');
        render();
      } catch (error) {
        if (error instanceof ApiError && error.status === 402 && error.body) {
          errorNode.hidden = true;
          status = error.body;
          result.replaceChildren(declined(error.body));
          cvv.value = '';
        }
      }
    });
    window.setTimeout(() => number.focus(), 0);
    return form;
  }

  function walletPanel(method) {
    const name = METHOD_LABELS[method];
    const box = el('div', { class: 'wallet' });
    const errorNode = formError();
    const pending = status.status === 'PENDING' && status.method === method && status.qrPayload;
    if (!pending) {
      const generate = el('button', { type: 'button', class: 'btn btn--primary btn--lg', 'data-testid': 'wallet-start' }, t('Generar QR de {0}', name));
      generate.addEventListener('click', async () => {
        try {
          status = await submitting(generate, errorNode, () => paymentApi.wallet(id, method));
          render();
        } catch { /* error mostrado */ }
      });
      const expired = status.method === method && status.status === 'EXPIRED';
      const wasDeclined = status.method === method && status.status === 'DECLINED';
      box.append(
        expired ? el('div', { class: 'notice-box notice-box--warn', 'data-testid': 'wallet-expired' }, el('strong', { text: t('El código QR venció. ') }),
          t('Las solicitudes de {0} valen 60 segundos. Genera una nueva para pagar.', name)) : null,
        wasDeclined ? declined(status) : null,
        el('p', { text: t('Generaremos una solicitud de cobro de {0} por {1}. Tendrás 60 segundos para aprobarla desde tu app.', name, amount) }),
        generate, errorNode, sandboxNote(method));
      return box;
    }
    let left = Math.max(0, Number(status.remainingSeconds) || 0);
    const total = 60;
    const seconds = el('strong', { class: 'ttl-ring__value', 'data-testid': 'wallet-seconds' });
    const ring = el('div', { class: 'ttl-ring', role: 'timer', 'aria-label': t('Tiempo restante') }, seconds, el('small', { text: t('segundos') }));
    const phone = el('input', { type: 'tel', inputmode: 'numeric', autocomplete: 'tel-national', maxlength: '11', placeholder: '987 654 321' });
    const approval = el('input', { inputmode: 'numeric', autocomplete: 'one-time-code', maxlength: '6', placeholder: '123456' });
    const confirm = el('button', { type: 'submit', class: 'btn btn--primary btn--block', 'data-testid': 'wallet-confirm' }, t('Confirmar pago de {0}', amount));
    const draw = () => {
      seconds.textContent = String(Math.ceil(left));
      ring.style.setProperty('--ttl', String(Math.max(0, Math.min(1, left / total))));
      ring.classList.toggle('is-low', left <= 10);
    };
    const expire = async () => {
      stopTimers();
      status = await paymentApi.status(id).catch(() => ({ ...status, status: 'EXPIRED' }));
      render();
    };
    countdown = window.setInterval(() => {
      if (!box.isConnected) { stopTimers(); return; }
      left = Math.max(0, left - 0.25);
      draw();
      if (left <= 0) expire();
    }, 250);
    // Sincroniza con el servidor (TTL real de la solicitud en Redis o su hora de vencimiento).
    syncer = window.setInterval(async () => {
      try {
        const fresh = await paymentApi.status(id);
        if (fresh.status !== 'PENDING') { status = fresh; render(); return; }
        left = Number(fresh.remainingSeconds);
      } catch { /* se mantiene el reloj local */ }
    }, 5000);
    const form = el('form', { class: 'form', novalidate: true },
      el('div', { class: 'form-grid' }, field(t('Celular de tu {0}', name), phone), field(method === 'YAPE' ? t('Código de aprobación') : t('Código de confirmación'), approval,
        method === 'YAPE' ? t('Lo ves en tu app de Yape (6 dígitos).') : t('Lo ves en la app de tu banco (6 dígitos).'))),
      confirm, errorNode);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      try {
        status = await submitting(confirm, errorNode, () => paymentApi.confirm(id, status.paymentId, phone.value, approval.value));
        stopTimers();
        toast(t('Pago aprobado'), 'ok');
        render();
      } catch (error) {
        if (error instanceof ApiError && error.status === 402 && error.body) { status = error.body; stopTimers(); render(); }
        if (error instanceof ApiError && error.status === 409) { expire(); }
      }
    });
    box.append(
      el('div', { class: `wallet__qr wallet__qr--${method.toLowerCase()}` },
        el('div', { class: 'wallet__code', 'data-testid': 'wallet-qr' }, qrSvg(status.qrPayload)),
        el('div', { class: 'wallet__aside' }, ring,
          el('p', { class: 'small', text: t('Escanea el código con {0} o confirma con tu celular y el código de tu app. La solicitud vence en 60 segundos.', name) }))),
      form, sandboxNote(method));
    draw();
    return box;
  }

  root.append(view(
    head(t('Pago'), t('Paga tu pedido {0}', orderRef(order)), t('Total a pagar: {0}', amount)),
    el('ol', { class: 'checkout-steps', 'aria-label': t('Pasos de la compra') },
      el('li', { class: 'is-done', text: t('Carrito') }), el('li', { class: 'is-done', text: t('Entrega y pago') }), el('li', { class: 'is-current', text: t('Pago') })),
    el('div', { class: 'cart-layout' },
      el('div', { class: 'stack' }, tabs, panel),
      el('aside', { class: 'card cart-summary' }, el('h2', { class: 'card__title', text: t('Resumen del pedido') }),
        order.lines.map((l) => el('div', { class: 'summary-row' }, el('span', { text: `${l.quantity} × ${l.productName}` }), el('span', { text: money(l.lineTotal, order.currency) }))),
        el('div', { class: 'summary-row summary-row--total' }, el('span', { text: t('Total') }), el('span', { text: amount })),
        el('p', { class: 'small muted', text: t('El total incluye IGV. Recibirás la boleta en tu correo al aprobarse el pago.') }),
        el('a', { class: 'btn btn--ghost btn--block', href: link(`/mis-pedidos/${id}`), text: t('Ver detalle del pedido') })))));
  render();
}, { roles: ['CUSTOMER'], title: t('Pago del pedido') });

// ---- Boleta imprimible ------------------------------------------------------------------------------
route('/boleta/:id', async (root, { id }) => {
  const holder = el('div', { class: 'card' }, skeletons(6, 'skeleton--row'));
  root.append(view(head(t('Comprobante'), t('Boleta de venta'), '',
    el('button', { type: 'button', class: 'btn btn--primary', onclick: () => window.print() }, icon('file'), t(' Imprimir o guardar PDF')),
    el('a', { class: 'btn btn--ghost', href: link(`/mis-pedidos/${id}`), text: t('Volver al pedido') })), holder));
  let receipt;
  let store = null;
  try {
    [receipt, store] = await Promise.all([paymentApi.receipt(id), storeApi.info().catch(() => null)]);
  } catch (error) {
    holder.replaceChildren(emptyState(t('Este pedido aún no tiene boleta'), errorMessage(error), 'file',
      el('a', { class: 'btn btn--primary', href: link(`/pago/${id}`), text: t('Ir a pagar') })));
    return;
  }
  const cur = receipt.currency;
  const method = receipt.cardLast4 ? `${BRANDS[receipt.cardBrand] || receipt.cardBrand} •••• ${receipt.cardLast4}` : METHOD_LABELS[receipt.paymentMethod];
  const row = (label, value, strong = false) => el('div', { class: `summary-row${strong ? ' summary-row--total' : ''}` }, el('span', { text: label }), el('span', { text: value }));
  holder.replaceWith(el('article', { class: 'card receipt', 'data-testid': 'receipt' },
    el('header', { class: 'receipt__head' },
      el('div', {}, el('img', { src: '/img/polymer-logo.svg', alt: 'POLYMER', height: '36' }),
        store ? el('p', { class: 'small muted', text: `${store.fullAddress} · ${store.phone}` }) : null),
      el('div', { class: 'receipt__code' }, el('strong', { text: t('BOLETA DE VENTA') }), el('span', { class: 'mono', text: receipt.code }))),
    el('dl', { class: 'receipt__facts' },
      el('dt', { text: t('Cliente') }), el('dd', { text: `${receipt.customerName} · ${receipt.customerEmail}` }),
      el('dt', { text: t('Fecha de emisión') }), el('dd', { text: formatDateTime(receipt.issuedAt) }),
      el('dt', { text: t('Pedido') }), el('dd', { class: 'mono', text: String(receipt.orderId) }),
      el('dt', { text: t('Pagado con') }), el('dd', { text: method }),
      el('dt', { text: t('N.º de operación') }), el('dd', { class: 'mono', text: receipt.reference })),
    el('div', { class: 'table-scroll' }, el('table', { class: 'table receipt__lines' },
      el('thead', {}, el('tr', {}, [t('Producto'), t('Cant.'), t('P. unit.'), t('Importe')].map((h) => el('th', { scope: 'col', text: h })))),
      el('tbody', {}, receipt.lines.map((l) => el('tr', {}, el('td', { text: l.productName }), el('td', { text: String(l.quantity) }),
        el('td', { class: 'mono', text: money(l.unitPrice, cur) }), el('td', { class: 'mono', text: money(l.lineTotal, cur) })))))),
    el('div', { class: 'receipt__totals' },
      row(t('Subtotal'), money(receipt.subtotal, cur)),
      Number(receipt.savings) > 0 ? row(t('Ahorro en promociones'), `-${money(receipt.savings, cur)}`) : null,
      row(t('Envío'), Number(receipt.shippingCost) ? money(receipt.shippingCost, cur) : t('Gratis')),
      row(t('Op. gravada'), money(receipt.taxableBase, cur)),
      row(t('IGV (18 %)'), money(receipt.igv, cur)),
      row(t('Total pagado'), money(receipt.total, cur), true)),
    el('p', { class: 'small muted receipt__note', text: t('Comprobante interno de la tienda de demostración POLYMER (proyecto académico): no es un comprobante electrónico de SUNAT y no se realizó ningún cobro real.') })));
}, { roles: ['CUSTOMER'], title: t('Boleta de venta') });

void toast;
