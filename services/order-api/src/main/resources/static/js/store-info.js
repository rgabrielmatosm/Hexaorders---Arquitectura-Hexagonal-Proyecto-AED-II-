// v7 · Tienda física: mapa de Google Maps (iframe sin clave), «Cómo llegar», horario de hoy con un
// calendario del mes, estado abierto/cerrado en la hora de Lima y «Agregar a Google Calendar».
// Los datos (dirección, coordenadas, horario, feriados) los decide el servidor (/api/store).

import { storeApi } from './api.js';
import { icon } from './components.js';
import { el } from './ui.js';
import { locale, t } from './i18n.js';

const ZONE = 'America/Lima';
let infoCache = null;
let todayCache = null;
export const storeInfo = () => { if (!infoCache) infoCache = storeApi.info(); return infoCache; };
export const storeToday = () => { if (!todayCache) todayCache = storeApi.today(); return todayCache; };

const weekdayName = (isoDay) => {
  // 2024-01-01 fue lunes: isoDay 1..7 → lunes..domingo.
  const date = new Date(Date.UTC(2024, 0, isoDay, 12));
  return new Intl.DateTimeFormat(locale, { weekday: 'long', timeZone: 'UTC' }).format(date);
};
const longDate = (iso) => new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
  .format(new Date(`${iso}T12:00:00Z`));
const capitalize = (text) => text.charAt(0).toLocaleUpperCase(locale) + text.slice(1);

