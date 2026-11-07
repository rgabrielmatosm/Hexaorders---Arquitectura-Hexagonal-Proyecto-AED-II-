// Estructura común del sitio: cabecera comercial (buscador, cuenta, carrito), megamenú de categorías,
// accesos rápidos, barra lateral de cada portal del personal, breadcrumbs y pie de página.
// Cada página HTML solo declara su sección y su título; este módulo genera la navegación a partir de
// esa información, de las categorías reales del catálogo y de la sesión.

import { catalogApi } from './api.js';
import { onSoundChange, play, setSound, soundEnabled } from './audio.js';
import { BRAND, CONTACT, LEGAL, PAYMENTS, SOCIAL, SOCIAL_FALLBACK } from './brand.js';
import { cart } from './cart.js';
import { MAIN_CATEGORY_ICONS, icon } from './components.js';
import { ROLE_LABELS, session } from './session.js';
import { $, $$, el, toast } from './ui.js';

/** Accesos rápidos de la tienda (debajo del buscador). */
export const MAIN_NAV = [
  ['/ofertas.html', 'Ofertas'],
  ['/catalogo.html?orden=newest', 'Novedades'],
  ['/catalogo.html?categoria=computadoras', 'Laptops y PC'],
  ['/catalogo.html?categoria=componentes', 'Componentes'],
  ['/catalogo.html?categoria=gaming', 'Gaming'],
  ['/catalogo.html?categoria=smartphones-tablets', 'Smartphones'],
  ['/catalogo.html?categoria=smart-home', 'Smart Home'],
  ['/ayuda.html', 'Ayuda'],
];

/** Portal de cada rol (ADMIN entra en todos menos en el del cliente). */
export const AREAS = [
  ['CUSTOMER', '/cliente/dashboard.html', 'Mi cuenta', 'user'],
  ['ADMIN', '/admin/dashboard.html', 'Administración', 'grid'],
  ['ORDER_OPERATOR', '/operaciones/dashboard.html', 'Operaciones', 'list'],
  ['LOGISTICS_OPERATOR', '/logistica/dashboard.html', 'Logística', 'truck'],
  ['OBSERVABILITY_VIEWER', '/monitoreo/dashboard.html', 'Monitoreo', 'activity'],
];

/** Barra lateral de cada portal. La autorización real la aplica Spring Security. */
export const SECTIONS = {
  cliente: { label: 'Mi cuenta', home: '/cliente/dashboard.html', pages: [
    ['/cliente/dashboard.html', 'Resumen', 'home'], ['/cliente/pedidos.html', 'Mis pedidos', 'box'],
    ['/carrito.html', 'Mi carrito', 'cart'], ['/cliente/perfil.html', 'Perfil y seguridad', 'user'],
    ['/ayuda.html', 'Centro de ayuda', 'help'], ['/catalogo.html', 'Seguir comprando', 'tag']] },
  admin: { label: 'Administración', home: '/admin/dashboard.html', pages: [
    ['/admin/dashboard.html', 'Panel', 'grid'], ['/admin/productos.html', 'Productos', 'box'],
    ['/admin/categorias.html', 'Categorías', 'tag'], ['/admin/inventario.html', 'Inventario', 'layers'],
    ['/admin/promociones.html', 'Promociones', 'percent'], ['/admin/pedidos.html', 'Pedidos', 'list'],
    ['/admin/usuarios.html', 'Usuarios y roles', 'users'], ['/admin/incidencias.html', 'Incidencias', 'alert'],
    ['/admin/reportes.html', 'Reportes', 'chart'], ['/admin/auditoria.html', 'Auditoría', 'file']] },
  operaciones: { label: 'Operaciones', home: '/operaciones/dashboard.html', pages: [
    ['/operaciones/dashboard.html', 'Resumen', 'grid'], ['/operaciones/pedidos.html', 'Bandeja de pedidos', 'list'],
    ['/operaciones/revisiones.html', 'Revisiones de riesgo', 'shield'], ['/operaciones/incidencias.html', 'Incidencias', 'alert']] },
  logistica: { label: 'Logística', home: '/logistica/dashboard.html', pages: [
    ['/logistica/dashboard.html', 'Resumen', 'grid'], ['/logistica/calendario.html', 'Calendario', 'calendar'],
    ['/logistica/entregas.html', 'Entregas', 'truck'], ['/logistica/asignaciones.html', 'Asignaciones', 'users']] },
  monitoreo: { label: 'Monitoreo', home: '/monitoreo/dashboard.html', pages: [
    ['/monitoreo/dashboard.html', 'Resumen', 'activity'], ['/monitoreo/servicios.html', 'Servicios', 'server'],
    ['/monitoreo/metricas.html', 'Métricas', 'chart'], ['/monitoreo/alertas.html', 'Alertas', 'alert'],
    ['/arquitectura/overview.html', 'Documentación técnica', 'hexagon']] },
  // Documentación técnica interna (personal autorizado): no forma parte de la tienda.
  arquitectura: { label: 'Documentación técnica', home: '/arquitectura/overview.html', pages: [
    ['/arquitectura/overview.html', 'Visión general', 'hexagon'], ['/arquitectura/flujo-pedidos.html', 'Flujo de pedidos', 'route'],
    ['/laboratorio.html', 'Laboratorio de la API', 'bolt'], ['/monitoreo/dashboard.html', 'Volver a monitoreo', 'activity']] },
};

