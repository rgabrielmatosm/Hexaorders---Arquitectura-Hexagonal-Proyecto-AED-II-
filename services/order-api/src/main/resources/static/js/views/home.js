// Tienda: portada comercial, ofertas, centro de ayuda, nosotros, contacto y páginas de error.
// Cada sección de productos procede de la búsqueda real del catálogo (PostgreSQL).

import { catalogApi, contactApi } from '../api.js';
import { BRAND, CONTACT } from '../brand.js';
import { MAIN_CATEGORY_ICONS, emptyState, errorMessage, field, formError, icon, submitting } from '../components.js';
import { categoryTree, storeCategories } from '../layout.js';
import { route } from '../router.js';
import { homeFor, session } from '../session.js';
import { el, formatDateTime, reducedMotion } from '../ui.js';
import { visitSection } from '../store-info.js';
import { testimonialsSection } from '../testimonials.js';
import { productCard, productRail, skeletonCards } from '../store-ui.js';
import { t } from '../i18n.js';

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

/**
 * v7: video de fondo de la portada (pieza original de la marca, sin audio, en bucle). v9: sin botón de pausa
 * (a pedido); con «reducir movimiento» no se reproduce y se ve el póster. Se pausa solo cuando no está a la vista.
 */
function heroVideo() {
  const video = el('video', { class: 'hero-video__media', muted: true, loop: true, playsinline: true, preload: 'metadata',
    poster: '/media/polymer-hero.jpg', 'aria-hidden': 'true', 'data-testid': 'hero-video' },
  el('source', { src: '/media/polymer-hero.mp4', type: 'video/mp4' }));
  video.muted = true; // requisito de la reproducción automática en los navegadores
  if (!reducedMotion()) {
    video.play().catch(() => {});
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(([entry]) => { if (entry.isIntersecting) video.play().catch(() => {}); else video.pause(); }, { threshold: 0.1 }).observe(video);
    }
  }
  return el('div', { class: 'hero-video' }, video, el('div', { class: 'hero-video__shade', 'aria-hidden': 'true' }));
}

