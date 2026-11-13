// Estructura común del sitio: cabecera comercial (buscador, cuenta, carrito), megamenú de categorías,
// accesos rápidos, barra lateral de cada portal del personal, breadcrumbs y pie de página.
// Cada página HTML solo declara su sección y su título; este módulo genera la navegación a partir de
// esa información, de las categorías reales del catálogo y de la sesión.

import { accountApi, catalogApi } from './api.js';
import { onSoundChange, play, setSound, soundEnabled } from './audio.js';
import { BRAND, CONTACT, LEGAL, PAYMENTS, SOCIAL, SOCIAL_FALLBACK } from './brand.js';
import { cart } from './cart.js';
import { MAIN_CATEGORY_ICONS, icon } from './components.js';
import { ROLE_LABELS, session } from './session.js';
import { $, $$, el, toast } from './ui.js';
import { todayBadge } from './store-info.js';
import { LANGUAGES, equivalentUrl, lang, localizeHref, switchLanguage, t } from './i18n.js';

/** Accesos rápidos de la tienda (debajo del buscador). */
export const MAIN_NAV = [
  ['/ofertas.html', t('Ofertas')],
  ['/catalogo.html?orden=newest', t('Novedades')],
  ['/catalogo.html?categoria=computadoras', t('Laptops y PC')],
  ['/catalogo.html?categoria=componentes', t('Componentes')],
  ['/catalogo.html?categoria=gaming', t('Gaming')],
  ['/catalogo.html?categoria=smartphones-tablets', t('Smartphones')],
  ['/catalogo.html?categoria=smart-home', t('Smart Home')],
  ['/ayuda.html', t('Ayuda')],
];

/** Portal de cada rol (ADMIN entra en todos menos en el del cliente). */
export const AREAS = [
  ['CUSTOMER', '/cliente/dashboard.html', t('Mi cuenta'), 'user'],
  ['ADMIN', '/admin/dashboard.html', t('Administración'), 'grid'],
  ['ORDER_OPERATOR', '/operaciones/dashboard.html', t('Operaciones'), 'list'],
  ['LOGISTICS_OPERATOR', '/logistica/dashboard.html', t('Logística'), 'truck'],
  ['OBSERVABILITY_VIEWER', '/monitoreo/dashboard.html', t('Monitoreo'), 'activity'],
];

