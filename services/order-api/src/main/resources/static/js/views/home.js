// Tienda: portada comercial, ofertas, centro de ayuda, nosotros, contacto y páginas de error.
// Cada sección de productos procede de la búsqueda real del catálogo (PostgreSQL).

import { catalogApi, contactApi } from '../api.js';
import { MAIN_CATEGORY_ICONS, emptyState, errorMessage, field, formError, icon, submitting } from '../components.js';
import { categoryTree, storeCategories } from '../layout.js';
import { route } from '../router.js';
import { homeFor, session } from '../session.js';
import { el, formatDateTime } from '../ui.js';
import { productCard, productRail, skeletonCards } from '../store-ui.js';

const view = (...children) => el('section', { class: 'view view--store' }, ...children);
const pageHead = (eyebrow, title, lead) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }),
    lead ? el('p', { class: 'section__lead', text: lead }) : null));

const bySlug = (list, slug) => list.find((c) => c.slug === slug);

/** Sección de productos que se rellena con una búsqueda real del catálogo. */
function liveRail(title, params, moreHref, lead) {
  const holder = el('div', {}, skeletonCards(4));
  catalogApi.search({ size: 8, available: 'true', ...params })
    .then((page) => holder.replaceChildren(productRail(title, page.items, moreHref, lead) || el('span')))
    .catch(() => holder.replaceChildren());
  return holder;
}

// ---- Portada ------------------------------------------------------------------------------------
route('/inicio', async (root) => {
  const categories = await storeCategories();
  const tree = categoryTree(categories);
  const id = (slug) => (bySlug(categories, slug) || {}).id;
  const promotions = await catalogApi.promotions().catch(() => []);
  const top = promotions[0];

  const hero = el('section', { class: 'hero-store' },
    el('div', { class: 'hero-store__main' },
      el('p', { class: 'hero-store__eyebrow' }, icon('sparkle'), top ? `${promotions.length} campañas activas · hasta ${Math.max(...promotions.map((p) => p.discountPercent))}% de descuento` : 'Tecnología para trabajar, jugar y crear'),
      el('h1', { class: 'hero-store__title', text: 'La tecnología que buscas, al mejor precio' }),
      el('p', { class: 'hero-store__lead', text: 'Laptops, componentes, monitores, smartphones, gaming y smart home de las mejores marcas, con garantía oficial y envío a todo el Perú.' }),
      el('div', { class: 'row-actions' },
        el('a', { class: 'btn btn--primary', href: '/ofertas.html' }, icon('percent'), ' Ver ofertas'),
        el('a', { class: 'btn btn--ghost', href: '/catalogo.html' }, 'Explorar catálogo'))),
    el('div', { class: 'hero-store__side' }, promotions.slice(0, 3).map((promo) => el('a', { class: 'promo-tile', href: `/ofertas.html#${promo.slug}` },
      el('strong', { class: 'promo-tile__off', text: `-${promo.discountPercent}%` }),
      el('span', { class: 'promo-tile__name', text: promo.name }),
      el('small', { text: `Hasta el ${formatDateTime(promo.endsAt).split(',')[0]}` })))));

  const categoryGrid = el('section', { class: 'rail' },
    el('div', { class: 'rail__head' }, el('h2', { class: 'rail__title', text: 'Compra por categoría' })),
    el('div', { class: 'category-grid' }, tree.map(({ main, subs }) => el('a', { class: 'category-tile', href: `/catalogo.html?categoria=${main.slug}` },
      el('span', { class: 'category-tile__icon' }, icon(MAIN_CATEGORY_ICONS[main.slug] || 'box')),
      el('strong', { text: main.name }), el('small', { text: subs.slice(0, 3).map((s) => s.name).join(' · ') })))));

  root.append(view(
    hero,
    categoryGrid,
    liveRail('Ofertas destacadas', { offers: 'true', sort: 'discount' }, '/ofertas.html', 'Los mayores descuentos de la semana.'),
    liveRail('Novedades tecnológicas', { sort: 'newest' }, '/catalogo.html?orden=newest', 'Lo último que llegó a nuestra tienda.'),
    liveRail('Lo más popular', { sort: 'popular' }, '/catalogo.html?orden=popular', 'Lo que más compran nuestros clientes.'),
    liveRail('Recomendados para ti', { status: 'FEATURED' }, '/catalogo.html?estado=FEATURED'),
    liveRail('Componentes para PC', { category: id('componentes'), sort: 'popular' }, '/catalogo.html?categoria=componentes', 'Procesadores, gráficas, memorias, almacenamiento y más.'),
    liveRail('Zona gaming', { category: id('gaming') }, '/catalogo.html?categoria=gaming', 'Consolas, periféricos y accesorios para jugar en serio.'),
    liveRail('Laptops', { category: id('computadoras') }, '/catalogo.html?categoria=computadoras'),
    liveRail('Smartphones y tablets', { category: id('smartphones-tablets') }, '/catalogo.html?categoria=smartphones-tablets'),
    liveRail('Dispositivos inteligentes', { category: id('smart-home') }, '/catalogo.html?categoria=smart-home', 'Focos, enchufes, cámaras y asistentes para tu hogar.'),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: '¿Necesitas ayuda para elegir?' }),
        el('p', { text: 'Nuestro equipo te asesora con compatibilidad de componentes, cotizaciones para empresas y garantías.' })),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/contacto.html', text: 'Escríbenos' }),
        el('a', { class: 'btn btn--ghost', href: '/ayuda.html', text: 'Centro de ayuda' })))));
}, { title: 'Inicio' });