/** «Abierto ahora · cierra a las 19:00» o «Cerrado · abre el lunes a las 9:00». */
export function statusText(today) {
  if (today.openNow) return t('Abierto ahora · cierra a las {0}', today.closes);
  if (today.nextOpening) {
    const [date, time] = today.nextOpening.split('T');
    const sameDay = date === today.date;
    const day = new Intl.DateTimeFormat(locale, { weekday: 'long', timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`));
    return sameDay ? t('Cerrado · abre hoy a las {0}', time) : t('Cerrado · abre el {0} a las {1}', day, time);
  }
  return t('Cerrado');
}

/** Insignia compacta para el pie o la cabecera. */
export function todayBadge() {
  const badge = el('a', { class: 'today-badge', href: '/contacto.html#visitanos', 'data-testid': 'today-badge' }, icon('clock'), el('span', { text: '…' }));
  storeToday().then((today) => {
    badge.classList.toggle('is-open', today.openNow);
    badge.lastChild.textContent = statusText(today);
  }).catch(() => badge.remove());
  return badge;
}

/** Calendario del mes con hoy resaltado y los días sin atención atenuados. */
function monthCalendar(today, week) {
  const [year, month, day] = today.date.split('-').map(Number);
  const first = new Date(Date.UTC(year, month - 1, 1));
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const offset = (first.getUTCDay() + 6) % 7; // lunes = 0
  const closedWeekdays = new Set(week.filter((d) => !d.opens).map((d) => d.day));
  const title = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(first);
  const cells = [];
  for (let i = 0; i < offset; i += 1) cells.push(el('span', { class: 'mini-cal__cell is-empty' }));
  for (let d = 1; d <= daysInMonth; d += 1) {
    const isoDay = ((offset + d - 1) % 7) + 1;
    const classes = ['mini-cal__cell'];
    if (d === day) classes.push('is-today');
    if (closedWeekdays.has(isoDay)) classes.push('is-closed');
    if (d < day) classes.push('is-past');
    cells.push(el('span', { class: classes.join(' '), 'aria-current': d === day ? 'date' : null, text: String(d) }));
  }
  return el('div', { class: 'mini-cal', 'aria-label': capitalize(title) },
    el('div', { class: 'mini-cal__title', text: capitalize(title) }),
    el('div', { class: 'mini-cal__grid' },
      [1, 2, 3, 4, 5, 6, 7].map((d) => el('span', { class: 'mini-cal__dow', text: weekdayName(d).slice(0, 2) })), cells));
}

/** Tarjeta «Hoy en la tienda»: fecha, estado, horario de hoy, semana y Google Calendar. */
export function hoursCard({ withCalendar = true } = {}) {
  const card = el('article', { class: 'card hours-card', 'data-testid': 'store-hours' }, el('h3', { class: 'card__title', text: t('Horario de atención') }));
  Promise.all([storeInfo(), storeToday()]).then(([store, today]) => {
    const hours = today.opens ? `${today.opens} – ${today.closes}` : t('Cerrado');
    card.replaceChildren(
      el('div', { class: 'hours-card__today' },
        el('div', { class: 'hours-card__date' },
          el('span', { class: 'hours-card__day', text: today.date.split('-')[2] }),
          el('span', { class: 'hours-card__label', text: capitalize(longDate(today.date)) })),
        el('div', {},
          el('p', { class: 'eyebrow', text: t('Hoy') }),
          el('strong', { class: 'hours-card__hours', 'data-testid': 'store-hours-today', text: hours }),
          el('span', { class: `status-pill ${today.openNow ? 'status-pill--open' : 'status-pill--closed'}`, text: statusText(today) }),
          today.holiday ? el('small', { class: 'muted', text: t('Feriado: {0}', today.holiday) }) : null)),
      withCalendar ? monthCalendar(today, store.week) : null,
      el('table', { class: 'hours-table' }, el('tbody', {}, store.week.map((d) => el('tr', { class: d.day === today.weekday ? 'is-today' : '' },
        el('th', { scope: 'row', text: capitalize(weekdayName(d.day)) }),
        el('td', { text: d.opens ? `${d.opens} – ${d.closes}` : t('Cerrado') }))))),
      el('p', { class: 'small muted', text: t('Feriados nacionales: cerrado. Hora de Lima ({0}).', ZONE) }),
      el('a', { class: 'btn btn--ghost btn--block', href: today.googleCalendarUrl, target: '_blank', rel: 'noopener', 'data-testid': 'google-calendar' },
        icon('calendar'), t(' Agregar visita a Google Calendar')),
      store.calendarEmbedUrl ? el('iframe', { class: 'calendar-embed', src: store.calendarEmbedUrl, title: t('Calendario de la tienda (Google Calendar)'),
        loading: 'lazy', referrerpolicy: 'no-referrer-when-downgrade' }) : null);
  }).catch(() => card.append(el('p', { class: 'muted small', text: t('Horario no disponible en este momento.') })));
  return card;
}

/** Mapa de Google Maps con la ubicación de la tienda y el botón «Cómo llegar». */
export function mapCard() {
  const card = el('article', { class: 'card map-card', 'data-testid': 'store-map' }, el('div', { class: 'map-card__frame skeleton' }));
  storeInfo().then((store) => {
    card.replaceChildren(
      el('div', { class: 'map-card__frame' }, el('iframe', {
        src: store.mapsEmbedUrl, title: t('Ubicación de la tienda {0} en Google Maps', store.name), loading: 'lazy',
        referrerpolicy: 'no-referrer-when-downgrade', allowfullscreen: '' })),
      el('div', { class: 'map-card__body' },
        el('h3', { class: 'card__title' }, icon('map'), ` ${t('Tienda {0}', store.name)}`),
        el('address', { text: store.fullAddress }),
        el('p', { class: 'small muted' }, icon('call'), ` ${store.phone} · `, icon('mail'), ` ${store.email}`),
        el('div', { class: 'row-actions' },
          el('a', { class: 'btn btn--primary', href: store.mapsDirectionsUrl, target: '_blank', rel: 'noopener', 'data-testid': 'maps-directions' }, icon('route'), t(' Cómo llegar')),
          el('a', { class: 'btn btn--ghost', href: `https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}`, target: '_blank', rel: 'noopener', text: t('Abrir en Google Maps') }))));
  }).catch(() => card.replaceChildren(el('p', { class: 'muted small', text: t('Mapa no disponible en este momento.') })));
  return card;
}

/** Sección «Visítanos»: mapa + horario. */
export function visitSection({ title = t('Visítanos'), lead = t('Recoge tu pedido o prueba los equipos en nuestra tienda.') } = {}) {
  return el('section', { class: 'rail visit', id: 'visitanos' },
    el('div', { class: 'rail__head' }, el('div', {}, el('h2', { class: 'rail__title', text: title }), el('p', { class: 'section__lead', text: lead }))),
    el('div', { class: 'visit__grid' }, mapCard(), hoursCard()));
}
