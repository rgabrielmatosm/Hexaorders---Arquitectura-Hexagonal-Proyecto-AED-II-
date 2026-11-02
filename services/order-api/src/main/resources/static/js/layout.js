// Estructura común del sitio multipágina: cabecera, menús por rol, barra lateral de cada portal,
// breadcrumbs, pie de página y navegación móvil. Cada página HTML solo declara su sección y su
// título; este módulo genera la navegación a partir de esa información y de la sesión real.

import { cart } from './cart.js';
import { icon } from './components.js';
import { ROLE_LABELS, session } from './session.js';
import { $, $$, el, toast } from './ui.js';

/** Navegación pública principal. */
export const MAIN_NAV = [
  ['/', 'Inicio'],
  ['/catalogo.html', 'Catálogo'],
  ['/arquitectura/overview.html', 'Arquitectura'],
  ['/laboratorio.html', 'Laboratorio'],
  ['/nosotros.html', 'Nosotros'],
  ['/contacto.html', 'Contacto'],
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
    ['/cliente/perfil.html', 'Perfil y seguridad', 'user'], ['/catalogo.html', 'Seguir comprando', 'cart']] },
  admin: { label: 'Administración', home: '/admin/dashboard.html', pages: [
    ['/admin/dashboard.html', 'Panel', 'grid'], ['/admin/productos.html', 'Productos', 'box'],
    ['/admin/categorias.html', 'Categorías', 'tag'], ['/admin/inventario.html', 'Inventario', 'layers'],
    ['/admin/pedidos.html', 'Pedidos', 'list'], ['/admin/usuarios.html', 'Usuarios y roles', 'users'],
    ['/admin/incidencias.html', 'Incidencias', 'alert'], ['/admin/reportes.html', 'Reportes', 'chart'],
    ['/admin/auditoria.html', 'Auditoría', 'file']] },
  operaciones: { label: 'Operaciones', home: '/operaciones/dashboard.html', pages: [
    ['/operaciones/dashboard.html', 'Resumen', 'grid'], ['/operaciones/pedidos.html', 'Bandeja de pedidos', 'list'],
    ['/operaciones/revisiones.html', 'Revisiones de riesgo', 'shield'], ['/operaciones/incidencias.html', 'Incidencias', 'alert']] },
  logistica: { label: 'Logística', home: '/logistica/dashboard.html', pages: [
    ['/logistica/dashboard.html', 'Resumen', 'grid'], ['/logistica/calendario.html', 'Calendario', 'calendar'],
    ['/logistica/entregas.html', 'Entregas', 'truck'], ['/logistica/asignaciones.html', 'Asignaciones', 'users']] },
  monitoreo: { label: 'Monitoreo', home: '/monitoreo/dashboard.html', pages: [
    ['/monitoreo/dashboard.html', 'Resumen', 'activity'], ['/monitoreo/servicios.html', 'Servicios', 'server'],
    ['/monitoreo/metricas.html', 'Métricas', 'chart'], ['/monitoreo/alertas.html', 'Alertas', 'alert']] },
  arquitectura: { label: 'Arquitectura', home: '/arquitectura/overview.html', pages: [
    ['/arquitectura/overview.html', 'Visión general', 'hexagon'], ['/arquitectura/flujo-pedidos.html', 'Flujo de pedidos', 'route'],
    ['/laboratorio.html', 'Laboratorio interactivo', 'bolt']] },
};

const here = () => (window.location.pathname === '/index.html' ? '/' : window.location.pathname);

function areasFor() {
  return AREAS.filter(([role]) => session.has(role) || (role !== 'CUSTOMER' && session.has('ADMIN')));
}

function setActive(link, active) {
  link.classList.toggle('is-active', active);
  if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
}

