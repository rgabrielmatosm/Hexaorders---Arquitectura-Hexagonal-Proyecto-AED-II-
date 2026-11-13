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
import { t } from '../i18n.js';

const head = (title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: t('Operaciones') }), el('h1', { class: 'view__title', text: title }),
    el('p', { class: 'section__lead', text: lead })),
  actions.length ? el('div', { class: 'row-actions' }, actions) : null);

const ROLES = { roles: ['ORDER_OPERATOR', 'ADMIN'] };

// ---- Resumen -----------------------------------------------------------------------------
route('/operaciones', async (root) => {
  const counters = el('div', { class: 'kpis' }, skeletons(6, 'skeleton--row'));
  const oldest = el('div', {}, skeletons(4, 'skeleton--row'));
  const statusList = el('div', {}, skeletons(4, 'skeleton--row'));
  root.append(el('section', { class: 'view' },
    head(t('Resumen de operaciones'), t('Estado de la cola de pedidos y lo que requiere atención ahora.'),
      el('a', { class: 'btn btn--primary btn--sm', href: '/operaciones/revisiones.html', text: t('Revisar riesgo') }),
      el('a', { class: 'btn btn--ghost btn--sm', href: '/operaciones/pedidos.html', text: t('Bandeja completa') })),
    counters,
    el('div', { class: 'grid-2 card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Pedidos que esperan más tiempo') }), oldest),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Distribución por estado') }), statusList))));
  const all = await operationsApi.queue([]);
  const count = (s) => all.filter((o) => o.status === s).length;
  counters.replaceChildren(
    kpi(t('En revisión de riesgo'), count('RISK_REVIEW'), count('RISK_REVIEW') ? 'warn' : 'ok', t('Requieren decisión humana')),
    kpi(t('Aprobados por riesgo'), count('RISK_APPROVED'), 'ok', t('Pendientes de confirmar')),
    kpi(t('Confirmados'), count('CONFIRMED'), 'violet'),
    kpi(t('En preparación'), count('PREPARING'), 'violet'),
    kpi(t('Listos para despacho'), count('READY_FOR_DISPATCH'), 'blue'),
    kpi(t('Rechazados / cancelados'), count('REJECTED') + count('CANCELLED'), count('REJECTED') + count('CANCELLED') ? 'error' : ''));
  const waiting = all.filter((o) => ['RISK_REVIEW', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING'].includes(o.status))
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)).slice(0, 6);
  oldest.replaceChildren(waiting.length ? el('ul', { class: 'activity' }, waiting.map((o) => el('li', {},
    lifecycleBadge(o.status), el('strong', { text: ` ${o.customerId} · ${money(o.amount)}` }),
    el('small', { text: t('creado {0} · riesgo {1}/100', formatRelative(o.createdAt), o.riskScore) }),
    el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Gestionar'), onclick: () => orderDetailModal(o.id, operationsApi, () => window.location.reload()) }))))
    : emptyState(t('Cola al día'), t('No hay pedidos esperando acciones de operaciones.'), 'shield'));
  statusList.replaceChildren(el('ul', { class: 'activity' }, Object.keys(LIFECYCLE).map((s) => el('li', {}, lifecycleBadge(s), el('strong', { text: ` ${count(s)}` })))));
}, { ...ROLES, title: t('Operaciones') });

// ---- Bandeja de pedidos -------------------------------------------------------------------
route('/operaciones/pedidos', async (root) => {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  let statuses = ['RISK_REVIEW', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING'];
  let text = '';
  let orders = [];
  const draw = () => {
    const list = orders.filter((o) => !text || `${o.id} ${o.customerId}`.toLowerCase().includes(text));
    body.replaceChildren(list.length ? paginated(list, (page) => ordersTable(page, operationsApi, load), { pageSize: 12, label: t('pedidos') })
      : emptyState(t('Sin pedidos en este filtro'), t('Cambia el estado o la búsqueda.'), 'box'));
  };
  async function load() {
    body.replaceChildren(...skeletons(6, 'skeleton--row'));
    try { orders = await operationsApi.queue(statuses); draw(); }
    catch (error) { body.replaceChildren(emptyState(t('No se pudo cargar la bandeja'), errorMessage(error))); }
  }
  root.append(el('section', { class: 'view' },
    head(t('Bandeja de pedidos'), t('Confirma, prepara y marca pedidos listos para despacho. Solo aparecen las acciones que tu rol puede ejecutar.')),
    el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: t('Pedidos') }),
      el('div', { class: 'row-actions' }, searchBox(t('Buscar por cliente o ID'), (value) => { text = value; draw(); }),
        statusFilter((value) => { statuses = value; load(); }, statuses))), body)));
  await load();
}, { ...ROLES, title: t('Bandeja de pedidos') });

