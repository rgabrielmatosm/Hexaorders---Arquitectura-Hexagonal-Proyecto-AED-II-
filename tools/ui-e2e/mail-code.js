// v7 · Lee el código de verificación de 6 dígitos que POLYMER envió por correo (Mailpit en Docker) y lo
// escribe en las casillas del paso de verificación, como haría una persona con su bandeja de entrada.
const MAILPIT = process.env.MAILPIT_URL || 'http://127.0.0.1:8025';

async function codeFor(request, email, since, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const response = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}&limit=20`).catch(() => null);
    const data = response && response.ok() ? await response.json() : { messages: [] };
    const message = (data.messages || []).find((m) => /^\d{6} /.test(m.Subject) && new Date(m.Created).getTime() >= since - 5000);
    if (message) return message.Subject.slice(0, 6);
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`No llegó el código de verificación a ${email} (${MAILPIT})`);
}

/** Si aparece el paso de verificación, escribe el código del correo. Devuelve true si hizo falta. */
async function completeVerification(page, email, since, { timeoutMs = 15000 } = {}) {
  const form = page.locator('[data-testid="verification-form"]');
  try { await form.waitFor({ state: 'visible', timeout: timeoutMs }); } catch { return false; }
  const code = await codeFor(page.context().request, email, since);
  await page.locator('.otp__box').first().click();
  await page.keyboard.type(code);
  return true;
}

async function messagesTo(request, email) {
  const response = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${email}"`)}&limit=50`);
  return (await response.json()).messages || [];
}

module.exports = { MAILPIT, codeFor, completeVerification, messagesTo };
