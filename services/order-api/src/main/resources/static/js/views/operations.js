// Operaciones (ORDER_OPERATOR y ADMIN): resumen, bandeja de pedidos, revisiones de riesgo e
// incidencias. Cada cambio de estado pasa por el caso de uso del backend (POST …/transitions),
// que valida la política del dominio, el rol, la versión (concurrencia) y deja historial y auditoría.

import { operationsApi } from '../api.js';
import {
  LIFECYCLE, emptyState, errorMessage, field, formError, kpi, lifecycleBadge, modal, money, paginated, searchBox, skeletons, submitting,
} from '../components.js';
import { route } from '../router.js';
import { el, formatDateTime, formatRelative } from '../ui.js';
import { incidentsPanel, orderDetailModal, ordersTable, statusFilter } from './admin.js';

const head = (title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: 'Operaciones' }), el('h1', { class: 'view__title', text: title }),
    el('p', { class: 'section__lead', text: lead })),
  actions.length ? el('div', { class: 'row-actions' }, actions) : null);

const ROLES = { roles: ['ORDER_OPERATOR', 'ADMIN'] };

// ---- Resumen -----------------------------------------------------------------------------
route('/operaciones', async (root) => {
  const counters = el('div', { class: 'kpis' }, skeletons(6, 'skeleton--row'));
  const oldest = el('div', {}, skeletons(4, 'skeleton--row'));
  const statusList = el('div', {}, skeletons(4, 'skeleton--row'));
  root.append(el('section', { class: 'view' },
    head('Resumen de operaciones', 'Estado de la cola de pedidos y lo que requiere atención ahora.',
      el('a', { class: 'btn btn--primary btn--sm', href: '/operaciones/revisiones.html', text: 'Revisar riesgo' }),
      el('a', { class: 'btn btn--ghost btn--sm', href: '/operaciones/pedidos.html', text: 'Bandeja completa' })),
    counters,
    el('div', { class: 'grid-2 card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos que esperan más tiempo' }), oldest),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Distribución por estado' }), statusList))));
  const all = await operationsApi.queue([]);
  const count = (s) => all.filter((o) => o.status === s).length;
  counters.replaceChildren(
    kpi('En revisión de riesgo', count('RISK_REVIEW'), count('RISK_REVIEW') ? 'warn' : 'ok', 'Requieren decisión humana'),
    kpi('Aprobados por riesgo', count('RISK_APPROVED'), 'ok', 'Pendientes de confirmar'),
    kpi('Confirmados', count('CONFIRMED'), 'violet'),
    kpi('En preparación', count('PREPARING'), 'violet'),
    kpi('Listos para despacho', count('READY_FOR_DISPATCH'), 'blue'),
    kpi('Rechazados / cancelados', count('REJECTED') + count('CANCELLED'), count('REJECTED') + count('CANCELLED') ? 'error' : ''));
  const waiting = all.filter((o) => ['RISK_REVIEW', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING'].includes(o.status))
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)).slice(0, 6);
  oldest.replaceChildren(waiting.length ? el('ul', { class: 'activity' }, waiting.map((o) => el('li', {},
    lifecycleBadge(o.status), el('strong', { text: ` ${o.customerId} · ${money(o.amount)}` }),
    el('small', { text: `creado ${formatRelative(o.createdAt)} · riesgo ${o.riskScore}/100` }),
    el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Gestionar', onclick: () => orderDetailModal(o.id, operationsApi, () => window.location.reload()) }))))
    : emptyState('Cola al día', 'No hay pedidos esperando acciones de operaciones.', 'shield'));
  statusList.replaceChildren(el('ul', { class: 'activity' }, Object.keys(LIFECYCLE).map((s) => el('li', {}, lifecycleBadge(s), el('strong', { text: ` ${count(s)}` })))));
}, { ...ROLES, title: 'Operaciones' });

// ---- Bandeja de pedidos -------------------------------------------------------------------
route('/operaciones/pedidos', async (root) => {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  let statuses = ['RISK_REVIEW', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING'];
  let text = '';
  let orders = [];
  const draw = () => {
    const list = orders.filter((o) => !text || `${o.id} ${o.customerId}`.toLowerCase().includes(text));
    body.replaceChildren(list.length ? paginated(list, (page) => ordersTable(page, operationsApi, load), { pageSize: 12, label: 'pedidos' })
      : emptyState('Sin pedidos en este filtro', 'Cambia el estado o la búsqueda.', 'box'));
  };
  async function load() {
    body.replaceChildren(...skeletons(6, 'skeleton--row'));
    try { orders = await operationsApi.queue(statuses); draw(); }
    catch (error) { body.replaceChildren(emptyState('No se pudo cargar la bandeja', errorMessage(error))); }
  }
  root.append(el('section', { class: 'view' },
    head('Bandeja de pedidos', 'Confirma, prepara y marca pedidos listos para despacho. Solo aparecen las acciones que tu rol puede ejecutar.'),
    el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Pedidos' }),
      el('div', { class: 'row-actions' }, searchBox('Buscar por cliente o ID', (value) => { text = value; draw(); }),
        statusFilter((value) => { statuses = value; load(); }, statuses))), body)));
  await load();
}, { ...ROLES, title: 'Bandeja de pedidos' });

// ---- Revisiones de riesgo -----------------------------------------------------------------
function decisionModal(order, target, onDone) {
  const note = el('textarea', { maxlength: '500', rows: '3', placeholder: target === 'REJECTED' ? 'Motivo del rechazo (recomendado)' : 'Comentario de la revisión (opcional)' });
  const errorNode = formError();
  const save = el('button', { type: 'button', class: target === 'REJECTED' ? 'btn btn--ghost' : 'btn btn--primary', text: target === 'REJECTED' ? 'Rechazar pedido' : 'Confirmar pedido' });
  const dialog = modal(`${target === 'REJECTED' ? 'Rechazar' : 'Confirmar'} pedido ${order.id.slice(0, 8)}`, el('div', {},
    el('p', { class: 'muted small', text: `${order.customerId} · ${money(order.amount)} · riesgo ${order.riskScore}/100 · creado ${formatDateTime(order.createdAt)}` }),
    target === 'REJECTED' ? el('p', { class: 'muted small', text: 'Al rechazar un pedido del portal, sus unidades vuelven al stock.' }) : null,
    field('Nota (queda en el historial)', note), errorNode),
  { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => operationsApi.transition(order.id, target, note.value), target === 'REJECTED' ? 'Pedido rechazado' : 'Pedido confirmado')
      .then(() => { dialog.close(); onDone(); }).catch(() => {});
  });
}

route('/operaciones/revisiones', async (root) => {
  const body = el('div', {}, skeletons(4, 'skeleton--card'));
  root.append(el('section', { class: 'view' },
    head('Revisiones de riesgo', 'Pedidos con puntaje ≥ 70 que el motor de riesgo (Python) envió a revisión. La decisión es humana y queda auditada.'),
    body));
  async function load() {
    body.replaceChildren(...skeletons(4, 'skeleton--card'));
    try {
      const orders = (await operationsApi.queue(['RISK_REVIEW'])).sort((a, b) => b.riskScore - a.riskScore);
      body.replaceChildren(orders.length ? paginated(orders, (page) => el('div', { class: 'review-grid' }, page.map((o) => el('article', { class: 'card review-card' },
        el('div', { class: 'card__head' }, el('strong', { text: o.customerId }), el('span', { class: 'badge badge--warn', text: `Riesgo ${o.riskScore}/100` })),
        el('p', { class: 'review-card__amount', text: money(o.amount) }),
        el('p', { class: 'muted small', text: `${o.id.slice(0, 8)} · ${o.channel === 'SHOP' ? 'Portal' : 'Laboratorio'} · ${formatRelative(o.createdAt)}` }),
        el('div', { class: 'row-actions' },
          el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Confirmar', onclick: () => decisionModal(o, 'CONFIRMED', load) }),
          el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Rechazar', onclick: () => decisionModal(o, 'REJECTED', load) }),
          el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Detalle', onclick: () => orderDetailModal(o.id, operationsApi, load) }))))),
      { pageSize: 9, label: 'pedidos en revisión' })
        : el('div', { class: 'card' }, emptyState('Sin pedidos en revisión', 'El motor de riesgo no ha marcado pedidos pendientes.', 'shield')));
    } catch (error) {
      body.replaceChildren(el('div', { class: 'card' }, emptyState('No se pudieron cargar las revisiones', errorMessage(error))));
    }
  }
  await load();
}, { ...ROLES, title: 'Revisiones de riesgo' });

// ---- Incidencias ---------------------------------------------------------------------------
route('/operaciones/incidencias', async (root) => {
  const container = el('div', {});
  root.append(el('section', { class: 'view' },
    head('Incidencias', 'Registra y resuelve incidencias (daños, retrasos, dirección, stock). Cada acción queda en la auditoría.'),
    container));
  await incidentsPanel(container, true);
}, { roles: ['ORDER_OPERATOR', 'LOGISTICS_OPERATOR', 'ADMIN'], title: 'Incidencias' });
