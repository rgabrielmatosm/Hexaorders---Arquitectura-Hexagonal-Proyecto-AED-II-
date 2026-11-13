// Observabilidad en vivo. Cada dato indica su origen; si Prometheus no responde se avisa
// sin bloquear el resto de la aplicación. Solo se consultan métricas que el código expone:
//  - Java (Micrometer): orders_created_total{status}, orders_risk_failures_total,
//    http_server_requests_seconds_* (Spring Boot Actuator).
//  - Python (prometheus_client): risk_evaluations_total{decision}, risk_evaluation_duration_seconds_*.

import { GRAFANA_URL, PROMETHEUS_URL, grafanaReachable, prometheusApi } from './api.js';
import { $, el, formatDateTime } from './ui.js';

const METRICS = [
  { label: 'Pedidos creados (aprobados)', promql: 'sum(orders_created_total{status="APPROVED"})', format: 'int' },
  { label: 'Pedidos creados (revisión)', promql: 'sum(orders_created_total{status="REVIEW"})', format: 'int' },
  { label: 'Fallos del motor de riesgo', promql: 'sum(orders_risk_failures_total)', format: 'int' },
  { label: 'Evaluaciones en Python', promql: 'sum(risk_evaluations_total)', format: 'int' },
  { label: 'Peticiones /api/orders por segundo (5 min)', promql: 'sum(rate(http_server_requests_seconds_count{uri=~"/api/orders.*"}[5m]))', format: 'rate' },
  { label: 'Respuestas 5xx por segundo (5 min)', promql: 'sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))', format: 'rate' },
  { label: 'Latencia p95 /api/orders (5 min)', promql: 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri=~"/api/orders.*"}[5m])))', format: 'seconds' },
];

const JOB_NAMES = { 'order-api': 'API Java (order-api)', 'risk-engine': 'Motor de riesgo Python (risk-engine)', prometheus: 'Prometheus' };

let apiState = { ok: null, detail: 'Sin comprobar todavía', at: null };

function formatValue(value, format) {
  if (value === null || Number.isNaN(value)) return 'sin datos';
  if (format === 'int') return String(Math.round(value));
  if (format === 'rate') return `${value.toFixed(3)} /s`;
  if (format === 'seconds') return value < 1 ? `${(value * 1000).toFixed(0)} ms` : `${value.toFixed(2)} s`;
  return String(value);
}

function healthItem(name, source, kind, statusText) {
  return el('li', {},
    el('span', { class: `dot dot--${kind}` }),
    el('span', { class: 'health__name' }, name, el('span', { class: 'health__source', text: source })),
    el('span', { class: `badge badge--${kind === 'ok' ? 'ok' : kind === 'warn' ? 'warn' : kind === 'error' ? 'error' : 'neutral'}`, text: statusText }));
}

function renderHealth({ targets, prometheusOk, grafanaOk }) {
  const items = [];
  const api = apiState.ok === null ? ['pending', 'comprobando'] : apiState.ok ? ['ok', 'responde'] : ['error', 'sin respuesta'];
  items.push(healthItem('API Java (Spring Boot)', 'Última llamada del navegador a /api/orders/statistics', api[0], api[1]));
  const db = apiState.ok === null ? ['pending', 'comprobando'] : apiState.ok ? ['ok', 'consulta correcta'] : ['warn', 'desconocido'];
  items.push(healthItem('PostgreSQL', 'Indirecto: la API respondió a una consulta agregada sobre la tabla orders', db[0], db[1]));

  const target = (job) => targets && targets.find((item) => item.labels.job === job);
  const risk = target('risk-engine');
  const riskState = !prometheusOk ? ['warn', 'no comprobable'] : !risk ? ['warn', 'sin target'] : risk.health === 'up' ? ['ok', 'up'] : ['error', risk.health];
  items.push(healthItem('Motor de riesgo (Python)', 'Prometheus: métrica up del job risk-engine (scrape de /metrics)', riskState[0], riskState[1]));
  items.push(healthItem('Prometheus', `Consulta directa a ${PROMETHEUS_URL}/api/v1/targets`, prometheusOk ? 'ok' : 'error', prometheusOk ? 'accesible' : 'no accesible'));
  items.push(healthItem('Grafana', `Petición no-cors a ${GRAFANA_URL} (solo confirma que el servidor responde)`, grafanaOk ? 'ok' : 'error', grafanaOk ? 'accesible' : 'no accesible'));
  $('#health-list').replaceChildren(...items);
}

