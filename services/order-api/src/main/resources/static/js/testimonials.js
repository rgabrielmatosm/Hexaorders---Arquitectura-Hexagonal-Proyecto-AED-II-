// v9 · Testimonios: carrusel (equipo POLYMER con su LinkedIn y clientes con cuenta) y formulario para dejar el tuyo.
// Datos: GET /api/testimonials (público) y PUT /api/testimonials/mine (cliente con sesión). Uno por cuenta, editable.

import { testimonialsApi } from './api.js';
import { play } from './audio.js';
import { field, formError, icon, submitting } from './components.js';
import { session } from './session.js';
import { el, reducedMotion } from './ui.js';
import { localizeHref, t } from './i18n.js';

const AUTOPLAY_MS = 7000;

function stars(rating) {
  if (!rating) return null;
  return el('p', { class: 'tm-card__stars', 'aria-label': t('{0} de 5 estrellas', rating) },
    [1, 2, 3, 4, 5].map((n) => el('span', { class: n <= rating ? 'is-on' : '', 'aria-hidden': 'true', text: '★' })));
}

/** Tarjeta: 1) perfil (foto, nombre, rol), 2) enlace a LinkedIn, 3) comentario. */
function card(item) {
  const team = item.kind === 'TEAM';
  const avatar = item.photoUrl
    ? el('img', { class: 'tm-card__photo', src: item.photoUrl, alt: t('Foto de {0}', item.name), width: '96', height: '96', loading: 'lazy' })
    : el('span', { class: 'tm-card__photo tm-card__photo--initial', 'aria-hidden': 'true', text: (item.name || '?').trim().charAt(0).toUpperCase() });
  return el('article', { class: `tm-card${team ? ' tm-card--team' : ''}`, 'data-testid': 'testimonial', 'data-kind': item.kind },
    el('header', { class: 'tm-card__profile' }, avatar,
      el('div', {}, el('strong', { class: 'tm-card__name', text: item.name }),
        el('small', { class: 'tm-card__role', text: item.role || (team ? t('Equipo POLYMER') : t('Cliente POLYMER')) }),
        team ? el('span', { class: 'tm-card__badge', text: t('Equipo POLYMER') }) : null)),
    item.linkedinUrl ? el('a', { class: 'tm-card__linkedin', href: item.linkedinUrl, target: '_blank', rel: 'noopener noreferrer',
      'data-testid': 'testimonial-linkedin', 'aria-label': t('Perfil de LinkedIn de {0}', item.name) }, icon('linkedin'), el('span', { text: t('Ver perfil en LinkedIn') })) : null,
    stars(item.rating),
    el('blockquote', { class: 'tm-card__quote' }, el('p', { text: item.comment })));
}

