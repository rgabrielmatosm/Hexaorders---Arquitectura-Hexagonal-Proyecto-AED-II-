// Punto de entrada del frontend de HexaOrders (módulos ES nativos, sin frameworks).

import { ordersApi, prometheusApi, reportApiStatus } from './api.js';
import { initArchitecture } from './architecture.js';
import { renderRecent, renderRecentError, renderStatistics, renderStatisticsError } from './dashboard.js';
import { initGrid } from './grid-effect.js';
import { initObservability, refreshObservability } from './observability.js';
import { initOrders, renderOrders, renderOrdersError } from './orders.js';
import { $, $$, el, reducedMotion, toast } from './ui.js';
import { cart } from './cart.js';
import { navigate, startRouter } from './router.js';
import { ROLE_LABELS, homeFor, session } from './session.js';
// Vistas de la plataforma: cada módulo registra sus rutas.
import './views/shop.js';
import './views/admin.js';
import './views/operations.js';
import './views/logistics.js';
import './views/ops.js';

const DATA_REFRESH_MS = 20000;
const OBS_REFRESH_MS = 15000;

// ---- Estado de la API en la barra superior --------------------------------
window.addEventListener('hexa:api-status', ({ detail }) => {
  const pill = $('#api-status');
  pill.querySelector('.dot').className = `dot ${detail.ok ? 'dot--ok' : 'dot--error'}`;
  pill.querySelector('.status-pill__text').textContent = detail.ok ? 'API conectada' : 'API no disponible';
  pill.title = detail.ok ? `Última respuesta: ${detail.at.toLocaleTimeString('es-ES')}` : detail.detail;
});

// ---- Datos de pedidos ------------------------------------------------------
let loading = null;
async function refreshData() {
  if (loading) return loading;
  loading = (async () => {
    const [statsResult, listResult] = await Promise.allSettled([ordersApi.statistics(), ordersApi.list()]);
    if (statsResult.status === 'fulfilled') {
      renderStatistics(statsResult.value.data);
      reportApiStatus(true);
    } else {
      renderStatisticsError(statsResult.reason.message);
      reportApiStatus(false, statsResult.reason.message);
    }
    if (listResult.status === 'fulfilled') {
      renderRecent(listResult.value.data);
      renderOrders(listResult.value.data);
    } else {
      renderRecentError(listResult.reason.message);
      renderOrdersError(listResult.reason.message);
    }
    return statsResult.status === 'fulfilled';
  })();
  try { return await loading; } finally { loading = null; }
}

// ---- Bienvenida -------------------------------------------------------------
function setSplashCheck(name, ok, text) {
  const item = $(`#splash [data-check="${name}"]`);
  if (!item) return;
  item.querySelector('.dot').className = `dot ${ok ? 'dot--ok' : 'dot--warn'}`;
  item.querySelector('em').textContent = text;
}

function initSplash(apiCheck) {
  const splash = $('#splash');
  let seen = false;
  try { seen = sessionStorage.getItem('hexaorders.splash') === 'seen'; } catch { /* almacenamiento no disponible */ }
  if (seen) { splash.hidden = true; return; }
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    try { sessionStorage.setItem('hexaorders.splash', 'seen'); } catch { /* sin almacenamiento */ }
    splash.classList.add('is-leaving');
    setTimeout(() => { splash.hidden = true; }, reducedMotion() ? 0 : 460);
    document.removeEventListener('keydown', onKey);
  };
  const onKey = (event) => { if (event.key === 'Enter' || event.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  $('#splash-enter').addEventListener('click', close);
  splash.addEventListener('click', (event) => { if (event.target === splash) close(); });
  $('#splash-enter').focus({ preventScroll: true });

  // Comprobaciones reales; la bienvenida nunca espera por ellas para dejar entrar.
  apiCheck.then((ok) => setSplashCheck('api', ok, ok ? 'disponible' : 'no responde'));
  prometheusApi.targets()
    .then(() => setSplashCheck('prometheus', true, 'disponible'))
    .catch(() => setSplashCheck('prometheus', false, 'no responde (la aplicación sigue funcionando)'));
  setTimeout(close, reducedMotion() ? 1200 : 2400);
}

// ---- Navegación -------------------------------------------------------------
function initNavigation() {
  const links = $$('[data-nav]');
  const sections = links.map((link) => $(link.getAttribute('href'))).filter(Boolean);
  if (!('IntersectionObserver' in window)) return;
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      links.forEach((link) => {
        const active = link.getAttribute('href') === `#${entry.target.id}`;
        link.classList.toggle('is-active', active);
        if (active) link.setAttribute('aria-current', 'true'); else link.removeAttribute('aria-current');
      });
    }
  }, { rootMargin: '-45% 0px -50% 0px' });
  sections.forEach((section) => observer.observe(section));

  if (!reducedMotion()) {
    const reveal = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) { entry.target.classList.add('is-visible'); reveal.unobserve(entry.target); }
      }
    }, { rootMargin: '0px 0px -8% 0px' });
    $$('.section .card, .stats .stat').forEach((node) => { node.classList.add('reveal'); reveal.observe(node); });
  }
}

