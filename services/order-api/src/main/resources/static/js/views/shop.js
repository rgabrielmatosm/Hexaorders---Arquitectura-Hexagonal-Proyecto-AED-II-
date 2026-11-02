// Portal comercial: catálogo, producto, carrito, checkout, mis pedidos, seguimiento, perfil y acceso.
// Precios, stock, totales, permisos y estados los decide siempre el backend.

import { ApiError, accountApi, authApi, catalogApi, shopApi } from '../api.js';
import { cart } from '../cart.js';
import {
  TRANSITION_VERBS, emptyState, errorMessage, field, formError, icon, lifecycleBadge, modal, money,
  paginated, productArt, progressBar, showFormError, skeletons, stockNote, submitting,
} from '../components.js';
import { link, navigate, rerender, route } from '../router.js';
import { ROLE_LABELS, homeFor, session } from '../session.js';
import { $, $$, el, formatDateTime, formatRelative, toast } from '../ui.js';

const view = (...children) => el('section', { class: 'view' }, ...children);
const head = (eyebrow, title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }),
    lead ? el('p', { class: 'section__lead', text: lead }) : null),
  actions.length ? el('div', { class: 'row-actions' }, actions) : null);

// ---- Catálogo ----------------------------------------------------------------
export function productCard(product) {
  const add = el('button', { type: 'button', class: 'btn btn--primary btn--sm', disabled: product.stock <= 0, text: product.stock <= 0 ? 'Agotado' : 'Añadir' });
  add.addEventListener('click', () => {
    cart.add(product.id, 1);
    toast(`${product.name} añadido al carrito`, 'ok', 2200);
  });
  return el('article', { class: 'product-card', dataset: { productId: String(product.id) } },
    el('a', { href: link(`/producto/${product.id}`), 'aria-label': product.name, tabindex: '-1' },
      (() => { const art = productArt(product, 'product-card__media'); art.append(el('span', { class: 'badge badge--neutral product-card__tag', text: product.categoryName })); return art; })()),
    el('div', { class: 'product-card__body' },
      el('h3', {}, el('a', { href: link(`/producto/${product.id}`), text: product.name })),
      el('p', { class: 'product-card__desc', text: product.description }),
      stockNote(product.stock),
      el('div', { class: 'product-card__foot' }, el('span', { class: 'price', text: money(product.price) }), add)));
}

