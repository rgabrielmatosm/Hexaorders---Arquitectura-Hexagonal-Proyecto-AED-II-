// Operaciones (ORDER_OPERATOR y ADMIN): cola de pedidos, revisión de riesgo, preparación e incidencias.

import { operationsApi } from '../api.js';
import { emptyState, errorMessage, skeletons } from '../components.js';
import { route } from '../router.js';
import { el } from '../ui.js';
import { incidentsPanel, ordersTable, statusFilter } from './admin.js';

route('/operaciones', async (root) => {
  const counters = el('div', { class: 'kpis' });
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  const incidents = el('div', {});
  let statuses = ['RISK_REVIEW', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING'];
  const load = async () => {
    body.replaceChildren(...skeletons(6, 'skeleton--row'));
    try {
      const [queue, all] = await Promise.all([operationsApi.queue(statuses), operationsApi.queue([])]);
      const count = (s) => all.filter((o) => o.status === s).length;
      counters.replaceChildren(...[
        ['En revisión de riesgo', count('RISK_REVIEW'), count('RISK_REVIEW') ? 'warn' : ''],
        ['Aprobados por riesgo', count('RISK_APPROVED'), 'ok'], ['Confirmados', count('CONFIRMED'), 'violet'],
        ['En preparación', count('PREPARING'), 'violet'], ['Listos para despacho', count('READY_FOR_DISPATCH'), ''],
      ].map(([label, value, tone]) => el('div', { class: `kpi${tone ? ` kpi--${tone}` : ''}` }, el('span', { text: label }), el('strong', { text: String(value) }))));
      body.replaceChildren(ordersTable(queue, operationsApi, load));
    } catch (error) {
      body.replaceChildren(emptyState('No se pudo cargar la cola', errorMessage(error)));
    }
  };
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Operaciones' }), el('h1', { class: 'view__title', text: 'Cola de pedidos' }),
      el('p', { class: 'section__lead', text: 'Revisa los pedidos marcados por el motor de riesgo, confírmalos y prepáralos. Solo se muestran las transiciones que tu rol puede ejecutar; el servidor las valida igualmente.' }))),
    counters,
    el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Pedidos' }),
      statusFilter((value) => { statuses = value; load(); }, statuses)), body),
    el('div', { style: null }, incidents)));
  incidents.parentElement.style.marginTop = '16px';
  await Promise.all([load(), incidentsPanel(incidents, true)]);
}, { roles: ['ORDER_OPERATOR', 'ADMIN'], title: 'Operaciones' });
