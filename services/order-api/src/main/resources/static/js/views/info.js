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

const view = (...children) => el('section', { class: 'view view--store' }, ...children);
const head = (eyebrow, title, lead) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }), el('p', { class: 'section__lead', text: lead })));
const block = (id, title, ...content) => el('section', { class: 'info-block', id }, el('h2', { text: title }), ...content.flat());
const list = (items) => el('ul', {}, items.map((item) => el('li', { text: item })));
const p = (text) => el('p', { text });
const demoNote = (text) => el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: 'Demostración. ' }), text);

function infoPage(blocks) {
  const toc = el('nav', { class: 'info-toc', 'aria-label': 'En esta página' }, blocks.map((b) => el('a', { href: `#${b.id}`, text: b.querySelector('h2').textContent })));
  return el('div', { class: 'info-layout' }, toc, el('div', {}, blocks));
}

// ---- Empresa ------------------------------------------------------------------------------------
route('/empresa', async (root) => {
  root.append(view(
    head('Empresa', `Comprar en ${BRAND.displayName}`, `${BRAND.slogan} Canales de atención, compra segura, métodos de pago, tiendas y cobertura.`),
    infoPage([
      block('canales', 'Canales de atención',
        p(`Te atendemos ${CONTACT.hours.toLowerCase()}`),
        list([`Formulario de contacto: respuesta en menos de 24 horas hábiles.`, `Correo de soporte: ${CONTACT.supportEmail}`,
          `Correo corporativo: ${CONTACT.corporateEmail}`, `WhatsApp y teléfono: ${CONTACT.phone}`]),
        el('p', {}, el('a', { class: 'btn btn--primary btn--sm', href: '/contacto.html', text: 'Escríbenos' })),
        demoNote('Los teléfonos y correos son datos de ejemplo (dominio reservado example.com): no corresponden a líneas reales.')),
      block('compra-segura', 'Compra fácil y segura',
        list(['Precios en soles con IGV incluido: lo que ves en el carrito es lo que pagas.', 'Tu carrito se guarda en tu cuenta y lo recuperas en cualquier dispositivo.',
          'Cada pedido recibe un número (HX-…) y puedes seguir su estado paso a paso.', 'No pedimos ni guardamos datos de tarjetas en la web.',
          'Las sesiones están protegidas y cada compra se registra una sola vez aunque pulses dos veces «Confirmar».'])),
      block('metodos-pago', 'Métodos de pago',
        p('Disponibles en el checkout:'), list(PAYMENTS.available),
        p('Aún no integrados (no se aceptan por ahora):'), list(PAYMENTS.notIntegrated),
        p('El pago contra entrega no está disponible con recojo en tienda, y el pago en tienda solo se ofrece con recojo.')),
      block('tiendas', 'Nuestras tiendas',
        p(`Tienda principal y punto de recojo: ${CONTACT.city}. Horario: ${CONTACT.hours.toLowerCase()}.`),
        p('Al elegir «Recojo en tienda» en el checkout te avisamos por correo cuando tu pedido esté listo (normalmente en 24 horas hábiles).'),
        demoNote('La dirección exacta de la tienda no se publica en esta demostración.')),
      block('cobertura', 'Cobertura de delivery',
        list(['Lima Metropolitana: envío estándar de 2 a 4 días hábiles (S/ 15.00, gratis desde S/ 299).', 'Envío express en 24 a 48 horas (S/ 29.90).',
          'Provincias: envío estándar a todo el Perú; el plazo depende del destino.', 'Programamos una ventana de entrega y te la enviamos por correo.'])),
      block('disponibilidad', 'Disponibilidad de productos',
        p('El stock que ves en cada ficha es el del inventario en ese momento. Si un producto se agota mientras compras, el carrito te avisa antes de confirmar.'),
        list(['«En stock»: lo despachamos de inmediato.', '«Últimas unidades»: quedan pocas unidades.', '«Agotado»: puedes consultarnos por reposición desde el formulario de contacto.'])),
      block('redes', 'Redes sociales',
        p(`${BRAND.displayName} todavía no tiene cuentas oficiales en redes sociales. Cuando existan, los iconos del pie de página llevarán a ellas.`),
        list(SOCIAL.map((s) => `${s.label}: ${s.url ? s.url : 'próximamente'}`)),
        demoNote('No sigas cuentas que digan ser de esta tienda: en esta demostración no existe ninguna oficial.')),
    ])));
}, { title: 'Empresa' });

