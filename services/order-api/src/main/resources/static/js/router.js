// Navegación de POLYMER (sitio multipágina).
//
// v4 — sitio multipágina: cada página HTML real (catalogo.html, admin/usuarios.html…) declara en
// <body data-route="…"> qué vista renderiza, y esta capa la busca en el registro de rutas.
// Las vistas siguen registrándose con route(path, render) igual que en la v3, así que una misma
// vista sirve para su página HTML y para la URL antigua con hash (#/tienda, #/admin/usuarios…),
// que ahora redirige a la página real para no romper enlaces guardados.

import { session } from './session.js';

const routes = [];
let current = null;

/** path con parámetros ":id"; roles = roles permitidos (vacío = público, 'auth' = cualquier sesión). */
export function route(path, render, { roles = [], title = 'POLYMER' } = {}) {
  const keys = [];
  const pattern = new RegExp(`^${path.replace(/:[a-zA-Z]+/g, (m) => { keys.push(m.slice(1)); return '([^/]+)'; })}$`);
  routes.push({ path, pattern, keys, render, roles, title });
}

// ---- Ruta lógica → página HTML ---------------------------------------------------------------
// Cada entrada: patrón de ruta lógica y función que construye la URL de la página real.
const ADMIN_TAB = (tab) => (tab === 'panel' ? 'dashboard' : tab);
const PAGE_URLS = [
  [/^\/inicio$/, () => '/'],
  [/^\/tienda$/, () => '/catalogo.html'],
  [/^\/producto\/([^/]+)$/, (id) => `/producto.html?id=${encodeURIComponent(id)}`],
  [/^\/carrito$/, () => '/carrito.html'],
  [/^\/ofertas$/, () => '/ofertas.html'],
  [/^\/ayuda$/, () => '/ayuda.html'],
  [/^\/empresa$/, () => '/empresa.html'],
  [/^\/legal$/, () => '/legal.html'],
  [/^\/libro-reclamaciones$/, () => '/libro-reclamaciones.html'],
  [/^\/checkout$/, () => '/checkout.html'],
  [/^\/entrar$/, () => '/login.html'],
  [/^\/registro$/, () => '/registro.html'],
  [/^\/nosotros$/, () => '/nosotros.html'],
  [/^\/contacto$/, () => '/contacto.html'],
  [/^\/cliente$/, () => '/cliente/dashboard.html'],
  [/^\/mis-pedidos$/, () => '/cliente/pedidos.html'],
  [/^\/mis-pedidos\/([^/]+)$/, (id) => `/cliente/pedido-detalle.html?id=${encodeURIComponent(id)}`],
  [/^\/seguimiento\/([^/]+)$/, (id) => `/cliente/seguimiento.html?id=${encodeURIComponent(id)}`],
  [/^\/perfil$/, () => '/cliente/perfil.html'],
  [/^\/admin$/, () => '/admin/dashboard.html'],
  [/^\/admin\/([a-z-]+)$/, (tab) => `/admin/${ADMIN_TAB(tab)}.html`],
  [/^\/operaciones$/, () => '/operaciones/dashboard.html'],
  [/^\/operaciones\/([a-z-]+)$/, (tab) => `/operaciones/${tab}.html`],
  [/^\/logistica$/, () => '/logistica/dashboard.html'],
  [/^\/logistica\/([a-z-]+)$/, (tab) => `/logistica/${tab}.html`],
  [/^\/observabilidad-tecnica$/, () => '/monitoreo/dashboard.html'],
  [/^\/monitoreo$/, () => '/monitoreo/dashboard.html'],
  [/^\/monitoreo\/([a-z-]+)$/, (tab) => `/monitoreo/${tab}.html`],
  [/^\/arquitectura$/, () => '/arquitectura/overview.html'],
  [/^\/arquitectura\/([a-z-]+)$/, (tab) => `/arquitectura/${tab}.html`],
];

/**
 * Convierte una ruta lógica ("/tienda?categoria=2", "#/mis-pedidos/abc") en la URL de su página
 * HTML. Las URLs que ya son páginas ("/catalogo.html", "/laboratorio.html#panel") no cambian.
 */
