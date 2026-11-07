// Interactive Grid: cuadrícula de fondo que se ilumina en violeta bajo el cursor y se
// desvanece progresivamente (~1.7 s). Un solo <canvas>, sin librerías ni miles de nodos DOM.
//  - Las líneas estáticas se dibujan una vez en un canvas fuera de pantalla (por resize).
//  - requestAnimationFrame solo corre mientras haya celdas iluminadas; si no, se detiene.
//  - prefers-reduced-motion: sin estela animada; solo se resalta la celda actual.

const FADE_MS = 1700;
const VIOLET = [61, 226, 208];
const VIOLET_LIGHT = [166, 184, 255];

export function initGrid(canvas) {
  if (!canvas || !canvas.getContext) return;
  const ctx = canvas.getContext('2d');
  const staticLayer = document.createElement('canvas');
  const staticCtx = staticLayer.getContext('2d');
  const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

  let cell = 32;
  let cols = 0;
  let rows = 0;
  let dpr = 1;
  let width = 0;
  let height = 0;
  let frame = 0;
  let lastIndex = -1;
  const active = new Map(); // índice de celda -> instante en que se iluminó

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth;
    height = window.innerHeight;
    cell = width < 640 ? 26 : 32;
    cols = Math.ceil(width / cell);
    rows = Math.ceil(height / cell);
    for (const target of [canvas, staticLayer]) {
      target.width = Math.round(width * dpr);
      target.height = Math.round(height * dpr);
    }
    canvas.style.width = `${width}px`;
    canvas.style.height = `${height}px`;
    drawStaticGrid();
    draw(performance.now());
  }

  function drawStaticGrid() {
    staticCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    staticCtx.clearRect(0, 0, width, height);
    staticCtx.strokeStyle = 'rgba(148, 163, 184, 0.055)';
    staticCtx.lineWidth = 1;
    staticCtx.beginPath();
    for (let x = 0; x <= cols; x += 1) {
      const px = Math.round(x * cell) + 0.5;
      staticCtx.moveTo(px, 0);
      staticCtx.lineTo(px, height);
    }
    for (let y = 0; y <= rows; y += 1) {
      const py = Math.round(y * cell) + 0.5;
      staticCtx.moveTo(0, py);
      staticCtx.lineTo(width, py);
    }
    staticCtx.stroke();
  }

  function paintCell(index, intensity) {
    const x = (index % cols) * cell;
    const y = Math.floor(index / cols) * cell;
    ctx.fillStyle = `rgba(${VIOLET[0]}, ${VIOLET[1]}, ${VIOLET[2]}, ${0.42 * intensity})`;
    ctx.fillRect(x + 1, y + 1, cell - 1, cell - 1);
    ctx.strokeStyle = `rgba(${VIOLET_LIGHT[0]}, ${VIOLET_LIGHT[1]}, ${VIOLET_LIGHT[2]}, ${0.75 * intensity})`;
    ctx.strokeRect(x + 1.5, y + 1.5, cell - 2, cell - 2);
  }

  function draw(now) {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(staticLayer, 0, 0);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.lineWidth = 1;
    for (const [index, startedAt] of active) {
      const progress = (now - startedAt) / FADE_MS;
      if (progress >= 1) { active.delete(index); continue; }
      const intensity = 1 - progress * progress * (3 - 2 * progress); // smoothstep inverso
      paintCell(index, intensity);
    }
  }

  function loop(now) {
    draw(now);
    frame = active.size > 0 && !document.hidden ? requestAnimationFrame(loop) : 0;
  }

  function onPointer(event) {
    const col = Math.floor(event.clientX / cell);
    const row = Math.floor(event.clientY / cell);
    if (col < 0 || row < 0 || col >= cols || row >= rows) return;
    const index = row * cols + col;
    if (index === lastIndex) return;
    lastIndex = index;
    if (motionQuery.matches) {
      // Movimiento reducido: resaltado inmediato y estático de la celda actual.
      active.clear();
      draw(performance.now());
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      paintCell(index, 0.6);
      return;
    }
    active.set(index, performance.now()); // respuesta inmediata al entrar en la celda
    if (!frame) frame = requestAnimationFrame(loop);
  }

  let resizeTimer = 0;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(resize, 120);
  });
  window.addEventListener('pointermove', onPointer, { passive: true });
  window.addEventListener('pointerdown', onPointer, { passive: true });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && active.size && !frame) frame = requestAnimationFrame(loop);
  });
  motionQuery.addEventListener?.('change', () => { active.clear(); draw(performance.now()); });
  resize();
}
