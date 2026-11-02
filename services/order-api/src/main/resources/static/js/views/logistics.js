// Calendario logístico (LOGISTICS_OPERATOR y ADMIN). Vistas mensual, semanal y diaria con datos de
// PostgreSQL. La validación de conflictos la hace el backend (409 SCHEDULE_CONFLICT) y se muestra tal cual.

import { logisticsApi } from '../api.js';
import {
  LIFECYCLE, TRANSITION_VERBS, emptyState, errorMessage, field, formError, kpi, lifecycleBadge, modal, money, paginated, skeletons, submitting,
} from '../components.js';
import { route } from '../router.js';
export { startOfDay, addDays, startOfWeek, timeFmt, dayFmt };
import { el, formatDateTime } from '../ui.js';
import { reportIncidentModal } from './admin.js';

const HOURS = Array.from({ length: 14 }, (_, i) => i + 7); // 07:00 – 20:00
const HOUR_PX = 44;
const PALETTE = ['#8B5CF6', '#3B82F6', '#10B981', '#F59E0B', '#EC4899', '#14B8A6'];
const monthFmt = new Intl.DateTimeFormat('es-ES', { month: 'long', year: 'numeric' });
const dayFmt = new Intl.DateTimeFormat('es-ES', { weekday: 'short', day: 'numeric' });
const longDayFmt = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const timeFmt = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' });

const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const startOfWeek = (d) => addDays(startOfDay(d), -((d.getDay() + 6) % 7)); // lunes
const sameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
const pad = (n) => String(n).padStart(2, '0');
const toLocalInput = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;