// ---- Portada ------------------------------------------------------------------------------------
route('/inicio', async (root) => {
  const categories = await storeCategories();
  const tree = categoryTree(categories);
  const id = (slug) => (bySlug(categories, slug) || {}).id;
  const promotions = await catalogApi.promotions().catch(() => []);
  const top = promotions[0];

  const hero = el('section', { class: 'hero-store hero-store--video' },
    heroVideo(),
    el('div', { class: 'hero-store__main' },
      el('p', { class: 'hero-store__eyebrow' }, icon('sparkle'), top ? t('{0} campañas activas · hasta {1}% de descuento', promotions.length, Math.max(...promotions.map((p) => p.discountPercent))) : t('Tecnología para trabajar, jugar y crear')),
      el('h1', { class: 'hero-store__title', text: t('La tecnología que buscas, al mejor precio') }),
      el('p', { class: 'hero-store__lead', text: t('Laptops, componentes, monitores, smartphones, gaming y smart home de las mejores marcas, con garantía oficial y envío a todo el Perú.') }),
      el('div', { class: 'row-actions' },
        el('a', { class: 'btn btn--primary', href: '/ofertas.html' }, icon('percent'), t(' Ver ofertas')),
        el('a', { class: 'btn btn--ghost', href: '/catalogo.html' }, t('Explorar catálogo')))),
    el('div', { class: 'hero-store__side' }, promotions.slice(0, 3).map((promo) => el('a', { class: 'promo-tile', href: `/ofertas.html#${promo.slug}` },
      el('strong', { class: 'promo-tile__off', text: `-${promo.discountPercent}%` }),
      el('span', { class: 'promo-tile__name', text: promo.name }),
      el('small', { text: t('Hasta el {0}', formatDateTime(promo.endsAt).split(',')[0]) })))));

  const categoryGrid = el('section', { class: 'rail' },
    el('div', { class: 'rail__head' }, el('h2', { class: 'rail__title', text: t('Compra por categoría') })),
    el('div', { class: 'category-grid' }, tree.map(({ main, subs }) => el('a', { class: 'category-tile', href: `/catalogo.html?categoria=${main.slug}` },
      el('span', { class: 'category-tile__icon' }, icon(MAIN_CATEGORY_ICONS[main.slug] || 'box')),
      el('strong', { text: main.name }), el('small', { text: subs.slice(0, 3).map((s) => s.name).join(' · ') })))));

  root.append(view(
    hero,
    categoryGrid,
    liveRail(t('Ofertas destacadas'), { offers: 'true', sort: 'discount' }, '/ofertas.html', t('Los mayores descuentos de la semana.')),
    liveRail(t('Novedades tecnológicas'), { sort: 'newest' }, '/catalogo.html?orden=newest', t('Lo último que llegó a nuestra tienda.')),
    liveRail(t('Lo más popular'), { sort: 'popular' }, '/catalogo.html?orden=popular', t('Lo que más compran nuestros clientes.')),
    liveRail(t('Recomendados para ti'), { status: 'FEATURED' }, '/catalogo.html?estado=FEATURED'),
    liveRail(t('Componentes para PC'), { category: id('componentes'), sort: 'popular' }, '/catalogo.html?categoria=componentes', t('Procesadores, gráficas, memorias, almacenamiento y más.')),
    liveRail(t('Zona gaming'), { category: id('gaming') }, '/catalogo.html?categoria=gaming', t('Consolas, periféricos y accesorios para jugar en serio.')),
    liveRail(t('Laptops'), { category: id('computadoras') }, '/catalogo.html?categoria=computadoras'),
    liveRail(t('Smartphones y tablets'), { category: id('smartphones-tablets') }, '/catalogo.html?categoria=smartphones-tablets'),
    liveRail(t('Dispositivos inteligentes'), { category: id('smart-home') }, '/catalogo.html?categoria=smart-home', t('Focos, enchufes, cámaras y asistentes para tu hogar.')),
    testimonialsSection(),
    visitSection(),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: t('¿Necesitas ayuda para elegir?') }),
        el('p', { text: t('Nuestro equipo te asesora con compatibilidad de componentes, cotizaciones para empresas y garantías.') })),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/contacto.html', text: t('Escríbenos') }),
        el('a', { class: 'btn btn--ghost', href: '/ayuda.html', text: t('Centro de ayuda') })))));
}, { title: t('Inicio') });

// ---- Ofertas ------------------------------------------------------------------------------------
route('/ofertas', async (root) => {
  const [promotions, categories] = await Promise.all([catalogApi.promotions().catch(() => []), storeCategories()]);
  const name = (idValue) => (categories.find((c) => c.id === idValue) || {}).name;
  const grid = el('div', {}, skeletonCards(8));
  root.append(view(
    pageHead(t('Ofertas'), t('Ofertas y promociones'), t('Descuentos vigentes en tecnología. El precio final se calcula al momento y se respeta en el checkout.')),
    promotions.length ? el('div', { class: 'promo-list' }, promotions.map((promo) => el('article', { class: 'promo-card', id: promo.slug },
      el('div', { class: 'promo-card__off', text: `-${promo.discountPercent}%` }),
      el('div', { class: 'promo-card__body' }, el('h2', { text: promo.name }), el('p', { class: 'muted', text: promo.description }),
        el('p', { class: 'small' }, t('Vigente hasta el {0} · {1} productos', formatDateTime(promo.endsAt), promo.products)),
        el('div', { class: 'row-actions' }, promo.categoryIds.slice(0, 4).map((cid) => {
          const category = categories.find((c) => c.id === cid);
          return category ? el('a', { class: 'btn btn--ghost btn--sm', href: `/catalogo.html?categoria=${category.slug}&ofertas=1`, text: name(cid) }) : null;
        })))))) : el('div', { class: 'card' }, emptyState(t('No hay promociones vigentes'), t('Vuelve pronto: publicamos nuevas campañas cada semana.'), 'percent')),
    el('div', { class: 'rail__head' }, el('h2', { class: 'rail__title', text: t('Todos los productos en oferta') }),
      el('a', { class: 'rail__more', href: '/catalogo.html?ofertas=1' }, t('Filtrar ofertas'), icon('chevron'))),
    grid));
  try {
    const page = await catalogApi.search({ offers: 'true', sort: 'discount', size: 24, available: 'true' });
    grid.replaceChildren(page.items.length ? el('div', { class: 'product-grid' }, page.items.map(productCard))
      : el('div', { class: 'card' }, emptyState(t('Sin ofertas disponibles'), t('No hay productos en oferta con stock en este momento.'), 'percent')));
  } catch (error) {
    grid.replaceChildren(el('div', { class: 'card' }, emptyState(t('No se pudieron cargar las ofertas'), errorMessage(error))));
  }
}, { title: t('Ofertas') });

