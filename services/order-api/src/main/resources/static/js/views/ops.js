// Monitoreo (OBSERVABILITY_VIEWER y ADMIN), solo lectura. Combina:
//  · /api/ops/health: comprobaciones activas del backend contra PostgreSQL y el motor de riesgo.
//  · /api/ops/monitoring: targets, reglas de alerta e indicadores que order-api lee de Prometheus
//    por la red interna de Docker (puerto de salida MonitoringPort → PrometheusMonitoringAdapter).
// No sustituye a Grafana: enlaza a sus dashboards, que siguen siendo la herramienta principal.

import { GRAFANA_URL, PROMETHEUS_URL, opsApi } from '../api.js';
import { LIFECYCLE, emptyState, errorMessage, kpi, lifecycleBadge, skeletons } from '../components.js';
import { route } from '../router.js';
import { el, formatDateTime, formatRelative } from '../ui.js';

const ROLES = ['OBSERVABILITY_VIEWER', 'ADMIN'];

export const DASHBOARDS = [
  ['hexaorders', 'HexaOrders · Puertos, adaptadores y métricas', 'Dashboard original (conservado sin cambios).'],
  ['hexaorders-business', 'Business Operations', 'Ciclo de vida, checkout, logística e incidencias.'],
  ['hexaorders-risk', 'Risk Engine', 'Evaluaciones, latencias e integración Java → Python.'],
  ['hexaorders-api', 'API Performance', 'Tráfico, percentiles y errores HTTP.'],
  ['hexaorders-infrastructure', 'Infrastructure', 'Targets, JVM, HikariCP y scraping.'],
  ['hexaorders-security', 'Security & Audit', 'Inicios de sesión, accesos denegados y actividad.'],
  ['hexaorders-logistics', 'Logistics Operations', 'Entregas, conflictos de agenda y despacho.'],
];

const head = (title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: 'Monitoreo' }), el('h1', { class: 'view__title', text: title }),
    el('p', { class: 'section__lead', text: lead })),
  actions.length ? el('div', { class: 'row-actions' }, actions) : null);

const external = (href, text) => el('a', { class: 'btn btn--ghost btn--sm', href, target: '_blank', rel: 'noopener', text: `${text} ↗` });

function unavailableNotice() {
  return el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: 'Prometheus no responde. ' }),
    'order-api no pudo consultar http://prometheus:9090 dentro de la red de Docker. El resto de la aplicación sigue funcionando; revisa ',
    el('code', { text: '.\\hexaorders.cmd status' }), '.');
}

const STATE = { firing: ['Disparada', 'error'], pending: ['Pendiente', 'warn'], inactive: ['Inactiva', 'ok'] };
const stateBadge = (state) => { const [label, tone] = STATE[state] || [state, 'neutral']; return el('span', { class: `badge badge--${tone === 'error' ? 'error' : tone === 'warn' ? 'warn' : tone === 'ok' ? 'ok' : 'neutral'}`, text: label }); };

function formatValue(indicator) {
  if (indicator.value === null || indicator.value === undefined) return 'sin datos';
  const v = indicator.value;
  if (indicator.unit === 's') return v < 1 ? `${(v * 1000).toFixed(0)} ms` : `${v.toFixed(2)} s`;
  if (indicator.unit === '%') return `${v.toFixed(2)} %`;
  if (indicator.unit === 'MB') return `${v.toFixed(0)} MB`;
  if (indicator.unit === 'req/s' || indicator.unit === '/min') return `${v.toFixed(2)} ${indicator.unit}`;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
}

