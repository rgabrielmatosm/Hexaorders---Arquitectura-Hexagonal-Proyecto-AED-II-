// v7 · Acceso: «Continuar con Google», verificación con código de 6 dígitos enviado por correo y
// «Recordarme durante 30 días». El servidor decide si hace falta el código (la sesión no queda
// autenticada hasta verificarlo); aquí solo se presenta el paso y se envía el código.

import { authApi } from './api.js';
import { formError, showFormError, submitting } from './components.js';
import { session } from './session.js';
import { el, toast } from './ui.js';
import { t } from './i18n.js';

let providersCache = null;
/** Métodos de acceso disponibles (se pide una vez por página). */
export function providers() {
  if (!providersCache) providersCache = authApi.providers().catch(() => ({ google: false, emailVerification: false, rememberDays: 30 }));
  return providersCache;
}

/** Botón «Continuar con Google» (o «Registrarse con Google»). Navegación completa: Google exige redirecciones. */
export function googleButton(label, next) {
  const from = window.location.pathname;
  return el('a', { class: 'btn btn--google btn--block', href: authApi.googleUrl(next || null, from), 'data-testid': 'google-signin' },
    el('img', { src: '/img/google-g.svg', alt: '', width: '18', height: '18' }), el('span', { text: label }));
}

export const divider = (text) => el('div', { class: 'auth-divider', role: 'separator' }, el('span', { text }));

export function rememberCheckbox(checked = false) {
  const input = el('input', { type: 'checkbox', name: 'remember', checked });
  const label = el('label', { class: 'check-row' }, input,
    el('span', {}, el('strong', { text: t('Recordarme durante 30 días') }),
      el('small', { class: 'muted', text: t('En este dispositivo no volveremos a pedirte el código ni la contraseña durante 30 días. No lo marques en equipos compartidos.') })));
  return { element: label, input };
}

