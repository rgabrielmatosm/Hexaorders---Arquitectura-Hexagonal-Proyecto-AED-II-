// POLYMER — campo de contraseña con indicador de seguridad, mostrar/ocultar y confirmación.
// Replica en el navegador la política del servidor (domain/identity/PasswordPolicy.java) solo como
// AYUDA: quien acepta o rechaza la contraseña es el backend. Usa la misma lista local de contraseñas
// comunes (/security/common-passwords.txt); nada se envía a servicios externos.

import { el } from './ui.js';
import { t } from './i18n.js';

export const MIN_LENGTH = 12;
export const MAX_LENGTH = 64;
const MAX_BYTES = 72;

let blocklist = null;
async function loadBlocklist() {
  if (blocklist) return blocklist;
  try {
    const text = await (await fetch('/security/common-passwords.txt')).text();
    blocklist = new Set(text.split('\n').map((l) => l.trim().toLowerCase()).filter((l) => l && !l.startsWith('#')));
  } catch {
    blocklist = new Set();
  }
  return blocklist;
}

/** Mensajes de cada problema (los mismos códigos que devuelve el servidor en «problems»). */
export const PROBLEM_TEXT = {
  TOO_SHORT: () => t('Usa al menos {0} caracteres; una frase de varias palabras es ideal.', MIN_LENGTH),
  TOO_LONG: () => t('Usa como máximo {0} caracteres.', MAX_LENGTH),
  COMMON: () => t('Es una contraseña muy común o conocida: elige otra.'),
  CONTAINS_PERSONAL_DATA: () => t('No incluyas tu usuario, nombre ni correo.'),
  REPETITIVE: () => t('Evita repeticiones y secuencias como 123456 o abcdef.'),
};

/** Misma evaluación que PasswordPolicy.assess (WEAK, FAIR o STRONG y la lista de problemas). */
export function assessPassword(password, personal = [], common = blocklist || new Set()) {
  const value = password || '';
  const problems = [];
  const length = [...value].length;
  if (length < MIN_LENGTH) problems.push('TOO_SHORT');
  if (length > MAX_LENGTH || new TextEncoder().encode(value).length > MAX_BYTES) problems.push('TOO_LONG');
  const lower = value.toLowerCase();
  const core = lower.replace(/^[^\p{L}]+|[^\p{L}]+$/gu, '');
  const compact = lower.replace(/[\s._-]+/g, '');
  if (lower && (common.has(lower) || common.has(compact) || (core.length >= 4 && common.has(core)))) problems.push('COMMON');
  outer: for (const data of personal) {
    for (const part of String(data || '').toLowerCase().split(/[@\s._-]+/)) {
      if (part.length >= 4 && compact.includes(part)) { problems.push('CONTAINS_PERSONAL_DATA'); break outer; }
    }
  }
  if (length >= MIN_LENGTH && (new Set(compact).size < 5 || isSequence(compact))) problems.push('REPETITIVE');
  if (problems.length) return { strength: 'WEAK', problems };
  return { strength: score(value, length) >= 24 ? 'STRONG' : 'FAIR', problems };
}

function score(value, length) {
  const classes = [/\p{Ll}/u, /\p{Lu}/u, /\d/, /[^\p{L}\d]/u].filter((re) => re.test(value)).length;
  const words = value.trim().split(/[\s._-]+/).length;
  return Math.min(length, 40) + 4 * Math.max(0, classes - 1) + (words >= 3 ? 6 : 0);
}

function isSequence(value) {
  if (value.length < 4) return false;
  let up = 0;
  let down = 0;
  for (let i = 1; i < value.length; i++) {
    const step = value.charCodeAt(i) - value.charCodeAt(i - 1);
    if (step === 1 || step === -9) up++;
    if (step === -1 || step === 9) down++;
  }
  return up >= value.length - 2 || down >= value.length - 2;
}

