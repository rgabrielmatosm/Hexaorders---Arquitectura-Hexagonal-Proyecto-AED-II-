import { DEFAULT_LANG, lang, productName, t, td } from './i18n.js';
// Cliente HTTP del frontend: único módulo que habla con el backend y con Prometheus.
// No contiene reglas de negocio: el dominio Java es la autoridad.

export class ApiError extends Error {
  constructor(message, { status = 0, title = '', body = null } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.body = body;
  }
}

const DEFAULT_TIMEOUT_MS = 8000;

/** Petición JSON con tiempo límite. Devuelve { data, status, headers, ms }. */
export async function requestJson(url, { method = 'GET', body, timeout = DEFAULT_TIMEOUT_MS } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const started = performance.now();
  let response;
  try {
    response = await fetch(url, {
      method,
      cache: 'no-store',
      headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    const reason = error.name === 'AbortError' ? t('Tiempo de espera agotado') : t('Sin conexión con el servidor');
    throw new ApiError(reason, { status: 0 });
  } finally {
    clearTimeout(timer);
  }
  const ms = performance.now() - started;
  let data = null;
  const text = await response.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }
  if (!response.ok) {
    // Spring devuelve ProblemDetail (RFC 9457): { title, detail, status }.
    const message = (data && (data.detail || data.title)) || t('Error HTTP {0}', response.status);
    throw new ApiError(message, { status: response.status, title: (data && data.title) || '', body: data });
  }
  return { data, status: response.status, headers: response.headers, ms };
}

// ---- API de pedidos (Java) ------------------------------------------------

export const ordersApi = {
  list: () => requestJson('/api/orders'),
  statistics: () => requestJson('/api/orders/statistics'),
  get: (id) => requestJson(`/api/orders/${encodeURIComponent(id)}`),
  // El importe se envía como número JSON; Java lo recibe como BigDecimal y valida 2 decimales.
  create: (customerId, amount) => requestJson('/api/orders', {
    method: 'POST',
    body: { customerId, amount: Number(amount), currency: 'USD' },
  }),
};

// ---- Prometheus y Grafana ---------------------------------------------------
// Prometheus (puerto 9091) y Grafana (3000) se publican en el mismo host que la API.
// Prometheus admite CORS por defecto (--web.cors.origin='.*'), por eso el navegador puede consultarlo.

const host = window.location.hostname || '127.0.0.1';
const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
export const PROMETHEUS_URL = `${protocol}//${host}:9091`;
export const GRAFANA_URL = `${protocol}//${host}:3000`;

export const prometheusApi = {
  targets: () => requestJson(`${PROMETHEUS_URL}/api/v1/targets?state=active`, { timeout: 4000 }),
  query: (promql) => requestJson(`${PROMETHEUS_URL}/api/v1/query?query=${encodeURIComponent(promql)}`, { timeout: 4000 }),
};

/** Grafana no envía cabeceras CORS: una petición no-cors solo confirma que el servidor responde. */
export async function grafanaReachable() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try {
    await fetch(`${GRAFANA_URL}/api/health`, { mode: 'no-cors', cache: 'no-store', signal: controller.signal });
    return true;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

// ---- Estado de conexión compartido -----------------------------------------

/** Notifica a la interfaz el resultado de la última llamada a la API Java. */
export function reportApiStatus(ok, detail = '') {
  window.dispatchEvent(new CustomEvent('polymer:api-status', { detail: { ok, detail, at: new Date() } }));
}

// ---- Plataforma v3: escrituras con sesión y token CSRF ------------------------
// La sesión viaja en una cookie HttpOnly (el navegador la envía sola). Las escrituras añaden
// la cabecera X-CSRF-TOKEN, que se obtiene de GET /api/auth/csrf.

let csrfToken = null;

export async function refreshCsrf() {
  const { data } = await requestJson('/api/auth/csrf');
  csrfToken = data.token;
  return csrfToken;
}

export function forgetCsrf() { csrfToken = null; }

/** Petición JSON de la plataforma. method distinto de GET incluye el token CSRF (un reintento si caduca). */
export async function send(method, url, body, { headers = {}, retry = true } = {}) {
  if (method !== 'GET' && !csrfToken) await refreshCsrf();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);
  let response;
  try {
    response = await fetch(url, {
      method,
      cache: 'no-store',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(method !== 'GET' && csrfToken ? { 'X-CSRF-TOKEN': csrfToken } : {}),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    throw new ApiError(error.name === 'AbortError' ? t('Tiempo de espera agotado') : t('Sin conexión con el servidor'), { status: 0 });
  } finally {
    clearTimeout(timer);
  }
  const text = await response.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch { data = null; } }
  if (response.status === 403 && retry && data && data.title === 'Token CSRF no válido') {
    await refreshCsrf();
    return send(method, url, body, { headers, retry: false });
  }
  if (response.status === 401 && url !== '/api/auth/login') {
    window.dispatchEvent(new CustomEvent('polymer:unauthenticated'));
  }
  if (!response.ok) {
    const message = (data && (data.detail || data.title)) || t('Error HTTP {0}', response.status);
    throw new ApiError(message, { status: response.status, title: (data && data.title) || '', body: data });
  }
  return { data, status: response.status, headers: response.headers };
}

const get = (url) => send('GET', url).then((r) => r.data);
const post = (url, body, options) => send('POST', url, body ?? {}, options).then((r) => r.data);
const put = (url, body) => send('PUT', url, body).then((r) => r.data);
const qs = (params) => {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  return entries.length ? `?${new URLSearchParams(entries).toString()}` : '';
};

export const authApi = {
  me: () => get('/api/auth/me'),
  login: (username, password) => post('/api/auth/login', { username, password }),
  logout: () => post('/api/auth/logout'),
  register: (payload) => post('/api/auth/register', payload),
};

// ---- Datos del catálogo en el idioma de la página -----------------------------------------------
// Las categorías, frases comerciales, etiquetas técnicas, promociones y estados vienen en español
// de la base de datos; se traducen aquí, en un único punto, con el mismo catálogo que la interfaz.
// NO se traducen marcas, modelos, nombres de producto, SKU ni importes. Lo que no tenga traducción
// se muestra en español (alternativa válida). La administración edita siempre los datos originales.
const LONG_DESCRIPTION = /^(.+) es (.+)\. Sus características principales son (.+)\. Producto nuevo y sellado, con (\d+) meses de garantía de marca, comprobante electrónico y soporte postventa de Polymer\. Revisa la ficha técnica completa antes de comprar\.$/s;

function localizeProduct(p) {
  const originalName = p.name;
  p.name = productName(p.name, p.brand);
  for (const key of ['categoryName', 'mainCategoryName', 'promotionName', 'statusLabel', 'availabilityLabel']) p[key] = td(p[key]);
  const specs = Array.isArray(p.specs) ? p.specs : [];
  p.specs = specs.map((s) => ({ ...s, label: td(s.label), value: td(s.value) }));
  if (p.description) p.description = p.description.split(' · ').map(td).join(' · ');
  const match = typeof p.longDescription === 'string' && p.longDescription.match(LONG_DESCRIPTION);
  if (match) {
    const highlights = p.specs.slice(0, 5).map((s) => `${lang === 'de' ? s.label : s.label.toLowerCase()}: ${s.value}`).join('; ');
    p.longDescription = t('{0} es {1}. Sus características principales son {2}. Producto nuevo y sellado, con {3} meses de garantía de marca, comprobante electrónico y soporte postventa de Polymer. Revisa la ficha técnica completa antes de comprar.',
      match[1] === originalName ? p.name : match[1], td(match[2]), highlights, match[4]);
  } else if (p.longDescription && lang !== DEFAULT_LANG) {
    p.longDescriptionLang = 'es'; // texto libre sin traducción: se marca como español
  }
  return p;
}

function localizeCatalog(value) {
  if (lang === DEFAULT_LANG || value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(localizeCatalog);
  if ('sku' in value) return localizeProduct(value);
  const out = { ...value };
  if (typeof out.name === 'string' && ('slug' in out || 'count' in out)) out.name = td(out.name);
  if (typeof out.description === 'string' && 'slug' in out) out.description = td(out.description);
  if (typeof out.label === 'string' && 'key' in out) out.label = td(out.label);
  if (out.attributeLabels) out.attributeLabels = Object.fromEntries(Object.entries(out.attributeLabels).map(([k, v]) => [k, td(v)]));
  for (const [key, child] of Object.entries(out)) {
    if (child && typeof child === 'object' && key !== 'attributeLabels') out[key] = localizeCatalog(child);
  }
  return out;
}

export const catalogApi = {
  categories: () => get('/api/catalog/categories').then(localizeCatalog),
  products: (q, category) => get(`/api/catalog/products${qs({ q, category })}`).then(localizeCatalog),
  product: (id) => get(`/api/catalog/products/${encodeURIComponent(id)}`).then(localizeCatalog),
  related: (id, limit = 8) => get(`/api/catalog/products/${encodeURIComponent(id)}/related?limit=${limit}`).then(localizeCatalog),
  promotions: () => get('/api/catalog/promotions').then(localizeCatalog),
  /** params: URLSearchParams o pares {clave: valor | [valores]}; los filtros técnicos van como attr.CLAVE. */
  search: (params) => {
    const search = params instanceof URLSearchParams ? params : new URLSearchParams();
    if (!(params instanceof URLSearchParams)) {
      Object.entries(params || {}).forEach(([key, value]) => {
        (Array.isArray(value) ? value : [value]).filter((v) => v !== undefined && v !== null && v !== '').forEach((v) => search.append(key, v));
      });
    }
    const text = search.toString();
    return get(`/api/catalog/search${text ? `?${text}` : ''}`).then(localizeCatalog);
  },
};

/**
 * Datos de compra (opciones de entrega y pago, resúmenes, pedidos): las etiquetas vienen del dominio en
 * español y se traducen aquí, en la presentación. Los nombres de producto de las líneas solo traducen su
 * sustantivo genérico inicial; importes, códigos e identificadores no se tocan.
 */
const SHOP_TEXT_KEYS = new Set(['label', 'eta', 'detail', 'instructions', 'deliveryLabel', 'paymentLabel']);
function localizeShop(value) {
  if (lang === DEFAULT_LANG || value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(localizeShop);
  const out = {};
  for (const [key, child] of Object.entries(value)) {
    if (typeof child === 'string' && SHOP_TEXT_KEYS.has(key)) out[key] = td(child);
    else if (typeof child === 'string' && key === 'productName') out[key] = productName(child);
    else out[key] = child && typeof child === 'object' ? localizeShop(child) : child;
  }
  return out;
}

export const shopApi = {
  quote: (items) => post('/api/shop/quote', { items }).then(localizeShop),
  /** La misma Idempotency-Key en un reintento devuelve el mismo pedido (nunca se duplica). */
  place: (items, idempotencyKey, shipping) => send('POST', '/api/shop/orders', shipping ? { items, shipping } : { items },
    { headers: { 'Idempotency-Key': idempotencyKey } }),
  /** v6: totales con promociones y envío según el método elegido (también para visitantes). */
  summary: (items, deliveryMethod, paymentMethod) => post('/api/shop/checkout-summary', { items, deliveryMethod, paymentMethod }).then(localizeShop),
  options: () => get('/api/shop/checkout-options').then(localizeShop),
  /** v6: entrega y pago elegidos en el checkout. */
  checkout: (payload, idempotencyKey) => send('POST', '/api/shop/orders', payload, { headers: { 'Idempotency-Key': idempotencyKey } }),
  cart: () => get('/api/shop/cart'),
  cartAdd: (productId, quantity) => post('/api/shop/cart/items', { productId, quantity }),
  cartSet: (productId, quantity) => put(`/api/shop/cart/items/${productId}`, { quantity }),
  cartRemove: (productId) => send('DELETE', `/api/shop/cart/items/${productId}`).then((r) => r.data),
  cartClear: () => send('DELETE', '/api/shop/cart').then((r) => r.data),
  cartMerge: (items) => post('/api/shop/cart/merge', { items }),
  orders: () => get('/api/shop/orders').then(localizeShop),
  order: (id) => get(`/api/shop/orders/${encodeURIComponent(id)}`).then(localizeShop),
  cancel: (id) => post(`/api/shop/orders/${encodeURIComponent(id)}/cancel`),
};

export const accountApi = {
  me: () => get('/api/account/me'),
  profile: (displayName, email) => put('/api/account/profile', { displayName, email }),
  password: (currentPassword, newPassword) => send('PUT', '/api/account/password', { currentPassword, newPassword }),
  /** Idioma preferido (correos de notificación). */
  locale: (language) => send('PUT', '/api/account/locale', { language }),
};

export const adminApi = {
  dashboard: () => get('/api/admin/dashboard'),
  users: () => get('/api/admin/users'),
  roles: (id, roles) => put(`/api/admin/users/${id}/roles`, { roles }),
  enabled: (id, enabled) => put(`/api/admin/users/${id}/enabled`, { enabled }),
  audit: (limit = 150) => get(`/api/admin/audit?limit=${limit}`),
  products: () => get('/api/admin/products'),
  createProduct: (product) => post('/api/admin/products', product),
  updateProduct: (id, product) => put(`/api/admin/products/${id}`, product),
  adjustStock: (id, delta, reason) => post(`/api/admin/products/${id}/stock`, { delta, reason }),
  createCategory: (slug, name, parentId) => post('/api/admin/categories', { slug, name, parentId: parentId || null }),
  categories: () => get('/api/admin/categories'),
  promotions: () => get('/api/admin/promotions'),
  createPromotion: (promotion) => post('/api/admin/promotions', promotion),
  updatePromotion: (id, promotion) => put(`/api/admin/promotions/${id}`, promotion),
  setPromotionActive: (id, enabled) => put(`/api/admin/promotions/${id}/active`, { enabled }),
  contactMessages: (limit = 50) => get(`/api/admin/contact-messages?limit=${limit}`),
};

export const operationsApi = {
  queue: (statuses) => get(`/api/operations/orders${qs({ status: (statuses || []).join(',') })}`),
  order: (id) => get(`/api/operations/orders/${encodeURIComponent(id)}`),
  transition: (id, status, note) => post(`/api/operations/orders/${encodeURIComponent(id)}/transitions`, { status, note }),
};

export const incidentsApi = {
  list: (status) => get(`/api/incidents${qs({ status })}`),
  report: (orderId, category, description) => post('/api/incidents', { orderId: orderId || null, category, description }),
  resolve: (id, resolution) => post(`/api/incidents/${id}/resolve`, { resolution }),
};

export const logisticsApi = {
  deliveries: (from, to, assigneeId) => get(`/api/logistics/deliveries${qs({ from, to, assigneeId })}`),
  assignees: () => get('/api/logistics/assignees'),
  schedulable: () => get('/api/logistics/schedulable-orders'),
  schedule: (payload) => post('/api/logistics/deliveries', payload),
  reschedule: (id, payload) => put(`/api/logistics/deliveries/${id}`, payload),
  history: (id) => get(`/api/logistics/deliveries/${id}/history`),
  transition: (id, status, note) => post(`/api/logistics/orders/${encodeURIComponent(id)}/transitions`, { status, note }),
};

export const opsApi = {
  health: () => get('/api/ops/health'),
  /** Targets, alertas e indicadores que order-api lee de Prometheus por la red interna de Docker. */
  monitoring: () => get('/api/ops/monitoring'),
};

export const contactApi = { send: (payload) => post('/api/contact', payload) };