// ---- Ofertas ------------------------------------------------------------------------------------
route('/ofertas', async (root) => {
  const [promotions, categories] = await Promise.all([catalogApi.promotions().catch(() => []), storeCategories()]);
  const name = (idValue) => (categories.find((c) => c.id === idValue) || {}).name;
  const grid = el('div', {}, skeletonCards(8));
  root.append(view(
    pageHead('Ofertas', 'Ofertas y promociones', 'Descuentos vigentes en tecnología. El precio final se calcula al momento y se respeta en el checkout.'),
    promotions.length ? el('div', { class: 'promo-list' }, promotions.map((promo) => el('article', { class: 'promo-card', id: promo.slug },
      el('div', { class: 'promo-card__off', text: `-${promo.discountPercent}%` }),
      el('div', { class: 'promo-card__body' }, el('h2', { text: promo.name }), el('p', { class: 'muted', text: promo.description }),
        el('p', { class: 'small' }, `Vigente hasta el ${formatDateTime(promo.endsAt)} · ${promo.products} productos`),
        el('div', { class: 'row-actions' }, promo.categoryIds.slice(0, 4).map((cid) => {
          const category = categories.find((c) => c.id === cid);
          return category ? el('a', { class: 'btn btn--ghost btn--sm', href: `/catalogo.html?categoria=${category.slug}&ofertas=1`, text: name(cid) }) : null;
        })))))) : el('div', { class: 'card' }, emptyState('No hay promociones vigentes', 'Vuelve pronto: publicamos nuevas campañas cada semana.', 'percent')),
    el('div', { class: 'rail__head' }, el('h2', { class: 'rail__title', text: 'Todos los productos en oferta' }),
      el('a', { class: 'rail__more', href: '/catalogo.html?ofertas=1' }, 'Filtrar ofertas', icon('chevron'))),
    grid));
  try {
    const page = await catalogApi.search({ offers: 'true', sort: 'discount', size: 24, available: 'true' });
    grid.replaceChildren(page.items.length ? el('div', { class: 'product-grid' }, page.items.map(productCard))
      : el('div', { class: 'card' }, emptyState('Sin ofertas disponibles', 'No hay productos en oferta con stock en este momento.', 'percent')));
  } catch (error) {
    grid.replaceChildren(el('div', { class: 'card' }, emptyState('No se pudieron cargar las ofertas', errorMessage(error))));
  }
}, { title: 'Ofertas' });

