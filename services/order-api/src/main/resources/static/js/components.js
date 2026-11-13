// Componentes reutilizables de la plataforma (sin frameworks). Texto dinámico siempre con textContent.

import { ApiError } from './api.js';
import { PROBLEM_TEXT } from './password-strength.js';
import { el, formatMoney, toast } from './ui.js';
import { t } from './i18n.js';

const SVG = 'http://www.w3.org/2000/svg';

// ---- Ciclo de vida ---------------------------------------------------------
export const LIFECYCLE = {
  CREATED: { label: t('Creado'), tone: 'neutral' },
  RISK_APPROVED: { label: t('Aprobado por riesgo'), tone: 'ok' },
  RISK_REVIEW: { label: t('En revisión de riesgo'), tone: 'warn' },
  CONFIRMED: { label: t('Confirmado'), tone: 'violet' },
  PREPARING: { label: t('En preparación'), tone: 'violet' },
  READY_FOR_DISPATCH: { label: t('Listo para despacho'), tone: 'blue' },
  IN_TRANSIT: { label: t('En tránsito'), tone: 'blue' },
  DELIVERED: { label: t('Entregado'), tone: 'ok' },
  REJECTED: { label: t('Rechazado'), tone: 'error' },
  CANCELLED: { label: t('Cancelado'), tone: 'error' },
};
export const HAPPY_PATH = ['CREATED', 'RISK_APPROVED', 'CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH', 'IN_TRANSIT', 'DELIVERED'];
export const TRANSITION_VERBS = {
  CONFIRMED: t('Confirmar'), PREPARING: t('Iniciar preparación'), READY_FOR_DISPATCH: t('Marcar listo para despacho'),
  IN_TRANSIT: t('Despachar'), DELIVERED: t('Marcar entregado'), REJECTED: t('Rechazar'), CANCELLED: t('Cancelar'),
};

export const lifecycleBadge = (status) => {
  const info = LIFECYCLE[status] || { label: status, tone: 'neutral' };
  return el('span', { class: `lc lc--${info.tone}`, text: info.label, title: status });
};

export const channelBadge = (channel) => el('span', {
  class: `badge ${channel === 'SHOP' ? 'badge--neutral' : 'badge--warn'}`,
  text: channel === 'SHOP' ? t('Tienda') : 'API', title: channel === 'SHOP' ? t('Pedido de la tienda') : t('Pedido registrado por la API de pedidos'),
});

/** Barra de progreso del pedido sobre el camino feliz. */
export function progressBar(status) {
  const failed = status === 'REJECTED' || status === 'CANCELLED';
  const index = status === 'RISK_REVIEW' ? 1 : HAPPY_PATH.indexOf(status);
  return el('div', { class: 'progress-steps', role: 'img', 'aria-label': t('Progreso: {0}', (LIFECYCLE[status] || {}).label || status) },
    HAPPY_PATH.slice(1).map((_, i) => el('span', { class: failed ? (i === 0 ? 'is-failed' : '') : (i < index ? 'is-done' : '') })));
}