route('/logistica/calendario', async (root) => {
  const state = { mode: 'month', cursor: startOfDay(new Date()), assignee: '', deliveries: [], assignees: [], colors: new Map() };
  const title = el('h3', {});
  const modes = el('div', { class: 'segmented', role: 'group', 'aria-label': 'Vista del calendario' },
    [['month', 'Mes'], ['week', 'Semana'], ['day', 'Día']].map(([value, label]) => el('button', {
      type: 'button', class: `segmented__btn${value === state.mode ? ' is-active' : ''}`, dataset: { mode: value }, 'aria-pressed': String(value === state.mode), text: label,
    })));
  const assigneeFilter = el('select', { 'aria-label': 'Filtrar por responsable' }, el('option', { value: '', text: 'Todos los responsables' }));
  assigneeFilter.style.width = 'auto';
  const scheduleButton = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Programar entrega' });
  const calendar = el('div', { 'aria-live': 'polite' });
  const legend = el('div', { class: 'cal-legend' });
  const pending = el('div', {});
  const nav = (delta) => {
    const c = state.cursor;
    state.cursor = state.mode === 'month' ? new Date(c.getFullYear(), c.getMonth() + delta, 1) : addDays(c, delta * (state.mode === 'week' ? 7 : 1));
    load();
  };

  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Logística' }), el('h1', { class: 'view__title', text: 'Calendario de entregas' }),
      el('p', { class: 'section__lead', text: 'Programa, reasigna y reprograma entregas. Cada cambio queda en el historial; el servidor impide solapamientos del mismo responsable.' })),
      el('div', { class: 'row-actions' }, scheduleButton)),
    el('article', { class: 'card' },
      el('div', { class: 'cal-toolbar' },
        el('div', { class: 'cal-toolbar__nav' },
          el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Anterior', text: '‹', onclick: () => nav(-1) }),
          el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Hoy', onclick: () => { state.cursor = startOfDay(new Date()); load(); } }),
          el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Siguiente', text: '›', onclick: () => nav(1) }), title),
        el('div', { class: 'row-actions' }, assigneeFilter, modes)),
      calendar, legend),
    el('div', {}, pending)));
  pending.parentElement.style.marginTop = '16px';

  modes.addEventListener('click', (event) => {
    const button = event.target.closest('[data-mode]');
    if (!button) return;
    state.mode = button.dataset.mode;
    modes.querySelectorAll('button').forEach((b) => { b.classList.toggle('is-active', b === button); b.setAttribute('aria-pressed', String(b === button)); });
    load();
  });
  assigneeFilter.addEventListener('change', () => { state.assignee = assigneeFilter.value; load(); });
  scheduleButton.addEventListener('click', () => scheduleModal(null));

  function range() {
    if (state.mode === 'month') {
      const first = new Date(state.cursor.getFullYear(), state.cursor.getMonth(), 1);
      const start = startOfWeek(first);
      return [start, addDays(start, 42)];
    }
    if (state.mode === 'week') { const start = startOfWeek(state.cursor); return [start, addDays(start, 7)]; }
    return [startOfDay(state.cursor), addDays(startOfDay(state.cursor), 1)];
  }

  const colorOf = (assigneeId) => {
    if (!state.colors.has(assigneeId)) state.colors.set(assigneeId, PALETTE[state.colors.size % PALETTE.length]);
    return state.colors.get(assigneeId);
  };

  async function load() {
    const [from, to] = range();
    title.textContent = state.mode === 'month' ? monthFmt.format(state.cursor)
      : state.mode === 'week' ? `${dayFmt.format(from)} – ${dayFmt.format(addDays(to, -1))}` : longDayFmt.format(from);
    try {
      state.deliveries = await logisticsApi.deliveries(from.toISOString(), to.toISOString(), state.assignee || undefined);
      render(from);
      renderPending();
    } catch (error) {
      calendar.replaceChildren(emptyState('No se pudo cargar el calendario', errorMessage(error), 'calendar'));
    }
  }

  function eventButton(view, className) {
    const d = view.delivery;
    const button = el('button', { type: 'button', class: className, title: `${timeFmt.format(new Date(d.start))} · ${view.customerId} · ${d.assigneeName}`,
      text: `${timeFmt.format(new Date(d.start))} ${view.customerId}`, onclick: (event) => { event.stopPropagation(); detailModal(view); } });
    button.style.background = colorOf(d.assigneeId);
    if (view.orderStatus === 'DELIVERED') button.style.opacity = '0.55';
    return button;
  }

  function render(from) {
    const today = new Date();
    if (state.mode === 'month') {
      const grid = el('div', { class: 'cal-month', role: 'grid', 'aria-label': title.textContent },
        ['lun', 'mar', 'mié', 'jue', 'vie', 'sáb', 'dom'].map((d) => el('div', { class: 'cal-month__dow', role: 'columnheader', text: d })));
      for (let i = 0; i < 42; i += 1) {
        const day = addDays(from, i);
        const events = state.deliveries.filter((v) => sameDay(new Date(v.delivery.start), day));
        const cell = el('button', { type: 'button', class: `cal-day${day.getMonth() !== state.cursor.getMonth() ? ' is-other' : ''}${sameDay(day, today) ? ' is-today' : ''}`,
          'aria-label': `${longDayFmt.format(day)}: ${events.length} entregas` },
        el('span', { class: 'cal-day__num', text: String(day.getDate()) }),
        events.slice(0, 3).map((v) => eventButton(v, 'cal-event')),
        events.length > 3 ? el('span', { class: 'cal-more', text: `+${events.length - 3} más` }) : null);
        cell.addEventListener('click', () => { state.mode = 'day'; state.cursor = day; modes.querySelectorAll('button').forEach((b) => b.classList.toggle('is-active', b.dataset.mode === 'day')); load(); });
        grid.append(cell);
      }
      calendar.replaceChildren(grid);
    } else if (state.mode === 'week') {
      const grid = el('div', { class: 'cal-week' }, el('div', { class: 'cal-week__head' }));
      for (let i = 0; i < 7; i += 1) {
        const day = addDays(from, i);
        grid.append(el('div', { class: `cal-week__head${sameDay(day, today) ? ' is-today' : ''}`, text: dayFmt.format(day) }));
      }
      grid.append(el('div', { class: 'cal-week__hours' }, HOURS.map((h) => el('div', { class: 'cal-week__hour', text: `${pad(h)}:00` }))));
      for (let i = 0; i < 7; i += 1) {
        const day = addDays(from, i);
        const column = el('div', { class: 'cal-week__col' }, HOURS.map((h) => {
          const slot = el('div', { class: 'cal-week__slot', title: `Programar ${dayFmt.format(day)} ${pad(h)}:00` });
          slot.addEventListener('click', () => scheduleModal(null, new Date(day.getFullYear(), day.getMonth(), day.getDate(), h)));
          return slot;
        }));
        state.deliveries.filter((v) => sameDay(new Date(v.delivery.start), day)).forEach((v) => {
          const start = new Date(v.delivery.start);
          const end = new Date(v.delivery.end);
          const top = Math.max(0, (start.getHours() + start.getMinutes() / 60 - HOURS[0]) * HOUR_PX);
          const height = Math.max(22, ((end - start) / 3600000) * HOUR_PX - 2);
          const button = eventButton(v, 'cal-week__event');
          button.style.top = `${top}px`;
          button.style.height = `${height}px`;
          button.textContent = `${timeFmt.format(start)}–${timeFmt.format(end)} · ${v.customerId} · ${v.delivery.assigneeName}`;
          column.append(button);
        });
        grid.append(column);
      }
      calendar.replaceChildren(grid);
    } else {
      const events = [...state.deliveries].sort((a, b) => new Date(a.delivery.start) - new Date(b.delivery.start));
      calendar.replaceChildren(events.length ? el('div', { class: 'day-list' }, events.map((v) => el('div', { class: 'day-list__item' },
        el('span', { class: 'day-list__time', text: `${timeFmt.format(new Date(v.delivery.start))}–${timeFmt.format(new Date(v.delivery.end))}` }),
        el('div', {}, el('strong', { text: `${v.customerId} · ${money(v.amount)}` }), el('small', { class: 'muted', text: ` · ${v.delivery.assigneeName}${v.delivery.notes ? ` · ${v.delivery.notes}` : ''}` })),
        el('div', { class: 'row-actions' }, lifecycleBadge(v.orderStatus), el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Gestionar', onclick: () => detailModal(v) })))))
        : emptyState('Sin entregas este día', 'Usa «Programar entrega» o elige otro día.', 'calendar'));
    }
    legend.replaceChildren(...state.assignees.map((a) => el('span', {}, (() => { const i = el('i'); i.style.background = colorOf(a.id); return i; })(), a.displayName)));
  }

  async function renderPending() {
    try {
      const orders = await logisticsApi.schedulable();
      pending.replaceChildren(el('article', { class: 'card' },
        el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: `Pedidos pendientes de programar (${orders.length})` })),
        orders.length ? el('div', { class: 'day-list' }, orders.slice(0, 12).map((o) => el('div', { class: 'day-list__item' },
          el('span', { class: 'day-list__time', text: o.id.slice(0, 8) }),
          el('div', {}, el('strong', { text: `${o.customerId} · ${money(o.amount)}` })),
          el('div', { class: 'row-actions' }, lifecycleBadge(o.status), el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Programar', onclick: () => scheduleModal(o.id) })))))
          : emptyState('Nada pendiente', 'Todos los pedidos confirmados ya tienen entrega.', 'truck')));
    } catch (error) {
      pending.replaceChildren(el('p', { class: 'muted small', text: errorMessage(error) }));
    }
  }

  const scheduleModal = (orderId, startAt) => openScheduleModal(state.assignees, orderId, startAt, load);
  const detailModal = (view) => openDeliveryModal(view, state.assignees, load);

  try {
    state.assignees = await logisticsApi.assignees();
    assigneeFilter.append(...state.assignees.map((a) => el('option', { value: String(a.id), text: a.displayName })));
  } catch (error) {
    state.assignees = [];
  }
  await load();
}, { roles: ['LOGISTICS_OPERATOR', 'ADMIN'], title: 'Calendario logístico' });

// ---- Diálogos reutilizados por el calendario, las entregas y las asignaciones ----------------
export async function openScheduleModal(assignees, orderId, startAt, onDone) {
  const orders = await logisticsApi.schedulable().catch(() => []);
  const order = el('select', {}, orders.map((o) => el('option', { value: o.id, text: `${o.id.slice(0, 8)} · ${o.customerId} · ${money(o.amount)} · ${LIFECYCLE[o.status].label}`, selected: o.id === orderId })));
  const start = startAt || (() => { const d = addDays(startOfDay(new Date()), 1); d.setHours(10); return d; })();
  const startInput = el('input', { type: 'datetime-local', value: toLocalInput(start) });
  const endInput = el('input', { type: 'datetime-local', value: toLocalInput(new Date(start.getTime() + 2 * 3600000)) });
  const assignee = el('select', {}, assignees.map((a) => el('option', { value: String(a.id), text: a.displayName })));
  const notes = el('input', { maxlength: '500', placeholder: 'Instrucciones de entrega (opcional)' });
  const errorNode = formError();
  const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Programar' });
  const body = orders.length ? el('div', { class: 'form-grid' },
    (() => { const f = field('Pedido', order); f.classList.add('field--full'); return f; })(),
    field('Inicio', startInput), field('Fin (máx. 8 h)', endInput), field('Responsable', assignee), field('Notas', notes),
    (() => { errorNode.classList.add('field--full'); return errorNode; })())
    : emptyState('No hay pedidos para programar', 'Solo se programan pedidos CONFIRMED, PREPARING o READY_FOR_DISPATCH sin entrega.', 'truck');
  const dialog = modal('Programar entrega', body, { wide: true, actions: orders.length ? [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] : [] });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => logisticsApi.schedule({ orderId: order.value, assigneeId: Number(assignee.value),
      start: new Date(startInput.value).toISOString(), end: new Date(endInput.value).toISOString(), notes: notes.value }), 'Entrega programada')
      .then(() => { dialog.close(); onDone(); }).catch(() => {});
  });
}