// ---- Revisiones de riesgo -----------------------------------------------------------------
function decisionModal(order, target, onDone) {
  const note = el('textarea', { maxlength: '500', rows: '3', placeholder: target === 'REJECTED' ? t('Motivo del rechazo (recomendado)') : t('Comentario de la revisión (opcional)') });
  const errorNode = formError();
  const save = el('button', { type: 'button', class: target === 'REJECTED' ? 'btn btn--ghost' : 'btn btn--primary', text: target === 'REJECTED' ? t('Rechazar pedido') : t('Confirmar pedido') });
  const dialog = modal(target === 'REJECTED' ? t('Rechazar pedido {0}', order.id.slice(0, 8)) : t('Confirmar pedido {0}', order.id.slice(0, 8)), el('div', {},
    el('p', { class: 'muted small', text: t('{0} · {1} · riesgo {2}/100 · creado {3}', order.customerId, money(order.amount), order.riskScore, formatDateTime(order.createdAt)) }),
    target === 'REJECTED' ? el('p', { class: 'muted small', text: t('Al rechazar un pedido del portal, sus unidades vuelven al stock.') }) : null,
    field(t('Nota (queda en el historial)'), note), errorNode),
  { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: t('Cancelar'), onclick: () => dialog.close() }), save] });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => operationsApi.transition(order.id, target, note.value), target === 'REJECTED' ? t('Pedido rechazado') : t('Pedido confirmado'))
      .then(() => { dialog.close(); onDone(); }).catch(() => {});
  });
}

route('/operaciones/revisiones', async (root) => {
  const body = el('div', {}, skeletons(4, 'skeleton--card'));
  root.append(el('section', { class: 'view' },
    head(t('Revisiones de riesgo'), t('Pedidos con puntaje ≥ 70 que el motor de riesgo (Python) envió a revisión. La decisión es humana y queda auditada.')),
    body));
  async function load() {
    body.replaceChildren(...skeletons(4, 'skeleton--card'));
    try {
      const orders = (await operationsApi.queue(['RISK_REVIEW'])).sort((a, b) => b.riskScore - a.riskScore);
      body.replaceChildren(orders.length ? paginated(orders, (page) => el('div', { class: 'review-grid' }, page.map((o) => el('article', { class: 'card review-card' },
        el('div', { class: 'card__head' }, el('strong', { text: o.customerId }), el('span', { class: 'badge badge--warn', text: t('Riesgo {0}/100', o.riskScore) })),
        el('p', { class: 'review-card__amount', text: money(o.amount) }),
        el('p', { class: 'muted small', text: `${o.id.slice(0, 8)} · ${o.channel === 'SHOP' ? 'Portal' : 'Laboratorio'} · ${formatRelative(o.createdAt)}` }),
        el('div', { class: 'row-actions' },
          el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: t('Confirmar'), onclick: () => decisionModal(o, 'CONFIRMED', load) }),
          el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Rechazar'), onclick: () => decisionModal(o, 'REJECTED', load) }),
          el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Detalle'), onclick: () => orderDetailModal(o.id, operationsApi, load) }))))),
      { pageSize: 9, label: t('pedidos en revisión') })
        : el('div', { class: 'card' }, emptyState(t('Sin pedidos en revisión'), t('El motor de riesgo no ha marcado pedidos pendientes.'), 'shield')));
    } catch (error) {
      body.replaceChildren(el('div', { class: 'card' }, emptyState(t('No se pudieron cargar las revisiones'), errorMessage(error))));
    }
  }
  await load();
}, { ...ROLES, title: t('Revisiones de riesgo') });

// ---- Incidencias ---------------------------------------------------------------------------
route('/operaciones/incidencias', async (root) => {
  const container = el('div', {});
  root.append(el('section', { class: 'view' },
    head(t('Incidencias'), t('Registra y resuelve incidencias (daños, retrasos, dirección, stock). Cada acción queda en la auditoría.')),
    container));
  await incidentsPanel(container, true);
}, { roles: ['ORDER_OPERATOR', 'LOGISTICS_OPERATOR', 'ADMIN'], title: t('Incidencias') });