// ---- Resumen --------------------------------------------------------------------------------
route('/observabilidad-tecnica', async (root) => {
  const health = el('div', { class: 'kpis' }, skeletons(4, 'skeleton--row'));
  const prom = el('div', {}, skeletons(3, 'skeleton--row'));
  const statuses = el('div', {}, skeletons(4, 'skeleton--row'));
  root.append(el('section', { class: 'view' },
    head('Salud técnica', 'Comprobaciones activas del backend y estado de Prometheus. Vista de solo lectura.',
      external(`${GRAFANA_URL}/d/hexaorders`, 'Grafana'), external(`${PROMETHEUS_URL}/targets`, 'Prometheus')),
    health,
    el('div', { class: 'grid-2-even card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Prometheus' }), prom),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos por estado (PostgreSQL)' }), statuses)),
    el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Dashboards de Grafana' }),
      el('ul', { class: 'dash-links' }, DASHBOARDS.map(([uid, title, text]) => el('li', {},
        el('a', { href: `${GRAFANA_URL}/d/${uid}`, target: '_blank', rel: 'noopener' }, el('strong', { text: `${title} ↗` }), el('small', { text }))))))));
  const [h, m] = await Promise.allSettled([opsApi.health(), opsApi.monitoring()]);
  if (h.status === 'fulfilled') {
    const v = h.value;
    const tile = (label, ok, detail) => el('div', { class: `kpi kpi--${ok ? 'ok' : 'error'}` }, el('span', { text: label }), el('strong', { text: ok ? 'Disponible' : 'Caído' }), el('small', { class: 'kpi__hint', text: detail }));
    health.replaceChildren(tile('PostgreSQL', v.database, 'SELECT 1 desde order-api'), tile('Motor de riesgo', v.riskEngine, 'GET /health/ready'),
      kpi('Incidencias abiertas', v.openIncidents, v.openIncidents ? 'warn' : ''), kpi('Comprobado', formatDateTime(v.checkedAt).split(',').pop().trim()));
    statuses.replaceChildren(el('ul', { class: 'activity' }, Object.keys(LIFECYCLE).map((status) => el('li', {}, lifecycleBadge(status), el('strong', { text: ` ${v.ordersByStatus[status] ?? 0}` })))));
  } else {
    health.replaceChildren(el('div', { class: 'card' }, emptyState('No se pudo consultar la salud', errorMessage(h.reason), 'shield')));
    statuses.replaceChildren();
  }
  if (m.status === 'fulfilled' && m.value.available) {
    const s = m.value;
    const up = s.targets.filter((t) => t.health === 'up').length;
    const firing = s.alerts.filter((a) => a.state === 'firing').length;
    prom.replaceChildren(el('div', { class: 'kpis kpis--compact' },
      kpi('Targets activos', `${up}/${s.targets.length}`, up === s.targets.length ? 'ok' : 'error'),
      kpi('Alertas disparadas', firing, firing ? 'error' : 'ok'), kpi('Reglas de alerta', s.alerts.length)),
    el('div', { class: 'row-actions row-actions--start' }, el('a', { class: 'btn btn--ghost btn--sm', href: '/monitoreo/servicios.html', text: 'Servicios' }),
      el('a', { class: 'btn btn--ghost btn--sm', href: '/monitoreo/alertas.html', text: 'Alertas' }), el('a', { class: 'btn btn--ghost btn--sm', href: '/monitoreo/metricas.html', text: 'Métricas' })));
  } else {
    prom.replaceChildren(m.status === 'fulfilled' ? unavailableNotice() : emptyState('No se pudo consultar el monitoreo', errorMessage(m.reason)));
  }
}, { roles: ROLES, title: 'Monitoreo' });

// ---- Servicios ------------------------------------------------------------------------------
route('/monitoreo/servicios', async (root) => {
  const body = el('div', {}, skeletons(4, 'skeleton--row'));
  const deps = el('div', { class: 'kpis' }, skeletons(2, 'skeleton--row'));
  root.append(el('section', { class: 'view' },
    head('Servicios', 'Targets que Prometheus recoge cada 10 s y comprobaciones directas del backend.', external(`${PROMETHEUS_URL}/targets`, 'Targets en Prometheus')),
    deps, el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Targets de Prometheus' }), body)));
  const [h, m] = await Promise.allSettled([opsApi.health(), opsApi.monitoring()]);
  if (h.status === 'fulfilled') {
    deps.replaceChildren(kpi('PostgreSQL (desde order-api)', h.value.database ? 'Disponible' : 'Caído', h.value.database ? 'ok' : 'error'),
      kpi('Motor de riesgo (desde order-api)', h.value.riskEngine ? 'Disponible' : 'Caído', h.value.riskEngine ? 'ok' : 'error'),
      kpi('order-api', 'Disponible', 'ok', 'Esta página la sirve order-api'));
  } else deps.replaceChildren();
  if (m.status !== 'fulfilled') { body.replaceChildren(emptyState('No se pudo consultar el monitoreo', errorMessage(m.reason))); return; }
  if (!m.value.available) { body.replaceChildren(unavailableNotice()); return; }
  body.replaceChildren(el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
    el('thead', {}, el('tr', {}, ['Job', 'Instancia', 'Estado', 'Último scrape', 'Duración', 'Error'].map((x) => el('th', { scope: 'col', text: x })))),
    el('tbody', {}, m.value.targets.map((t) => el('tr', {},
      el('td', {}, el('strong', { text: t.job })), el('td', { class: 'mono small', text: t.instance }),
      el('td', {}, el('span', { class: `badge badge--${t.health === 'up' ? 'ok' : 'error'}`, text: t.health.toUpperCase() })),
      el('td', { text: t.lastScrape ? formatRelative(t.lastScrape) : '—' }),
      el('td', { class: 'mono', text: `${(t.scrapeSeconds * 1000).toFixed(1)} ms` }),
      el('td', { class: 'small', text: t.lastError || '—' })))))));
}, { roles: ROLES, title: 'Servicios' });