function format(seconds) {
  const s = Math.max(0, Math.round(seconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/** Seis casillas de un dígito: avanzan solas, aceptan pegar el código completo y se borran con retroceso. */
function codeInputs(digits, onComplete) {
  const boxes = Array.from({ length: digits }, (_, i) => el('input', {
    class: 'otp__box', inputmode: 'numeric', pattern: '[0-9]*', maxlength: '1', autocomplete: i === 0 ? 'one-time-code' : 'off',
    'aria-label': t('Dígito {0} de {1}', i + 1, digits),
  }));
  const value = () => boxes.map((b) => b.value).join('');
  const fill = (text, from = 0) => {
    const only = text.replace(/\D/g, '').slice(0, digits - from);
    only.split('').forEach((d, i) => { boxes[from + i].value = d; });
    const next = Math.min(from + only.length, digits - 1);
    boxes[next].focus();
    if (value().length === digits) onComplete(value());
  };
  boxes.forEach((box, i) => {
    box.addEventListener('input', () => {
      const typed = box.value.replace(/\D/g, '');
      if (typed.length > 1) { box.value = ''; fill(typed, i); return; }
      box.value = typed;
      if (typed && i < digits - 1) boxes[i + 1].focus();
      if (value().length === digits) onComplete(value());
    });
    box.addEventListener('keydown', (event) => {
      if (event.key === 'Backspace' && !box.value && i > 0) { boxes[i - 1].focus(); boxes[i - 1].value = ''; }
      if (event.key === 'ArrowLeft' && i > 0) boxes[i - 1].focus();
      if (event.key === 'ArrowRight' && i < digits - 1) boxes[i + 1].focus();
    });
    box.addEventListener('paste', (event) => {
      event.preventDefault();
      fill((event.clipboardData || window.clipboardData).getData('text'), 0);
    });
  });
  return {
    element: el('div', { class: 'otp', role: 'group', 'aria-label': t('Código de verificación') }, boxes),
    value,
    clear: () => { boxes.forEach((b) => { b.value = ''; }); boxes[0].focus(); },
    focus: () => boxes[0].focus(),
    disable: (off) => boxes.forEach((b) => { b.disabled = off; }),
  };
}

/**
 * Paso de verificación. data: respuesta 202 del login o GET /api/auth/verify. onDone(result) recibe
 * { user, next } cuando la sesión queda abierta.
 */
export function verificationPanel(data, { onDone, onCancel } = {}) {
  let challenge = data.challenge;
  const remember = rememberCheckbox(Boolean(data.rememberMe));
  const errorNode = formError();
  const timer = el('strong', { class: 'otp__timer', 'aria-live': 'off' });
  const attempts = el('small', { class: 'muted' });
  const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block', text: t('Verificar y entrar') });
  const resend = el('button', { type: 'button', class: 'btn btn--ghost btn--sm' });
  let resendAt = Date.now() + (challenge.resendInSeconds || 0) * 1000;
  let expiresAt = new Date(challenge.expiresAt).getTime();
  const inputs = codeInputs(challenge.digits || 6, () => form.requestSubmit());
  const purposeText = challenge.purpose === 'EMAIL_VERIFICATION'
    ? t('Confirma tu correo: te enviamos un código de 6 dígitos a {0}.', challenge.email)
    : t('Por tu seguridad, te enviamos un código de 6 dígitos a {0}.', challenge.email);

  function tick() {
    const left = (expiresAt - Date.now()) / 1000;
    timer.textContent = left > 0 ? t('El código vence en {0}', format(left)) : t('El código venció: pide uno nuevo');
    timer.classList.toggle('is-expired', left <= 0);
    const wait = (resendAt - Date.now()) / 1000;
    resend.disabled = wait > 0;
    resend.textContent = wait > 0 ? t('Reenviar código en {0}', format(wait)) : t('Reenviar código');
    attempts.textContent = t('Intentos restantes: {0}', challenge.attemptsLeft);
  }
  const interval = window.setInterval(() => { if (!form.isConnected) window.clearInterval(interval); else tick(); }, 500);

  resend.addEventListener('click', async () => {
    try {
      const fresh = await authApi.resend();
      challenge = fresh.challenge;
      expiresAt = new Date(challenge.expiresAt).getTime();
      resendAt = Date.now() + 60_000;
      inputs.clear();
      errorNode.hidden = true;
      toast(t('Te enviamos un código nuevo'), 'ok');
      tick();
    } catch (error) { showFormError(errorNode, error); }
  });

  const form = el('form', { class: 'card form verify-card', novalidate: true, 'data-testid': 'verification-form' },
    el('div', { class: 'verify-card__icon', 'aria-hidden': 'true', text: '✉' }),
    el('h2', { class: 'card__title', text: t('Revisa tu correo') }),
    el('p', { class: 'muted', text: purposeText }),
    inputs.element,
    el('div', { class: 'verify-card__meta' }, timer, attempts),
    remember.element,
    submit, errorNode,
    el('div', { class: 'verify-card__actions' }, resend,
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Usar otra cuenta'), onclick: async () => {
        await authApi.cancelVerification().catch(() => {});
        if (onCancel) onCancel();
      } })),
    el('p', { class: 'small muted', text: t('¿No lo ves? Revisa la carpeta de spam o promociones. El código vale 10 minutos y solo una vez.') }));

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const code = inputs.value();
    if (code.length !== (challenge.digits || 6)) { showFormError(errorNode, new Error(t('Escribe los 6 dígitos del código.'))); inputs.focus(); return; }
    try {
      inputs.disable(true);
      const result = await submitting(submit, errorNode, () => session.verify(code, remember.input.checked));
      window.clearInterval(interval);
      if (onDone) onDone(result);
    } catch {
      inputs.disable(false);
      inputs.clear();
      try { challenge = (await authApi.pending()).challenge; } catch { /* la verificación terminó */ }
      tick();
    }
  });
  tick();
  window.setTimeout(() => inputs.focus(), 50);
  return form;
}