const LEVEL_TEXT = { WEAK: () => t('Contraseña débil'), FAIR: () => t('Contraseña aceptable'), STRONG: () => t('Contraseña fuerte') };

/** Botón accesible para mostrar u ocultar el texto de un campo de contraseña. */
export function revealToggle(input) {
  const button = el('button', { type: 'button', class: 'pw-reveal', 'aria-pressed': 'false', text: t('Mostrar') });
  button.setAttribute('aria-label', t('Mostrar contraseña'));
  button.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? t('Ocultar') : t('Mostrar');
    button.setAttribute('aria-pressed', String(show));
    button.setAttribute('aria-label', show ? t('Ocultar contraseña') : t('Mostrar contraseña'));
    input.focus();
  });
  return button;
}

/**
 * Campo «nueva contraseña» + «repetir contraseña» con indicador dinámico.
 * @param personal función que devuelve usuario, nombre y correo actuales (no deben aparecer).
 * @returns { element, input, confirm, check() } — check() devuelve un mensaje de error o null.
 */
export function newPasswordFields({ label = t('Contraseña'), personal = () => [] } = {}) {
  const id = `pw-${Math.random().toString(36).slice(2)}`;
  const input = el('input', { id, type: 'password', autocomplete: 'new-password', maxlength: String(MAX_LENGTH), 'aria-describedby': `${id}-meter ${id}-help` });
  const confirm = el('input', { id: `${id}-2`, type: 'password', autocomplete: 'new-password', maxlength: String(MAX_LENGTH) });
  const bar = el('span', { class: 'pw-meter__bar' });
  const level = el('strong', { class: 'pw-meter__level', text: '' });
  const meter = el('div', { class: 'pw-meter', id: `${id}-meter`, 'aria-live': 'polite', dataset: { level: 'EMPTY' } },
    el('span', { class: 'pw-meter__track', 'aria-hidden': 'true' }, bar), level);
  const tips = el('ul', { class: 'pw-tips' });
  const match = el('small', { class: 'pw-match', 'aria-live': 'polite' });
  const help = el('small', { class: 'field__help', id: `${id}-help`,
    text: t('Mínimo {0} caracteres. Mejor una frase larga y única (puedes usar espacios); combinar letras, números y símbolos la refuerza.', MIN_LENGTH) });

  const update = () => {
    const value = input.value;
    if (!value) {
      meter.dataset.level = 'EMPTY';
      level.textContent = '';
      tips.replaceChildren();
    } else {
      const result = assessPassword(value, personal());
      meter.dataset.level = result.strength;
      level.textContent = LEVEL_TEXT[result.strength]();
      tips.replaceChildren(...result.problems.map((code) => el('li', { text: PROBLEM_TEXT[code]() })));
    }
    if (confirm.value) {
      const same = confirm.value === value;
      match.textContent = same ? t('Las contraseñas coinciden.') : t('Las contraseñas no coinciden.');
      match.dataset.ok = String(same);
    } else {
      match.textContent = '';
    }
  };
  loadBlocklist().then(update);
  input.addEventListener('input', update);
  confirm.addEventListener('input', update);

  const element = el('div', { class: 'pw-group field--full' },
    el('div', { class: 'field' }, el('label', { for: id, text: label }),
      el('div', { class: 'pw-input' }, input, revealToggle(input)), meter, help, tips),
    el('div', { class: 'field' }, el('label', { for: `${id}-2`, text: t('Repite la contraseña') }),
      el('div', { class: 'pw-input' }, confirm, revealToggle(confirm)), match));

  return {
    element, input, confirm,
    check() {
      const result = assessPassword(input.value, personal());
      if (result.problems.length) return PROBLEM_TEXT[result.problems[0]]();
      if (input.value !== confirm.value) return t('Las contraseñas no coinciden.');
      return null;
    },
    reset() { input.value = ''; confirm.value = ''; update(); },
  };
}