// ---- Cabecera ----------------------------------------------------------------------------------
function accountMenu() {
  const user = session.user();
  if (!user) {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    return el('div', { class: 'row-actions' },
      el('a', { class: 'btn btn--ghost btn--sm hide-sm', href: '/registro.html', text: 'Crear cuenta' }),
      el('a', { class: 'btn btn--primary btn--sm', href: `/login.html${window.location.pathname === '/login.html' ? '' : `?next=${next}`}`, text: 'Entrar' }));
  }
  const initials = user.displayName.split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase();
  const panel = el('div', { class: 'menu__panel', hidden: true, role: 'menu' },
    el('div', { class: 'menu__meta' }, el('strong', { text: user.displayName }),
      el('small', { text: `@${user.username} · ${user.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}` })),
    ...areasFor().map(([, href, label]) => el('a', { href, role: 'menuitem', text: label })),
    el('a', { href: '/cliente/perfil.html', role: 'menuitem', text: 'Perfil y seguridad' }),
    el('button', { type: 'button', role: 'menuitem', text: 'Cerrar sesión', onclick: async () => {
      await session.logout();
      toast('Sesión cerrada', 'ok');
      window.location.assign('/');
    } }));
  const toggle = el('button', { type: 'button', class: 'icon-btn user-chip', 'aria-haspopup': 'menu', 'aria-expanded': 'false', 'aria-label': `Cuenta de ${user.displayName}` },
    el('span', { class: 'avatar', text: initials }), el('span', { class: 'user-chip__name', text: user.displayName.split(' ')[0] }));
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

function renderHeader() {
  const header = $('#site-header');
  if (!header) return;
  const current = here();
  const section = document.body.dataset.section || '';
  const mainLinks = MAIN_NAV.map(([href, label]) => {
    const link = el('a', { href, text: label });
    const isSection = section === 'arquitectura' && href.startsWith('/arquitectura/');
    setActive(link, current === href || isSection);
    return link;
  });
  const areaLinks = areasFor().map(([, href, label]) => {
    const link = el('a', { href, text: label, class: 'nav__area' });
    setActive(link, Boolean(section) && SECTIONS[section] && SECTIONS[section].home === href);
    return link;
  });
  const count = cart.count();
  const cartLink = el('a', { class: 'icon-btn', href: '/carrito.html', id: 'cart-button', 'aria-label': `Carrito: ${count} unidades` },
    icon('cart'), el('span', { class: 'icon-btn__badge', id: 'cart-count', hidden: count === 0, text: String(count) }));
  const burger = el('button', { type: 'button', class: 'icon-btn nav-toggle', 'aria-label': 'Abrir menú', 'aria-expanded': 'false', 'aria-controls': 'primary-nav' }, icon('menu'));
  const nav = el('nav', { class: 'nav', id: 'primary-nav', 'aria-label': 'Navegación principal' },
    ...mainLinks, areaLinks.length ? el('span', { class: 'nav__group', 'aria-label': 'Áreas de tu rol' }, areaLinks) : null);
  burger.addEventListener('click', () => {
    const open = !header.classList.contains('is-open');
    header.classList.toggle('is-open', open);
    burger.setAttribute('aria-expanded', String(open));
    burger.setAttribute('aria-label', open ? 'Cerrar menú' : 'Abrir menú');
  });
  header.replaceChildren(
    el('a', { class: 'brand', href: '/', 'aria-label': 'HexaOrders, inicio' },
      el('img', { src: '/img/logo.svg', alt: '', width: '34', height: '34' }),
      el('span', { class: 'brand__name' }, 'HEXA', el('span', { text: 'ORDERS' }))),
    nav,
    el('div', { class: 'topbar__actions' }, cartLink, accountMenu(), burger));
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

// ---- Pie de página -----------------------------------------------------------------------------
function renderFooter() {
  const footer = $('#site-footer');
  if (!footer) return;
  const column = (title, links) => el('div', { class: 'site-footer__col' }, el('strong', { text: title }),
    el('ul', {}, links.map(([href, label]) => el('li', {}, el('a', { href, text: label })))));
  footer.replaceChildren(
    el('div', { class: 'site-footer__grid' },
      el('div', { class: 'site-footer__brand' },
        el('a', { class: 'brand', href: '/' }, el('img', { src: '/img/logo.svg', alt: '', width: '30', height: '30' }),
          el('span', { class: 'brand__name' }, 'HEXA', el('span', { text: 'ORDERS' }))),
        el('p', { class: 'muted small', text: 'Order Management & Risk Assessment System. Arquitectura hexagonal con Java 21, Python, PostgreSQL, Docker Compose, Prometheus y Grafana.' })),
      column('Portal', [['/catalogo.html', 'Catálogo'], ['/carrito.html', 'Carrito'], ['/cliente/pedidos.html', 'Mis pedidos'], ['/registro.html', 'Crear cuenta']]),
      column('Plataforma', [['/arquitectura/overview.html', 'Arquitectura'], ['/arquitectura/flujo-pedidos.html', 'Flujo de pedidos'], ['/laboratorio.html', 'Laboratorio'], ['/monitoreo/dashboard.html', 'Monitoreo']]),
      column('Proyecto', [['/nosotros.html', 'Nosotros'], ['/contacto.html', 'Contacto'], ['/login.html', 'Acceso de equipos']])),
    el('p', { class: 'site-footer__legal muted small', text: 'Proyecto académico · Pagos de demostración: no se procesan pagos reales ni se almacenan datos bancarios.' }));
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