const here = () => (window.location.pathname === '/index.html' ? '/' : window.location.pathname);

function areasFor() {
  return AREAS.filter(([role]) => session.has(role) || (role !== 'CUSTOMER' && session.has('ADMIN')));
}

function setActive(link, active) {
  link.classList.toggle('is-active', active);
  if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
}

// ---- Categorías (para el megamenú y el pie) --------------------------------------------------
const CATEGORY_CACHE = 'hexaorders.categories.v6';
let categoriesPromise = null;

/** Categorías visibles de la tienda, en memoria de la pestaña durante 5 minutos. */
export function storeCategories() {
  if (!categoriesPromise) {
    categoriesPromise = (async () => {
      try {
        const cached = JSON.parse(sessionStorage.getItem(CATEGORY_CACHE) || 'null');
        if (cached && Date.now() - cached.at < 5 * 60 * 1000) return cached.list;
      } catch { /* sin almacenamiento */ }
      const list = await catalogApi.categories();
      try { sessionStorage.setItem(CATEGORY_CACHE, JSON.stringify({ at: Date.now(), list })); } catch { /* sin almacenamiento */ }
      return list;
    })().catch(() => []);
  }
  return categoriesPromise;
}

/** Árbol: [{ main, subs: [] }] en el orden de la tienda. */
export function categoryTree(list) {
  const mains = list.filter((c) => c.parentId === null).sort((a, b) => a.position - b.position);
  return mains.map((main) => ({ main, subs: list.filter((c) => c.parentId === main.id).sort((a, b) => a.position - b.position) }));
}

// ---- Cabecera ----------------------------------------------------------------------------------
function accountMenu() {
  const user = session.user();
  if (!user) {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    return el('a', { class: 'store-action', href: `/login.html${window.location.pathname === '/login.html' ? '' : `?next=${next}`}` },
      icon('user'), el('span', { class: 'store-action__text' }, el('small', { text: 'Hola, inicia sesión' }), el('strong', { text: 'Mi cuenta' })));
  }
  const panel = el('div', { class: 'menu__panel', hidden: true, role: 'menu' },
    el('div', { class: 'menu__meta' }, el('strong', { text: user.displayName }),
      el('small', { text: `@${user.username} · ${user.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}` })),
    ...areasFor().map(([, href, label]) => el('a', { href, role: 'menuitem', text: label })),
    session.has('CUSTOMER') ? el('a', { href: '/cliente/pedidos.html', role: 'menuitem', text: 'Mis pedidos' }) : null,
    el('a', { href: '/cliente/perfil.html', role: 'menuitem', text: 'Perfil y seguridad' }),
    el('button', { type: 'button', role: 'menuitem', text: 'Cerrar sesión', onclick: async () => {
      await session.logout();
      toast('Sesión cerrada', 'ok');
      window.location.assign('/');
    } }));
  const toggle = el('button', { type: 'button', class: 'store-action user-chip', 'aria-haspopup': 'menu', 'aria-expanded': 'false',
    'aria-label': `Cuenta de ${user.displayName}` },
  icon('user'), el('span', { class: 'store-action__text' }, el('small', { text: `Hola, ${user.displayName.split(' ')[0]}` }),
    el('strong', { class: 'user-chip__name', text: 'Mi cuenta' })));
  const close = () => { panel.hidden = true; toggle.setAttribute('aria-expanded', 'false'); };
  toggle.addEventListener('click', (event) => {
    event.stopPropagation();
    panel.hidden = !panel.hidden;
    toggle.setAttribute('aria-expanded', String(!panel.hidden));
  });
  document.addEventListener('click', close);
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') close(); });
  return el('div', { class: 'menu' }, toggle, panel);
}

