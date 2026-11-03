// Utilidades de presentación. Todo el texto dinámico se inserta con textContent (sin innerHTML).

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/** Crea un elemento: el(tag, {class, text, attrs...}, ...children). */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key === 'dataset') Object.assign(node.dataset, value);
    else if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const moneyFormats = {
  USD: new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 }),
  PEN: new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', minimumFractionDigits: 2 }),
};
const integer = new Intl.NumberFormat('es-ES');
const dateTime = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'medium' });
const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

/** El laboratorio usa USD (contrato de la API original); la tienda, soles (PEN). */
export const formatMoney = (value, currency = 'USD') => (moneyFormats[currency] || moneyFormats.USD).format(Number(value));
export const formatInt = (value) => integer.format(value);
export const formatDateTime = (iso) => dateTime.format(new Date(iso));

export function formatRelative(iso) {
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 60) return relative.format(seconds, 'second');
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour');
  return relative.format(Math.round(seconds / 86400), 'day');
}

export const statusLabel = (status) => (status === 'REVIEW' ? 'REVISIÓN' : 'APROBADO');
export const statusBadge = (status) => el('span', {
  class: `badge ${status === 'REVIEW' ? 'badge--warn' : 'badge--ok'}`,
  text: statusLabel(status),
});

export function scoreView(score) {
  const color = score >= 70 ? 'var(--amber)' : score >= 40 ? 'var(--violet-2)' : 'var(--green)';
  const fill = el('span');
  fill.style.width = `${Math.max(0, Math.min(100, score))}%`;
  fill.style.background = color;
  return el('span', { class: 'score', title: `Puntaje de riesgo ${score} de 100` },
    el('span', { class: 'score__bar' }, fill), `${score}/100`);
}

/** Notificación breve, anunciada por lectores de pantalla (aria-live en el contenedor). */
export function toast(message, kind = 'ok', timeout = 4200) {
  const container = $('#toasts');
  if (!container) return;
  const dotKind = { ok: 'ok', warn: 'warn', error: 'error' }[kind] || 'pending';
  const node = el('div', { class: `toast toast--${kind}`, role: kind === 'error' ? 'alert' : 'status' },
    el('span', { class: `dot dot--${dotKind}` }), el('span', { text: message }));
  container.append(node);
  setTimeout(() => {
    node.classList.add('is-leaving');
    setTimeout(() => node.remove(), 320);
  }, timeout);
}

/** Actualiza el texto de una estadística resaltando el cambio. */
export function setStat(node, text) {
  if (!node) return;
  const changed = node.textContent !== text;
  node.removeAttribute('data-loading');
  node.textContent = text;
  if (changed && !reducedMotion()) {
    node.classList.remove('is-updated');
    void node.offsetWidth; // reinicia la animación
    node.classList.add('is-updated');
  }
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('ID copiado al portapapeles', 'ok', 2000);
  } catch {
    toast('No se pudo copiar automáticamente', 'warn', 2500);
  }
}