// ---- Centro de ayuda ----------------------------------------------------------------------------
const FAQ = [
  ['envios', 'Envíos y entregas', 'truck', [
    ['¿Cuánto cuesta el envío?', 'El envío estándar cuesta S/ 15.00 y es gratis en compras desde S/ 299. El envío express cuesta S/ 29.90. El recojo en tienda es gratuito.'],
    ['¿Cuánto tarda mi pedido?', 'Envío estándar: 2 a 4 días hábiles. Envío express: 24 a 48 horas. Recojo en tienda: listo en 24 horas hábiles; te avisamos por correo.'],
    ['¿Cómo sigo mi pedido?', 'En «Mi cuenta → Mis pedidos» verás el número de pedido (HX-…), su estado actualizado y la ventana de entrega programada.'],
  ]],
  ['pagos', 'Métodos de pago', 'card', [
    ['¿Qué métodos de pago aceptan?', 'Pago contra entrega (efectivo o tarjeta al recibir), transferencia bancaria y pago en tienda al recoger. No solicitamos datos de tarjeta en la web.'],
    ['¿Cuándo se despacha si pago por transferencia?', 'Te enviamos los datos de la cuenta por correo y despachamos cuando se confirma el abono.'],
    ['¿Los precios incluyen IGV?', 'Sí. Todos los precios están en soles (S/) e incluyen IGV. El precio que ves en el carrito es el que pagas.'],
  ]],
  ['garantias', 'Garantías y devoluciones', 'shield', [
    ['¿Los productos tienen garantía?', 'Todos los productos son nuevos y sellados, con garantía oficial de marca (12 a 36 meses según el producto, indicado en su ficha).'],
    ['¿Puedo devolver un producto?', 'Puedes solicitar el cambio o la devolución hasta 7 días después de recibirlo si presenta fallas de fábrica. Escríbenos desde «Contáctanos».'],
    ['¿Puedo cancelar mi pedido?', 'Sí, mientras no haya sido despachado. Desde el detalle del pedido pulsa «Cancelar pedido»; las unidades vuelven al inventario.'],
  ]],
  ['cuenta', 'Mi cuenta y compras', 'user', [
    ['¿Necesito una cuenta para comprar?', 'Puedes armar tu carrito sin cuenta. Para confirmar la compra inicia sesión o crea una cuenta: tu carrito se conserva.'],
    ['¿Mi carrito se guarda?', 'Sí. Con tu sesión iniciada el carrito se guarda en tu cuenta y lo encuentras en cualquier dispositivo.'],
    ['¿Venden a empresas?', 'Sí: cotizaciones por volumen, equipos empresariales y servidores. Elige «Ventas corporativas» en el formulario de contacto.'],
  ]],
];

route('/ayuda', async (root) => {
  root.append(view(
    pageHead('Ayuda', 'Centro de ayuda', 'Respuestas rápidas sobre envíos, pagos, garantías y tu cuenta.'),
    el('div', { class: 'help-topics' }, FAQ.map(([slug, title, ic]) => el('a', { class: 'help-topic', href: `#${slug}` }, icon(ic), el('strong', { text: title })))),
    FAQ.map(([slug, title, , questions]) => el('section', { class: 'faq', id: slug },
      el('h2', { class: 'faq__title', text: title }),
      questions.map(([question, answer]) => el('details', { class: 'faq__item' }, el('summary', { text: question }), el('p', { text: answer }))))),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: '¿No encontraste lo que buscabas?' }), el('p', { text: 'Escríbenos y te respondemos en menos de 24 horas hábiles.' })),
      el('a', { class: 'btn btn--primary', href: '/contacto.html', text: 'Contáctanos' }))));
}, { title: 'Centro de ayuda' });

// ---- Nosotros -----------------------------------------------------------------------------------
route('/nosotros', async (root) => {
  root.append(view(
    pageHead('Nosotros', 'Somos HexaOrders', 'Una tienda de tecnología hecha por especialistas, para que compres con confianza.'),
    el('div', { class: 'about-grid' }, [
      ['sparkle', 'Especialistas en tecnología', 'Solo vendemos tecnología: computadoras, componentes, gaming, redes, smart home y equipos empresariales. Te asesoramos con compatibilidad y rendimiento.'],
      ['shield', 'Productos originales', 'Trabajamos con marcas y distribuidores oficiales. Cada producto llega nuevo, sellado y con garantía de marca.'],
      ['truck', 'Entregas confiables', 'Preparamos tu pedido el mismo día y coordinamos la entrega en una ventana horaria. Puedes seguirlo en tiempo real.'],
      ['users', 'Atención cercana', 'Un equipo de operaciones y logística revisa cada pedido y te acompaña antes y después de la compra.'],
    ].map(([ic, title, text]) => el('article', { class: 'card about-card' }, el('span', { class: 'category-tile__icon' }, icon(ic)),
      el('h3', { text: title }), el('p', { class: 'muted', text })))),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: '¿Compras para tu empresa?' }), el('p', { text: 'Cotizaciones por volumen, facturación y equipos para oficinas, data centers y puntos de venta.' })),
      el('a', { class: 'btn btn--primary', href: '/contacto.html?tema=SALES', text: 'Solicitar cotización' }))));
}, { title: 'Nosotros' });