/** Barra lateral de cada portal. La autorización real la aplica Spring Security. */
export const SECTIONS = {
  cliente: { label: t('Mi cuenta'), home: '/cliente/dashboard.html', pages: [
    ['/cliente/dashboard.html', t('Resumen'), 'home'], ['/cliente/pedidos.html', t('Mis pedidos'), 'box'],
    ['/carrito.html', t('Mi carrito'), 'cart'], ['/cliente/perfil.html', t('Perfil y seguridad'), 'user'],
    ['/ayuda.html', t('Centro de ayuda'), 'help'], ['/catalogo.html', t('Seguir comprando'), 'tag']] },
  admin: { label: t('Administración'), home: '/admin/dashboard.html', pages: [
    ['/admin/dashboard.html', t('Panel'), 'grid'], ['/admin/productos.html', t('Productos'), 'box'],
    ['/admin/categorias.html', t('Categorías'), 'tag'], ['/admin/inventario.html', t('Inventario'), 'layers'],
    ['/admin/promociones.html', t('Promociones'), 'percent'], ['/admin/pedidos.html', t('Pedidos'), 'list'],
    ['/admin/usuarios.html', t('Usuarios y roles'), 'users'], ['/admin/incidencias.html', t('Incidencias'), 'alert'],
    ['/admin/reportes.html', t('Reportes'), 'chart'], ['/admin/auditoria.html', t('Auditoría'), 'file']] },
  operaciones: { label: t('Operaciones'), home: '/operaciones/dashboard.html', pages: [
    ['/operaciones/dashboard.html', t('Resumen'), 'grid'], ['/operaciones/pedidos.html', t('Bandeja de pedidos'), 'list'],
    ['/operaciones/revisiones.html', t('Revisiones de riesgo'), 'shield'], ['/operaciones/incidencias.html', t('Incidencias'), 'alert']] },
  logistica: { label: t('Logística'), home: '/logistica/dashboard.html', pages: [
    ['/logistica/dashboard.html', t('Resumen'), 'grid'], ['/logistica/calendario.html', t('Calendario'), 'calendar'],
    ['/logistica/entregas.html', t('Entregas'), 'truck'], ['/logistica/asignaciones.html', t('Asignaciones'), 'users']] },
  monitoreo: { label: t('Monitoreo'), home: '/monitoreo/dashboard.html', pages: [
    ['/monitoreo/dashboard.html', t('Resumen'), 'activity'], ['/monitoreo/servicios.html', t('Servicios'), 'server'],
    ['/monitoreo/metricas.html', t('Métricas'), 'chart'], ['/monitoreo/alertas.html', t('Alertas'), 'alert'],
    ['/arquitectura/overview.html', t('Documentación técnica'), 'hexagon']] },
  // Documentación técnica interna (personal autorizado): no forma parte de la tienda.
  arquitectura: { label: t('Documentación técnica'), home: '/arquitectura/overview.html', pages: [
    ['/arquitectura/overview.html', t('Visión general'), 'hexagon'], ['/arquitectura/flujo-pedidos.html', t('Flujo de pedidos'), 'route'],
    ['/laboratorio.html', t('Laboratorio de la API'), 'bolt'], ['/monitoreo/dashboard.html', t('Volver a monitoreo'), 'activity']] },
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
// Una caché por idioma: los nombres de categoría llegan ya traducidos.
const CATEGORY_CACHE = `polymer.categories.v6.${lang}`;
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
      icon('user'), el('span', { class: 'store-action__text' }, el('small', { text: t('Hola, inicia sesión') }), el('strong', { text: t('Mi cuenta') })));
  }
  const panel = el('div', { class: 'menu__panel', hidden: true, role: 'menu' },
    el('div', { class: 'menu__meta' }, el('strong', { text: user.displayName }),
      el('small', { text: `@${user.username} · ${user.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}` })),
    ...areasFor().map(([, href, label]) => el('a', { href, role: 'menuitem', text: label })),
    session.has('CUSTOMER') ? el('a', { href: '/cliente/pedidos.html', role: 'menuitem', text: t('Mis pedidos') }) : null,
    el('a', { href: '/cliente/perfil.html', role: 'menuitem', text: t('Perfil y seguridad') }),
    el('button', { type: 'button', role: 'menuitem', text: t('Cerrar sesión'), onclick: async () => {
      await session.logout();
      toast(t('Sesión cerrada'), 'ok');
      window.location.assign(localizeHref('/'));
    } }));
  const toggle = el('button', { type: 'button', class: 'store-action user-chip', 'aria-haspopup': 'menu', 'aria-expanded': 'false',
    'aria-label': t('Cuenta de {0}', user.displayName) },
  icon('user'), el('span', { class: 'store-action__text' }, el('small', { text: t('Hola, {0}', user.displayName.split(' ')[0]) }),
    el('strong', { class: 'user-chip__name', text: t('Mi cuenta') })));
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
  const input = el('input', { type: 'search', name: 'q', id: 'store-search', placeholder: t('Busca laptops, procesadores, monitores, marcas o modelos…'),
    autocomplete: 'off', maxlength: '80', value: window.location.pathname === '/catalogo.html' ? (params.get('q') || '') : '',
    'aria-label': t('Buscar productos') });
  const form = el('form', { class: 'store-search', role: 'search', action: '/catalogo.html', method: 'get' },
    input, el('button', { type: 'submit', class: 'store-search__go', 'aria-label': t('Buscar') }, icon('search')));
  form.addEventListener('submit', (event) => {
    if (!input.value.trim()) { event.preventDefault(); input.focus(); }
  });
  return form;
}

function megaMenu() {
  const button = el('button', { type: 'button', class: 'mega__toggle', 'aria-expanded': 'false', 'aria-controls': 'mega-panel' },
    icon('menu'), el('span', { text: t('Categorías') }));
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
        el('a', { class: 'mega__title', href: `/catalogo.html?categoria=${main.slug}` }, el('strong', { text: main.name }), el('span', { text: t('Ver todo') })),
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
  return el('a', { class: 'brand brand--logo', href: '/', 'aria-label': t('{0}, inicio', BRAND.name) },
    el('img', { class: 'brand__lockup', src: BRAND.logo, alt: BRAND.name, height: String(height), width: String(Math.round(height * 3.8)) }),
    el('img', { class: 'brand__mark', src: BRAND.mark, alt: '', height: String(height), width: String(height) }));
}