export function link(target) {
  let value = String(target || '/');
  if (value.startsWith('#/')) value = value.slice(1);
  if (!value.startsWith('/') || /\.html(\?|#|$)/.test(value) || value === '/') return value;
  const [path, query = ''] = value.split('?');
  for (const [pattern, build] of PAGE_URLS) {
    const match = path.match(pattern);
    if (!match) continue;
    const url = build(...match.slice(1).map(decodeURIComponent));
    if (!query) return url;
    return url + (url.includes('?') ? '&' : '?') + query;
  }
  return value;
}

/** Navega a una ruta lógica o a una página. */
export function navigate(target) {
  const url = link(target);
  if (url === window.location.pathname + window.location.search) { rerender(); return; }
  window.location.assign(url);
}

/** Vuelve a renderizar la vista actual (tras guardar un formulario, por ejemplo). */
export function rerender() {
  if (current) render(current.route, current.params);
}

function messageView(root, title, text, href, label) {
  const box = document.createElement('section');
  box.className = 'view';
  const card = document.createElement('div');
  card.className = 'card empty-state';
  const strong = document.createElement('strong');
  strong.textContent = title;
  const p = document.createElement('p');
  p.textContent = text;
  card.append(strong, p);
  if (href) {
    const a = document.createElement('a');
    a.className = 'btn btn--ghost';
    a.href = href;
    a.textContent = label;
    card.append(a);
  }
  box.append(card);
  root.replaceChildren(box);
}

async function render(r, params) {
  const root = document.getElementById('view-root');
  if (current && current.cleanup) { try { current.cleanup(); } catch { /* sin limpieza */ } }
  current = { route: r, params, cleanup: null };
  if (r.roles.length) {
    if (!session.isAuthenticated()) {
      const here = window.location.pathname + window.location.search;
      window.location.replace(`/login.html?next=${encodeURIComponent(here)}`);
      return;
    }
    if (!r.roles.includes('auth') && !session.has(...r.roles)) {
      messageView(root, 'Acceso restringido', 'Tu rol no tiene acceso a esta sección. El servidor también lo impide.', '/', 'Volver al inicio');
      return;
    }
  }
  root.replaceChildren();
  root.setAttribute('aria-busy', 'true');
  try {
    current.cleanup = (await r.render(root, params)) || null;
  } catch (error) {
    const status = error && error.status;
    if (status === 404) messageView(root, 'No encontrado', error.message || 'El recurso no existe o no te pertenece.', '/', 'Volver al inicio');
    else messageView(root, 'No se pudo cargar la sección', error && error.message ? error.message : String(error), window.location.href, 'Reintentar');
  } finally {
    root.removeAttribute('aria-busy');
  }
}

/**
 * Arranque de una página multipágina: busca la vista registrada con ese path exacto y toma los
 * parámetros (":id", ":tab") de la query string o de data-params del <body>.
 */
export async function startPage(path) {
  const r = routes.find((candidate) => candidate.path === path);
  const root = document.getElementById('view-root');
  if (!r) { messageView(root, 'Página no disponible', `No hay ninguna vista registrada para ${path}.`, '/', 'Volver al inicio'); return; }
  const query = new URLSearchParams(window.location.search);
  const fixed = JSON.parse(document.body.dataset.params || '{}');
  const params = { query };
  for (const key of r.keys) params[key] = fixed[key] ?? query.get(key) ?? '';
  if (r.keys.includes('id') && !params.id) {
    messageView(root, 'Falta el identificador', 'Esta página necesita un parámetro ?id= en la URL.', '/', 'Volver al inicio');
    return;
  }
  document.title = `${document.body.dataset.title || r.title} · POLYMER`;
  await render(r, params);
}

/**
 * Compatibilidad con la v3: una URL con hash de la plataforma (#/tienda, #/admin/usuarios…)
 * redirige a su página real. Devuelve true si redirige.
 */
export function redirectLegacyHash() {
  const hash = window.location.hash || '';
  if (!hash.startsWith('#/')) return false;
  window.location.replace(link(hash));
  return true;
}

/** Laboratorio (laboratorio.html): solo atiende la compatibilidad de los hashes antiguos. */
export function startRouter() {
  window.addEventListener('hashchange', redirectLegacyHash);
  redirectLegacyHash();
}
