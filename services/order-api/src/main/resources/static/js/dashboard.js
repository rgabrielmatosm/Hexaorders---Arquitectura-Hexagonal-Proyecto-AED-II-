// Panel: estadísticas de GET /api/orders/statistics (todos los pedidos en PostgreSQL)
// y últimos pedidos de GET /api/orders. No se calcula ni inventa ningún dato.

import { $, el, formatInt, formatMoney, formatRelative, setStat, statusBadge } from './ui.js';

const RECENT_LIMIT = 6;

export function renderStatistics(stats) {
  setStat($('#stat-total'), formatInt(stats.total));
  setStat($('#stat-approved'), formatInt(stats.approved));
  setStat($('#stat-review'), formatInt(stats.review));
  const rate = stats.total > 0 ? (stats.review / stats.total) * 100 : 0;
  setStat($('#stat-rate'), stats.total > 0 ? `${rate.toFixed(1)} %` : '—');

  const approvedPct = stats.total > 0 ? (stats.approved / stats.total) * 100 : 0;
  const reviewPct = stats.total > 0 ? 100 - approvedPct : 0;
  $('.distribution__bar--approved').style.width = `${approvedPct}%`;
  $('.distribution__bar--review').style.width = `${reviewPct}%`;
  $('#distribution').setAttribute('aria-label',
    `Aprobados ${stats.approved} (${approvedPct.toFixed(1)} %), en revisión ${stats.review} (${reviewPct.toFixed(1)} %)`);
  $('#legend-approved').textContent = formatInt(stats.approved);
  $('#legend-review').textContent = formatInt(stats.review);
}

export function renderStatisticsError(message) {
  for (const id of ['#stat-total', '#stat-approved', '#stat-review', '#stat-rate']) {
    setStat($(id), '—');
  }
  $('#legend-approved').textContent = '—';
  $('#legend-review').textContent = '—';
  $('#distribution').setAttribute('aria-label', `Sin datos: ${message}`);
}

export function renderRecent(orders) {
  const list = $('#recent-orders');
  if (!orders.length) {
    list.replaceChildren(el('li', { class: 'recent__empty', text: 'Todavía no hay pedidos. Crea el primero en la sección Pedidos.' }));
    return;
  }
  list.replaceChildren(...orders.slice(0, RECENT_LIMIT).map((order) => el('li', {},
    el('span', { class: 'recent__customer' }, order.customerId,
      el('span', { class: 'recent__meta', text: `${formatRelative(order.createdAt)} · riesgo ${order.riskScore}/100` })),
    el('span', { class: 'recent__amount', text: formatMoney(order.amount) }),
    statusBadge(order.status))));
}

export function renderRecentError(message) {
  $('#recent-orders').replaceChildren(el('li', { class: 'recent__empty', text: `No se pudieron cargar los pedidos: ${message}` }));
}