route('/tienda', async (root, { query }) => {
  let category = query.get('categoria') || '';
  let search = query.get('q') || '';
  let sort = query.get('orden') || 'name';
  let onlyAvailable = query.get('disponibles') === '1';
  let maxPrice = Number(query.get('max')) || 0;
  const grid = el('div', { class: 'catalog-results' }, el('div', { class: 'products' }, skeletons(8, 'skeleton--card')));
  const chips = el('div', { class: 'chips', role: 'group', 'aria-label': 'Categorías' });
  const searchInput = el('input', { type: 'search', placeholder: 'Buscar productos…', value: search, 'aria-label': 'Buscar productos' });
  const sortSelect = el('select', { 'aria-label': 'Ordenar' },
    el('option', { value: 'name', text: 'Nombre' }), el('option', { value: 'price-asc', text: 'Precio: menor a mayor' }),
    el('option', { value: 'price-desc', text: 'Precio: mayor a menor' }));
  sortSelect.value = sort;
  const availableToggle = el('input', { type: 'checkbox', checked: onlyAvailable });
  const priceSelect = el('select', { 'aria-label': 'Precio máximo' },
    [['0', 'Cualquier precio'], ['100', 'Hasta 100 USD'], ['300', 'Hasta 300 USD'], ['800', 'Hasta 800 USD'], ['1500', 'Hasta 1500 USD']]
      .map(([value, label]) => el('option', { value, text: label })));
  priceSelect.value = String(maxPrice);
  const resultInfo = el('p', { class: 'muted small', 'aria-live': 'polite' });
  // El estado de los filtros vive en la URL: la página se puede recargar o compartir.
  const syncUrl = () => {
    const params = new URLSearchParams();
    if (category) params.set('categoria', category);
    if (search) params.set('q', search);
    if (sort !== 'name') params.set('orden', sort);
    if (onlyAvailable) params.set('disponibles', '1');
    if (maxPrice) params.set('max', String(maxPrice));
    const qs = params.toString();
    window.history.replaceState(null, '', `${window.location.pathname}${qs ? `?${qs}` : ''}`);
  };
  const stats = el('div', { class: 'shop-hero__stats' });

  root.append(view(
    el('div', { class: 'shop-hero' },
      el('p', { class: 'eyebrow', text: 'HexaOrders Store' }),
      el('h1', {}, 'Tecnología y equipamiento con ', el('span', { class: 'gradient-text', text: 'evaluación de riesgo en tiempo real' })),
      el('p', { text: 'Cada compra pasa por el mismo núcleo hexagonal: el backend calcula precios y stock, el motor de riesgo en Python evalúa el pedido y PostgreSQL lo guarda con su historial.' }),
      stats),
    el('div', { class: 'feature-row' },
      [['shield', 'Precios del servidor', 'El total se calcula y valida en Java, nunca en el navegador.'],
        ['bolt', 'Riesgo en milisegundos', 'Cada pedido consulta el motor FastAPI antes de guardarse.'],
        ['truck', 'Seguimiento completo', 'Del pago de demostración a la entrega, con historial auditable.'],
        ['box', 'Stock sin sobreventa', 'Reserva atómica en PostgreSQL y devolución al cancelar.']]
        .map(([ic, title, text]) => el('div', { class: 'feature' }, el('div', { class: 'feature__icon' }, icon(ic)), el('strong', { text: title }), el('p', { text })))),
    el('div', { class: 'catalog-toolbar catalog-toolbar--wide' }, searchInput, priceSelect, sortSelect,
      el('label', { class: 'switch' }, availableToggle, 'Solo disponibles')),
    chips,
    resultInfo,
    grid));
  chips.style.marginBottom = '16px';

  let products = [];
  const render = () => {
    syncUrl();
    const list = [...products]
      .filter((p) => !onlyAvailable || p.stock > 0)
      .filter((p) => !maxPrice || Number(p.price) <= maxPrice)
      .sort((a, b) => (sort === 'price-asc' ? a.price - b.price : sort === 'price-desc' ? b.price - a.price : a.name.localeCompare(b.name)));
    resultInfo.textContent = `${list.length} producto(s) encontrados`;
    if (!list.length) { grid.replaceChildren(emptyState('Sin resultados', 'Prueba con otra búsqueda, categoría o precio.', 'empty')); return; }
    grid.replaceChildren(paginated(list, (page) => el('div', { class: 'products' }, page.map(productCard)), { pageSize: 8, label: 'productos' }));
  };
  const load = async () => {
    grid.replaceChildren(el('div', { class: 'products' }, skeletons(8, 'skeleton--card')));
    try {
      products = await catalogApi.products(search || undefined, category || undefined);
      render();
    } catch (error) {
      grid.replaceChildren(emptyState('No se pudo cargar el catálogo', errorMessage(error)));
    }
  };
  try {
    const categories = await catalogApi.categories();
    const chip = (id, name) => {
      const button = el('button', { type: 'button', class: `chip${String(category) === String(id) ? ' is-active' : ''}`, text: name, 'aria-pressed': String(String(category) === String(id)) });
      button.addEventListener('click', () => {
        category = id;
        $$('.chip', chips).forEach((c) => { c.classList.toggle('is-active', c === button); c.setAttribute('aria-pressed', String(c === button)); });
        load();
      });
      return button;
    };
    chips.replaceChildren(chip('', 'Todas'), ...categories.map((c) => chip(c.id, c.name)));
    const all = await catalogApi.products();
    stats.replaceChildren(
      el('div', {}, el('strong', { text: String(all.length) }), el('span', { text: 'productos activos' })),
      el('div', {}, el('strong', { text: String(categories.length) }), el('span', { text: 'categorías' })),
      el('div', {}, el('strong', { text: String(all.reduce((s, p) => s + p.stock, 0)) }), el('span', { text: 'unidades en stock' })));
  } catch (error) {
    chips.replaceChildren(el('p', { class: 'muted small', text: errorMessage(error) }));
  }
  let timer = 0;
  searchInput.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => { search = searchInput.value.trim(); load(); }, 250); });
  sortSelect.addEventListener('change', () => { sort = sortSelect.value; render(); });
  availableToggle.addEventListener('change', () => { onlyAvailable = availableToggle.checked; render(); });
  priceSelect.addEventListener('change', () => { maxPrice = Number(priceSelect.value) || 0; render(); });
  await load();
}, { title: 'Tienda' });

