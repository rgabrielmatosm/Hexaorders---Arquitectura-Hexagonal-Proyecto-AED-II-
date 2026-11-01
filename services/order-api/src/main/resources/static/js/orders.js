// Gestión de pedidos: alta, consulta por ID y listado. Todos los datos vienen del backend.
// La validación del formulario es solo ayuda al usuario; OrderDraft (dominio Java) es la autoridad.

import { ApiError, ordersApi } from './api.js';
import { $, $$, copyText, el, formatDateTime, formatMoney, formatRelative, scoreView, statusBadge, statusLabel, toast } from './ui.js';

const CUSTOMER_PATTERN = /^[A-Za-z0-9_-]{1,64}$/;
const AMOUNT_PATTERN = /^\d{1,7}(\.\d{1,2})?$/;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

let allOrders = [];
let filter = 'ALL';
let search = '';

export function validateCustomer(value) {
  if (!value) return 'Introduce un identificador de cliente.';
  if (!CUSTOMER_PATTERN.test(value)) return 'Usa entre 1 y 64 letras, dígitos, guiones o guiones bajos.';
  return '';
}

export function normalizeAmount(raw) {
  return String(raw).trim().replace(',', '.');
}

export function validateAmount(raw) {
  const value = normalizeAmount(raw);
  if (!value) return 'Introduce un importe.';
  if (!AMOUNT_PATTERN.test(value)) return 'Usa un número con como máximo 2 decimales (ej. 125.50).';
  const amount = Number(value);
  if (!(amount > 0)) return 'El importe debe ser mayor que 0.';
  if (amount > 1000000) return 'El importe máximo es 1 000 000.00.';
  return '';
}

function showFieldError(input, errorNode, message) {
  input.setAttribute('aria-invalid', message ? 'true' : 'false');
  errorNode.textContent = message;
  errorNode.hidden = !message;
}

function orderDetails(order) {
  return el('dl', {},
    el('dt', { text: 'ID' }), el('dd', { text: order.id }),
    el('dt', { text: 'Cliente' }), el('dd', { text: order.customerId }),
    el('dt', { text: 'Importe' }), el('dd', { text: formatMoney(order.amount) }),
    el('dt', { text: 'Riesgo' }), el('dd', { text: `${order.riskScore}/100` }),
    el('dt', { text: 'Decisión' }), el('dd', { text: statusLabel(order.status) }),
    el('dt', { text: 'Creado' }), el('dd', { text: formatDateTime(order.createdAt) }));
}

function showResult(container, kind, title, ...content) {
  container.className = `result result--${kind}`;
  const dot = { ok: 'ok', warn: 'warn', error: 'error' }[kind];
  container.replaceChildren(el('div', { class: 'result__title' }, el('span', { class: `dot dot--${dot}` }), title), ...content);
  container.hidden = false;
}

function errorText(error) {
  if (error instanceof ApiError && error.status) return `HTTP ${error.status} · ${error.title || 'Error'}: ${error.message}`;
  return error.message;
}

// ---- Listado ---------------------------------------------------------------

function visibleOrders() {
  const term = search.trim().toLowerCase();
  return allOrders.filter((order) => (filter === 'ALL' || order.status === filter)
    && (!term || order.customerId.toLowerCase().includes(term)));
}

function renderTable() {
  const body = $('#orders-body');
  const rows = visibleOrders();
  if (!rows.length) {
    const message = allOrders.length ? 'Ningún pedido coincide con el filtro.' : 'Todavía no hay pedidos registrados.';
    body.replaceChildren(el('tr', {}, el('td', { colspan: '5', class: 'table__empty', text: message })));
  } else {
    body.replaceChildren(...rows.map((order) => el('tr', {},
      el('td', {}, el('strong', { text: order.customerId }),
        el('span', { class: 'table__id', title: 'Copiar ID', tabindex: '0', role: 'button', text: order.id,
          onclick: () => copyText(order.id),
          onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); copyText(order.id); } } })),
      el('td', { class: 'mono', text: formatMoney(order.amount) }),
      el('td', {}, scoreView(order.riskScore)),
      el('td', {}, statusBadge(order.status)),
      el('td', { title: formatDateTime(order.createdAt), text: formatRelative(order.createdAt) }))));
  }
  $('#orders-caption').textContent = `${rows.length} de ${allOrders.length} pedidos visibles · el API devuelve los 50 más recientes.`;
}

