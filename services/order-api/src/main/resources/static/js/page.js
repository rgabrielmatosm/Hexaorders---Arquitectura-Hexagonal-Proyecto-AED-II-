// Punto de entrada de TODAS las páginas HTML del sitio multipágina (salvo laboratorio.html, que
// conserva su app.js original). Orden: compatibilidad de URLs antiguas → sesión → layout → vista.

import { cart } from './cart.js';
import { initGrid } from './grid-effect.js';
import { startIntro } from './intro.js';
import { renderLayout } from './layout.js';
import { startMotion } from './motion.js';
import { redirectLegacyHash, startPage } from './router.js';
import { session } from './session.js';
import { $, toast } from './ui.js';
// Vistas: cada módulo registra sus rutas lógicas (las mismas que usaba la v3).
import './views/home.js';
import './views/info.js';
import './views/shop.js';
import './views/customer.js';
import './views/payment.js';
import './views/admin.js';
import './views/operations.js';
import './views/logistics.js';
import './views/ops.js';
import './views/architecture-pages.js';
import { startLinkLocalization, t } from './i18n.js';

const LAB_ANCHORS = ['#inicio', '#panel', '#pedidos', '#arquitectura', '#observabilidad'];

async function start() {
  // v3 → v4: /#/tienda redirige a /catalogo.html y /#panel al laboratorio, sin romper enlaces.
  const isHome = window.location.pathname === '/' || window.location.pathname === '/index.html';
  if (isHome && LAB_ANCHORS.includes(window.location.hash)) { window.location.replace(`/laboratorio.html${window.location.hash}`); return; }
  if (redirectLegacyHash()) return;
  // POLYMER: la intro (si toca) se superpone mientras la página se prepara detrás; nunca la bloquea.
  startIntro();
  // i18n: con prefijo de idioma (/en/…), los enlaces internos que creen las vistas se localizan solos.
  startLinkLocalization();

  // El fondo animado solo se usa en las áreas del personal; la tienda tiene un diseño comercial limpio.
  const section = document.body.dataset.section || '';
  const canvas = $('#grid-canvas');
  if (canvas && section && section !== 'cliente') initGrid(canvas);
  else if (canvas) canvas.remove();
  window.addEventListener('polymer:unauthenticated', () => {
    if (session.isAuthenticated()) {
      session.expire();
      toast(t('Tu sesión ha caducado: vuelve a iniciar sesión'), 'warn');
    }
  });
  await session.load();
  await cart.sync(); // cliente con sesión: carrito guardado (y fusión del carrito del visitante)
  renderLayout();
  document.body.classList.add('is-ready');
  startMotion();
  await startPage(document.body.dataset.route);
}

start();