route('/producto/:id', async (root, { id }) => {
  const product = await catalogApi.product(id);
  let quantity = 1;
  const qtyInput = el('input', { type: 'number', min: '1', max: String(Math.max(1, Math.min(99, product.stock))), value: '1', 'aria-label': 'Cantidad' });
  const change = (delta) => {
    quantity = Math.max(1, Math.min(99, Math.min(product.stock || 1, quantity + delta)));
    qtyInput.value = String(quantity);
  };
  qtyInput.addEventListener('change', () => { quantity = Math.max(1, Math.min(99, Number(qtyInput.value) || 1)); qtyInput.value = String(quantity); });
  const add = el('button', { type: 'button', class: 'btn btn--primary', disabled: product.stock <= 0, text: product.stock <= 0 ? 'Agotado' : 'Añadir al carrito' });
  add.addEventListener('click', () => { cart.add(product.id, quantity); toast(`${quantity} × ${product.name} en el carrito`, 'ok'); });
  root.append(view(
    el('nav', { class: 'breadcrumbs', 'aria-label': 'Ruta del producto' }, el('a', { href: '/catalogo.html', text: 'Catálogo' }), '/',
      el('a', { href: `/catalogo.html?categoria=${product.categoryId}`, text: product.categoryName }), '/', el('span', { text: product.name })),
    el('div', { class: 'product-detail' },
      productArt(product, 'product-detail__media'),
      el('div', { class: 'card' },
        el('span', { class: 'badge badge--neutral', text: product.categoryName }),
        el('h1', { text: product.name }),
        el('p', { class: 'muted', text: product.description }),
        el('span', { class: 'price', text: money(product.price) }),
        stockNote(product.stock),
        el('div', { class: 'row-actions', style: null }, el('div', { class: 'qty' },
          el('button', { type: 'button', 'aria-label': 'Menos', onclick: () => change(-1), text: '−' }), qtyInput,
          el('button', { type: 'button', 'aria-label': 'Más', onclick: () => change(1), text: '+' })), add),
        el('ul', { class: 'spec-list' },
          el('li', {}, icon('shield'), `SKU ${product.sku}`),
          el('li', {}, icon('bolt'), 'Evaluación de riesgo automática al confirmar el pedido'),
          el('li', {}, icon('truck'), 'Entrega programada por el equipo de logística'))))));
  $('.row-actions', root).style.justifyContent = 'flex-start';
  $('.row-actions', root).style.marginTop = '18px';
  document.title = `${product.name} · HexaOrders`;
  const crumb = document.querySelector('#breadcrumbs [aria-current="page"]');
  if (crumb) crumb.textContent = product.name;
  // Relacionados: otros productos activos de la misma categoría (datos de PostgreSQL).
  try {
    const related = (await catalogApi.products(undefined, product.categoryId)).filter((p) => p.id !== product.id).slice(0, 4);
    if (related.length) {
      root.firstElementChild.append(el('section', { class: 'related', 'aria-labelledby': 'related-title' },
        el('h2', { class: 'section-title', id: 'related-title', text: `Más en ${product.categoryName}` }),
        el('div', { class: 'products' }, related.map(productCard))));
    }
  } catch { /* los relacionados son opcionales */ }
}, { title: 'Producto' });