// ---- Barra superior de la plataforma ----------------------------------------------
const ROLE_AREAS = [
  ['ADMIN', '#/admin', 'Administración'],
  ['ORDER_OPERATOR', '#/operaciones', 'Operaciones'],
  ['LOGISTICS_OPERATOR', '#/logistica', 'Logística'],
  ['OBSERVABILITY_VIEWER', '#/observabilidad-tecnica', 'Salud técnica'],
];

function renderAccount() {
  const user = session.user();
  const container = $('#account');
  const roleNav = $('#role-nav');
  roleNav.replaceChildren(...ROLE_AREAS.filter(([role]) => session.has(role) || session.has('ADMIN'))
    .map(([, href, label]) => el('a', { href, dataset: { routeLink: href.slice(1) }, text: label })));
  if (!user) {
    container.replaceChildren(el('a', { class: 'btn btn--primary btn--sm', href: '#/entrar', text: 'Entrar' }));
    return;
  }
  const initials = user.displayName.split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase();
  const panel = el('div', { class: 'menu__panel', hidden: true, role: 'menu' },
    el('div', { class: 'menu__meta' }, el('strong', { text: user.displayName }), el('small', { text: `@${user.username} · ${user.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}` })),
    session.has('CUSTOMER') ? el('a', { href: '#/mis-pedidos', role: 'menuitem', text: 'Mis pedidos' }) : null,
    el('a', { href: homeFor(user), role: 'menuitem', text: 'Mi área' }),
    el('a', { href: '#/perfil', role: 'menuitem', text: 'Perfil y seguridad' }),
    el('button', { type: 'button', role: 'menuitem', text: 'Cerrar sesión', onclick: async () => { await session.logout(); toast('Sesión cerrada', 'ok'); navigate('#/tienda'); } }));
  const toggle = el('button', { type: 'button', class: 'icon-btn user-chip', 'aria-haspopup': 'menu', 'aria-expanded': 'false' },
    el('span', { class: 'avatar', text: initials }), el('span', { class: 'user-chip__name', text: user.displayName.split(' ')[0] }));
  toggle.addEventListener('click', (event) => {
    event.stopPropagation();
    panel.hidden = !panel.hidden;
    toggle.setAttribute('aria-expanded', String(!panel.hidden));
  });
  document.addEventListener('click', () => { panel.hidden = true; toggle.setAttribute('aria-expanded', 'false'); });
  panel.addEventListener('click', () => { panel.hidden = true; });
  container.replaceChildren(toggle, panel);
}

function renderCartCount() {
  const count = cart.count();
  const badge = $('#cart-count');
  badge.textContent = String(count);
  badge.hidden = count === 0;
  $('#cart-button').setAttribute('aria-label', `Carrito: ${count} unidades`);
}

function markActiveRoute(path) {
  $$('[data-route-link]').forEach((link) => {
    const active = path && path.startsWith(link.dataset.routeLink);
    link.classList.toggle('is-active', Boolean(active));
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
}

// ---- Arranque ----------------------------------------------------------------
async function start() {
  initGrid($('#grid-canvas'));
  window.addEventListener('hexa:session', renderAccount);
  window.addEventListener('hexa:cart', renderCartCount);
  window.addEventListener('hexa:route', (event) => markActiveRoute(event.detail.path));
  window.addEventListener('hexa:unauthenticated', () => {
    if (session.isAuthenticated()) { session.expire(); toast('Tu sesión ha caducado: vuelve a iniciar sesión', 'warn'); }
  });
  renderCartCount();
  renderAccount();
  const routedOnLoad = (window.location.hash || '').startsWith('#/');
  await session.load();
  await startRouter();
  initNavigation();
  initOrders({ onChanged: refreshData });
  initArchitecture();
  initObservability();

  const firstLoad = refreshData();
  if (routedOnLoad) { $('#splash').hidden = true; firstLoad.catch(() => {}); } else initSplash(firstLoad);
  firstLoad.finally(() => refreshObservability());

  $$('[data-refresh]').forEach((button) => button.addEventListener('click', refreshData));
  window.addEventListener('hexa:orders-changed', () => { refreshData(); });

  setInterval(() => { if (!document.hidden) refreshData(); }, DATA_REFRESH_MS);
  setInterval(() => { if (!document.hidden) refreshObservability(); }, OBS_REFRESH_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshData(); });
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
else start();