function renderTargets(targets) {
  const body = $('#targets-body');
  if (!targets) {
    body.replaceChildren(el('tr', {}, el('td', { colspan: '6', class: 'table__empty', text: 'Prometheus no está accesible desde el navegador.' })));
    return;
  }
  if (!targets.length) {
    body.replaceChildren(el('tr', {}, el('td', { colspan: '6', class: 'table__empty', text: 'Prometheus no tiene targets activos.' })));
    return;
  }
  body.replaceChildren(...targets.map((target) => el('tr', {},
    el('td', {}, el('strong', { text: JOB_NAMES[target.labels.job] || target.labels.job })),
    el('td', { class: 'mono', text: target.scrapeUrl }),
    el('td', {}, el('span', { class: `badge ${target.health === 'up' ? 'badge--ok' : target.health === 'down' ? 'badge--error' : 'badge--neutral'}`, text: target.health })),
    el('td', { text: target.lastScrape && !target.lastScrape.startsWith('0001') ? formatDateTime(target.lastScrape) : '—' }),
    el('td', { class: 'mono', text: target.lastScrapeDuration ? `${(target.lastScrapeDuration * 1000).toFixed(1)} ms` : '—' }),
    el('td', { class: 'small', text: target.lastError || '—' }))));
}

async function renderMetrics(prometheusOk) {
  const list = $('#metrics-list');
  if (!prometheusOk) {
    list.replaceChildren(el('dt', { text: 'Métricas no disponibles: Prometheus no responde.' }), el('dd', { text: '—' }));
    return;
  }
  const results = await Promise.all(METRICS.map(async (metric) => {
    try {
      const { data } = await prometheusApi.query(metric.promql);
      const sample = data && data.data && data.data.result && data.data.result[0];
      return sample ? Number(sample.value[1]) : null;
    } catch {
      return null;
    }
  }));
  list.replaceChildren(...METRICS.flatMap((metric, index) => [
    el('dt', {}, metric.label, el('code', { text: metric.promql.length > 64 ? `${metric.promql.slice(0, 61)}…` : metric.promql, title: metric.promql })),
    el('dd', { text: formatValue(results[index], metric.format) }),
  ]));
}

export async function refreshObservability() {
  let targets = null;
  let prometheusOk = false;
  try {
    const { data } = await prometheusApi.targets();
    targets = (data && data.data && data.data.activeTargets) || [];
    prometheusOk = true;
  } catch {
    prometheusOk = false;
  }
  const grafanaOk = await grafanaReachable();
  const warning = $('#obs-warning');
  const problems = [];
  if (!prometheusOk) problems.push(`Prometheus no responde en ${PROMETHEUS_URL}`);
  if (!grafanaOk) problems.push(`Grafana no responde en ${GRAFANA_URL}`);
  warning.hidden = problems.length === 0;
  warning.textContent = problems.length ? `${problems.join(' · ')}. El resto de la aplicación sigue funcionando.` : '';
  renderHealth({ targets, prometheusOk, grafanaOk });
  renderTargets(prometheusOk ? targets : null);
  await renderMetrics(prometheusOk);
  $('#targets-updated').textContent = `Actualizado ${new Date().toLocaleTimeString('es-ES')}`;
  return { prometheusOk, grafanaOk };
}

export function initObservability() {
  $('#link-grafana').href = `${GRAFANA_URL}/d/polymer`;
  $('#link-prometheus').href = `${PROMETHEUS_URL}/targets`;
  window.addEventListener('polymer:api-status', (event) => {
    apiState = event.detail;
  });
}