// ---- Centro de ayuda ----------------------------------------------------------------------------
const FAQ = [
  ['envios', t('Envíos y entregas'), 'truck', [
    [t('¿Cuánto cuesta el envío?'), t('El envío estándar cuesta S/ 15.00 y es gratis en compras desde S/ 299. El envío express cuesta S/ 29.90. El recojo en tienda es gratuito.')],
    [t('¿Cuánto tarda mi pedido?'), t('Envío estándar: 2 a 4 días hábiles. Envío express: 24 a 48 horas. Recojo en tienda: listo en 24 horas hábiles; te avisamos por correo.')],
    [t('¿Cómo sigo mi pedido?'), t('En «Mi cuenta → Mis pedidos» verás el número de pedido (HX-…), su estado actualizado y la ventana de entrega programada.')],
  ]],
  ['pagos', t('Métodos de pago'), 'card', [
    [t('¿Qué métodos de pago aceptan?'), t('En línea: tarjeta de crédito o débito (aprobación inmediata), Yape y Plin (código QR que vence en 60 segundos). Al recibir: pago contra entrega, transferencia bancaria o pago en tienda al recoger. Al pagar en línea recibes la boleta en tu correo.')],
    [t('¿Es seguro pagar con tarjeta?'), t('No guardamos el número ni el CVV de tu tarjeta: solo la marca y los 4 últimos dígitos. En esta tienda de demostración las pasarelas son de prueba y no se cobra nada.')],
    [t('¿Cuándo se despacha si pago por transferencia?'), t('Te enviamos los datos de la cuenta por correo y despachamos cuando se confirma el abono.')],
    [t('¿Los precios incluyen IGV?'), t('Sí. Todos los precios están en soles (S/) e incluyen IGV. El precio que ves en el carrito es el que pagas.')],
  ]],
  ['garantias', t('Garantías y devoluciones'), 'shield', [
    [t('¿Los productos tienen garantía?'), t('Todos los productos son nuevos y sellados, con garantía oficial de marca (12 a 36 meses según el producto, indicado en su ficha).')],
    [t('¿Puedo devolver un producto?'), t('Puedes solicitar el cambio o la devolución hasta 7 días después de recibirlo si presenta fallas de fábrica. Escríbenos desde «Contáctanos».')],
    [t('¿Puedo cancelar mi pedido?'), t('Sí, mientras no haya sido despachado. Desde el detalle del pedido pulsa «Cancelar pedido»; las unidades vuelven al inventario.')],
  ]],
  ['cuenta', t('Mi cuenta y compras'), 'user', [
    [t('¿Necesito una cuenta para comprar?'), t('Puedes armar tu carrito sin cuenta. Para confirmar la compra inicia sesión o crea una cuenta: tu carrito se conserva.')],
    [t('¿Mi carrito se guarda?'), t('Sí. Con tu sesión iniciada el carrito se guarda en tu cuenta y lo encuentras en cualquier dispositivo.')],
    [t('¿Venden a empresas?'), t('Sí: cotizaciones por volumen, equipos empresariales y servidores. Elige «Ventas corporativas» en el formulario de contacto.')],
  ]],
];