/** Carrusel accesible: flechas, puntos, teclado, deslizamiento táctil y avance automático (se pausa al interactuar). */
function carousel(items) {
  let index = 0;
  let timer = null;
  const track = el('div', { class: 'tm-track', 'aria-live': 'polite' }, items.map(card));
  const viewport = el('div', { class: 'tm-viewport', tabindex: '0', role: 'group', 'aria-roledescription': t('carrusel'),
    'aria-label': t('Testimonios de POLYMER') }, track);
  const dots = el('div', { class: 'tm-dots', role: 'tablist' });
  const paintDots = (pages) => {
    if (dots.children.length === pages) return;
    dots.replaceChildren(...Array.from({ length: pages }, (_, i) => el('button', {
      type: 'button', class: 'tm-dot', role: 'tab', 'aria-label': t('Testimonio {0} de {1}: {2}', i + 1, pages, items[i].name), onclick: () => go(i, true),
    })));
  };
  const prev = el('button', { type: 'button', class: 'tm-nav tm-nav--prev', 'aria-label': t('Testimonio anterior'), onclick: () => go(index - 1, true) }, icon('chevron'));
  const next = el('button', { type: 'button', class: 'tm-nav tm-nav--next', 'aria-label': t('Testimonio siguiente'), onclick: () => go(index + 1, true) }, icon('chevron'));
  // Tarjetas visibles según el ancho; siempre una menos que el total para que el carrusel avance.
  const perView = () => {
    const base = window.innerWidth >= 1100 ? 3 : window.innerWidth >= 720 ? 2 : 1;
    return items.length > 1 ? Math.max(1, Math.min(base, items.length - 1)) : 1;
  };
  function go(target, byUser) {
    track.dataset.per = String(perView());
    const pages = Math.max(1, items.length - perView() + 1);
    paintDots(pages);
    index = ((target % pages) + pages) % pages;
    const first = track.children[0];
    void track.offsetWidth; // aplica el nuevo ancho antes de medir
    const step = first ? first.getBoundingClientRect().width + 18 : 0;
    track.style.transform = `translateX(${-index * step}px)`;
    [...dots.children].forEach((dot, i) => { dot.classList.toggle('is-active', i === index); dot.setAttribute('aria-selected', String(i === index)); });
    [...track.children].forEach((child, i) => child.classList.toggle('is-visible', i >= index && i < index + perView()));
    if (byUser) { play('slide'); restart(); }
  }
  function restart() {
    window.clearInterval(timer);
    if (!reducedMotion() && items.length > perView()) timer = window.setInterval(() => go(index + 1, false), AUTOPLAY_MS);
  }
  viewport.addEventListener('keydown', (event) => {
    if (event.key === 'ArrowRight') { event.preventDefault(); go(index + 1, true); }
    if (event.key === 'ArrowLeft') { event.preventDefault(); go(index - 1, true); }
  });
  let startX = null;
  viewport.addEventListener('pointerdown', (event) => { startX = event.clientX; });
  viewport.addEventListener('pointerup', (event) => {
    if (startX !== null && Math.abs(event.clientX - startX) > 40) go(index + (event.clientX < startX ? 1 : -1), true);
    startX = null;
  });
  const root = el('div', { class: 'tm-carousel' }, prev, viewport, next, dots);
  root.addEventListener('mouseenter', () => window.clearInterval(timer));
  root.addEventListener('mouseleave', restart);
  root.addEventListener('focusin', () => window.clearInterval(timer));
  window.addEventListener('resize', () => go(index, false));
  requestAnimationFrame(() => { go(0, false); restart(); });
  root.goTo = (id) => { const i = items.findIndex((item) => item.id === id); if (i >= 0) go(Math.min(i, items.length - 1), false); };
  return root;
}