// ---- Legal --------------------------------------------------------------------------------------
route('/legal', async (root) => {
  root.append(view(
    head('Legal', 'Información legal', 'Términos, privacidad, cookies, garantías, cambios y devoluciones, y facturación.'),
    infoPage([
      block('terminos', 'Términos y condiciones',
        p(`Al comprar en ${BRAND.displayName} aceptas estas condiciones. Los precios incluyen IGV y pueden cambiar sin previo aviso; el precio válido es el que se muestra al confirmar el pedido.`),
        list(['Las promociones tienen vigencia y no se acumulan: se aplica el mayor descuento disponible.', 'Puedes cancelar un pedido mientras no haya sido despachado.',
          'Algunas compras pasan por una verificación de seguridad antes de prepararse.']),
        demoNote(`${LEGAL.note} Precios, stock y promociones de demostración: no se realizan ventas reales.`)),
      block('privacidad', 'Política de privacidad',
        p('Usamos tus datos (nombre, correo, dirección y teléfono) solo para procesar tus pedidos, coordinar entregas y responder tus consultas. No los vendemos ni los cedemos a terceros.'),
        p('Puedes actualizar tus datos desde «Mi cuenta → Perfil y seguridad» o pedirnos su eliminación desde el formulario de contacto.')),
      block('cookies', 'Política de cookies',
        p('Usamos solo almacenamiento técnico necesario: la cookie de sesión, la protección contra falsificación de solicitudes y, en tu navegador, el carrito del visitante y tus preferencias (sonido e intro).'),
        p('No usamos cookies publicitarias ni de seguimiento de terceros.')),
      block('devoluciones', 'Cambios y devoluciones',
        list(['Hasta 7 días desde la entrega por fallas de fábrica.', 'El producto debe conservar sus accesorios, manuales y empaque.',
          'Solicítalo desde el formulario de contacto indicando tu número de pedido (HX-…).'])),
      block('garantias', 'Garantías',
        p('Todos los productos son nuevos, sellados y con garantía oficial de marca de 12 a 36 meses, según se indica en su ficha. Te ayudamos a gestionarla con el fabricante o el servicio técnico autorizado.')),
      block('facturacion', 'Facturación',
        p('Emitimos boleta o factura electrónica con los datos de tu compra. Para factura, indica la razón social y el RUC de tu empresa en el formulario de contacto antes del despacho.'),
        demoNote('Esta demostración no emite comprobantes electrónicos reales.')),
    ])));
}, { title: 'Información legal' });

// ---- Libro de reclamaciones ----------------------------------------------------------------------
route('/libro-reclamaciones', async (root) => {
  const user = session.user();
  const input = (attrs) => el('input', { maxlength: '120', ...attrs });
  const name = input({ autocomplete: 'name', required: true, maxlength: '80', value: user?.displayName || '' });
  const email = input({ type: 'email', autocomplete: 'email', required: true, maxlength: '254' });
  const documentId = input({ inputmode: 'numeric', maxlength: '12', placeholder: 'DNI o CE' });
  const phone = input({ type: 'tel', autocomplete: 'tel', maxlength: '20' });
  const kind = el('select', {}, [['RECLAMO', 'Reclamo: disconformidad con un producto o servicio'], ['QUEJA', 'Queja: malestar con la atención']]
    .map(([value, text]) => el('option', { value, text })));
  const order = input({ placeholder: 'HX-…', maxlength: '20' });
  const amount = input({ inputmode: 'decimal', maxlength: '12', placeholder: 'S/' });
  const detail = el('textarea', { rows: '5', maxlength: '1200', required: true });
  const request = el('textarea', { rows: '3', maxlength: '400', required: true });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: 'Registrar hoja de reclamación' });
  const result = el('div', { 'aria-live': 'polite' });
  const full = (f) => { f.classList.add('field--full'); return f; };
  const form = el('form', { class: 'card form', novalidate: true, 'aria-labelledby': 'claim-title' },
    el('h2', { class: 'card__title', id: 'claim-title', text: 'Hoja de reclamación virtual' }),
    el('div', { class: 'form-grid' },
      field('Nombre completo', name), field('Correo electrónico', email), field('Documento de identidad', documentId), field('Teléfono', phone),
      full(field('Tipo', kind)), field('Número de pedido (opcional)', order), field('Monto reclamado (opcional)', amount),
      full(field('Detalle', detail, 'Describe qué ocurrió (mínimo 10 caracteres).')), full(field('Pedido del consumidor', request, '¿Qué solución esperas?'))),
    submit, errorNode, result);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const message = [
      `Tipo: ${kind.value}`, `Documento: ${documentId.value.trim() || '—'}`, `Teléfono: ${phone.value.trim() || '—'}`,
      `Pedido: ${order.value.trim() || '—'}`, `Monto: ${amount.value.trim() || '—'}`, '', `Detalle: ${detail.value.trim()}`, '', `Pedido del consumidor: ${request.value.trim()}`,
    ].join('\n').slice(0, 2000);
    if (detail.value.trim().length < 10 || request.value.trim().length < 3) {
      errorNode.hidden = false;
      errorNode.textContent = 'Completa el detalle (mínimo 10 caracteres) y lo que solicitas.';
      detail.focus();
      return;
    }
    try {
      const receipt = await submitting(submit, errorNode, () => contactApi.send({ name: name.value, email: email.value, topic: 'CLAIM', message }), 'Hoja de reclamación registrada');
      form.reset();
      result.replaceChildren(el('div', { class: 'notice-box notice-box--ok' }, el('strong', { text: `Hoja N.° ${String(receipt.id).padStart(6, '0')} registrada. ` }),
        'Te responderemos por correo en un plazo máximo de 15 días hábiles. Guarda este número para hacer seguimiento.'));
    } catch { /* el error ya se muestra en el formulario */ }
  });
  root.append(view(
    head('Atención al cliente', 'Libro de reclamaciones', 'Registra un reclamo o una queja sobre tu compra. Lo revisa nuestro equipo de atención.'),
    el('div', { class: 'contact-layout' }, form,
      el('aside', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title' }, icon('book'), ' ¿Reclamo o queja?'),
          el('p', { class: 'muted small', text: 'Reclamo: disconformidad con el producto o servicio adquirido. Queja: malestar o descontento con la atención recibida.' })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Proveedor' }),
          el('p', { class: 'small', text: `${BRAND.name} · Razón social: ${LEGAL.companyName || 'no configurada'} · RUC: ${LEGAL.taxId || 'no configurado'}` }),
          el('p', { class: 'muted small', text: LEGAL.address })),
        demoNote('Formulario de demostración: la hoja se guarda en el sistema y la ve el administrador, pero no sustituye a un Libro de Reclamaciones inscrito ante la autoridad.')))));
}, { title: 'Libro de reclamaciones' });