// ---- Carrito y checkout -----------------------------------------------------------
async function renderCart(root, checkout) {
  const items = cart.items();
  if (!items.length) {
    root.append(view(head('Carrito', 'Tu carrito está vacío', ''), el('div', { class: 'card' },
      emptyState('Aún no has añadido productos', 'Explora el catálogo y añade lo que necesites.', 'cart',
        el('a', { class: 'btn btn--primary', href: '/catalogo.html', text: 'Ir al catálogo' })))));
    return;
  }
  let quote;
  let products;
  try {
    [quote, products] = await Promise.all([shopApi.quote(items), catalogApi.products()]);
  } catch (error) {
    root.append(view(head('Carrito', 'Revisa tu carrito', ''), el('div', { class: 'card' },
      emptyState('Algún producto ya no está disponible', errorMessage(error), 'empty',
        el('button', { type: 'button', class: 'btn btn--ghost', text: 'Vaciar carrito', onclick: () => { cart.clear(); rerender(); } })))));
    return;
  }
  const byId = new Map(products.map((p) => [p.id, p]));
  let shippingCard = null;
  const lines = quote.lines.map((line) => {
    const product = byId.get(line.productId) || { hue: 260, categoryName: '' };
    const qty = el('input', { type: 'number', min: '1', max: '99', value: String(line.quantity), 'aria-label': `Cantidad de ${line.productName}`, style: null });
    qty.style.width = '72px';
    qty.disabled = checkout;
    qty.addEventListener('change', () => { cart.set(line.productId, Math.max(0, Number(qty.value) || 0)); handleReload(); });
    return el('div', { class: 'cart-line' },
      productArt(product, 'cart-line__thumb'),
      el('div', {}, el('strong', { text: line.productName }), el('small', { text: `${money(line.unitPrice)} por unidad` }), product.stock !== undefined ? stockNote(product.stock) : null),
      checkout ? el('span', { text: `× ${line.quantity}` }) : qty,
      el('div', { class: 'row-actions' }, el('strong', { text: money(line.lineTotal) }),
        checkout ? null : el('button', { type: 'button', class: 'btn btn--ghost btn--sm', 'aria-label': `Quitar ${line.productName}`, text: 'Quitar',
          onclick: () => { cart.remove(line.productId); handleReload(); } })));
  });
  const summary = el('aside', { class: 'card' },
    el('h3', { class: 'card__title', text: 'Resumen' }),
    el('div', { class: 'summary-row' }, el('span', { text: 'Subtotal' }), el('span', { text: money(quote.total) })),
    el('div', { class: 'summary-row' }, el('span', { text: 'Envío' }), el('span', { text: 'Incluido' })),
    el('div', { class: 'summary-row summary-row--total' }, el('span', { text: 'Total' }), el('span', { text: money(quote.total) })),
    el('p', { class: 'muted small', text: 'Total calculado por el servidor con los precios actuales del catálogo.' }));
  if (!checkout) {
    summary.append(el('a', { class: 'btn btn--primary btn--block', href: '/checkout.html', text: 'Continuar al checkout' }),
      el('a', { class: 'btn btn--ghost btn--block', href: '/catalogo.html', text: 'Seguir comprando' }));
  } else {
    const errorNode = formError();
    const confirm = el('button', { type: 'button', class: 'btn btn--primary btn--block', text: 'Confirmar pedido' });
    // Datos de envío: el dominio (ShippingAddress) los valida de nuevo en el servidor.
    const recipient = el('input', { autocomplete: 'name', maxlength: '80', required: true, value: session.user().displayName });
    const address = el('input', { autocomplete: 'street-address', maxlength: '160', required: true, placeholder: 'Av. Ejemplo 123, dpto. 4' });
    const city = el('input', { autocomplete: 'address-level2', maxlength: '60', required: true, placeholder: 'Lima' });
    const phone = el('input', { type: 'tel', autocomplete: 'tel', maxlength: '20', placeholder: '+51 999 999 999' });
    const instructions = el('textarea', { maxlength: '300', rows: '2', placeholder: 'Referencias para el repartidor (opcional)' });
    shippingCard = el('form', { class: 'card form', novalidate: true, 'aria-labelledby': 'shipping-title' },
      el('h3', { class: 'card__title', id: 'shipping-title', text: 'Datos de entrega' }),
      el('div', { class: 'form-grid' }, field('Destinatario', recipient), field('Ciudad', city),
        (() => { const f = field('Dirección', address); f.classList.add('field--full'); return f; })(),
        field('Teléfono (opcional)', phone),
        (() => { const f = field('Indicaciones', instructions); f.classList.add('field--full'); return f; })()));
    const readShipping = () => ({ recipient: recipient.value.trim(), address: address.value.trim(), city: city.value.trim(),
      phone: phone.value.trim(), instructions: instructions.value.trim() });
    // Una clave por intento de compra: si la red falla y el usuario reintenta, no se duplica el pedido.
    const idempotencyKey = `web-${crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)}`.slice(0, 64);
    confirm.addEventListener('click', async () => {
      try {
        const shipping = readShipping();
        if (shipping.address.length < 5 || shipping.city.length < 2 || shipping.recipient.length < 2) {
          showFormError(errorNode, new Error('Completa destinatario, dirección (mínimo 5 caracteres) y ciudad.'));
          address.focus();
          return;
        }
        const result = await submitting(confirm, errorNode, () => shopApi.place(items, idempotencyKey, shipping));
        cart.clear();
        const order = result.data;
        toast(order.status === 'RISK_REVIEW' ? 'Pedido creado: queda en revisión de riesgo' : 'Pedido creado y aprobado por riesgo', order.status === 'RISK_REVIEW' ? 'warn' : 'ok');
        window.location.assign(`/cliente/seguimiento.html?id=${encodeURIComponent(order.id)}&nuevo=1`);
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) handleReload();
      }
    });
    summary.append(el('div', { class: 'notice-box' }, el('strong', { text: 'Pago de demostración. ' }),
      'No se solicitan ni almacenan datos bancarios: al confirmar, el pedido se evalúa y se registra.'), el('div', { style: null }), confirm, errorNode);
    confirm.style.marginTop = '14px';
  }
  root.append(view(
    head(checkout ? 'Checkout' : 'Carrito', checkout ? 'Confirmar pedido' : 'Tu carrito', checkout ? `Cliente: ${session.user().displayName}` : `${cart.count()} unidades`),
    checkout ? el('ol', { class: 'checkout-steps', 'aria-label': 'Pasos de la compra' },
      el('li', { class: 'is-done', text: 'Carrito' }), el('li', { class: 'is-current', text: 'Entrega y confirmación' }), el('li', { text: 'Evaluación de riesgo y seguimiento' })) : null,
    el('div', { class: 'cart-layout' }, el('div', { class: 'stack' }, el('div', { class: 'card' }, lines), shippingCard), summary)));
}

