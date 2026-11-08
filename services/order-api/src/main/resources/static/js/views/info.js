// POLYMER — páginas informativas enlazadas desde el pie: empresa (canales, compra segura, métodos de
// pago, tiendas, cobertura, disponibilidad, redes), legal (términos, privacidad, cookies, cambios y
// devoluciones, garantías, facturación) y Libro de reclamaciones (formulario real que se guarda en
// PostgreSQL con el tema CLAIM y que el administrador ve en su panel de mensajes).
// Los datos de empresa son de demostración y se leen de brand.js.

import { contactApi } from '../api.js';
import { BRAND, CONTACT, LEGAL, PAYMENTS, SOCIAL } from '../brand.js';
import { field, formError, icon, submitting } from '../components.js';
import { route } from '../router.js';
import { session } from '../session.js';
import { el } from '../ui.js';
import { t } from '../i18n.js';

const view = (...children) => el('section', { class: 'view view--store' }, ...children);
const head = (eyebrow, title, lead) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }), el('p', { class: 'section__lead', text: lead })));
const block = (id, title, ...content) => el('section', { class: 'info-block', id }, el('h2', { text: title }), ...content.flat());
const list = (items) => el('ul', {}, items.map((item) => el('li', { text: item })));
const p = (text) => el('p', { text });
const demoNote = (text) => el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: t('Demostración. ') }), text);

function infoPage(blocks) {
  const toc = el('nav', { class: 'info-toc', 'aria-label': t('En esta página') }, blocks.map((b) => el('a', { href: `#${b.id}`, text: b.querySelector('h2').textContent })));
  return el('div', { class: 'info-layout' }, toc, el('div', {}, blocks));
}

// ---- Empresa ------------------------------------------------------------------------------------
route('/empresa', async (root) => {
  root.append(view(
    head(t('Empresa'), t('Comprar en {0}', BRAND.displayName), t('{0} Canales de atención, compra segura, métodos de pago, tiendas y cobertura.', BRAND.slogan)),
    infoPage([
      block('canales', t('Canales de atención'),
        p(t('Te atendemos {0}', CONTACT.hours.toLowerCase())),
        list([t('Formulario de contacto: respuesta en menos de 24 horas hábiles.'), t('Correo de soporte: {0}', CONTACT.supportEmail),
          t('Correo corporativo: {0}', CONTACT.corporateEmail), t('WhatsApp y teléfono: {0}', CONTACT.phone)]),
        el('p', {}, el('a', { class: 'btn btn--primary btn--sm', href: '/contacto.html', text: t('Escríbenos') })),
        demoNote(t('Los teléfonos y correos son datos de ejemplo (dominio reservado example.com): no corresponden a líneas reales.'))),
      block('compra-segura', t('Compra fácil y segura'),
        list([t('Precios en soles con IGV incluido: lo que ves en el carrito es lo que pagas.'), t('Tu carrito se guarda en tu cuenta y lo recuperas en cualquier dispositivo.'),
          t('Cada pedido recibe un número (HX-…) y puedes seguir su estado paso a paso.'), t('No pedimos ni guardamos datos de tarjetas en la web.'),
          t('Las sesiones están protegidas y cada compra se registra una sola vez aunque pulses dos veces «Confirmar».')])),
      block('metodos-pago', t('Métodos de pago'),
        p(t('Disponibles en el checkout:')), list(PAYMENTS.available),
        p(t('Aún no integrados (no se aceptan por ahora):')), list(PAYMENTS.notIntegrated),
        p(t('El pago contra entrega no está disponible con recojo en tienda, y el pago en tienda solo se ofrece con recojo.'))),
      block('tiendas', t('Nuestras tiendas'),
        p(t('Tienda principal y punto de recojo: {0}. Horario: {1}.', CONTACT.city, CONTACT.hours.toLowerCase())),
        p(t('Al elegir «Recojo en tienda» en el checkout te avisamos por correo cuando tu pedido esté listo (normalmente en 24 horas hábiles).')),
        demoNote(t('La dirección exacta de la tienda no se publica en esta demostración.'))),
      block('cobertura', t('Cobertura de delivery'),
        list([t('Lima Metropolitana: envío estándar de 2 a 4 días hábiles (S/ 15.00, gratis desde S/ 299).'), t('Envío express en 24 a 48 horas (S/ 29.90).'),
          t('Provincias: envío estándar a todo el Perú; el plazo depende del destino.'), t('Programamos una ventana de entrega y te la enviamos por correo.')])),
      block('disponibilidad', t('Disponibilidad de productos'),
        p(t('El stock que ves en cada ficha es el del inventario en ese momento. Si un producto se agota mientras compras, el carrito te avisa antes de confirmar.')),
        list([t('«En stock»: lo despachamos de inmediato.'), t('«Últimas unidades»: quedan pocas unidades.'), t('«Agotado»: puedes consultarnos por reposición desde el formulario de contacto.')])),
      block('redes', t('Redes sociales'),
        p(t('{0} todavía no tiene cuentas oficiales en redes sociales. Cuando existan, los iconos del pie de página llevarán a ellas.', BRAND.displayName)),
        list(SOCIAL.map((s) => `${s.label}: ${s.url ? s.url : 'próximamente'}`)),
        demoNote(t('No sigas cuentas que digan ser de esta tienda: en esta demostración no existe ninguna oficial.'))),
    ])));
}, { title: t('Empresa') });