// ---- Iconos (trazos simples, diseño propio) -----------------------------------
const ICONS = {
  tecnologia: 'M3 5h18v11H3z M1 19h22 M9 16v3 M15 16v3',
  audio: 'M4 14v-2a8 8 0 0 1 16 0v2 M4 14h3v6H4z M17 14h3v6h-3z',
  oficina: 'M3 8h18v12H3z M8 8V5h8v3 M3 13h18',
  hogar: 'M3 11 12 4l9 7 M5 10v10h14V10 M10 20v-6h4v6',
  accesorios: 'M6 8h12l-1 12H7z M9 8a3 3 0 0 1 6 0',
  bienestar: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  cart: 'M3 4h2l2.4 11h11L21 7H7 M10 20h.01 M18 20h.01',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0',
  shield: 'M12 3 4 6v6c0 5 3.4 8 8 9 4.6-1 8-4 8-9V6z M9 12l2 2 4-4',
  truck: 'M2 6h11v10H2z M13 10h5l3 3v3h-8 M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  chart: 'M4 20V10 M10 20V4 M16 20v-7 M22 20H2',
  bolt: 'M13 2 4 14h7l-1 8 9-12h-7z',
  box: 'M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10',
  empty: 'M4 7h16v13H4z M4 7l3-4h10l3 4 M9 12h6',
  calendar: 'M4 5h16v16H4z M4 10h16 M9 3v4 M15 3v4',
  home: 'M3 11 12 4l9 7 M5 10v10h14V10',
  grid: 'M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z',
  tag: 'M3 12V4h8l10 10-8 8z M7.5 8.5h.01',
  layers: 'M12 3 2 8l10 5 10-5z M2 13l10 5 10-5 M2 17l10 5 10-5',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  users: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M2 21a7 7 0 0 1 14 0 M17 11a3 3 0 1 0 0-6 M22 21a6 6 0 0 0-4-5.6',
  file: 'M6 2h9l5 5v15H6z M14 2v6h6 M9 13h8 M9 17h6',
  alert: 'M12 3 2 21h20z M12 10v5 M12 18h.01',
  server: 'M3 4h18v6H3z M3 14h18v6H3z M7 7h.01 M7 17h.01',
  activity: 'M2 12h4l3-8 6 16 3-8h4',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2',
  map: 'M9 4 3 6v14l6-2 6 2 6-2V4l-6 2z M9 4v14 M15 6v14',
  mail: 'M3 5h18v14H3z M3 6l9 7 9-7',
  hexagon: 'M12 2 21 7v10l-9 5-9-5V7z',
  route: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z M6 15V9a4 4 0 0 1 4-4h6 M18 9v6a4 4 0 0 1-4 4H8',
  logout: 'M15 4h4v16h-4 M10 16l4-4-4-4 M14 12H3',
  menu: 'M3 6h18 M3 12h18 M3 18h18',
  // v6: categorías de la tienda y elementos comerciales.
  laptop: 'M4 5h16v10H4z M2 19h20',
  cpu: 'M7 7h10v10H7z M10 10h4v4h-4z M9 2v3 M15 2v3 M9 19v3 M15 19v3 M2 9h3 M2 15h3 M19 9h3 M19 15h3',
  monitor: 'M3 4h18v12H3z M8 20h8 M12 16v4',
  phone: 'M7 2h10v20H7z M11 18h2',
  gamepad: 'M6 11h4 M8 9v4 M15 12h.01 M18 10h.01 M17.3 5H6.7A4 4 0 0 0 3 9l-1 6a3 3 0 0 0 5 2l2-2h6l2 2a3 3 0 0 0 5-2l-1-6a4 4 0 0 0-3.7-4z',
  keyboard: 'M2 6h20v12H2z M6 10h.01 M10 10h.01 M14 10h.01 M18 10h.01 M7 14h10',
  headphones: 'M3 18v-6a9 9 0 0 1 18 0v6 M21 19a2 2 0 0 1-2 2h-1v-6h3z M3 19a2 2 0 0 0 2 2h1v-6H3z',
  wifi: 'M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M2 9a15 15 0 0 1 20 0 M12 20h.01',
  disk: 'M4 6h16v12H4z M8 14h.01 M12 14h4',
  tv: 'M3 6h18v12H3z M8 21h8 M7 2l5 4 5-4',
  camera: 'M3 7h4l2-3h6l2 3h4v13H3z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  briefcase: 'M3 7h18v13H3z M8 7V4h8v3 M3 13h18',
  chip: 'M12 2v3 M5 8h14v12H5z M9 13h.01 M15 13h.01 M9 17h6 M2 12v4 M22 12v4',
  printer: 'M6 9V3h12v6 M6 18H4v-7h16v7h-2 M6 14h12v7H6z',
  watch: 'M7 6h10v12H7z M9 2h6l1 4H8z M9 22h6l1-4H8z',
  vr: 'M3 8h18v8h-6l-3-3-3 3H3z M7 12h.01 M17 12h.01',
  presentation: 'M2 3h20 M4 3v11h16V3 M12 14v4 M8 22l4-4 4 4',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M21 21l-4.3-4.3',
  percent: 'M19 5L5 19 M6.5 9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z M17.5 20a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  help: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3 M12 17h.01',
  chevron: 'M9 18l6-6-6-6',
  card: 'M2 5h20v14H2z M2 10h20',
  refresh: 'M3 12a9 9 0 0 1 15-6.7L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-15 6.7L3 16 M3 21v-5h5',
  sparkle: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z M19 17l.8 2.2L22 20l-2.2.8L19 23l-.8-2.2L16 20l2.2-.8z',
  // POLYMER: pie comercial, sonido y redes (trazos genéricos, no logotipos oficiales).
  volume: 'M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M18.5 6.5a8 8 0 0 1 0 11',
  volumeOff: 'M4 9h4l5-4v14l-5-4H4z M17 9l5 6 M22 9l-5 6',
  call: 'M5 3h4l2 5-2.5 1.5a11 11 0 0 0 6 6L16 13l5 2v4a2 2 0 0 1-2 2A17 17 0 0 1 3 5a2 2 0 0 1 2-2z',
  book: 'M5 4h11a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z M5 17a3 3 0 0 1 3-3h11 M9 8h6',
  store: 'M3 9l2-5h14l2 5 M3 9h18v1a3 3 0 0 1-6 0a3 3 0 0 1-6 0a3 3 0 0 1-6 0z M5 13v7h14v-7 M10 20v-4h4v4',
  play: 'M7 4l13 8-13 8z',
  facebook: 'M15 3h-3a4 4 0 0 0-4 4v3H6v4h2v7h4v-7h3l1-4h-4V7a1 1 0 0 1 1-1h3z',
  instagram: 'M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M17.5 6.5h.01',
  tiktok: 'M14 3v11.5a3.5 3.5 0 1 1-3.5-3.5 M14 3c.5 2.5 2.5 4.5 5 4.8',
  youtube: 'M3 8a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3z M10 9l5 3-5 3z',
  linkedin: 'M4 4h16v16H4z M8 10v6 M8 7.5v.01 M12 16v-6 M12 12.5a2.5 2.5 0 0 1 5 0V16',
  globe: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M3 12h18 M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9 M12 3c-2.5 2.6-3.7 5.6-3.7 9s1.2 6.4 3.7 9',
  whatsapp: 'M4 20l1.3-3.9A8 8 0 1 1 8 19z M9 9.5c0 3 2.5 5.5 5.5 5.5l1-1.5-2-1-1 .8c-1-.4-1.8-1.2-2.2-2.2l.8-1-1-2z',
};

