// POLYMER — intro cinematográfica (≈4 s): textura tecnológica, isotipo que se arma con líneas de luz,
// letras P·O·L·Y·M·E·R escalonadas, apertura de telón y entrada de la página ya cargada detrás.
// Reglas: una vez por sesión (sessionStorage), sin botones (v9): suena la bienvenida por defecto (si el navegador
// aún no lo permite, suena con el primer clic), un clic o Esc la abren antes, no bloquea la navegación y no se
// muestra con prefers-reduced-motion (intro-gate.js ni siquiera la activa). Solo transform, opacity y stroke-dashoffset.

import { BRAND, INTRO, MARK_GEOMETRY } from './brand.js';
import { play } from './audio.js';
import { t } from './i18n.js';

const NS = 'http://www.w3.org/2000/svg';

function svgEl(name, attrs = {}) {
  const node = document.createElementNS(NS, name);
  Object.entries(attrs).forEach(([k, v]) => node.setAttribute(k, String(v)));
  return node;
}

function buildMark() {
  const svg = svgEl('svg', { viewBox: '0 0 64 64', class: 'intro__mark', 'aria-hidden': 'true' });
  const defs = svgEl('defs');
  const grad = svgEl('linearGradient', { id: 'intro-g', gradientUnits: 'userSpaceOnUse', x1: 14, y1: 10, x2: 54, y2: 54 });
  grad.append(svgEl('stop', { offset: 0, 'stop-color': '#3DE2D0' }), svgEl('stop', { offset: 1, 'stop-color': '#A6B8FF' }));
  defs.append(grad);
  const lines = svgEl('g', { fill: 'none', stroke: 'url(#intro-g)', 'stroke-width': 3.4, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' });
  const paths = MARK_GEOMETRY.lines.map((d) => svgEl('path', { d, pathLength: 1, class: 'intro__line' }));
  const signal = svgEl('path', { d: MARK_GEOMETRY.signal, pathLength: 1, class: 'intro__line intro__signal' });
  lines.append(...paths, signal);
  const nodes = svgEl('g', { fill: '#101827', stroke: 'url(#intro-g)', 'stroke-width': 2.6 });
  const circles = MARK_GEOMETRY.nodes.map(([cx, cy, r]) => svgEl('circle', { cx, cy, r, class: 'intro__node' }));
  nodes.append(...circles);
  const [cx, cy, r] = MARK_GEOMETRY.core;
  const core = svgEl('circle', { cx, cy, r, fill: '#3DE2D0', class: 'intro__node intro__core' });
  svg.append(defs, lines, nodes, core);
  return { svg, paths: [...paths, signal], nodes: [...circles, core] };
}

let running = false;

/** Inicia la intro si intro-gate.js la marcó como pendiente. Devuelve una promesa que no bloquea nada. */
export function startIntro() {
  const html = document.documentElement;
  if (running || !html.classList.contains('intro-pending') || !INTRO.enabled) return Promise.resolve(false);
  running = true;
  html.classList.add('intro-running');
  try { sessionStorage.setItem(INTRO.storageKey, '1'); } catch { /* sin almacenamiento: se mostraría de nuevo */ }

  const root = document.createElement('div');
  root.id = 'polymer-intro';
  root.className = 'intro';
  root.setAttribute('role', 'dialog');
  root.setAttribute('aria-modal', 'false');
  root.setAttribute('aria-label', t('Bienvenida a {0}', BRAND.name));
  const left = document.createElement('div'); left.className = 'intro__curtain intro__curtain--left';
  const right = document.createElement('div'); right.className = 'intro__curtain intro__curtain--right';
  const stage = document.createElement('div'); stage.className = 'intro__stage';
  const { svg, paths, nodes } = buildMark();
  const word = document.createElement('p');
  word.className = 'intro__word';
  word.setAttribute('aria-label', BRAND.name);
  const letters = [...BRAND.name].map((ch) => { const s = document.createElement('span'); s.textContent = ch; s.setAttribute('aria-hidden', 'true'); return s; });
  word.append(...letters);
  const slogan = document.createElement('p'); slogan.className = 'intro__slogan'; slogan.textContent = BRAND.slogan;
  stage.append(svg, word, slogan);
  const hint = document.createElement('p'); hint.className = 'visually-hidden'; hint.textContent = t('Pulsa Esc o haz clic para continuar');
  root.append(left, right, stage, hint);
  document.body.append(root);
  html.classList.remove('intro-pending'); // el telón ya cubre la página
  root.tabIndex = -1;
  root.focus({ preventScroll: true });
  play('welcome'); // v9: sonido por defecto (si el navegador lo bloquea, suena con el primer clic)

  const anims = [];
  const run = (node, frames, options) => { const a = node.animate(frames, { fill: 'both', ...options }); anims.push(a); return a; };
  const ease = 'cubic-bezier(.22,1,.36,1)';
  // 1. Líneas de luz que arman el isotipo.
  paths.forEach((p, i) => run(p, [{ strokeDashoffset: 1, opacity: 0.2 }, { strokeDashoffset: 0, opacity: 1 }], { duration: 900, delay: 150 + i * 160, easing: ease }));
  nodes.forEach((n, i) => run(n, [{ opacity: 0, transform: 'scale(.3)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 380, delay: 760 + i * 90, easing: ease }));
  run(svg, [{ filter: 'drop-shadow(0 0 0 rgba(61,226,208,0))' }, { filter: 'drop-shadow(0 0 18px rgba(61,226,208,.55))' }, { filter: 'drop-shadow(0 0 8px rgba(61,226,208,.25))' }], { duration: 1400, delay: 900 });
  // 2. Letras P O L Y M E R: desplazamiento + opacidad + desenfoque.
  letters.forEach((s, i) => run(s, [{ opacity: 0, transform: 'translateY(14px)', filter: 'blur(8px)' }, { opacity: 1, transform: 'none', filter: 'blur(0)' }], { duration: 620, delay: 1150 + i * 85, easing: ease }));
  run(slogan, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }], { duration: 600, delay: 1900, easing: ease });

  let finished = false;
  let resolveDone;
  const done = new Promise((resolve) => { resolveDone = resolve; });
  const timer = window.setTimeout(() => open(), INTRO.durationMs);

  function open() {
    if (finished) return;
    finished = true;
    window.clearTimeout(timer);
    document.removeEventListener('keydown', onKey);
    anims.forEach((a) => { try { a.finish(); } catch { /* ya terminada */ } });
    root.classList.add('is-opening');
    run(stage, [{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(1.06)' }], { duration: 420, easing: 'ease-in' });
    run(left, [{ transform: 'translateX(0)' }, { transform: 'translateX(-101%)' }], { duration: 900, delay: 160, easing: 'cubic-bezier(.7,0,.2,1)' });
    const last = run(right, [{ transform: 'translateX(0)' }, { transform: 'translateX(101%)' }], { duration: 900, delay: 160, easing: 'cubic-bezier(.7,0,.2,1)' });
    html.classList.add('polymer-enter'); // entrada escalonada de cabecera, héroe y productos (CSS)
    last.finished.catch(() => {}).finally(() => {
      root.remove();
      html.classList.remove('intro-running');
      window.setTimeout(() => html.classList.remove('polymer-enter'), 1400);
      resolveDone(true);
    });
  }
  function onKey(event) { if (event.key === 'Escape') open(); }
  document.addEventListener('keydown', onKey);
  // Un clic en cualquier punto abre (la intro nunca atrapa a la persona). El sonido pendiente suena con ese clic.
  root.addEventListener('click', () => window.setTimeout(open, 350));
  return done;
}