/**
 * Selector de idioma. Son enlaces reales a la página equivalente (/en/products?…), así que también
 * funcionan sin JavaScript y los buscadores los siguen; con JavaScript además se guarda la
 * preferencia (y, con sesión, el idioma de los correos) antes de navegar.
 */
function languageLinks() {
  return LANGUAGES.map(({ code, name }) => {
    const link = el('a', { href: equivalentUrl(code), hreflang: code, lang: code, class: 'lang-menu__item', text: name });
    if (code === lang) link.setAttribute('aria-current', 'true');
    link.addEventListener('click', (event) => {
      event.preventDefault();
      switchLanguage(code, { persist: session.isAuthenticated() ? (value) => accountApi.locale(value) : null });
    });
    return el('li', {}, link);
  });
}

function languageMenu() {
  const current = LANGUAGES.find((l) => l.code === lang) || LANGUAGES[0];
  const menu = el('details', { class: 'lang-menu' },
    el('summary', { class: 'lang-menu__button', 'aria-label': t('Idioma: {0}. Cambiar idioma', current.name) },
      icon('globe'), el('span', { text: current.name }), el('span', { class: 'lang-menu__caret', 'aria-hidden': 'true', text: '▾' })),
    el('ul', { class: 'lang-menu__list', 'aria-label': t('Idiomas disponibles') }, languageLinks()));
  document.addEventListener('click', (event) => { if (!menu.contains(event.target)) menu.open = false; });
  menu.addEventListener('keydown', (event) => { if (event.key === 'Escape') { menu.open = false; menu.querySelector('summary').focus(); } });
  return menu;
}

/** Selector compacto de la cabecera (sobre todo para móvil). */
function languageSelect() {
  const select = el('select', { class: 'lang-select', 'aria-label': t('Idioma') },
    LANGUAGES.map(({ code }) => el('option', { value: code, text: code.toUpperCase(), selected: code === lang })));
  select.addEventListener('change', () => switchLanguage(select.value, { persist: session.isAuthenticated() ? (value) => accountApi.locale(value) : null }));
  return select;
}