export function renderOrders(orders) {
  allOrders = orders;
  renderTable();
}

export function renderOrdersError(message) {
  allOrders = [];
  $('#orders-body').replaceChildren(el('tr', {}, el('td', { colspan: '5', class: 'table__empty', text: `No se pudieron cargar los pedidos: ${message}` })));
}

// ---- Inicialización --------------------------------------------------------

export function initOrders({ onChanged }) {
  const form = $('#order-form');
  const customer = $('#customer');
  const amount = $('#amount');
  const submit = $('#order-submit');
  const result = $('#order-result');

  customer.addEventListener('input', () => showFieldError(customer, $('#customer-error'), ''));
  amount.addEventListener('input', () => showFieldError(amount, $('#amount-error'), ''));
  $$('[data-amount]').forEach((button) => button.addEventListener('click', () => {
    amount.value = button.dataset.amount;
    showFieldError(amount, $('#amount-error'), '');
    amount.focus();
  }));

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const customerError = validateCustomer(customer.value.trim());
    const amountError = validateAmount(amount.value);
    showFieldError(customer, $('#customer-error'), customerError);
    showFieldError(amount, $('#amount-error'), amountError);
    if (customerError || amountError) {
      (customerError ? customer : amount).focus();
      return;
    }
    submit.disabled = true;
    submit.textContent = 'Consultando motor de riesgo…';
    try {
      const { data: order, ms } = await ordersApi.create(customer.value.trim(), normalizeAmount(amount.value));
      const review = order.status === 'REVIEW';
      showResult(result, review ? 'warn' : 'ok',
        review ? 'Pedido creado · requiere revisión' : 'Pedido creado · aprobado',
        el('p', { class: 'muted small', text: `HTTP 201 Created en ${ms.toFixed(0)} ms · guardado en PostgreSQL` }),
        orderDetails(order));
      toast(`${review ? 'Pedido en revisión' : 'Pedido aprobado'} · riesgo ${order.riskScore}/100`, review ? 'warn' : 'ok');
      $('#lookup-id').value = order.id;
      onChanged();
    } catch (error) {
      const unavailable = error instanceof ApiError && error.status === 503;
      showResult(result, 'error', unavailable ? 'Servicio no disponible: no se creó el pedido' : 'No se pudo crear el pedido',
        el('p', { text: errorText(error) }));
      toast(error.message, 'error');
    } finally {
      submit.disabled = false;
      submit.textContent = 'Evaluar riesgo y crear pedido';
    }
  });

  // Consulta por ID
  const lookupForm = $('#lookup-form');
  const lookupInput = $('#lookup-id');
  const lookupResult = $('#lookup-result');
  lookupInput.addEventListener('input', () => showFieldError(lookupInput, $('#lookup-error'), ''));
  lookupForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const id = lookupInput.value.trim();
    const message = UUID_PATTERN.test(id) ? '' : 'Introduce un UUID válido (36 caracteres con guiones).';
    showFieldError(lookupInput, $('#lookup-error'), message);
    if (message) { lookupInput.focus(); return; }
    try {
      const { data: order, ms } = await ordersApi.get(id);
      showResult(lookupResult, order.status === 'REVIEW' ? 'warn' : 'ok', 'Pedido encontrado en PostgreSQL',
        el('p', { class: 'muted small', text: `HTTP 200 en ${ms.toFixed(0)} ms` }), orderDetails(order));
    } catch (error) {
      showResult(lookupResult, 'error', error.status === 404 ? 'Pedido no encontrado' : 'Error en la consulta',
        el('p', { text: errorText(error) }));
    }
  });

  // Filtros del listado
  $('#order-search').addEventListener('input', (event) => { search = event.target.value; renderTable(); });
  $$('[data-filter]').forEach((button) => button.addEventListener('click', () => {
    filter = button.dataset.filter;
    $$('[data-filter]').forEach((other) => {
      const activeButton = other === button;
      other.classList.toggle('is-active', activeButton);
      other.setAttribute('aria-pressed', String(activeButton));
    });
    renderTable();
  }));
}