function handleReload() { rerender(); }

route('/carrito', (root) => renderCart(root, false), { title: 'Carrito' });
route('/checkout', (root) => renderCart(root, true), { roles: ['CUSTOMER'], title: 'Checkout' });

// ---- Mis pedidos y seguimiento -------------------------------------------------------
route('/mis-pedidos', async (root) => {
  const list = el('div', { class: 'card' }, skeletons(4, 'skeleton--row'));
  root.append(view(head('Mi cuenta', 'Mis pedidos', 'Historial y seguimiento de tus compras.'), list));
  try {
    const orders = await shopApi.orders();
    if (!orders.length) {
      list.replaceChildren(emptyState('Todavía no tienes pedidos', 'Cuando compres algo aparecerá aquí con su seguimiento.', 'box',
        el('a', { class: 'btn btn--primary', href: '/catalogo.html', text: 'Ir al catálogo' })));
      return;
    }
    list.replaceChildren(paginated(orders, (page) => el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
      el('thead', {}, el('tr', {}, ['Pedido', 'Fecha', 'Artículos', 'Total', 'Estado', ''].map((h) => el('th', { scope: 'col', text: h })))),
      el('tbody', {}, page.map((o) => el('tr', {},
        el('td', {}, el('span', { class: 'table__id', text: o.id })),
        el('td', { text: formatDateTime(o.createdAt) }),
        el('td', { text: String(o.lines.reduce((s, l) => s + l.quantity, 0)) }),
        el('td', { class: 'mono', text: money(o.amount) }),
        el('td', {}, lifecycleBadge(o.status)),
        el('td', {}, el('div', { class: 'row-actions' },
          el('a', { class: 'btn btn--ghost btn--sm', href: link(`/mis-pedidos/${o.id}`), text: 'Detalle' }),
          el('a', { class: 'btn btn--ghost btn--sm', href: link(`/seguimiento/${o.id}`), text: 'Seguimiento' })))))))), { pageSize: 10, label: 'pedidos' }));
  } catch (error) {
    list.replaceChildren(emptyState('No se pudieron cargar tus pedidos', errorMessage(error)));
  }
}, { roles: ['CUSTOMER'], title: 'Mis pedidos' });