export function icon(name, extraClass = '') {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '1.7');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  if (extraClass) svg.setAttribute('class', extraClass);
  const path = document.createElementNS(SVG, 'path');
  path.setAttribute('d', ICONS[name] || ICONS.box);
  svg.append(path);
  return svg;
}

const CATEGORY_ICON = { 'Tecnología': 'tecnologia', Audio: 'audio', Oficina: 'oficina', 'Hogar inteligente': 'hogar', Accesorios: 'accesorios', Bienestar: 'bienestar' };

/** v6: icono de cada categoría principal de la tienda (por slug y por nombre). */
export const MAIN_CATEGORY_ICONS = {
  computadoras: 'laptop', componentes: 'cpu', monitores: 'monitor', 'smartphones-tablets': 'phone', gaming: 'gamepad',
  perifericos: 'keyboard', 'audio-entretenimiento': 'headphones', redes: 'wifi', almacenamiento: 'disk', 'smart-home': 'home',
  seguridad: 'shield', televisores: 'tv', fotografia: 'camera', empresarial: 'briefcase', robotica: 'chip',
  'impresion-3d': 'printer', wearables: 'watch', 'realidad-virtual': 'vr', energia: 'bolt', productividad: 'presentation',
};
const MAIN_NAME_ICONS = {
  'Computadoras y laptops': 'laptop', 'Componentes de PC': 'cpu', 'Monitores y pantallas': 'monitor', 'Smartphones y tablets': 'phone',
  Gaming: 'gamepad', 'Periféricos': 'keyboard', 'Audio y entretenimiento': 'headphones', 'Redes y conectividad': 'wifi',
  Almacenamiento: 'disk', 'Smart Home': 'home', 'Seguridad electrónica': 'shield', 'Televisores y entretenimiento digital': 'tv',
  'Fotografía y creación de contenido': 'camera', 'Tecnología empresarial': 'briefcase', 'Robótica y electrónica': 'chip',
  'Impresión 3D y fabricación digital': 'printer', 'Tecnología wearable': 'watch', 'Realidad virtual y aumentada': 'vr',
  'Energía y protección eléctrica': 'bolt', 'Tecnología para productividad': 'presentation',
};
export const categoryIcon = (product) => MAIN_NAME_ICONS[product.mainCategoryName] || CATEGORY_ICON[product.categoryName] || 'box';

