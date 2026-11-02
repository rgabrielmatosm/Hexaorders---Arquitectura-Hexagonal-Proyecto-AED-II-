// Enrutador por hash. Las rutas "#/..." muestran vistas de la plataforma; cualquier otro hash
// (vacío, #panel, #pedidos, #arquitectura, #observabilidad) muestra el laboratorio original intacto.

import { $ } from './ui.js';
import { session } from './session.js';

const routes = [];
let currentCleanup = null;

/** path con parámetros ":id"; roles = roles permitidos (vacío = público, 'auth' = cualquier sesión). */
export function route(path, render, { roles = [], title = 'HexaOrders' } = {}) {
  const keys = [];
  const pattern = new RegExp(`^${path.replace(/:[a-zA-Z]+/g, (m) => { keys.push(m.slice(1)); return '([^/]+)'; })}$`);
  routes.push({ pattern, keys, render, roles, title });
}

export function navigate(hash) {
  if (window.location.hash === hash) handle(); else window.location.hash = hash;
}

function forbiddenView(root) {
  root.replaceChildren();
  const box = document.createElement('div');
  box.className = 'view';
  box.innerHTML = '<div class="card empty-state"><strong>Acceso restringido</strong><p>Tu rol no tiene acceso a esta sección. El servidor también lo impide.</p><a class="btn btn--ghost" href="#/tienda">Volver a la tienda</a></div>';
  root.append(box);
}

export async function handle() {
  const hash = window.location.hash || '';
  const lab = $('[data-view="lab"]');
  const root = $('#view-root');
  if (!hash.startsWith('#/')) {
    if (currentCleanup) { currentCleanup(); currentCleanup = null; }
    root.hidden = true;
    lab.hidden = false;
    document.title = 'HexaOrders · Order Management & Risk Assessment';
    window.dispatchEvent(new CustomEvent('hexa:route', { detail: { path: hash } }));
    return;
  }
  const [path, query = ''] = hash.slice(1).split('?');
  const match = routes.map((r) => ({ r, m: path.match(r.pattern) })).find((x) => x.m);
  lab.hidden = true;
  root.hidden = false;
  if (currentCleanup) { currentCleanup(); currentCleanup = null; }
  window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'auto' });
  window.dispatchEvent(new CustomEvent('hexa:route', { detail: { path } }));
  if (!match) { navigate('#/tienda'); return; }
  const { r, m } = match;
  if (r.roles.length) {
    if (!session.isAuthenticated()) { navigate(`#/entrar?next=${encodeURIComponent(path)}`); return; }
    if (!r.roles.includes('auth') && !session.has(...r.roles)) { forbiddenView(root); return; }
  }
  const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
  params.query = new URLSearchParams(query);
  document.title = `${r.title} · HexaOrders`;
  root.replaceChildren();
  try {
    currentCleanup = (await r.render(root, params)) || null;
  } catch (error) {
    const box = document.createElement('div');
    box.className = 'view';
    const card = document.createElement('div');
    card.className = 'card empty-state';
    const strong = document.createElement('strong');
    strong.textContent = 'No se pudo cargar la sección';
    const p = document.createElement('p');
    p.textContent = error && error.message ? error.message : String(error);
    card.append(strong, p);
    box.append(card);
    root.replaceChildren(box);
  }
  root.focus({ preventScroll: true });
}

export function startRouter() {
  window.addEventListener('hashchange', handle);
  return handle();
}