export async function openDeliveryModal(view, assignees, onDone) {
  const d = view.delivery;
  const startInput = el('input', { type: 'datetime-local', value: toLocalInput(new Date(d.start)) });
  const endInput = el('input', { type: 'datetime-local', value: toLocalInput(new Date(d.end)) });
  const assignee = el('select', {}, assignees.map((a) => el('option', { value: String(a.id), text: a.displayName, selected: a.id === d.assigneeId })));
  const reason = el('input', { maxlength: '500', placeholder: 'Motivo (obligatorio, queda en el historial)' });
  const errorNode = formError();
  const save = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Guardar reprogramación' });
  const historyList = el('ol', { class: 'timeline' });
  const transitions = { READY_FOR_DISPATCH: ['IN_TRANSIT'], IN_TRANSIT: ['DELIVERED'] }[view.orderStatus] || [];
  const actionButtons = transitions.map((target) => {
    const button = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: TRANSITION_VERBS[target] });
    button.addEventListener('click', async () => {
      await submitting(button, errorNode, () => logisticsApi.transition(d.orderId, target, 'Actualización logística'), `Pedido: ${LIFECYCLE[target].label}`)
        .then(() => { dialog.close(); onDone(); }).catch(() => {});
    });
    return button;
  });
  const locked = ['IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'REJECTED'].includes(view.orderStatus);
  const dialog = modal(`Entrega · pedido ${d.orderId.slice(0, 8)}`, el('div', {},
    el('div', { class: 'row-actions', style: null }, lifecycleBadge(view.orderStatus), el('span', { class: 'badge badge--neutral', text: `${view.customerId} · ${money(view.amount)}` })),
    el('p', { class: 'muted small', text: `${formatDateTime(d.start)} – ${timeFmt.format(new Date(d.end))} · ${d.assigneeName}${d.notes ? ` · ${d.notes}` : ''}` }),
    actionButtons.length ? el('div', { class: 'row-actions', style: null }, actionButtons) : null,
    locked ? el('p', { class: 'muted small', text: 'La entrega ya no se puede reprogramar en este estado.' })
      : el('div', {}, el('h3', { class: 'card__title card__title--spaced', text: 'Reprogramar o reasignar' }),
        el('div', { class: 'form-grid' }, field('Inicio', startInput), field('Fin', endInput), field('Responsable', assignee), field('Motivo', reason)), save),
    errorNode,
    el('div', { class: 'row-actions', style: null }, el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Registrar incidencia', onclick: () => { dialog.close(); reportIncidentModal(d.orderId, null); } })),
    el('h3', { class: 'card__title card__title--spaced', text: 'Historial de cambios' }), historyList), { wide: true });
  dialog.root.querySelectorAll('.row-actions').forEach((r) => { r.style.justifyContent = 'flex-start'; r.style.marginBottom = '10px'; });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => logisticsApi.reschedule(d.id, { assigneeId: Number(assignee.value), start: new Date(startInput.value).toISOString(),
      end: new Date(endInput.value).toISOString(), reason: reason.value }), 'Entrega reprogramada').then(() => { dialog.close(); onDone(); }).catch(() => {});
  });
  try {
    const history = await logisticsApi.history(d.id);
    historyList.replaceChildren(...history.map((h, i) => el('li', { class: i === history.length - 1 ? 'is-current' : '' },
      el('strong', { text: h.action === 'CREATED' ? 'Programada' : 'Reprogramada' }),
      el('small', { text: `${formatDateTime(h.at)} · ${h.actor} · ${timeFmt.format(new Date(h.newStart))}–${timeFmt.format(new Date(h.newEnd))} · ${h.newAssignee}${h.reason ? ` · ${h.reason}` : ''}` }))));
  } catch (error) {
    historyList.replaceChildren(el('li', {}, el('small', { text: errorMessage(error) })));
  }
}