/** Ilustración del producto: degradado según su tono + icono de la categoría. */
export function productArt(product, className) {
  const hue = Number.isFinite(product.hue) ? product.hue : 260;
  const node = el('div', { class: className });
  node.style.background = `radial-gradient(120% 90% at 20% 0%, hsl(${hue} 85% 62% / .55), transparent 60%),
    linear-gradient(135deg, hsl(${hue} 70% 34%), hsl(${(hue + 40) % 360} 75% 22%))`;
  node.append(icon(categoryIcon(product)));
  return node;
}

export function stockNote(stock) {
  if (stock <= 0) return el('span', { class: 'stock-note stock-note--out', text: t('Agotado') });
  if (stock <= 5) return el('span', { class: 'stock-note stock-note--low', text: t('Últimas {0} unidades', stock) });
  return el('span', { class: 'stock-note', text: t('En stock') });
}

/** Importes de la tienda en soles (PEN); los pedidos anteriores conservan su moneda (USD). */
export const money = (value, currency = 'PEN') => formatMoney(value, currency);

/** Estados del pedido con el lenguaje que ve el cliente (el personal ve LIFECYCLE). */
export const CUSTOMER_LIFECYCLE = {
  CREATED: t('Pedido recibido'), RISK_APPROVED: t('Pedido verificado'), RISK_REVIEW: t('En verificación'), CONFIRMED: t('Confirmado'),
  PREPARING: t('En preparación'), READY_FOR_DISPATCH: t('Listo para envío'), IN_TRANSIT: t('En camino'), DELIVERED: t('Entregado'),
  REJECTED: t('No aprobado'), CANCELLED: t('Cancelado'),
};
export const customerBadge = (status) => {
  const info = LIFECYCLE[status] || { tone: 'neutral' };
  return el('span', { class: `lc lc--${info.tone}`, text: CUSTOMER_LIFECYCLE[status] || status, title: CUSTOMER_LIFECYCLE[status] || status });
};
/** Número de pedido visible para el cliente. */
export const orderRef = (order) => (order.orderNumber ? `HX-${order.orderNumber}` : t('Pedido {0}', String(order.id).slice(0, 8)));

// ---- Estados de carga y vacíos ------------------------------------------------
export function emptyState(title, text, iconName = 'empty', action = null) {
  return el('div', { class: 'empty-state' }, icon(iconName), el('strong', { text: title }), el('p', { text }), action);
}

export const skeletons = (count, className) => Array.from({ length: count }, () => el('div', { class: `skeleton ${className}` }));

