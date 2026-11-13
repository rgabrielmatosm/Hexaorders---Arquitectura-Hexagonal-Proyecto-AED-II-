// POLYMER — sonidos de la interfaz, sintetizados con Web Audio (sin archivos ni música de terceros).
// v9: ACTIVADOS por defecto (se pueden silenciar desde el pie de página; la preferencia vive en localStorage).
// Los navegadores no permiten sonar antes de la primera interacción: lo que se pida antes (la bienvenida de la
// intro) suena en el primer clic, toque o tecla si llega a tiempo. Volumen bajo, sin música continua.

const KEY = 'polymer.sound';
let ctx = null;
let master = null;

const listeners = new Set();

export function soundEnabled() {
  try { return localStorage.getItem(KEY) !== 'off'; } catch { return true; }
}

// Sonido pedido antes de que el navegador permita reproducir: se reproduce al desbloquear (máx. 6 s después).
let pending = null;
function unlock() {
  const audio = context();
  if (!audio) return;
  const resume = audio.state === 'suspended' ? audio.resume() : Promise.resolve();
  resume.then(() => {
    if (pending && Date.now() - pending.at < 6000 && soundEnabled()) { const name = pending.name; pending = null; play(name); }
  }).catch(() => {});
}
['pointerdown', 'keydown', 'touchstart'].forEach((type) => window.addEventListener(type, unlock, { capture: true, passive: true }));

export function setSound(on) {
  try { localStorage.setItem(KEY, on ? 'on' : 'off'); } catch { /* almacenamiento bloqueado */ }
  listeners.forEach((fn) => fn(on));
  if (on) context();
}

export function onSoundChange(fn) { listeners.add(fn); return () => listeners.delete(fn); }

function context() {
  if (ctx) { if (ctx.state === 'suspended') ctx.resume().catch(() => {}); return ctx; }
  const Ctor = window.AudioContext || window.webkitAudioContext;
  if (!Ctor) return null;
  ctx = new Ctor();
  master = ctx.createGain();
  master.gain.value = 0.16;
  // Compresor suave: evita picos si se encadenan sonidos.
  const comp = ctx.createDynamicsCompressor();
  master.connect(comp).connect(ctx.destination);
  return ctx;
}

/** Una nota con envolvente suave. */
function tone(freq, start, length, { type = 'sine', gain = 0.5, glide = 0 } = {}) {
  const osc = ctx.createOscillator();
  const env = ctx.createGain();
  const filter = ctx.createBiquadFilter();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, start);
  if (glide) osc.frequency.exponentialRampToValueAtTime(freq * glide, start + length);
  filter.type = 'lowpass';
  filter.frequency.value = 3200;
  env.gain.setValueAtTime(0.0001, start);
  env.gain.exponentialRampToValueAtTime(gain, start + 0.02);
  env.gain.exponentialRampToValueAtTime(0.0001, start + length);
  osc.connect(filter).connect(env).connect(master);
  osc.start(start);
  osc.stop(start + length + 0.05);
}

const SOUNDS = {
  // Bienvenida: acorde ascendente con un brillo final (≈1,3 s).
  welcome(t) {
    [[392, 0], [523.25, 0.12], [659.25, 0.24], [783.99, 0.36]].forEach(([f, d]) => {
      tone(f, t + d, 0.9, { type: 'triangle', gain: 0.28 });
      tone(f * 2, t + d, 0.5, { type: 'sine', gain: 0.07 });
    });
    tone(1567.98, t + 0.55, 0.8, { type: 'sine', gain: 0.1 });
  },
  confirm(t) { tone(659.25, t, 0.18, { type: 'triangle', gain: 0.3 }); tone(987.77, t + 0.1, 0.32, { type: 'triangle', gain: 0.3 }); },
  // v9: clic suave en botones y enlaces principales.
  tap(t) { tone(1318.5, t, 0.06, { type: 'sine', gain: 0.12, glide: 0.8 }); },
  // v9: cambio de diapositiva del carrusel.
  slide(t) { tone(523.25, t, 0.12, { type: 'sine', gain: 0.12, glide: 1.5 }); },
  // v9: aparición de una sección o panel (barrido breve ascendente).
  whoosh(t) { tone(220, t, 0.28, { type: 'sine', gain: 0.07, glide: 3.2 }); },
  // v9: producto añadido / formulario enviado.
  success(t) { [[783.99, 0], [1046.5, 0.08], [1318.5, 0.16]].forEach(([f, d]) => tone(f, t + d, 0.25, { type: 'triangle', gain: 0.2 })); },
  notify(t) { tone(880, t, 0.28, { type: 'sine', gain: 0.22 }); },
  error(t) { tone(311.13, t, 0.2, { type: 'triangle', gain: 0.3 }); tone(233.08, t + 0.14, 0.3, { type: 'triangle', gain: 0.3, glide: 0.94 }); },
};

/** Reproduce un sonido si están activados. `force` se usa solo tras un clic explícito de la persona. */
export function play(name, { force = false } = {}) {
  if (!force && !soundEnabled()) return false;
  const fn = SOUNDS[name];
  if (!fn) return false;
  try {
    if (!context()) return false;
    if (ctx.state !== 'running') {
      // Aún sin interacción: se intenta reanudar y, si no se puede, se deja pendiente para el primer gesto.
      pending = { name, at: Date.now() };
      ctx.resume().then(() => { if (pending && pending.name === name) { pending = null; fn(ctx.currentTime + 0.02); } }).catch(() => {});
      return false;
    }
    fn(ctx.currentTime + 0.02);
    return true;
  } catch {
    return false; // el audio nunca debe romper la página
  }
}