// ---- Contacto -----------------------------------------------------------------------------------
const TOPICS = [['ORDERS', 'Pedidos y envíos'], ['SUPPORT', 'Soporte técnico'], ['WARRANTY', 'Garantías y devoluciones'],
  ['SALES', 'Ventas corporativas'], ['OTHER', 'Otro tema']];

route('/contacto', async (root, { query }) => {
  const name = el('input', { autocomplete: 'name', maxlength: '80', required: true });
  const email = el('input', { type: 'email', autocomplete: 'email', maxlength: '254', required: true });
  const topic = el('select', {}, TOPICS.map(([value, text]) => el('option', { value, text })));
  const wanted = query.get('tema');
  if (TOPICS.some(([value]) => value === wanted)) topic.value = wanted;
  const message = el('textarea', { maxlength: '2000', rows: '6', required: true, placeholder: 'Cuéntanos en qué podemos ayudarte. Si es sobre un pedido, indica su número (HX-…).' });
  const user = session.user();
  if (user) name.value = user.displayName;
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: 'Enviar mensaje' });
  const result = el('div', { 'aria-live': 'polite' });
  const form = el('form', { class: 'card form', novalidate: true }, el('h2', { class: 'card__title', text: 'Escríbenos' }),
    el('div', { class: 'form-grid' }, field('Nombre', name), field('Correo electrónico', email), (() => { const f = field('Tema', topic); f.classList.add('field--full'); return f; })(),
      (() => { const f = field('Mensaje', message, 'Entre 10 y 2000 caracteres.'); f.classList.add('field--full'); return f; })()),
    submit, errorNode, result);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const receipt = await submitting(submit, errorNode, () => contactApi.send({ name: name.value, email: email.value, topic: topic.value, message: message.value }), 'Mensaje enviado');
      form.reset();
      if (user) name.value = user.displayName;
      result.replaceChildren(el('div', { class: 'notice-box notice-box--ok' }, el('strong', { text: 'Recibido. ' }),
        `Tu mensaje quedó registrado con el número ${receipt.id}. Te responderemos por correo en menos de 24 horas hábiles.`));
    } catch { /* el error ya se muestra en el formulario */ }
  });
  root.append(view(
    pageHead('Contacto', 'Contáctanos', 'Consultas de productos, pedidos, garantías o ventas para empresas.'),
    el('div', { class: 'contact-layout' }, form,
      el('aside', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Atención al cliente' }),
          el('p', { class: 'muted small', text: 'Lunes a sábado de 9:00 a 19:00.' }),
          el('p', { class: 'small', text: 'ventas@hexaorders.local · soporte@hexaorders.local' })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Antes de escribir' }),
          el('p', { class: 'muted small', text: 'Revisa el centro de ayuda: allí respondemos las dudas más frecuentes sobre envíos, pagos y garantías.' }),
          el('a', { class: 'btn btn--ghost btn--sm', href: '/ayuda.html', text: 'Centro de ayuda' })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Privacidad' }),
          el('p', { class: 'muted small', text: 'Solo usamos tus datos para responder a tu consulta.' }))))));
}, { title: 'Contacto' });

// ---- Errores ------------------------------------------------------------------------------------
function errorPage(code, title, text) {
  return async (root) => {
    const user = session.user();
    root.append(el('section', { class: 'view view--narrow' }, el('div', { class: 'card error-page' },
      el('p', { class: 'error-page__code gradient-text', text: code }), el('h1', { text: title }), el('p', { class: 'muted', text }),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/', text: 'Ir a la tienda' }),
        el('a', { class: 'btn btn--ghost', href: '/catalogo.html', text: 'Ver catálogo' }),
        user ? el('a', { class: 'btn btn--ghost', href: homeFor(user), text: 'Mi cuenta' }) : el('a', { class: 'btn btn--ghost', href: '/login.html', text: 'Iniciar sesión' })))));
  };
}

route('/error/403', errorPage('403', 'Acceso restringido', 'Tu cuenta no tiene permiso para abrir esta página.'), { title: 'Acceso restringido' });
route('/error/404', errorPage('404', 'Página no encontrada', 'La página que buscas no existe o cambió de dirección. Prueba con el buscador o vuelve a la tienda.'), { title: 'Página no encontrada' });