route('/mis-pedidos/:id', async (root, { id }) => {
  const details = await shopApi.order(id);
  const { order, history, delivery, allowedTransitions, shipping } = details;
  const cancelButton = allowedTransitions.includes('CANCELLED')
    ? el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar pedido' }) : null;
  if (cancelButton) {
    cancelButton.addEventListener('click', () => {
      const errorNode = formError();
      const confirm = el('button', { type: 'button', class: 'btn btn--primary', text: 'Sí, cancelar' });
      const dialog = modal('Cancelar pedido', el('div', {}, el('p', { class: 'muted', text: 'Las unidades vuelven al stock. Esta acción queda registrada en el historial.' }), errorNode),
        { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Volver', onclick: () => dialog.close() }), confirm] });
      confirm.addEventListener('click', async () => {
        await submitting(confirm, errorNode, () => shopApi.cancel(id), 'Pedido cancelado');
        dialog.close();
        handleReload();
      });
    });
  }
  root.append(view(
    head('Detalle del pedido', `Pedido ${order.id.slice(0, 8)}`, `Creado ${formatRelative(order.createdAt)} · riesgo ${order.riskScore}/100`,
      lifecycleBadge(order.status), el('a', { class: 'btn btn--primary btn--sm', href: link(`/seguimiento/${order.id}`), text: 'Ver seguimiento' }), cancelButton),
    el('div', { class: 'grid-2' },
      el('div', { class: 'card' },
        el('h3', { class: 'card__title', text: 'Progreso' }), progressBar(order.status),
        delivery ? el('div', { class: 'notice-box' }, el('strong', { text: 'Entrega programada: ' }),
          `${formatDateTime(delivery.start)} – ${new Date(delivery.end).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })} · responsable ${delivery.assigneeName}`)
          : el('p', { class: 'muted small', text: 'La entrega se programará cuando el pedido esté confirmado.' }),
        el('h3', { class: 'card__title card__title--spaced', text: 'Historial' }),
        el('ol', { class: 'timeline' }, history.map((h, index) => el('li', { class: index === history.length - 1 ? 'is-current' : '' },
          el('strong', {}, lifecycleBadge(h.to)), el('small', { text: `${formatDateTime(h.at)} · ${h.actor}${h.note ? ` · ${h.note}` : ''}` }))))),
      el('div', { class: 'card' },
        el('h3', { class: 'card__title', text: 'Artículos' }),
        order.lines.map((l) => el('div', { class: 'summary-row' }, el('span', { text: `${l.quantity} × ${l.productName}` }), el('span', { text: money(l.lineTotal) }))),
        el('div', { class: 'summary-row summary-row--total' }, el('span', { text: 'Total' }), el('span', { text: money(order.amount) })),
        el('p', { class: 'muted small', text: `Decisión del motor de riesgo: ${order.riskDecision} (${order.riskScore}/100).` }),
        el('h3', { class: 'card__title card__title--spaced', text: 'Entrega' }),
        shipping ? el('address', { class: 'address-card' }, el('strong', { text: shipping.recipient }), el('span', { text: shipping.address }),
          el('span', { text: shipping.city }), shipping.phone ? el('span', { text: shipping.phone }) : null,
          shipping.instructions ? el('small', { class: 'muted', text: shipping.instructions }) : null)
          : el('p', { class: 'muted small', text: 'Pedido sin dirección registrada (creado antes de la versión 4 o desde la API).' })))));
  void TRANSITION_VERBS;
}, { roles: ['CUSTOMER'], title: 'Detalle del pedido' });

// ---- Perfil -------------------------------------------------------------------------
route('/perfil', async (root) => {
  const me = await accountApi.me();
  const name = el('input', { value: me.displayName, maxlength: '80', autocomplete: 'name' });
  const email = el('input', { type: 'email', value: me.email, maxlength: '254', autocomplete: 'email' });
  const profileError = formError();
  const saveProfile = el('button', { type: 'submit', class: 'btn btn--primary', text: 'Guardar perfil' });
  const current = el('input', { type: 'password', autocomplete: 'current-password' });
  const next = el('input', { type: 'password', autocomplete: 'new-password', minlength: '8', maxlength: '72' });
  const passError = formError();
  const savePass = el('button', { type: 'submit', class: 'btn btn--ghost', text: 'Cambiar contraseña' });
  const profileForm = el('form', { class: 'card form', novalidate: true }, el('h3', { class: 'card__title', text: 'Datos personales' }),
    field('Nombre visible', name), field('Correo electrónico', email), saveProfile, profileError);
  const passForm = el('form', { class: 'card form', novalidate: true }, el('h3', { class: 'card__title', text: 'Seguridad' }),
    field('Contraseña actual', current), field('Nueva contraseña', next, 'Entre 8 y 72 caracteres, con letras y dígitos.'), savePass, passError);
  profileForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    await submitting(saveProfile, profileError, () => accountApi.profile(name.value, email.value), 'Perfil actualizado').catch(() => {});
  });
  passForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    await submitting(savePass, passError, () => accountApi.password(current.value, next.value), 'Contraseña actualizada').then(() => { current.value = ''; next.value = ''; }).catch(() => {});
  });
  root.append(view(head('Mi cuenta', 'Perfil', `@${me.username} · ${me.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}`),
    el('div', { class: 'grid-2-even' }, profileForm, passForm)));
}, { roles: ['auth'], title: 'Perfil' });