route('/ayuda', async (root) => {
  root.append(view(
    pageHead(t('Ayuda'), t('Centro de ayuda'), t('Respuestas rápidas sobre envíos, pagos, garantías y tu cuenta.')),
    el('div', { class: 'help-topics' }, FAQ.map(([slug, title, ic]) => el('a', { class: 'help-topic', href: `#${slug}` }, icon(ic), el('strong', { text: title })))),
    FAQ.map(([slug, title, , questions]) => el('section', { class: 'faq', id: slug },
      el('h2', { class: 'faq__title', text: title }),
      questions.map(([question, answer]) => el('details', { class: 'faq__item' }, el('summary', { text: question }), el('p', { text: answer }))))),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: t('¿No encontraste lo que buscabas?') }), el('p', { text: t('Escríbenos y te respondemos en menos de 24 horas hábiles.') })),
      el('a', { class: 'btn btn--primary', href: '/contacto.html', text: t('Contáctanos') }))));
}, { title: t('Centro de ayuda') });

// ---- Nosotros -----------------------------------------------------------------------------------
route('/nosotros', async (root) => {
  root.append(view(
    pageHead(t('Nosotros'), t('Somos {0}', BRAND.displayName), t('{0} Una tienda de tecnología hecha por especialistas, para que compres con confianza.', BRAND.slogan)),
    el('div', { class: 'about-grid' }, [
      ['sparkle', t('Especialistas en tecnología'), t('Solo vendemos tecnología: computadoras, componentes, gaming, redes, smart home y equipos empresariales. Te asesoramos con compatibilidad y rendimiento.')],
      ['shield', t('Productos originales'), t('Trabajamos con marcas y distribuidores oficiales. Cada producto llega nuevo, sellado y con garantía de marca.')],
      ['truck', t('Entregas confiables'), t('Preparamos tu pedido el mismo día y coordinamos la entrega en una ventana horaria. Puedes seguirlo en tiempo real.')],
      ['users', t('Atención cercana'), t('Un equipo de operaciones y logística revisa cada pedido y te acompaña antes y después de la compra.')],
    ].map(([ic, title, text]) => el('article', { class: 'card about-card' }, el('span', { class: 'category-tile__icon' }, icon(ic)),
      el('h3', { text: title }), el('p', { class: 'muted', text })))),
    el('section', { class: 'info-block', id: 'historia' },
      el('h2', { text: t('Nuestra historia') }),
      el('p', { text: t('{0} nació en {1} con una idea simple: que comprar tecnología sea tan fácil como usarla. Empezamos asesorando a estudiantes y profesionales que armaban su primera PC, y hoy ofrecemos más de 500 productos de 20 categorías.', BRAND.displayName, CONTACT.city) }),
      el('p', { text: t('Nuestro nombre viene de los polímeros: cadenas de piezas pequeñas que, unidas, forman algo más fuerte. Así vemos la tecnología y a nuestros clientes: conectados.') }),
      el('p', { class: 'small muted', text: t('Proyecto de demostración: la historia y los datos de la empresa son ilustrativos.') })),
    testimonialsSection({ withForm: true }),
    el('section', { class: 'help-banner' },
      el('div', {}, el('h2', { text: t('¿Compras para tu empresa?') }), el('p', { text: t('Cotizaciones por volumen, facturación y equipos para oficinas, data centers y puntos de venta.') })),
      el('a', { class: 'btn btn--primary', href: '/contacto.html?tema=SALES', text: t('Solicitar cotización') }))));
}, { title: t('Nosotros') });

// ---- Contacto -----------------------------------------------------------------------------------
const TOPICS = [['ORDERS', t('Pedidos y envíos')], ['SUPPORT', t('Soporte técnico')], ['WARRANTY', t('Garantías y devoluciones')],
  ['SALES', t('Ventas corporativas')], ['OTHER', t('Otro tema')]];