// ---- Resumen logístico ------------------------------------------------------------------------
const head = (title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: 'Logística' }), el('h1', { class: 'view__title', text: title }), el('p', { class: 'section__lead', text: lead })),
  actions.length ? el('div', { class: 'row-actions' }, actions) : null);

const deliveryTable = (views, assignees, onDone) => el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
  el('thead', {}, el('tr', {}, ['Fecha', 'Ventana', 'Pedido', 'Cliente', 'Destino', 'Responsable', 'Estado', ''].map((h) => el('th', { scope: 'col', text: h })))),
  el('tbody', {}, views.map((v) => el('tr', {},
    el('td', { text: dayFmt.format(new Date(v.delivery.start)) }),
    el('td', { class: 'mono', text: `${timeFmt.format(new Date(v.delivery.start))}–${timeFmt.format(new Date(v.delivery.end))}` }),
    el('td', {}, el('span', { class: 'table__id', text: v.delivery.orderId.slice(0, 8) })),
    el('td', { text: v.customerId }),
    el('td', { text: v.destination || '—' }),
    el('td', { text: v.delivery.assigneeName }),
    el('td', {}, lifecycleBadge(v.orderStatus)),
    el('td', {}, el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Gestionar', onclick: () => openDeliveryModal(v, assignees, onDone) })))))));

route('/logistica', async (root) => {
  const kpis = el('div', { class: 'kpis' }, skeletons(5, 'skeleton--row'));
  const today = el('div', {}, skeletons(3, 'skeleton--row'));
  const pending = el('div', {}, skeletons(3, 'skeleton--row'));
  root.append(el('section', { class: 'view' },
    head('Resumen logístico', 'Entregas de hoy, carga de la semana y pedidos listos para programar. Datos de PostgreSQL.',
      el('a', { class: 'btn btn--primary btn--sm', href: '/logistica/calendario.html', text: 'Abrir calendario' })),
    kpis,
    el('div', { class: 'grid-2-even card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Entregas de hoy' }), today),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pendientes de programar' }), pending))));
  const now = new Date();
  const [schedulable, week, recent, assignees] = await Promise.all([
    logisticsApi.schedulable(), logisticsApi.deliveries(startOfWeek(now).toISOString(), addDays(startOfWeek(now), 7).toISOString()),
    logisticsApi.deliveries(addDays(startOfDay(now), -7).toISOString(), addDays(startOfDay(now), 1).toISOString()), logisticsApi.assignees()]);
  const todays = week.filter((v) => new Date(v.delivery.start).toDateString() === now.toDateString());
  kpis.replaceChildren(
    kpi('Pendientes de programar', schedulable.length, schedulable.length ? 'warn' : 'ok'),
    kpi('Entregas hoy', todays.length, 'violet'),
    kpi('Entregas esta semana', week.length),
    kpi('En tránsito', week.concat(recent).filter((v, i, all) => v.orderStatus === 'IN_TRANSIT' && all.findIndex((x) => x.delivery.id === v.delivery.id) === i).length, 'blue'),
    kpi('Entregadas (7 días)', recent.filter((v) => v.orderStatus === 'DELIVERED').length, 'ok'),
    kpi('Repartidores', assignees.length));
  today.replaceChildren(todays.length ? deliveryTable(todays, assignees, () => window.location.reload())
    : emptyState('Sin entregas hoy', 'Programa entregas desde el calendario.', 'calendar'));
  pending.replaceChildren(schedulable.length ? el('div', { class: 'day-list' }, schedulable.slice(0, 8).map((o) => el('div', { class: 'day-list__item' },
    el('span', { class: 'day-list__time', text: o.id.slice(0, 8) }), el('div', {}, el('strong', { text: `${o.customerId} · ${money(o.amount)}` })),
    el('div', { class: 'row-actions' }, lifecycleBadge(o.status),
      el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Programar', onclick: () => openScheduleModal(assignees, o.id, null, () => window.location.reload()) })))))
    : emptyState('Nada pendiente', 'Todos los pedidos confirmados tienen entrega.', 'truck'));
}, { roles: ['LOGISTICS_OPERATOR', 'ADMIN'], title: 'Logística' });

// ---- Entregas -----------------------------------------------------------------------------
route('/logistica/entregas', async (root) => {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  const period = el('select', { 'aria-label': 'Periodo' }, el('option', { value: 'next7', text: 'Próximos 7 días' }),
    el('option', { value: 'next14', text: 'Próximos 14 días' }), el('option', { value: 'past7', text: 'Últimos 7 días' }), el('option', { value: 'past30', text: 'Últimos 30 días' }));
  const status = el('select', { 'aria-label': 'Estado del pedido' }, el('option', { value: '', text: 'Todos los estados' }),
    ...['CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'].map((s) => el('option', { value: s, text: LIFECYCLE[s].label })));
  [period, status].forEach((s) => { s.style.width = 'auto'; });
  root.append(el('section', { class: 'view' },
    head('Entregas', 'Listado de entregas programadas con su ventana, destino, responsable y estado de despacho.'),
    el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Entregas' }), el('div', { class: 'row-actions' }, period, status)), body)));
  const assignees = await logisticsApi.assignees();
  const load = async () => {
    body.replaceChildren(...skeletons(6, 'skeleton--row'));
    const today = startOfDay(new Date());
    const [from, to] = { next7: [today, addDays(today, 7)], next14: [today, addDays(today, 14)], past7: [addDays(today, -7), addDays(today, 1)], past30: [addDays(today, -30), addDays(today, 1)] }[period.value];
    try {
      const views = (await logisticsApi.deliveries(from.toISOString(), to.toISOString())).filter((v) => !status.value || v.orderStatus === status.value);
      body.replaceChildren(views.length ? paginated(views, (page) => deliveryTable(page, assignees, load), { pageSize: 12, label: 'entregas' })
        : emptyState('Sin entregas en este periodo', 'Cambia el periodo o el estado.', 'truck'));
    } catch (error) {
      body.replaceChildren(emptyState('No se pudieron cargar las entregas', errorMessage(error)));
    }
  };
  period.addEventListener('change', load);
  status.addEventListener('change', load);
  await load();
}, { roles: ['LOGISTICS_OPERATOR', 'ADMIN'], title: 'Entregas' });

// ---- Asignaciones -------------------------------------------------------------------------
route('/logistica/asignaciones', async (root) => {
  const board = el('div', { class: 'assign-board' }, skeletons(3, 'skeleton--card'));
  const weekLabel = el('strong', {});
  let cursor = startOfWeek(new Date());
  const nav = (delta) => { cursor = addDays(cursor, 7 * delta); load(); };
  root.append(el('section', { class: 'view' },
    head('Asignaciones', 'Carga semanal de cada responsable. Reasigna una entrega desde «Gestionar»; el servidor impide solapamientos.'),
    el('div', { class: 'cal-toolbar card' }, el('div', { class: 'cal-toolbar__nav' },
      el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Semana anterior', text: '‹', onclick: () => nav(-1) }),
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Esta semana', onclick: () => { cursor = startOfWeek(new Date()); load(); } }),
      el('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Semana siguiente', text: '›', onclick: () => nav(1) }), weekLabel)),
    board));
  const assignees = await logisticsApi.assignees();
  async function load() {
    weekLabel.textContent = `${dayFmt.format(cursor)} – ${dayFmt.format(addDays(cursor, 6))}`;
    try {
      const views = await logisticsApi.deliveries(cursor.toISOString(), addDays(cursor, 7).toISOString());
      board.replaceChildren(...assignees.map((a) => {
        const mine = views.filter((v) => v.delivery.assigneeId === a.id).sort((x, y) => new Date(x.delivery.start) - new Date(y.delivery.start));
        const hours = mine.reduce((s, v) => s + (new Date(v.delivery.end) - new Date(v.delivery.start)) / 3600000, 0);
        return el('article', { class: 'card assign-card' },
          el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: a.displayName }),
            el('span', { class: 'badge badge--neutral', text: `${mine.length} entregas · ${hours.toFixed(1)} h` })),
          el('div', { class: 'meter', role: 'img', 'aria-label': `${hours.toFixed(1)} horas asignadas de 40` },
            (() => { const bar = el('span', { class: hours > 40 ? 'is-error' : hours > 30 ? 'is-warn' : '' }); bar.style.width = `${Math.min(100, (hours / 40) * 100)}%`; return bar; })()),
          mine.length ? el('ul', { class: 'activity' }, mine.map((v) => el('li', {},
            el('strong', { text: `${dayFmt.format(new Date(v.delivery.start))} ${timeFmt.format(new Date(v.delivery.start))}` }),
            ` · ${v.customerId}${v.destination ? ` · ${v.destination}` : ''} `, lifecycleBadge(v.orderStatus),
            el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Gestionar', onclick: () => openDeliveryModal(v, assignees, load) }))))
            : el('p', { class: 'muted small', text: 'Sin entregas asignadas esta semana.' }));
      }));
    } catch (error) {
      board.replaceChildren(emptyState('No se pudieron cargar las asignaciones', errorMessage(error)));
    }
  }
  await load();
}, { roles: ['LOGISTICS_OPERATOR', 'ADMIN'], title: 'Asignaciones' });
