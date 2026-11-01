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