/** v9: interruptor discreto de los sonidos (activados por defecto) en el pie de página, no en la cabecera. */
function soundToggle() {
  const button = el('button', { type: 'button', class: 'sound-toggle', id: 'sound-toggle' });
  const paint = (on) => {
    button.replaceChildren(icon(on ? 'volume' : 'volumeOff'), el('span', { text: on ? t('Sonidos: activados') : t('Sonidos: silenciados') }));
    button.setAttribute('aria-pressed', String(on));
    button.setAttribute('aria-label', on ? t('Sonidos activados: pulsa para silenciar') : t('Sonidos silenciados: pulsa para activarlos'));
    button.title = on ? t('Silenciar sonidos') : t('Activar sonidos');
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
  const cartLink = el('a', { class: 'store-action store-action--cart', href: '/carrito.html', id: 'cart-button', 'aria-label': t('Carrito: {0} unidades', count) },
    el('span', { class: 'store-action__icon' }, icon('cart'), el('span', { class: 'icon-btn__badge', id: 'cart-count', hidden: count === 0, text: String(count) })),
    el('span', { class: 'store-action__text' }, el('small', { text: t('Tu') }), el('strong', { text: t('Carrito') })));
  const burger = el('button', { type: 'button', class: 'icon-btn nav-toggle', 'aria-label': t('Abrir menú'), 'aria-expanded': 'false', 'aria-controls': 'primary-nav' }, icon('menu'));
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
  const nav = el('nav', { class: 'nav store-nav__links', id: 'primary-nav', 'aria-label': t('Navegación principal') },
    el('a', { href: '/', text: t('Inicio'), class: here() === '/' ? 'is-active' : '' }), ...quick,
    areaLinks.length ? el('span', { class: 'nav__group', 'aria-label': t('Áreas del personal') }, areaLinks) : null);
  burger.addEventListener('click', () => {
    const open = !header.classList.contains('is-open');
    header.classList.toggle('is-open', open);
    burger.setAttribute('aria-expanded', String(open));
    burger.setAttribute('aria-label', open ? t('Cerrar menú') : t('Abrir menú'));
  });
  header.classList.add('store-header');
  header.replaceChildren(
    el('div', { class: 'store-strip' },
      el('span', {}, icon('truck'), t(' Envío gratis desde S/ 299 en Lima Metropolitana')),
      el('span', { class: 'hide-sm' }, icon('shield'), t(' Garantía oficial de marca')),
      el('span', { class: 'hide-sm' }, icon('card'), t(' Tarjeta, Yape, Plin o contra entrega')),
      el('a', { href: '/cliente/pedidos.html' }, t('Sigue tu pedido'))),
    el('div', { class: 'store-bar' },
      brandLink(),
      searchForm(),
      el('div', { class: 'topbar__actions' }, languageSelect(), accountMenu(), cartLink, burger)),
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
  const title = document.body.dataset.title ? t(document.body.dataset.title) : document.title;
  const crumbs = [el('a', { href: '/', text: t('Inicio') })];
  if (section) crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }), el('a', { href: section.home, text: section.label }));
  if (document.body.dataset.parent) {
    const [href, label] = document.body.dataset.parent.split('|');
    crumbs.push(el('span', { 'aria-hidden': 'true', text: '/' }), el('a', { href, text: t(label) }));
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
  const crumbs = [el('a', { href: '/', text: t('Inicio') })];
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
    ['truck', t('Envíos a todo el Perú'), t('Gratis desde S/ 299 en Lima Metropolitana')],
    ['card', t('Paga como prefieras'), t('Tarjeta, Yape, Plin, contra entrega o en tienda')],
    ['shield', t('Garantía oficial'), t('Productos nuevos y sellados de marca')],
    ['refresh', t('Cambios y devoluciones'), t('Hasta 7 días por fallas de fábrica')],
  ].map(([ic, title, text]) => el('div', { class: 'store-benefit' }, icon(ic), el('div', {}, el('strong', { text: title }), el('small', { text })))));
  const demo = CONTACT.demo ? el('span', { class: 'demo-pill', title: t('Dato de demostración'), text: 'DEMO' }) : null;
  const contact = el('ul', { class: 'pm-footer__contact' },
    el('li', {}, icon('whatsapp'), el('span', {}, t('WhatsApp: {0}', CONTACT.whatsapp), demo)),
    el('li', {}, icon('call'), el('span', {}, t('Teléfono: {0}', CONTACT.phone), demo?.cloneNode(true))),
    el('li', {}, icon('clock'), el('span', { text: CONTACT.hours })),
    el('li', { class: 'pm-footer__today' }, todayBadge()),
    el('li', {}, icon('mail'), el('span', {}, t('Soporte: '), el('a', { href: `mailto:${CONTACT.supportEmail}`, text: CONTACT.supportEmail }))),
    el('li', {}, icon('mail'), el('span', {}, t('Corporativo: '), el('a', { href: `mailto:${CONTACT.corporateEmail}`, text: CONTACT.corporateEmail }))),
    el('li', {}, icon('map'), el('span', { text: CONTACT.city })));
  const brandColumn = el('div', { class: 'site-footer__brand' },
    brandLink(30),
    el('p', { class: 'pm-footer__slogan', text: BRAND.slogan }),
    el('p', { class: 'muted small', text: BRAND.description }),
    contact,
    el('a', { class: 'pm-footer__claims', href: '/libro-reclamaciones.html' }, icon('book'), t('Libro de reclamaciones')));
  const pay = el('div', { class: 'footer-pay', 'aria-label': t('Métodos de pago') },
    el('span', { class: 'footer-pay__label', text: t('Métodos de pago') }),
    el('div', { class: 'footer-pay__list' },
      ...PAYMENTS.available.map((name) => el('span', { class: 'pay-badge', title: t('Disponible en el checkout') }, icon('card'), name)),
      ...PAYMENTS.notIntegrated.map((name) => el('span', { class: 'pay-badge pay-badge--off', title: t('Aún no integrado: no se acepta') }, t('{0} · próximamente', name))),
      el('a', { class: 'small', href: '/empresa.html#metodos-pago', text: t('Detalles') })));
  const social = el('ul', { class: 'pm-social', 'aria-label': t('Redes sociales') }, SOCIAL.map(({ id, label, url }) => el('li', {},
    el('a', url ? { href: url, target: '_blank', rel: 'noopener noreferrer', 'aria-label': label, title: label }
      : { href: SOCIAL_FALLBACK, 'aria-label': t('{0}: cuenta oficial aún no disponible', label), title: t('{0} (próximamente)', label) }, icon(id)))));
  const replay = el('button', { type: 'button', text: t('Ver la intro') });
  replay.addEventListener('click', () => {
    const url = new URL(window.location.href);
    url.searchParams.set('intro', '1');
    window.location.assign(url.pathname + url.search);
  });
  const legal = el('p', { class: 'pm-footer__legal' },
    el('strong', { text: `© ${new Date().getFullYear()} ${BRAND.name}. ` }), `${BRAND.slogan} `,
    t('Razón social: {0} · RUC: {1} · {2}. ', LEGAL.companyName || 'no configurada', LEGAL.taxId || 'no configurado', LEGAL.address),
    t('Precios en soles (S/) con IGV. Tienda de demostración: precios, stock y promociones ilustrativos; los pagos con tarjeta, Yape y Plin son de prueba y no cobran.'));
  footer.replaceChildren(
    benefits,
    el('div', { class: 'site-footer__grid' },
      brandColumn,
      column(t('Sobre nosotros'), [['/nosotros.html', t('Quiénes somos')], ['/nosotros.html#historia', t('Nuestra historia')],
        ['/nosotros.html#testimonios', t('Testimonios')], ['/empresa.html#canales', t('Canales de atención')], ['/empresa.html#compra-segura', t('Compra fácil y segura')],
        ['/empresa.html#metodos-pago', t('Métodos de pago')]]),
      column(t('Te informamos'), [['/empresa.html#tiendas', t('Nuestras tiendas')], ['/empresa.html#cobertura', t('Cobertura de delivery')],
        ['/legal.html#garantias', t('Garantías')], ['/legal.html#terminos', t('Términos y condiciones')], ['/legal.html#privacidad', t('Política de privacidad')],
        ['/legal.html#cookies', t('Política de cookies')], ['/legal.html#devoluciones', t('Cambios y devoluciones')]]),
      column(t('Servicio al cliente'), [['/contacto.html', t('Contacto')], ['/cliente/pedidos.html', t('Seguimiento de pedidos')],
        ['/ayuda.html', t('Preguntas frecuentes')], ['/legal.html#facturacion', t('Facturación')], ['/contacto.html?tema=SALES', t('Ventas corporativas')],
        ['/empresa.html#disponibilidad', t('Disponibilidad de productos')], ['/login.html?next=%2Fmonitoreo%2Fdashboard.html', t('Acceso del personal')]]),
      column(t('Destacados'), [['/cliente/pedidos.html', t('Mis compras')], ['/ofertas.html', t('Ofertas')], ['/catalogo.html?categoria=computadoras', t('Laptops y PC')],
        ['/catalogo.html?categoria=componentes', t('Componentes')], ['/catalogo.html?categoria=perifericos', t('Periféricos')],
        ['/catalogo.html?categoria=accesorios-moviles', t('Accesorios')], ['/catalogo.html', t('Catálogo completo')]])),
    pay,
    el('div', { class: 'pm-footer__bottom' },
      el('div', { class: 'pm-footer__lang' }, social, languageMenu()),
      el('ul', { class: 'pm-quick', 'aria-label': t('Enlaces rápidos') },
        [['/legal.html#terminos', t('Términos')], ['/legal.html#privacidad', t('Privacidad')], ['/legal.html#cookies', t('Cookies')],
          ['/libro-reclamaciones.html', t('Libro de reclamaciones')], ['/ayuda.html', t('Ayuda')]].map(([href, label]) => el('li', {}, el('a', { href, text: label }))),
        el('li', {}, replay), el('li', {}, soundToggle())),
      legal));
}

export function updateCartCount() {
  const count = cart.count();
  const badge = $('#cart-count');
  if (!badge) return;
  badge.textContent = String(count);
  badge.hidden = count === 0;
  const button = $('#cart-button');
  if (button) button.setAttribute('aria-label', t('Carrito: {0} unidades', count));
}

export function renderLayout() {
  renderHeader();
  renderSidebar();
  renderBreadcrumbs();
  renderFooter();
  window.addEventListener('polymer:cart', updateCartCount);
  window.addEventListener('polymer:session', () => { renderHeader(); });
  // Navegación con teclado: los enlaces de la barra lateral también se resaltan al enfocar.
  $$('.side-nav a').forEach((a) => a.addEventListener('focus', () => a.scrollIntoView({ block: 'nearest' })));
}