/** Formulario «Deja tu testimonio» (clientes con sesión). */
function testimonialForm(onSaved) {
  const box = el('div', { class: 'card tm-form', id: 'deja-tu-testimonio' });
  const user = session.user();
  if (!user) {
    box.append(el('h3', { text: t('¿Compraste en POLYMER? Cuéntanos tu experiencia') }),
      el('p', { class: 'muted', text: t('Inicia sesión con tu cuenta de cliente para dejar tu testimonio. Aparecerá en el carrusel al instante.') }),
      el('a', { class: 'btn btn--primary', href: localizeHref(`/login.html?next=${encodeURIComponent('/nosotros.html#testimonios')}`), text: t('Iniciar sesión para opinar') }));
    return box;
  }
  if (!session.has('CUSTOMER')) {
    box.append(el('h3', { text: t('Deja tu testimonio') }), el('p', { class: 'muted', text: t('Los testimonios son de clientes. Inicia sesión con una cuenta de cliente para dejar el tuyo.') }));
    return box;
  }
  const comment = el('textarea', { rows: '4', maxlength: '600', minlength: '20', required: true,
    placeholder: t('¿Qué te pareció tu compra, la entrega o la página? (20 a 600 caracteres)') });
  const counter = el('small', { class: 'muted tm-form__count', text: '0/600' });
  comment.addEventListener('input', () => { counter.textContent = `${comment.value.length}/600`; });
  let rating = 5;
  const starButtons = [1, 2, 3, 4, 5].map((n) => el('button', { type: 'button', class: 'tm-rate__star', 'aria-label': t('{0} de 5 estrellas', n), text: '★',
    onclick: () => { rating = n; paintStars(); play('tap'); } }));
  const paintStars = () => starButtons.forEach((b, i) => { b.classList.toggle('is-on', i < rating); b.setAttribute('aria-pressed', String(i < rating)); });
  paintStars();
  const role = el('input', { maxlength: '80', placeholder: t('Ej.: Estudiante de ingeniería, diseñadora, gamer') });
  const linkedin = el('input', { type: 'url', maxlength: '200', placeholder: 'https://www.linkedin.com/in/tu-perfil' });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: t('Publicar testimonio') });
  const form = el('form', { class: 'form', novalidate: true },
    el('h3', { text: t('Deja tu testimonio') }),
    el('p', { class: 'muted small', text: t('Publicarás como {0}. Puedes editarlo cuando quieras; solo hay uno por cuenta.', user.displayName) }),
    el('div', { class: 'tm-rate', role: 'group', 'aria-label': t('Tu calificación') }, el('span', { class: 'muted small', text: t('Tu calificación') }), starButtons),
    field(t('Tu testimonio'), comment), counter,
    field(t('¿A qué te dedicas? (opcional)'), role),
    field(t('Tu perfil de LinkedIn (opcional)'), linkedin),
    submit, errorNode);
  testimonialsApi.mine().then((mine) => {
    if (!mine) return;
    comment.value = mine.comment; counter.textContent = `${mine.comment.length}/600`;
    role.value = mine.role || ''; linkedin.value = mine.linkedinUrl || '';
    rating = mine.rating || 5; paintStars();
    submit.textContent = t('Actualizar testimonio');
  }).catch(() => {});
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (comment.value.trim().length < 20) { comment.focus(); errorNode.textContent = t('Cuéntanos tu experiencia en al menos 20 caracteres.'); errorNode.hidden = false; return; }
    try {
      const saved = await submitting(submit, errorNode, () => testimonialsApi.submit({ comment: comment.value, rating, role: role.value, linkedinUrl: linkedin.value }),
        t('¡Gracias! Tu testimonio ya está en el carrusel'));
      play('success');
      submit.textContent = t('Actualizar testimonio');
      onSaved(saved);
    } catch { /* el error ya se muestra */ }
  });
  box.append(form);
  return box;
}

/** Sección completa: título, carrusel y (opcional) el formulario. */
export function testimonialsSection({ withForm = false, compact = false } = {}) {
  const holder = el('div', { class: 'tm-holder' }, el('div', { class: 'tm-skeleton skeleton' }));
  const load = (focusId) => testimonialsApi.list().then((items) => {
    if (!items.length) { holder.replaceChildren(el('p', { class: 'muted', text: t('Aún no hay testimonios. ¡Sé el primero!') })); return; }
    const c = carousel(items);
    holder.replaceChildren(c);
    if (focusId) requestAnimationFrame(() => c.goTo(focusId));
  }).catch(() => holder.replaceChildren(el('p', { class: 'muted', text: t('Los testimonios no están disponibles en este momento.') })));
  load();
  return el('section', { class: `rail testimonials${compact ? ' testimonials--compact' : ''}`, id: 'testimonios', 'data-testid': 'testimonials' },
    el('div', { class: 'rail__head' }, el('div', {},
      el('p', { class: 'eyebrow', text: t('Testimonios') }),
      el('h2', { class: 'rail__title', text: t('Lo que dicen de POLYMER') }),
      el('p', { class: 'section__lead', text: t('Conoce al equipo que construyó POLYMER y lo que opinan nuestros clientes.') })),
    withForm ? null : el('a', { class: 'btn btn--ghost', href: '/nosotros.html#deja-tu-testimonio', text: t('Deja tu testimonio') })),
    holder,
    withForm ? testimonialForm((saved) => load(saved.id)) : null);
}