route('/contacto', async (root, { query }) => {
  const name = el('input', { autocomplete: 'name', maxlength: '80', required: true });
  const email = el('input', { type: 'email', autocomplete: 'email', maxlength: '254', required: true });
  const topic = el('select', {}, TOPICS.map(([value, text]) => el('option', { value, text })));
  const wanted = query.get('tema');
  if (TOPICS.some(([value]) => value === wanted)) topic.value = wanted;
  const message = el('textarea', { maxlength: '2000', rows: '6', required: true, placeholder: t('Cuéntanos en qué podemos ayudarte. Si es sobre un pedido, indica su número (HX-…).') });
  const user = session.user();
  if (user) name.value = user.displayName;
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: t('Enviar mensaje') });
  const result = el('div', { 'aria-live': 'polite' });
  const form = el('form', { class: 'card form', novalidate: true }, el('h2', { class: 'card__title', text: t('Escríbenos') }),
    el('div', { class: 'form-grid' }, field(t('Nombre'), name), field(t('Correo electrónico'), email), (() => { const f = field(t('Tema'), topic); f.classList.add('field--full'); return f; })(),
      (() => { const f = field(t('Mensaje'), message, t('Entre 10 y 2000 caracteres.')); f.classList.add('field--full'); return f; })()),
    submit, errorNode, result);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const receipt = await submitting(submit, errorNode, () => contactApi.send({ name: name.value, email: email.value, topic: topic.value, message: message.value }), t('Mensaje enviado'));
      form.reset();
      if (user) name.value = user.displayName;
      result.replaceChildren(el('div', { class: 'notice-box notice-box--ok' }, el('strong', { text: t('Recibido. ') }),
        t('Tu mensaje quedó registrado con el número {0}. Te responderemos por correo en menos de 24 horas hábiles.', receipt.id)));
    } catch { /* el error ya se muestra en el formulario */ }
  });
  root.append(view(
    pageHead(t('Contacto'), t('Contáctanos'), t('Consultas de productos, pedidos, garantías o ventas para empresas.')),
    el('div', { class: 'contact-layout' }, form,
      el('aside', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Atención al cliente') }),
          el('p', { class: 'muted small', text: CONTACT.hours }),
          el('p', { class: 'small' }, el('a', { href: `mailto:${CONTACT.supportEmail}`, text: CONTACT.supportEmail }), ' · ',
            el('a', { href: `mailto:${CONTACT.corporateEmail}`, text: CONTACT.corporateEmail })),
          el('p', { class: 'muted small', text: t('WhatsApp y teléfono: {0} (dato de demostración)', CONTACT.phone) }),
          el('a', { class: 'btn btn--ghost btn--sm', href: '/libro-reclamaciones.html', text: t('Libro de reclamaciones') })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Antes de escribir') }),
          el('p', { class: 'muted small', text: t('Revisa el centro de ayuda: allí respondemos las dudas más frecuentes sobre envíos, pagos y garantías.') }),
          el('a', { class: 'btn btn--ghost btn--sm', href: '/ayuda.html', text: t('Centro de ayuda') })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Privacidad') }),
          el('p', { class: 'muted small', text: t('Solo usamos tus datos para responder a tu consulta.') })))),
    visitSection({ title: t('Nuestra tienda'), lead: t('Visítanos, recoge tu pedido o prueba los equipos antes de comprar.') })));
  if (window.location.hash === '#visitanos') window.setTimeout(() => document.getElementById('visitanos')?.scrollIntoView(), 300);
}, { title: t('Contacto') });

// ---- Errores ------------------------------------------------------------------------------------
function errorPage(code, title, text) {
  return async (root) => {
    const user = session.user();
    root.append(el('section', { class: 'view view--narrow' }, el('div', { class: 'card error-page' },
      el('p', { class: 'error-page__code gradient-text', text: code }), el('h1', { text: title }), el('p', { class: 'muted', text }),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/', text: t('Ir a la tienda') }),
        el('a', { class: 'btn btn--ghost', href: '/catalogo.html', text: t('Ver catálogo') }),
        user ? el('a', { class: 'btn btn--ghost', href: homeFor(user), text: t('Mi cuenta') }) : el('a', { class: 'btn btn--ghost', href: '/login.html', text: t('Iniciar sesión') })))));
  };
}

route('/error/403', errorPage('403', t('Acceso restringido'), t('Tu cuenta no tiene permiso para abrir esta página.')), { title: t('Acceso restringido') });
route('/error/404', errorPage('404', t('Página no encontrada'), t('La página que buscas no existe o cambió de dirección. Prueba con el buscador o vuelve a la tienda.')), { title: t('Página no encontrada') });
