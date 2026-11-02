// Observabilidad técnica (OBSERVABILITY_VIEWER y ADMIN): solo lectura. Combina la comprobación
// del backend (/api/ops/health) con los enlaces a Prometheus y a los dashboards de Grafana.

import { GRAFANA_URL, PROMETHEUS_URL, opsApi } from '../api.js';
import { LIFECYCLE, emptyState, errorMessage, icon, lifecycleBadge } from '../components.js';
import { route } from '../router.js';
import { el, formatDateTime } from '../ui.js';

const DASHBOARDS = [
  ['hexaorders', 'HexaOrders · Puertos, adaptadores y métricas', 'Dashboard original (conservado sin cambios).'],
  ['hexaorders-business', 'Business Operations', 'Ciclo de vida, checkout, logística e incidencias.'],
  ['hexaorders-risk', 'Risk Engine', 'Evaluaciones, latencias e integración Java → Python.'],
  ['hexaorders-api', 'API Performance', 'Tráfico, percentiles y errores HTTP.'],
  ['hexaorders-infrastructure', 'Infrastructure', 'Targets, JVM, HikariCP y scraping.'],
  ['hexaorders-security', 'Security & Audit', 'Inicios de sesión, accesos denegados y actividad.'],
];

route('/observabilidad-tecnica', async (root) => {
  const health = el('div', { class: 'kpis' });
  const statuses = el('div', {});
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Observability' }), el('h1', { class: 'view__title', text: 'Salud técnica' }),
      el('p', { class: 'section__lead', text: 'Vista de solo lectura. Las comprobaciones de abajo las ejecuta el backend contra PostgreSQL y el motor de riesgo; no dependen de Prometheus.' })),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--ghost btn--sm', href: '#observabilidad', text: 'Métricas en vivo (laboratorio)' }),
        el('a', { class: 'btn btn--ghost btn--sm', href: `${PROMETHEUS_URL}/alerts`, target: '_blank', rel: 'noopener', text: 'Alertas ↗' }))),
    health,
    el('div', { class: 'grid-2-even' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos por estado (PostgreSQL)' }), statuses),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Dashboards de Grafana' }),
        el('ul', { class: 'activity' }, DASHBOARDS.map(([uid, title, text]) => el('li', {},
          el('a', { href: `${GRAFANA_URL}/d/${uid}`, target: '_blank', rel: 'noopener', text: `${title} ↗` }), el('small', { text }))))))));
  try {
    const h = await opsApi.health();
    const tile = (label, ok, detail) => el('div', { class: `kpi kpi--${ok ? 'ok' : 'error'}` }, el('span', { text: label }), el('strong', { text: ok ? 'Disponible' : 'Caído' }), el('span', { text: detail }));
    health.replaceChildren(
      tile('PostgreSQL', h.database, 'SELECT 1 desde order-api'),
      tile('Motor de riesgo', h.riskEngine, 'GET /health/ready'),
      el('div', { class: 'kpi kpi--violet' }, el('span', { text: 'Incidencias abiertas' }), el('strong', { text: String(h.openIncidents) })),
      el('div', { class: 'kpi' }, el('span', { text: 'Comprobado' }), el('strong', { text: formatDateTime(h.checkedAt).split(',').pop().trim() })));
    statuses.replaceChildren(el('ul', { class: 'activity' }, Object.keys(LIFECYCLE).map((status) => el('li', {},
      lifecycleBadge(status), el('strong', { text: ` ${h.ordersByStatus[status] ?? 0}` })))));
  } catch (error) {
    health.replaceChildren(el('div', { class: 'card' }, emptyState('No se pudo consultar la salud', errorMessage(error), 'shield')));
  }
  void icon;
}, { roles: ['OBSERVABILITY_VIEWER', 'ADMIN'], title: 'Observabilidad técnica' });