export function errorMessage(error) {
  if (error instanceof ApiError) {
    if (error.status === 0) return t('Sin conexión con el servidor.');
    // Contraseña rechazada por la política del servidor: motivo en el idioma de la interfaz.
    if (error.body && error.body.code === 'WEAK_PASSWORD' && Array.isArray(error.body.problems)) {
      return error.body.problems.map((code) => (PROBLEM_TEXT[code] ? PROBLEM_TEXT[code]() : code)).join(' ');
    }
    return `${error.title ? `${t(error.title)}: ` : ''}${t(error.message)}`;
  }
  return error && error.message ? error.message : String(error);
}

// ---- Modal accesible ------------------------------------------------------------
let openModal = null;

export function modal(title, body, { actions = [], wide = false, onClose } = {}) {
  closeModal();
  const previous = document.activeElement;
  const titleId = `modal-title-${Math.random().toString(36).slice(2)}`;
  const close = () => {
    root.remove();
    document.removeEventListener('keydown', onKey);
    openModal = null;
    if (previous && previous.focus) previous.focus();
    if (onClose) onClose();
  };
  const onKey = (event) => {
    if (event.key === 'Escape') close();
    if (event.key === 'Tab') {
      const focusables = Array.from(root.querySelectorAll('button, [href], input, select, textarea')).filter((n) => !n.disabled);
      if (!focusables.length) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  };
  const closeButton = el('button', { type: 'button', class: 'icon-btn', 'aria-label': t('Cerrar'), onclick: close, text: '✕' });
  const dialog = el('div', { class: `modal__dialog${wide ? ' modal__dialog--wide' : ''}`, role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId },
    el('div', { class: 'modal__head' }, el('h2', { id: titleId, text: title }), closeButton),
    body,
    actions.length ? el('div', { class: 'modal__actions' }, actions) : null);
  const root = el('div', { class: 'modal', onclick: (event) => { if (event.target === root) close(); } }, dialog);
  document.body.append(root);
  document.addEventListener('keydown', onKey);
  openModal = close;
  const firstInput = dialog.querySelector('input, select, textarea') || closeButton;
  firstInput.focus();
  return { close, root };
}

export function closeModal() { if (openModal) openModal(); }

// ---- Formularios ----------------------------------------------------------------
export function field(label, input, help = '') {
  const id = input.id || `f-${Math.random().toString(36).slice(2)}`;
  input.id = id;
  return el('div', { class: 'field' }, el('label', { for: id, text: label }), input, help ? el('small', { class: 'field__help', text: help }) : null);
}

export const formError = () => el('div', { class: 'form-error', role: 'alert', hidden: true });

export function showFormError(node, error) {
  node.textContent = errorMessage(error);
  node.hidden = false;
}

/** Envía un formulario deshabilitando el botón y mostrando el error del backend. */
export async function submitting(button, errorNode, action, successMessage) {
  const label = button.textContent;
  button.disabled = true;
  button.textContent = t('Procesando…');
  if (errorNode) errorNode.hidden = true;
  try {
    const result = await action();
    if (successMessage) toast(successMessage, 'ok');
    return result;
  } catch (error) {
    if (errorNode) showFormError(errorNode, error); else toast(errorMessage(error), 'error');
    throw error;
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
}

// ---- Gráfico de barras SVG (datos reales del backend) -----------------------------
export function barChart(series, { label = (k) => k, height = 220 } = {}) {
  const entries = Object.entries(series);
  const max = Math.max(1, ...entries.map(([, v]) => v));
  const width = 640;
  const pad = { top: 16, right: 8, bottom: 26, left: 30 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
  svg.setAttribute('class', 'chart');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', entries.map(([k, v]) => `${label(k)}: ${v}`).join(', '));
  const defs = document.createElementNS(SVG, 'defs');
  defs.innerHTML = '<linearGradient id="bar-gradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3DE2D0"/><stop offset="1" stop-color="#A6B8FF"/></linearGradient>';
  svg.append(defs);
  for (let i = 0; i <= 4; i += 1) {
    const y = pad.top + (innerH * i) / 4;
    const line = document.createElementNS(SVG, 'line');
    line.setAttribute('x1', pad.left); line.setAttribute('x2', width - pad.right);
    line.setAttribute('y1', y); line.setAttribute('y2', y); line.setAttribute('class', 'grid-line');
    svg.append(line);
    const text = document.createElementNS(SVG, 'text');
    text.setAttribute('x', pad.left - 6); text.setAttribute('y', y + 3); text.setAttribute('text-anchor', 'end');
    text.textContent = String(Math.round(max - (max * i) / 4));
    svg.append(text);
  }
  const slot = innerW / Math.max(1, entries.length);
  entries.forEach(([key, value], index) => {
    const h = (value / max) * innerH;
    const rect = document.createElementNS(SVG, 'rect');
    rect.setAttribute('x', pad.left + index * slot + slot * 0.18);
    rect.setAttribute('y', pad.top + innerH - h);
    rect.setAttribute('width', slot * 0.64);
    rect.setAttribute('height', Math.max(h, value > 0 ? 2 : 0));
    rect.setAttribute('rx', 4);
    rect.setAttribute('class', 'bar');
    const title = document.createElementNS(SVG, 'title');
    title.textContent = `${label(key)}: ${value}`;
    rect.append(title);
    svg.append(rect);
    if (entries.length <= 16 || index % 2 === 0) {
      const text = document.createElementNS(SVG, 'text');
      text.setAttribute('x', pad.left + index * slot + slot / 2);
      text.setAttribute('y', height - 8);
      text.setAttribute('text-anchor', 'middle');
      text.textContent = label(key);
      svg.append(text);
    }
  });
  return svg;
}

/** Descarga un CSV generado en el navegador a partir de datos ya cargados. */
/**
 * Paginación en el cliente para listas ya filtradas por el servidor. Devuelve un contenedor que
 * se re-renderiza al cambiar de página; renderPage(items) devuelve el contenido de una página.
 */
export function paginated(items, renderPage, { pageSize = 10, label = t('elementos') } = {}) {
  const container = el('div', { class: 'paginated' });
  let page = 1;
  const pages = Math.max(1, Math.ceil(items.length / pageSize));
  const draw = () => {
    const start = (page - 1) * pageSize;
    const slice = items.slice(start, start + pageSize);
    const info = el('span', { class: 'pager__info', text: items.length
      ? t('{0}–{1} de {2} {3}', start + 1, start + slice.length, items.length, label) : t('0 {0}', label) });
    const prev = el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('‹ Anterior'), disabled: page <= 1, onclick: () => { page -= 1; draw(); } });
    const next = el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Siguiente ›'), disabled: page >= pages, onclick: () => { page += 1; draw(); } });
    container.replaceChildren(renderPage(slice),
      pages > 1 || items.length ? el('nav', { class: 'pager', 'aria-label': t('Paginación') }, info,
        pages > 1 ? el('div', { class: 'row-actions' }, prev, el('span', { class: 'pager__page', text: t('Página {0} de {1}', page, pages) }), next) : null) : null);
  };
  draw();
  return container;
}

/** Campo de búsqueda con espera breve: onSearch(texto) se llama al dejar de escribir. */
export function searchBox(placeholder, onSearch, initial = '') {
  const input = el('input', { type: 'search', placeholder, value: initial, 'aria-label': placeholder });
  let timer = 0;
  input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(() => onSearch(input.value.trim().toLowerCase()), 200); });
  return input;
}

/** Tarjeta de indicador (KPI). */
export const kpi = (label, value, tone = '', hint = '') => el('div', { class: `kpi${tone ? ` kpi--${tone}` : ''}` },
  el('span', { text: label }), el('strong', { text: String(value) }), hint ? el('small', { class: 'kpi__hint', text: hint }) : null);

export function downloadCsv(filename, rows) {
  const escape = (value) => `"${String(value ?? '').replace(/"/g, '""')}"`;
  const csv = rows.map((row) => row.map(escape).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }));
  const link = el('a', { href: url, download: filename });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
