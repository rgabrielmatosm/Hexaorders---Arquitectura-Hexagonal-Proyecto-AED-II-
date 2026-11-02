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
    const reason = error.name === 'AbortError' ? 'Tiempo de espera agotado' : 'Sin conexión con el servidor';
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
    const message = (data && (data.detail || data.title)) || `Error HTTP ${response.status}`;
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
  window.dispatchEvent(new CustomEvent('hexa:api-status', { detail: { ok, detail, at: new Date() } }));
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
    throw new ApiError(error.name === 'AbortError' ? 'Tiempo de espera agotado' : 'Sin conexión con el servidor', { status: 0 });
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
    window.dispatchEvent(new CustomEvent('hexa:unauthenticated'));
  }
  if (!response.ok) {
    const message = (data && (data.detail || data.title)) || `Error HTTP ${response.status}`;
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

export const catalogApi = {
  categories: () => get('/api/catalog/categories'),
  products: (q, category) => get(`/api/catalog/products${qs({ q, category })}`),
  product: (id) => get(`/api/catalog/products/${encodeURIComponent(id)}`),
};

export const shopApi = {
  quote: (items) => post('/api/shop/quote', { items }),
  /** La misma Idempotency-Key en un reintento devuelve el mismo pedido (nunca se duplica). */
  place: (items, idempotencyKey, shipping) => send('POST', '/api/shop/orders', shipping ? { items, shipping } : { items },
    { headers: { 'Idempotency-Key': idempotencyKey } }),
  orders: () => get('/api/shop/orders'),
  order: (id) => get(`/api/shop/orders/${encodeURIComponent(id)}`),
  cancel: (id) => post(`/api/shop/orders/${encodeURIComponent(id)}/cancel`),
};

export const accountApi = {
  me: () => get('/api/account/me'),
  profile: (displayName, email) => put('/api/account/profile', { displayName, email }),
  password: (currentPassword, newPassword) => send('PUT', '/api/account/password', { currentPassword, newPassword }),
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
  createCategory: (slug, name) => post('/api/admin/categories', { slug, name }),
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