// ---- Legal --------------------------------------------------------------------------------------
route('/legal', async (root) => {
  root.append(view(
    head(t('Legal'), t('Información legal'), t('Términos, privacidad, cookies, garantías, cambios y devoluciones, y facturación.')),
    infoPage([
      block('terminos', t('Términos y condiciones'),
        p(t('Al comprar en {0} aceptas estas condiciones. Los precios incluyen IGV y pueden cambiar sin previo aviso; el precio válido es el que se muestra al confirmar el pedido.', BRAND.displayName)),
        list([t('Las promociones tienen vigencia y no se acumulan: se aplica el mayor descuento disponible.'), t('Puedes cancelar un pedido mientras no haya sido despachado.'),
          t('Algunas compras pasan por una verificación de seguridad antes de prepararse.')]),
        demoNote(t('{0} Precios, stock y promociones de demostración: no se realizan ventas reales.', LEGAL.note))),
      block('privacidad', t('Política de privacidad'),
        p(t('Usamos tus datos (nombre, correo, dirección y teléfono) solo para procesar tus pedidos, coordinar entregas y responder tus consultas. No los vendemos ni los cedemos a terceros.')),
        p(t('Puedes actualizar tus datos desde «Mi cuenta → Perfil y seguridad» o pedirnos su eliminación desde el formulario de contacto.'))),
      block('cookies', t('Política de cookies'),
        p(t('Usamos solo almacenamiento técnico necesario: la cookie de sesión, la protección contra falsificación de solicitudes y, en tu navegador, el carrito del visitante y tus preferencias (sonido e intro).')),
        p(t('No usamos cookies publicitarias ni de seguimiento de terceros.'))),
      block('devoluciones', t('Cambios y devoluciones'),
        list([t('Hasta 7 días desde la entrega por fallas de fábrica.'), t('El producto debe conservar sus accesorios, manuales y empaque.'),
          t('Solicítalo desde el formulario de contacto indicando tu número de pedido (HX-…).')])),
      block('garantias', t('Garantías'),
        p(t('Todos los productos son nuevos, sellados y con garantía oficial de marca de 12 a 36 meses, según se indica en su ficha. Te ayudamos a gestionarla con el fabricante o el servicio técnico autorizado.'))),
      block('facturacion', t('Facturación'),
        p(t('Emitimos boleta o factura electrónica con los datos de tu compra. Para factura, indica la razón social y el RUC de tu empresa en el formulario de contacto antes del despacho.')),
        demoNote(t('Esta demostración no emite comprobantes electrónicos reales.'))),
    ])));
}, { title: t('Información legal') });