// ---- Acceso -------------------------------------------------------------------------
const DEMO = [
  ['cliente', 'CUSTOMER'], ['admin', 'ADMIN'], ['operador', 'ORDER_OPERATOR'],
  ['logistica', 'LOGISTICS_OPERATOR'], ['observador', 'OBSERVABILITY_VIEWER'],
];

route('/entrar', async (root, { query }) => {
  const nextPath = query.get('next');
  const safeNext = nextPath && nextPath.startsWith('/') && !nextPath.startsWith('//') ? nextPath : null;
  if (session.isAuthenticated()) { window.location.replace(safeNext || homeFor(session.user())); return; }
  const username = el('input', { autocomplete: 'username', required: true, maxlength: '32' });
  const password = el('input', { type: 'password', autocomplete: 'current-password', required: true });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block', text: 'Iniciar sesión' });
  const form = el('form', { class: 'card form', novalidate: true },
    el('h2', { class: 'card__title', text: 'Iniciar sesión' }), field('Usuario', username), field('Contraseña', password), submit, errorNode,
    el('p', { class: 'muted small' }, '¿No tienes cuenta? ', el('a', { href: '/registro.html', text: 'Regístrate como cliente' })));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const user = await submitting(submit, errorNode, () => session.login(username.value.trim(), password.value));
      toast(`Hola, ${user.displayName}`, 'ok');
      // Solo rutas internas: evita redirecciones abiertas a otros dominios.
      window.location.assign(safeNext ? link(safeNext) : homeFor(user));
    } catch { /* el error ya se muestra */ }
  });
  root.append(view(el('div', { class: 'auth' },
    el('div', { class: 'auth__aside' },
      el('img', { src: '/img/logo.svg', alt: '', width: '56', height: '56' }),
      el('h2', { text: 'Un acceso, cinco roles' }),
      el('p', { text: 'Cada rol ve su área y el servidor comprueba los permisos en cada petición.' }),
      el('div', { class: 'demo-accounts' }, DEMO.map(([user, role]) => el('button', { type: 'button', onclick: () => { username.value = user; password.focus(); } },
        el('span', {}, el('strong', { text: user }), el('small', { text: ` · ${ROLE_LABELS[role]}` })), el('small', { text: 'usar' })))),
      el('p', { class: 'muted small', style: null, text: 'Cuentas de demostración: la contraseña está en secrets\\demo_password.txt (generada en tu equipo, fuera de Git).' })),
    form)));
  username.focus();
}, { title: 'Iniciar sesión' });