function searchForm() {
  const params = new URLSearchParams(window.location.search);
  const input = el('input', { type: 'search', name: 'q', id: 'store-search', placeholder: 'Busca laptops, procesadores, monitores, marcas o modelos…',
    autocomplete: 'off', maxlength: '80', value: window.location.pathname === '/catalogo.html' ? (params.get('q') || '') : '',
    'aria-label': 'Buscar productos' });
  const form = el('form', { class: 'store-search', role: 'search', action: '/catalogo.html', method: 'get' },
    input, el('button', { type: 'submit', class: 'store-search__go', 'aria-label': 'Buscar' }, icon('search')));
  form.addEventListener('submit', (event) => {
    if (!input.value.trim()) { event.preventDefault(); input.focus(); }
  });
  return form;
}

function megaMenu() {
  const button = el('button', { type: 'button', class: 'mega__toggle', 'aria-expanded': 'false', 'aria-controls': 'mega-panel' },
    icon('menu'), el('span', { text: 'Categorías' }));
  const mains = el('ul', { class: 'mega__mains', role: 'list' });
  const detail = el('div', { class: 'mega__detail' });
  const panel = el('div', { class: 'mega__panel', id: 'mega-panel', hidden: true }, mains, detail);
  const show = (node) => {
    $$('.mega__main', mains).forEach((n) => n.classList.toggle('is-active', n === node.parent));
    detail.replaceChildren(node.content);
  };
  storeCategories().then((list) => {
    const tree = categoryTree(list);
    tree.forEach(({ main, subs }, index) => {
      const content = el('div', { class: 'mega__group' },
        el('a', { class: 'mega__title', href: `/catalogo.html?categoria=${main.slug}` }, el('strong', { text: main.name }), el('span', { text: 'Ver todo' })),
        el('ul', { class: 'mega__subs' }, subs.map((sub) => el('li', {}, el('a', { href: `/catalogo.html?categoria=${sub.slug}`, text: sub.name })))));
      const link = el('a', { class: 'mega__main', href: `/catalogo.html?categoria=${main.slug}` },
        icon(MAIN_CATEGORY_ICONS[main.slug] || 'box'), el('span', { text: main.name }), icon('chevron', 'mega__chev'));
      const item = el('li', {}, link);
      const node = { parent: link, content };
      link.addEventListener('mouseenter', () => show(node));
      link.addEventListener('focus', () => show(node));
      mains.append(item);
      if (index === 0) show(node);
    });
  });
  const close = () => { panel.hidden = true; button.setAttribute('aria-expanded', 'false'); };
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    panel.hidden = !panel.hidden;
    button.setAttribute('aria-expanded', String(!panel.hidden));
  });
  panel.addEventListener('click', (event) => event.stopPropagation());
  document.addEventListener('click', close);
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') close(); });
  return el('div', { class: 'mega' }, button, panel);
}

/** Logotipo horizontal de POLYMER (SVG generado por tools/generate_brand.py). */
function brandLink(height = 34) {
  return el('a', { class: 'brand brand--logo', href: '/', 'aria-label': `${BRAND.name}, inicio` },
    el('img', { class: 'brand__lockup', src: BRAND.logo, alt: BRAND.name, height: String(height), width: String(Math.round(height * 3.8)) }),
    el('img', { class: 'brand__mark', src: BRAND.mark, alt: '', height: String(height), width: String(height) }));
}

/** Silencio global de los sonidos de la interfaz (apagados por defecto). */
function soundToggle() {
  const button = el('button', { type: 'button', class: 'icon-btn sound-toggle', id: 'sound-toggle' });
  const paint = (on) => {
    button.replaceChildren(icon(on ? 'volume' : 'volumeOff'));
    button.setAttribute('aria-pressed', String(on));
    button.setAttribute('aria-label', on ? 'Sonidos activados: pulsa para silenciar' : 'Sonidos silenciados: pulsa para activarlos');
    button.title = on ? 'Silenciar sonidos' : 'Activar sonidos';
  };
  paint(soundEnabled());
  button.addEventListener('click', () => {
    const on = !soundEnabled();
    setSound(on);
    if (on) play('notify', { force: true });
  });
  onSoundChange(paint);
  return button;
}

