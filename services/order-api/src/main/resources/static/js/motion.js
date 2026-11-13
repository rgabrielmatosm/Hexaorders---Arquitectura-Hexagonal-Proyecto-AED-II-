// POLYMER — sistema de movimiento: aparición al desplazarse, escalonado de tarjetas, indicador de
// navegación, «vuelo» al carrito, parallax sutil y check animado de confirmación.
// Solo anima transform y opacity. Con prefers-reduced-motion todo queda estático y visible.

const NS = 'http://www.w3.org/2000/svg';
export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// Elementos que aparecen al entrar en pantalla. Las tablas, formularios y gráficos del personal no
// se animan para no estorbar su lectura.
const REVEAL = [
  '.view__head', '.store-benefit', '.promo-tile', '.category-tile', '.product-card', '.store-section > header',
  '.offer-card', '.help-card', '.info-block', '.site-footer__col', '.footer-pay', '.card.reveal-ok',
].join(',');

let observer = null;

function observe(node) {
  if (!(node instanceof Element)) return;
  const targets = node.matches?.(REVEAL) ? [node] : [];
  targets.push(...node.querySelectorAll(REVEAL));
  targets.forEach((target) => {
    if (target.dataset.reveal) return;
    target.dataset.reveal = '1';
    // Escalonado: posición dentro de su contenedor (máx. 10 pasos para no retrasar la lectura).
    const index = Array.prototype.indexOf.call(target.parentElement?.children || [], target);
    target.style.setProperty('--i', String(Math.max(0, Math.min(index, 10))));
    target.classList.add('reveal');
    observer.observe(target);
  });
}

function startReveal() {
  if (reducedMotion() || !('IntersectionObserver' in window)) return;
  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-in');
      observer.unobserve(entry.target);
    });
  }, { rootMargin: '0px 0px -6% 0px', threshold: 0.06 });
  observe(document.body);
  new MutationObserver((records) => records.forEach((r) => r.addedNodes.forEach(observe)))
    .observe(document.body, { childList: true, subtree: true });
}

// ---- Indicador de navegación entre páginas --------------------------------------------------------
function startNavProgress() {
  const bar = document.createElement('div');
  bar.className = 'nav-progress';
  bar.setAttribute('aria-hidden', 'true');
  document.body.append(bar);
  const begin = () => { bar.classList.remove('is-done'); bar.classList.add('is-loading'); };
  document.addEventListener('click', (event) => {
    const a = event.target.closest?.('a[href]');
    if (!a || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    if (a.target && a.target !== '_self') return;
    if (a.hasAttribute('download')) return;
    const url = new URL(a.href, window.location.href);
    if (url.origin !== window.location.origin) return;
    if (url.pathname === window.location.pathname && url.search === window.location.search && url.hash) return; // ancla
    begin();
  });
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!event.defaultPrevented && form.method?.toLowerCase() === 'get' && form.action) begin();
  });
  // Al volver con el historial (bfcache) se limpia el indicador.
  window.addEventListener('pageshow', () => { bar.classList.remove('is-loading'); bar.classList.add('is-done'); });
}

// ---- Parallax sutil del héroe de la portada ---------------------------------------------------------
function startParallax() {
  if (reducedMotion()) return;
  let ticking = false;
  window.addEventListener('scroll', () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      ticking = false;
      const y = Math.min(window.scrollY, 600);
      document.querySelectorAll('[data-parallax]').forEach((node) => {
        node.style.transform = `translate3d(0, ${(y * Number(node.dataset.parallax)).toFixed(1)}px, 0)`;
      });
    });
  }, { passive: true });
}

// ---- Vuelo al carrito ---------------------------------------------------------------------------------
export function flyToCart(source) {
  const target = document.querySelector('#cart-button');
  if (!target || !source) return;
  target.classList.remove('is-bumped');
  void target.offsetWidth; // reinicia la animación del contador
  target.classList.add('is-bumped');
  if (reducedMotion() || !source.getBoundingClientRect || !document.body.animate) return;
  const from = source.getBoundingClientRect();
  const to = target.getBoundingClientRect();
  const dot = document.createElement('span');
  dot.className = 'fly-dot';
  dot.setAttribute('aria-hidden', 'true');
  dot.style.left = `${from.left + from.width / 2 - 7}px`;
  dot.style.top = `${from.top + from.height / 2 - 7}px`;
  document.body.append(dot);
  const dx = to.left + to.width / 2 - (from.left + from.width / 2);
  const dy = to.top + to.height / 2 - (from.top + from.height / 2);
  dot.animate([
    { transform: 'translate(0, 0) scale(1)', opacity: 1 },
    { transform: `translate(${dx * 0.5}px, ${dy * 0.5 - 70}px) scale(1.15)`, opacity: 1, offset: 0.55 },
    { transform: `translate(${dx}px, ${dy}px) scale(0.4)`, opacity: 0.2 },
  ], { duration: 720, easing: 'cubic-bezier(.5,0,.2,1)' }).finished.finally(() => dot.remove());
}

// ---- Check animado (confirmación de pedido) -----------------------------------------------------------
export function animatedCheck() {
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 52 52');
  svg.setAttribute('class', 'check-anim');
  svg.setAttribute('aria-hidden', 'true');
  const circle = document.createElementNS(NS, 'circle');
  Object.entries({ cx: 26, cy: 26, r: 23, pathLength: 1, class: 'check-anim__ring' }).forEach(([k, v]) => circle.setAttribute(k, v));
  const tick = document.createElementNS(NS, 'path');
  Object.entries({ d: 'M15 27 L23 34 L38 18', pathLength: 1, class: 'check-anim__tick' }).forEach(([k, v]) => tick.setAttribute(k, v));
  svg.append(circle, tick);
  return svg;
}

let started = false;
export function startMotion() {
  if (started) return;
  started = true;
  document.documentElement.classList.toggle('reduced-motion', reducedMotion());
  startReveal();
  startNavProgress();
  startParallax();
}
