// POLYMER — sonidos de la interfaz, sintetizados con Web Audio (sin archivos ni música de terceros).
// Política: apagado por defecto; solo suena si la persona lo activa (botón de la intro o de la
// cabecera). Nunca suena al pasar el ratón ni al desplazarse. La preferencia vive en localStorage.

const KEY = 'polymer.sound';
let ctx = null;
let master = null;

const listeners = new Set();

export function soundEnabled() {
  try { return localStorage.getItem(KEY) === 'on'; } catch { return false; }
}

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
    fn(ctx.currentTime + 0.02);
    return true;
  } catch {
    return false; // el audio nunca debe romper la página
  }
}