function renderHeader() {
  const header = $('#site-header');
  if (!header) return;
  const current = window.location.pathname + window.location.search;
  const count = cart.count();
  const cartLink = el('a', { class: 'store-action store-action--cart', href: '/carrito.html', id: 'cart-button', 'aria-label': `Carrito: ${count} unidades` },
    el('span', { class: 'store-action__icon' }, icon('cart'), el('span', { class: 'icon-btn__badge', id: 'cart-count', hidden: count === 0, text: String(count) })),
    el('span', { class: 'store-action__text' }, el('small', { text: 'Tu' }), el('strong', { text: 'Carrito' })));
  const burger = el('button', { type: 'button', class: 'icon-btn nav-toggle', 'aria-label': 'Abrir menú', 'aria-expanded': 'false', 'aria-controls': 'primary-nav' }, icon('menu'));
  const quick = MAIN_NAV.map(([href, label]) => {
    const link = el('a', { href, text: label });
    setActive(link, current === href || (href === '/ofertas.html' && here() === '/ofertas.html') || (href === '/ayuda.html' && here() === '/ayuda.html'));
    return link;
  });
  const areaLinks = areasFor().filter(([role]) => role !== 'CUSTOMER').map(([, href, label]) => {
    const link = el('a', { href, text: label, class: 'nav__area' });
    const section = SECTIONS[document.body.dataset.section || ''];
    setActive(link, Boolean(section) && section.home === href);
    return link;
  });
  const nav = el('nav', { class: 'nav store-nav__links', id: 'primary-nav', 'aria-label': 'Navegación principal' },
    el('a', { href: '/', text: 'Inicio', class: here() === '/' ? 'is-active' : '' }), ...quick,
    areaLinks.length ? el('span', { class: 'nav__group', 'aria-label': 'Áreas del personal' }, areaLinks) : null);
  burger.addEventListener('click', () => {
    const open = !header.classList.contains('is-open');
    header.classList.toggle('is-open', open);
    burger.setAttribute('aria-expanded', String(open));
    burger.setAttribute('aria-label', open ? 'Cerrar menú' : 'Abrir menú');
  });
  header.classList.add('store-header');
  header.replaceChildren(
    el('div', { class: 'store-strip' },
      el('span', {}, icon('truck'), ' Envío gratis desde S/ 299 en Lima Metropolitana'),
      el('span', { class: 'hide-sm' }, icon('shield'), ' Garantía oficial de marca'),
      el('span', { class: 'hide-sm' }, icon('card'), ' Pago contra entrega, transferencia o en tienda'),
      el('a', { href: '/cliente/pedidos.html' }, 'Sigue tu pedido')),
    el('div', { class: 'store-bar' },
      brandLink(),
      searchForm(),
      el('div', { class: 'topbar__actions' }, soundToggle(), accountMenu(), cartLink, burger)),
    el('div', { class: 'store-nav' }, megaMenu(), nav));
}

// ---- Barra lateral y breadcrumbs ---------------------------------------------------------------
function renderSidebar() {
  const aside = $('#side-nav');
  const section = SECTIONS[document.body.dataset.section || ''];
  if (!aside || !section) return;
  const current = here();
  const active = document.body.dataset.active || current;
  aside.replaceChildren(
    el('p', { class: 'side-nav__title', text: section.label }),
    el('ul', {}, section.pages.map(([href, label, ic]) => {
      const link = el('a', { href }, icon(ic), el('span', { text: label }));
      setActive(link, href === active);
      return el('li', {}, link);
    })));
}

function renderBreadcrumbs() {
  const nav = $('#breadcrumbs');
  if (!nav) return;
  const key = document.body.dataset.section || '';
  const section = SECTIONS[key];
  const title = document.body.dataset.title || document.title;
  const crumbs = [el('a', { href: '/', text: 'Inicio' })];
  if (section) crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }), el('a', { href: section.home, text: section.label }));
  if (document.body.dataset.parent) {
    const [href, label] = document.body.dataset.parent.split('|');
    crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }), el('a', { href, text: label }));
  }
  if (here() !== '/' && !(section && section.home === here())) {
    crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }), el('span', { 'aria-current': 'page', text: title }));
  }
  nav.replaceChildren(...crumbs);
  nav.hidden = here() === '/';
}