// ---- Métricas -------------------------------------------------------------------------------
route('/monitoreo/metricas', async (root) => {
  const grid = el('div', { class: 'metric-grid' }, skeletons(8, 'skeleton--row'));
  const checked = el('p', { class: 'muted small' });
  root.append(el('section', { class: 'view' },
    head('Métricas', 'Indicadores calculados por Prometheus con consultas fijas definidas en el adaptador (no se aceptan consultas arbitrarias desde el navegador).',
      external(`${GRAFANA_URL}/d/hexaorders-business`, 'Business Operations'), external(`${GRAFANA_URL}/d/hexaorders-api`, 'API Performance')),
    checked, grid,
    el('p', { class: 'muted small card--spaced', text: '«sin datos» significa que todavía no hay muestras (por ejemplo, sin tráfico en los últimos 5 minutos), nunca un cero inventado.' })));
  const s = await opsApi.monitoring();
  if (!s.available) { grid.replaceChildren(unavailableNotice()); return; }
  checked.textContent = `Consultado ${formatDateTime(s.checkedAt)}`;
  grid.replaceChildren(...s.indicators.map((i) => el('article', { class: `metric-card${i.value === null ? ' is-empty' : ''}` },
    el('span', { class: 'metric-card__label', text: i.label }), el('strong', { class: 'metric-card__value', text: formatValue(i) }),
    el('code', { class: 'metric-card__key', text: i.key }))));
}, { roles: ROLES, title: 'Métricas' });

// ---- Alertas --------------------------------------------------------------------------------
route('/monitoreo/alertas', async (root) => {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  const summary = el('div', { class: 'kpis' });
  root.append(el('section', { class: 'view' },
    head('Alertas', 'Reglas de alerta cargadas en Prometheus (infra/prometheus/alerts.yml) y su estado actual. No hay Alertmanager ni envío de notificaciones.',
      external(`${PROMETHEUS_URL}/alerts`, 'Alertas en Prometheus')),
    summary, el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Reglas' }), body)));
  const s = await opsApi.monitoring();
  if (!s.available) { body.replaceChildren(unavailableNotice()); return; }
  const by = (state) => s.alerts.filter((a) => a.state === state).length;
  summary.replaceChildren(kpi('Disparadas', by('firing'), by('firing') ? 'error' : 'ok'), kpi('Pendientes', by('pending'), by('pending') ? 'warn' : ''),
    kpi('Inactivas', by('inactive'), 'ok'), kpi('Total de reglas', s.alerts.length));
  const order = { firing: 0, pending: 1, inactive: 2 };
  body.replaceChildren(el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
    el('thead', {}, el('tr', {}, ['Alerta', 'Grupo', 'Severidad', 'Estado', 'Activa desde', 'Descripción'].map((x) => el('th', { scope: 'col', text: x })))),
    el('tbody', {}, [...s.alerts].sort((a, b) => (order[a.state] ?? 3) - (order[b.state] ?? 3)).map((a) => el('tr', {},
      el('td', {}, el('strong', { text: a.name })), el('td', { class: 'small', text: a.group }),
      el('td', {}, el('span', { class: `badge badge--${a.severity === 'critical' ? 'error' : a.severity === 'warning' ? 'warn' : 'neutral'}`, text: a.severity })),
      el('td', {}, stateBadge(a.state)), el('td', { text: a.activeSince ? formatRelative(a.activeSince) : '—' }),
      el('td', { class: 'small', text: a.summary || '—' })))))));
}, { roles: ROLES, title: 'Alertas' });