route('/registro', async (root) => {
  const username = el('input', { autocomplete: 'username', maxlength: '32' });
  const displayName = el('input', { autocomplete: 'name', maxlength: '80' });
  const email = el('input', { type: 'email', autocomplete: 'email', maxlength: '254' });
  const password = el('input', { type: 'password', autocomplete: 'new-password', maxlength: '72' });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block', text: 'Crear cuenta' });
  const form = el('form', { class: 'card form', novalidate: true }, el('h2', { class: 'card__title', text: 'Crear cuenta de cliente' }),
    field('Usuario', username, '3 a 32 letras, dígitos, guion o guion bajo.'), field('Nombre visible', displayName),
    field('Correo electrónico', email), field('Contraseña', password, 'Entre 8 y 72 caracteres, con letras y dígitos.'), submit, errorNode,
    el('p', { class: 'muted small' }, '¿Ya tienes cuenta? ', el('a', { href: '/login.html', text: 'Inicia sesión' })));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await submitting(submit, errorNode, async () => {
        await authApi.register({ username: username.value.trim(), displayName: displayName.value.trim(), email: email.value.trim(), password: password.value });
        return session.login(username.value.trim(), password.value);
      }, 'Cuenta creada');
      window.location.assign('/cliente/dashboard.html');
    } catch { /* el error ya se muestra */ }
  });
  root.append(view(el('div', { class: 'auth' },
    el('div', { class: 'auth__aside' }, el('img', { src: '/img/logo.svg', alt: '', width: '56', height: '56' }),
      el('h2', { text: 'Tu cuenta HexaOrders' }),
      el('p', { text: 'Compra, sigue tus pedidos en tiempo real y gestiona tu perfil. La contraseña se guarda con BCrypt; nunca en texto plano.' })),
    form)));
  username.focus();
}, { title: 'Registro' });

export function showError(node, error) { showFormError(node, error); }