/** Las vistas pueden completar la ruta (categoría y subcategoría del producto, por ejemplo). */
export function setBreadcrumbs(items) {
  const nav = $('#breadcrumbs');
  if (!nav) return;
  const crumbs = [el('a', { href: '/', text: 'Inicio' })];
  items.forEach(([href, label], index) => {
    crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }));
    crumbs.push(index === items.length - 1 || !href ? el('span', { 'aria-current': 'page', text: label }) : el('a', { href, text: label }));
  });
  nav.replaceChildren(...crumbs);
  nav.hidden = false;
}

// ---- Pie de página comercial (5 columnas) -------------------------------------------------------
// Todos los enlaces llevan a páginas reales. Los datos de contacto y legales salen de brand.js y son
// de demostración (se marcan como tales).
function renderFooter() {
  const footer = $('#site-footer');
  if (!footer) return;
  footer.classList.add('pm-footer');
  const column = (title, links) => el('nav', { class: 'site-footer__col', 'aria-label': title }, el('strong', { text: title }),
    el('ul', {}, links.map(([href, label]) => el('li', {}, el('a', { href, text: label })))));
  const benefits = el('div', { class: 'store-benefits' }, [
    ['truck', 'Envíos a todo el Perú', 'Gratis desde S/ 299 en Lima Metropolitana'],
    ['card', 'Paga como prefieras', 'Contra entrega, transferencia o en tienda'],
    ['shield', 'Garantía oficial', 'Productos nuevos y sellados de marca'],
    ['refresh', 'Cambios y devoluciones', 'Hasta 7 días por fallas de fábrica'],
  ].map(([ic, title, text]) => el('div', { class: 'store-benefit' }, icon(ic), el('div', {}, el('strong', { text: title }), el('small', { text })))));
  const demo = CONTACT.demo ? el('span', { class: 'demo-pill', title: 'Dato de demostración', text: 'DEMO' }) : null;
  const contact = el('ul', { class: 'pm-footer__contact' },
    el('li', {}, icon('whatsapp'), el('span', {}, `WhatsApp: ${CONTACT.whatsapp}`, demo)),
    el('li', {}, icon('call'), el('span', {}, `Teléfono: ${CONTACT.phone}`, demo?.cloneNode(true))),
    el('li', {}, icon('clock'), el('span', { text: CONTACT.hours })),
    el('li', {}, icon('mail'), el('span', {}, 'Soporte: ', el('a', { href: `mailto:${CONTACT.supportEmail}`, text: CONTACT.supportEmail }))),
    el('li', {}, icon('mail'), el('span', {}, 'Corporativo: ', el('a', { href: `mailto:${CONTACT.corporateEmail}`, text: CONTACT.corporateEmail }))),
    el('li', {}, icon('map'), el('span', { text: CONTACT.city })));
  const brandColumn = el('div', { class: 'site-footer__brand' },
    brandLink(30),
    el('p', { class: 'pm-footer__slogan', text: BRAND.slogan }),
    el('p', { class: 'muted small', text: BRAND.description }),
    contact,
    el('a', { class: 'pm-footer__claims', href: '/libro-reclamaciones.html' }, icon('book'), 'Libro de reclamaciones'));
  const pay = el('div', { class: 'footer-pay', 'aria-label': 'Métodos de pago' },
    el('span', { class: 'footer-pay__label', text: 'Métodos de pago' }),
    el('div', { class: 'footer-pay__list' },
      ...PAYMENTS.available.map((name) => el('span', { class: 'pay-badge', title: 'Disponible en el checkout' }, icon('card'), name)),
      ...PAYMENTS.notIntegrated.map((name) => el('span', { class: 'pay-badge pay-badge--off', title: 'Aún no integrado: no se acepta' }, `${name} · próximamente`)),
      el('a', { class: 'small', href: '/empresa.html#metodos-pago', text: 'Detalles' })));
  const social = el('ul', { class: 'pm-social', 'aria-label': 'Redes sociales' }, SOCIAL.map(({ id, label, url }) => el('li', {},
    el('a', url ? { href: url, target: '_blank', rel: 'noopener noreferrer', 'aria-label': label, title: label }
      : { href: SOCIAL_FALLBACK, 'aria-label': `${label}: cuenta oficial aún no disponible`, title: `${label} (próximamente)` }, icon(id)))));
  const replay = el('button', { type: 'button', text: 'Ver la intro' });
  replay.addEventListener('click', () => {
    const url = new URL(window.location.href);
    url.searchParams.set('intro', '1');
    window.location.assign(url.pathname + url.search);
  });
  const legal = el('p', { class: 'pm-footer__legal' },
    el('strong', { text: `© ${new Date().getFullYear()} ${BRAND.name}. ` }), `${BRAND.slogan} `,
    `Razón social: ${LEGAL.companyName || 'no configurada'} · RUC: ${LEGAL.taxId || 'no configurado'} · ${LEGAL.address}. `,
    'Precios en soles (S/) con IGV. Tienda de demostración: precios, stock y promociones ilustrativos; no se procesan pagos en línea.');
  footer.replaceChildren(
    benefits,
    el('div', { class: 'site-footer__grid' },
      brandColumn,
      column('Sobre nosotros', [['/nosotros.html', 'Quiénes somos'], ['/nosotros.html#historia', 'Nuestra historia'],
        ['/empresa.html#canales', 'Canales de atención'], ['/empresa.html#compra-segura', 'Compra fácil y segura'],
        ['/empresa.html#metodos-pago', 'Métodos de pago']]),
      column('Te informamos', [['/empresa.html#tiendas', 'Nuestras tiendas'], ['/empresa.html#cobertura', 'Cobertura de delivery'],
        ['/legal.html#garantias', 'Garantías'], ['/legal.html#terminos', 'Términos y condiciones'], ['/legal.html#privacidad', 'Política de privacidad'],
        ['/legal.html#cookies', 'Política de cookies'], ['/legal.html#devoluciones', 'Cambios y devoluciones']]),
      column('Servicio al cliente', [['/contacto.html', 'Contacto'], ['/cliente/pedidos.html', 'Seguimiento de pedidos'],
        ['/ayuda.html', 'Preguntas frecuentes'], ['/legal.html#facturacion', 'Facturación'], ['/contacto.html?tema=SALES', 'Ventas corporativas'],
        ['/empresa.html#disponibilidad', 'Disponibilidad de productos'], ['/login.html?next=%2Fmonitoreo%2Fdashboard.html', 'Acceso del personal']]),
      column('Destacados', [['/cliente/pedidos.html', 'Mis compras'], ['/ofertas.html', 'Ofertas'], ['/catalogo.html?categoria=computadoras', 'Laptops y PC'],
        ['/catalogo.html?categoria=componentes', 'Componentes'], ['/catalogo.html?categoria=perifericos', 'Periféricos'],
        ['/catalogo.html?categoria=accesorios-moviles', 'Accesorios'], ['/catalogo.html', 'Catálogo completo']])),
    pay,
    el('div', { class: 'pm-footer__bottom' },
      social,
      el('ul', { class: 'pm-quick', 'aria-label': 'Enlaces rápidos' },
        [['/legal.html#terminos', 'Términos'], ['/legal.html#privacidad', 'Privacidad'], ['/legal.html#cookies', 'Cookies'],
          ['/libro-reclamaciones.html', 'Libro de reclamaciones'], ['/ayuda.html', 'Ayuda']].map(([href, label]) => el('li', {}, el('a', { href, text: label }))),
        el('li', {}, replay)),
      legal));
}

export function updateCartCount() {
  const count = cart.count();
  const badge = $('#cart-count');
  if (!badge) return;
  badge.textContent = String(count);
  badge.hidden = count === 0;
  const button = $('#cart-button');
  if (button) button.setAttribute('aria-label', `Carrito: ${count} unidades`);
}

export function renderLayout() {
  renderHeader();
  renderSidebar();
  renderBreadcrumbs();
  renderFooter();
  window.addEventListener('hexa:cart', updateCartCount);
  window.addEventListener('hexa:session', () => { renderHeader(); });
  // Navegación con teclado: los enlaces de la barra lateral también se resaltan al enfocar.
  $$('.side-nav a').forEach((a) => a.addEventListener('focus', () => a.scrollIntoView({ block: 'nearest' })));
}
