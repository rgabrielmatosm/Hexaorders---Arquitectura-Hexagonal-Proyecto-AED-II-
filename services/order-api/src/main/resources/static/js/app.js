// Punto de entrada del frontend de HexaOrders (módulos ES nativos, sin frameworks).

import { ordersApi, prometheusApi, reportApiStatus } from './api.js';
import { initArchitecture } from './architecture.js';
import { renderRecent, renderRecentError, renderStatistics, renderStatisticsError } from './dashboard.js';
import { initGrid } from './grid-effect.js';
import { initObservability, refreshObservability } from './observability.js';
import { initOrders, renderOrders, renderOrdersError } from './orders.js';
import { $, $$, reducedMotion } from './ui.js';

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

// ---- Arranque ----------------------------------------------------------------
function start() {
  initGrid($('#grid-canvas'));
  initNavigation();
  initOrders({ onChanged: refreshData });
  initArchitecture();
  initObservability();

  const firstLoad = refreshData();
  initSplash(firstLoad);
  firstLoad.finally(() => refreshObservability());

  $$('[data-refresh]').forEach((button) => button.addEventListener('click', refreshData));
  window.addEventListener('hexa:orders-changed', () => { refreshData(); });

  setInterval(() => { if (!document.hidden) refreshData(); }, DATA_REFRESH_MS);
  setInterval(() => { if (!document.hidden) refreshObservability(); }, OBS_REFRESH_MS);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshData(); });
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
else start();