// ---- Libro de reclamaciones ----------------------------------------------------------------------
route('/libro-reclamaciones', async (root) => {
  const user = session.user();
  const input = (attrs) => el('input', { maxlength: '120', ...attrs });
  const name = input({ autocomplete: 'name', required: true, maxlength: '80', value: user?.displayName || '' });
  const email = input({ type: 'email', autocomplete: 'email', required: true, maxlength: '254' });
  const documentId = input({ inputmode: 'numeric', maxlength: '12', placeholder: t('DNI o CE') });
  const phone = input({ type: 'tel', autocomplete: 'tel', maxlength: '20' });
  const kind = el('select', {}, [['RECLAMO', t('Reclamo: disconformidad con un producto o servicio')], ['QUEJA', t('Queja: malestar con la atención')]]
    .map(([value, text]) => el('option', { value, text })));
  const order = input({ placeholder: 'HX-…', maxlength: '20' });
  const amount = input({ inputmode: 'decimal', maxlength: '12', placeholder: 'S/' });
  const detail = el('textarea', { rows: '5', maxlength: '1200', required: true });
  const request = el('textarea', { rows: '3', maxlength: '400', required: true });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: t('Registrar hoja de reclamación') });
  const result = el('div', { 'aria-live': 'polite' });
  const full = (f) => { f.classList.add('field--full'); return f; };
  const form = el('form', { class: 'card form', novalidate: true, 'aria-labelledby': 'claim-title' },
    el('h2', { class: 'card__title', id: 'claim-title', text: t('Hoja de reclamación virtual') }),
    el('div', { class: 'form-grid' },
      field(t('Nombre completo'), name), field(t('Correo electrónico'), email), field(t('Documento de identidad'), documentId), field(t('Teléfono'), phone),
      full(field(t('Tipo'), kind)), field(t('Número de pedido (opcional)'), order), field(t('Monto reclamado (opcional)'), amount),
      full(field(t('Detalle'), detail, t('Describe qué ocurrió (mínimo 10 caracteres).'))), full(field(t('Pedido del consumidor'), request, t('¿Qué solución esperas?')))),
    submit, errorNode, result);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const message = [
      t('Tipo: {0}', kind.value), t('Documento: {0}', documentId.value.trim() || '—'), t('Teléfono: {0}', phone.value.trim() || '—'),
      t('Pedido: {0}', order.value.trim() || '—'), t('Monto: {0}', amount.value.trim() || '—'), '', t('Detalle: {0}', detail.value.trim()), '', t('Pedido del consumidor: {0}', request.value.trim()),
    ].join('\n').slice(0, 2000);
    if (detail.value.trim().length < 10 || request.value.trim().length < 3) {
      errorNode.hidden = false;
      errorNode.textContent = t('Completa el detalle (mínimo 10 caracteres) y lo que solicitas.');
      detail.focus();
      return;
    }
    try {
      const receipt = await submitting(submit, errorNode, () => contactApi.send({ name: name.value, email: email.value, topic: 'CLAIM', message }), t('Hoja de reclamación registrada'));
      form.reset();
      result.replaceChildren(el('div', { class: 'notice-box notice-box--ok' }, el('strong', { text: t('Hoja N.° {0} registrada. ', String(receipt.id).padStart(6, '0')) }),
        t('Te responderemos por correo en un plazo máximo de 15 días hábiles. Guarda este número para hacer seguimiento.')));
    } catch { /* el error ya se muestra en el formulario */ }
  });
  root.append(view(
    head(t('Atención al cliente'), t('Libro de reclamaciones'), t('Registra un reclamo o una queja sobre tu compra. Lo revisa nuestro equipo de atención.')),
    el('div', { class: 'contact-layout' }, form,
      el('aside', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title' }, icon('book'), t(' ¿Reclamo o queja?')),
          el('p', { class: 'muted small', text: t('Reclamo: disconformidad con el producto o servicio adquirido. Queja: malestar o descontento con la atención recibida.') })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Proveedor') }),
          el('p', { class: 'small', text: t('{0} · Razón social: {1} · RUC: {2}', BRAND.name, LEGAL.companyName || 'no configurada', LEGAL.taxId || 'no configurado') }),
          el('p', { class: 'muted small', text: LEGAL.address })),
        demoNote(t('Formulario de demostración: la hoja se guarda en el sistema y la ve el administrador, pero no sustituye a un Libro de Reclamaciones inscrito ante la autoridad.'))))));
}, { title: t('Libro de reclamaciones') });
