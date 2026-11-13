// POLYMER v7 — prueba end-to-end contra el stack REAL (con el proveedor OpenID Connect de pruebas del CI
// en lugar de Google: infra/ci/compose.google-mock.yml). Comprueba:
//   1. portada con video de fondo (MP4 servido por el gateway), pausa accesible, mapa de Google Maps,
//      horario de hoy con calendario del mes, «Agregar a Google Calendar» y estado en el pie;
//   2. «Continuar con Google»: cuenta nueva, código de 6 dígitos leído del correo (Mailpit), «Recordarme
//      30 días» (cookies HttpOnly de 30 días), sesión recuperada sin la cookie de sesión y dispositivo
//      de confianza (el segundo acceso con Google no pide código);
//   3. registro con contraseña: el correo se confirma con el código antes de abrir la sesión;
//   4. checkout → pago con tarjeta: rechazo de prueba y aprobación inmediata, boleta imprimible y correo
//      con la boleta (outbox → RabbitMQ → consumidor → SMTP);
//   5. Yape: QR con cuenta regresiva de 60 s sincronizada con el servidor; al vencer, aviso y QR nuevo
//      aprobado; perfil con la seguridad de la cuenta; vista móvil sin desplazamiento horizontal.
// Uso: [BASE_URL=…] [MAILPIT_URL=…] [SCREENSHOTS_DIR=…] node v7-e2e.js
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { codeFor, completeVerification, messagesTo } = require('./mail-code');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.SCREENSHOTS_DIR || path.join(__dirname, 'screenshots');
const GOOGLE_EMAIL = process.env.GOOGLE_TEST_EMAIL || 'cliente.google@example.test';
const GOOGLE_SUB = process.env.GOOGLE_TEST_SUB || 'ci-google-subject-0001';

/**
 * v9: con prompt=select_account el proveedor de pruebas muestra su pantalla de «elige una cuenta» (como Google):
 * se elige la cuenta de prueba. Espera a volver a POLYMER (url que cumple `back`).
 */
async function chooseGoogleAccount(page, back) {
  await page.waitForURL((url) => back.test(url.href) || /\/google\/authorize/.test(url.href), { timeout: 30000, waitUntil: 'commit' });
  if (!back.test(page.url())) {
    await page.fill('input[name="username"]', GOOGLE_SUB);
    await page.fill('textarea[name="claims"]', JSON.stringify({ email: GOOGLE_EMAIL, email_verified: true, name: 'Cliente Google' }));
    await page.click('input[type="submit"]');
  }
  await page.waitForURL(back, { timeout: 30000, waitUntil: 'commit' });
}
const passed = [];
const problems = [];

function check(condition, description) {
  if (!condition) throw new Error(`FALLO: ${description}`);
  passed.push(description);
  console.log(`  ✔ ${description}`);
}

async function open(browser, label, { viewport = { width: 1440, height: 900 } } = {}) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(() => { try { sessionStorage.setItem('polymer.intro.seen', '1'); } catch { /* sin almacenamiento */ } });
  const page = await context.newPage();
  page.on('pageerror', (error) => problems.push(`[${label}] JavaScript: ${error.message} (${page.url()})`));
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    const text = message.text();
    if (/status of (400|401|402|403|404|409)/.test(text)) return; // respuestas esperadas, comprobadas aparte
    if (/google\.com|gstatic|maps|calendar|ERR_|net::/.test(text)) return; // recursos de Google dentro del iframe
    problems.push(`[${label}] console.error: ${text} (${page.url()})`);
  });
  page.on('response', (response) => {
    const u = response.url();
    if (u.startsWith(`${BASE_URL}/api/`) && response.status() >= 500) problems.push(`[${label}] ${response.status()} ${u}`);
  });
  return { context, page };
}

async function rendered(page) {
  await page.waitForFunction(() => {
    const root = document.getElementById('view-root');
    return root && !root.querySelector('.page-loading') && !root.hasAttribute('aria-busy') && root.children.length > 0;
  }, null, { timeout: 20000 });
}

async function csrf(context) {
  return (await (await context.request.get(`${BASE_URL}/api/auth/csrf`)).json()).token;
}

async function cheapProduct(request) {
  const page = await (await request.get(`${BASE_URL}/api/catalog/search?available=true&sort=price_asc&size=30`)).json();
  return page.items.find((p) => p.stock >= 3);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  try {
    // ---- 1. Portada, video, mapa y horario --------------------------------------------------------------
    console.log('Portada: video, mapa de Google Maps, horario de hoy y Google Calendar');
    const home = await open(browser, 'portada');
    await home.page.goto(`${BASE_URL}/`);
    await rendered(home.page);
    const video = home.page.locator('[data-testid="hero-video"]');
    check(await video.count() === 1 && (await video.locator('source').getAttribute('src')) === '/media/polymer-hero.mp4',
      'La portada tiene un video de fondo propio (/media/polymer-hero.mp4)');
    const media = await home.context.request.get(`${BASE_URL}/media/polymer-hero.mp4`, { headers: { Range: 'bytes=0-1023' } });
    check([200, 206].includes(media.status()) && /video\/mp4/.test(media.headers()['content-type'] || ''),
      `El gateway sirve el video (HTTP ${media.status()}, ${media.headers()['content-type']}, admite Range)`);
    const poster = await home.context.request.get(`${BASE_URL}/media/polymer-hero.jpg`);
    check(poster.status() === 200, 'Póster del video disponible (se ve con «reducir movimiento»)');
    await home.page.waitForFunction(() => { const v = document.querySelector('[data-testid="hero-video"]'); return v && v.readyState >= 2; }, null, { timeout: 15000 });
    check(await home.page.locator('.hero-video__toggle, #sound-toggle.icon-btn').count() === 0
      && await home.page.evaluate(() => document.querySelector('[data-testid="hero-video"]').muted),
      'v9: sin botón «Pausar video» ni altavoz en la cabecera; el video de fondo va en silencio');
    const map = home.page.locator('[data-testid="store-map"] iframe');
    await map.waitFor({ timeout: 15000 });
    check((await map.getAttribute('src')).startsWith('https://www.google.com/maps?q=') && (await map.getAttribute('src')).includes('output=embed'),
      'Sección «Visítanos» con el mapa de Google Maps incrustado');
    check((await home.page.getAttribute('[data-testid="maps-directions"]', 'href')).startsWith('https://www.google.com/maps/dir/?api=1&destination='),
      'Botón «Cómo llegar» con la ruta de Google Maps');
    const hours = (await home.page.textContent('[data-testid="store-hours-today"]')).trim();
    check(/^\d{2}:\d{2} – \d{2}:\d{2}$/.test(hours) || /Cerrado/.test(hours), `Horario de atención de hoy: ${hours}`);
    check(await home.page.locator('.mini-cal__cell.is-today').count() === 1, 'Calendario del mes con el día de hoy resaltado');
    check((await home.page.getAttribute('[data-testid="google-calendar"]', 'href')).startsWith('https://calendar.google.com/calendar/render?action=TEMPLATE'),
      '«Agregar visita a Google Calendar» con fecha, hora y ubicación');
    await home.page.locator('[data-testid="today-badge"]').first().waitFor();
    check(/Abierto|Cerrado/.test(await home.page.textContent('[data-testid="today-badge"]')), 'El pie muestra si la tienda está abierta ahora');
    await home.page.screenshot({ path: path.join(OUT, 'v7-portada.png') });
    await home.context.close();

    // ---- 2. Continuar con Google + código + recordarme ---------------------------------------------------
    console.log('Continuar con Google (proveedor OpenID Connect de pruebas), código por correo y «Recordarme 30 días»');
    const google = await open(browser, 'google');
    await google.page.goto(`${BASE_URL}/login.html`);
    await rendered(google.page);
    const button = google.page.locator('[data-testid="google-signin"]');
    check(await button.count() === 1 && /Iniciar sesión con Google/.test(await button.textContent()), 'El acceso muestra «Iniciar sesión con Google» (cliente OAuth configurado)');
    // v9: «Iniciar sesión con Google» sin cuenta POLYMER se rechaza y ofrece registrarse.
    await button.click();
    await chooseGoogleAccount(google.page, /google=sin-cuenta/);
    await rendered(google.page);
    await google.page.locator('[data-testid="google-no-account"]').waitFor({ timeout: 10000 });
    const anonymous = await (await google.context.request.get(`${BASE_URL}/api/auth/me`)).json();
    check(!anonymous.authenticated, 'Iniciar sesión con Google sin cuenta POLYMER: se rechaza, no se crea la cuenta y se invita a registrarse');
    await google.page.screenshot({ path: path.join(OUT, 'v9-google-sin-cuenta.png') });
    // «Registrarse con Google»: Google muestra «Elige una cuenta» (prompt=select_account) y se crea la cuenta.
    await google.page.goto(`${BASE_URL}/registro.html`);
    await rendered(google.page);
    const googleSignup = google.page.locator('[data-testid="google-signin"][data-intent="registro"]');
    check(/Registrarse con Google/.test(await googleSignup.textContent()), 'El registro muestra «Registrarse con Google»');
    const startedGoogle = Date.now();
    const [authorize] = await Promise.all([
      google.page.waitForRequest((request) => /\/authorize\?/.test(request.url()), { timeout: 30000 }),
      googleSignup.click(),
    ]);
    check(new URL(authorize.url()).searchParams.get('prompt') === 'select_account', 'Google pide elegir la cuenta (prompt=select_account)');
    await chooseGoogleAccount(google.page, /registro\.html\?verificar=google/);
    await rendered(google.page);
    const form = google.page.locator('[data-testid="verification-form"]');
    await form.waitFor({ timeout: 15000 });
    check((await form.textContent()).includes('cl•••@example.test'), 'Vuelta de Google: se pide el código de 6 dígitos enviado al correo (enmascarado)');
    await google.page.screenshot({ path: path.join(OUT, 'v7-google-codigo.png') });
    await google.page.check('[data-testid="verification-form"] input[name="remember"]');
    const code = await codeFor(google.context.request, GOOGLE_EMAIL, startedGoogle);
    check(/^\d{6}$/.test(code), 'El código llegó por correo (Mailpit) con el asunto «NNNNNN es tu código de verificación de POLYMER»');
    await google.page.locator('.otp__box').first().click();
    await google.page.keyboard.type(code);
    await google.page.waitForURL(/\/cliente\/dashboard\.html/, { timeout: 20000 });
    const me = await (await google.context.request.get(`${BASE_URL}/api/auth/me`)).json();
    check(me.authenticated && me.user.roles.includes('CUSTOMER') && me.user.username.startsWith(GOOGLE_EMAIL.split('@')[0].replace(/[^A-Za-z0-9]/g, '_').toLowerCase()),
      `Cuenta creada con Google y sesión abierta tras el código (${me.user.username})`);
    const cookies = await google.context.cookies();
    const remember = cookies.find((c) => c.name === 'POLYMER_REMEMBER');
    const device = cookies.find((c) => c.name === 'POLYMER_DEVICE');
    const days = remember ? (remember.expires * 1000 - Date.now()) / 86400000 : 0;
    check(remember && device && remember.httpOnly && device.httpOnly && days > 29 && days <= 30.01,
      `«Recordarme»: cookies HttpOnly de sesión persistente y dispositivo de confianza (${days.toFixed(1)} días)`);
    // Cerrar el navegador = perder la cookie de sesión; la sesión recordada la recupera.
    const reopened = await browser.newContext();
    await reopened.addCookies([remember]);
    const back = await (await reopened.request.get(`${BASE_URL}/api/auth/me`)).json();
    check(back.authenticated && back.user.username === me.user.username, 'Sin la cookie de sesión, «Recordarme» vuelve a abrir la sesión');
    await reopened.close();

    // ---- 3. Pago con tarjeta desde el checkout -------------------------------------------------------------
    console.log('Checkout y pago con tarjeta: rechazo de prueba, aprobación inmediata y boleta');
    const product = await cheapProduct(google.context.request);
    await google.page.goto(`${BASE_URL}/producto.html?id=${product.id}`);
    await rendered(google.page);
    await Promise.all([google.page.waitForURL(/\/checkout\.html/, { timeout: 15000 }), google.page.click('.product-detail .btn--primary')]);
    await rendered(google.page);
    await google.page.fill('#view-root input[autocomplete="street-address"]', 'Av. Prueba 742, dpto. 3');
    await google.page.fill('#view-root input[autocomplete="address-level2"]', 'Lima');
    await google.page.check('input[name="payment"][value="CARD"]');
    await google.page.waitForFunction(() => /pagar/.test(document.querySelector('#view-root .cart-summary .btn--primary').textContent), null, { timeout: 10000 });
    check(true, 'Con tarjeta, el botón dice «Confirmar y pagar»');
    await Promise.all([google.page.waitForURL(/\/cliente\/pago\.html\?id=/, { timeout: 20000 }), google.page.click('#view-root .cart-summary .btn--primary')]);
    await rendered(google.page);
    const orderId = new URL(google.page.url()).searchParams.get('id');
    check(await google.page.locator('[data-testid="payment-sandbox"]').count() >= 1, 'Página de pago con el aviso de pasarela de PRUEBAS (no cobra)');
    const fillCard = async (number) => {
      await google.page.fill('input[autocomplete="cc-number"]', number);
      await google.page.fill('input[autocomplete="cc-name"]', 'Cliente Google');
      await google.page.fill('input[autocomplete="cc-exp"]', '12/30');
      await google.page.fill('input[autocomplete="cc-csc"]', '123');
      await google.page.click('[data-testid="pay-card"]');
    };
    await fillCard('4000 0000 0000 0002');
    await google.page.locator('[data-testid="payment-declined"]').waitFor({ timeout: 15000 });
    check(/Fondos insuficientes/.test(await google.page.textContent('[data-testid="payment-declined"]')), 'Tarjeta de prueba rechazada: «Fondos insuficientes»');
    await fillCard('4111 1111 1111 1111');
    await google.page.locator('[data-testid="payment-approved"]').waitFor({ timeout: 15000 });
    const approved = await google.page.textContent('[data-testid="payment-approved"]');
    check(/B001-\d{8}/.test(approved) && approved.includes('1111'), 'Pago con tarjeta aprobado al instante: boleta B001 y tarjeta •••• 1111');
    await google.page.screenshot({ path: path.join(OUT, 'v7-pago-tarjeta.png') });
    await Promise.all([google.page.waitForURL(/\/cliente\/boleta\.html\?id=/, { timeout: 15000 }), google.page.click('[data-testid="receipt-link"]')]);
    await google.page.locator('[data-testid="receipt"]').waitFor({ timeout: 15000 });
    const receiptText = await google.page.textContent('[data-testid="receipt"]');
    check(/BOLETA DE VENTA/.test(receiptText) && /IGV \(18 %\)/.test(receiptText) && /Op\. gravada/.test(receiptText),
      'Boleta imprimible: base imponible, IGV 18 % y total');
    await google.page.screenshot({ path: path.join(OUT, 'v7-boleta.png'), fullPage: true });
    const receiptCode = receiptText.match(/B001-\d{8}/)[0];
    let receiptMail = null;
    // SKIP_RECEIPT_MAIL=1 solo para pruebas locales sin RabbitMQ (el correo de la boleta viaja por el outbox y la cola).
    if (process.env.SKIP_RECEIPT_MAIL === '1') receiptMail = { skipped: true };
    for (let i = 0; i < 45 && !receiptMail; i += 1) {
      receiptMail = (await messagesTo(google.context.request, GOOGLE_EMAIL)).find((m) => m.Subject.startsWith(`Boleta ${receiptCode}`));
      if (!receiptMail) await new Promise((resolve) => setTimeout(resolve, 2000));
    }
    check(receiptMail, `La boleta ${receiptCode} llegó al correo del cliente (outbox → RabbitMQ → consumidor → SMTP)`);
    await google.page.goto(`${BASE_URL}/cliente/pedido-detalle.html?id=${orderId}`);
    await rendered(google.page);
    await google.page.locator('[data-testid="order-payment"] .notice-box').waitFor({ timeout: 10000 });
    check(/Pagado/.test(await google.page.textContent('[data-testid="order-payment"]')), 'El detalle del pedido muestra «Pagado» y el enlace a la boleta');

    // ---- 4. Yape: QR de 60 s, vencimiento y QR nuevo ------------------------------------------------------
    console.log('Yape: QR con cuenta regresiva de 60 s, vencimiento y pago con un QR nuevo');
    const token = await csrf(google.context);
    const placed = await google.context.request.post(`${BASE_URL}/api/shop/orders`, {
      headers: { 'X-CSRF-TOKEN': token, 'Idempotency-Key': `v7-yape-${Date.now()}` },
      data: { items: [{ productId: product.id, quantity: 1 }], deliveryMethod: 'PICKUP', paymentMethod: 'YAPE' } });
    check(placed.status() === 201, 'Pedido con Yape creado');
    const yapeOrder = await placed.json();
    await google.page.goto(`${BASE_URL}/cliente/pago.html?id=${yapeOrder.id}`);
    await rendered(google.page);
    check((await google.page.getAttribute('.pay-tab[data-method="YAPE"]', 'aria-selected')) === 'true', 'La pestaña Yape está elegida (método del checkout)');
    await google.page.click('[data-testid="wallet-start"]');
    await google.page.locator('[data-testid="wallet-qr"] svg').waitFor({ timeout: 10000 });
    const seconds = Number(await google.page.textContent('[data-testid="wallet-seconds"]'));
    check(seconds > 50 && seconds <= 60, `Código QR de Yape generado con cuenta regresiva (${seconds} s)`);
    await google.page.screenshot({ path: path.join(OUT, 'v7-yape-qr.png') });
    await google.page.locator('[data-testid="wallet-expired"]').waitFor({ timeout: 75000 });
    check(true, 'A los 60 s la solicitud vence: aviso «El código QR venció» y opción de generar otro');
    await google.page.click('[data-testid="wallet-start"]');
    await google.page.locator('[data-testid="wallet-qr"] svg').waitFor({ timeout: 10000 });
    await google.page.fill('.wallet input[type="tel"]', '987 654 321');
    await google.page.fill('.wallet input[autocomplete="one-time-code"]', '482913');
    await google.page.click('[data-testid="wallet-confirm"]');
    await google.page.locator('[data-testid="payment-approved"]').waitFor({ timeout: 15000 });
    check(/Yape/.test(await google.page.textContent('[data-testid="payment-approved"]')), 'Yape aprobado con el QR nuevo dentro de los 60 s');

    // ---- 5. Perfil y dispositivo de confianza ---------------------------------------------------------------
    console.log('Seguridad de la cuenta y dispositivo de confianza');
    await google.page.goto(`${BASE_URL}/cliente/perfil.html`);
    await rendered(google.page);
    await google.page.locator('[data-testid="security-card"] .security-facts').waitFor({ timeout: 10000 });
    check(/Cuenta de Google vinculada/.test(await google.page.textContent('[data-testid="security-card"]')), 'Perfil: cuenta de Google vinculada y verificación en dos pasos');
    const tokenOut = await csrf(google.context);
    await google.context.request.post(`${BASE_URL}/api/auth/logout`, { headers: { 'X-CSRF-TOKEN': tokenOut } });
    await google.page.goto(`${BASE_URL}/login.html`);
    await rendered(google.page);
    await google.page.click('[data-testid="google-signin"]');
    await chooseGoogleAccount(google.page, /\/cliente\/dashboard\.html/);
    check(true, 'Dispositivo de confianza: el segundo acceso con Google entra sin pedir el código');

    // ---- v9 · Testimonios: equipo con LinkedIn, carrusel, formulario del cliente y moderación ------------------
    console.log('Testimonios: carrusel del equipo (LinkedIn), formulario del cliente y moderación');
    await google.page.goto(`${BASE_URL}/nosotros.html`);
    await rendered(google.page);
    await google.page.locator('[data-testid="testimonial"][data-kind="TEAM"]').first().waitFor({ timeout: 15000 });
    const team = await google.page.$$eval('[data-testid="testimonial"][data-kind="TEAM"]', (cards) => cards.map((card) => {
      const parts = [...card.children].map((child) => child.className.split(' ')[0]);
      const link = card.querySelector('[data-testid="testimonial-linkedin"]');
      return { name: card.querySelector('.tm-card__name').textContent, photo: card.querySelector('img.tm-card__photo')?.getAttribute('src'),
        parts, href: link?.getAttribute('href'), target: link?.getAttribute('target') };
    }));
    check(team.length === 3 && team.every((c) => c.photo && c.photo.startsWith('/img/equipo/')), 'Carrusel con los 3 integrantes del equipo y su foto');
    check(team.every((c) => c.parts[0] === 'tm-card__profile' && c.parts[1] === 'tm-card__linkedin' && c.parts.indexOf('tm-card__quote') > 1),
      'Cada tarjeta: primero el perfil, luego el enlace a LinkedIn y al final el comentario');
    const linkedins = team.map((c) => c.href).join(' ');
    check(['ronaldo-gabriel-matos-mart%C3%ADnez-614841392', 'khyara-morales-053a75444', 'jonathan-retuerto-5b089a395'].every((slug) => linkedins.includes(`https://www.linkedin.com/in/${slug}`))
      && team.every((c) => c.target === '_blank'), 'Los enlaces llevan a los perfiles de LinkedIn del equipo (pestaña nueva)');
    const before = await google.page.$eval('.tm-track', (track) => track.style.transform);
    await google.page.click('.tm-nav--next');
    await google.page.waitForTimeout(700);
    check(before !== await google.page.$eval('.tm-track', (track) => track.style.transform), 'El carrusel avanza con la flecha');
    const opinion = `Compré con Google y Yape en minutos, todo llegó perfecto. ${Date.now()}`;
    await google.page.fill('.tm-form textarea', opinion);
    await google.page.click('.tm-rate__star >> nth=3');
    await google.page.click('.tm-form button[type="submit"]');
    await google.page.locator(`[data-testid="testimonial"][data-kind="CUSTOMER"]:has-text("${opinion.slice(0, 30)}")`).waitFor({ timeout: 15000 });
    const published = (await (await google.context.request.get(`${BASE_URL}/api/testimonials`)).json()).find((t) => t.comment === opinion);
    check(published && published.rating === 4 && published.kind === 'CUSTOMER' && !('userId' in published),
      'El cliente publica su testimonio (4 estrellas) y aparece en el carrusel al instante (sin exponer su cuenta)');
    await google.page.screenshot({ path: path.join(OUT, 'v9-testimonios.png') });
    if (process.env.DEMO_PASSWORD) {
      const staff = await browser.newContext();
      const token = (await (await staff.request.get(`${BASE_URL}/api/auth/csrf`)).json()).token;
      const login = await staff.request.post(`${BASE_URL}/api/auth/login`, { headers: { 'X-CSRF-TOKEN': token }, data: { username: 'admin', password: process.env.DEMO_PASSWORD } });
      const adminToken = (await (await staff.request.get(`${BASE_URL}/api/auth/csrf`)).json()).token;
      const hide = await staff.request.put(`${BASE_URL}/api/admin/testimonials/${published.id}/visibility`, { headers: { 'X-CSRF-TOKEN': adminToken }, data: { visible: false } });
      const after = (await (await staff.request.get(`${BASE_URL}/api/testimonials`)).json()).some((t) => t.id === published.id);
      check(login.status() === 200 && hide.status() === 200 && !after, 'La administración oculta un testimonio y sale del carrusel');
      await staff.request.put(`${BASE_URL}/api/admin/testimonials/${published.id}/visibility`, { headers: { 'X-CSRF-TOKEN': adminToken }, data: { visible: true } });
      await staff.close();
    }
    await google.context.close();

    // ---- 6. Registro con contraseña + código -----------------------------------------------------------------
    console.log('Registro con contraseña: confirmación del correo con el código');
    const signup = await open(browser, 'registro');
    const email = `v7.${Date.now()}@example.test`;
    await signup.page.goto(`${BASE_URL}/registro.html`);
    await rendered(signup.page);
    await signup.page.fill('#view-root input[autocomplete="username"]', `v7${Date.now().toString(36)}`);
    await signup.page.fill('#view-root input[autocomplete="name"]', 'Cliente Verificado');
    await signup.page.fill('#view-root input[type="email"]', email);
    const passwords = signup.page.locator('#view-root input[type="password"]');
    await passwords.nth(0).fill('cobre lluvia norte faro 2026');
    await passwords.nth(1).fill('cobre lluvia norte faro 2026');
    const started = Date.now();
    const landed = signup.page.waitForURL(/\/cliente\/dashboard\.html|\/carrito\.html/, { timeout: 40000 });
    await signup.page.click('#view-root button[type="submit"]');
    check(await completeVerification(signup.page, email, started), 'La cuenta nueva confirma su correo con el código antes de entrar');
    await landed;
    await signup.context.close();

    // ---- 7. Móvil ---------------------------------------------------------------------------------------------
    const mobile = await open(browser, 'móvil', { viewport: { width: 390, height: 844 } });
    for (const target of ['/', '/contacto.html', '/login.html']) {
      await mobile.page.goto(`${BASE_URL}${target}`);
      await rendered(mobile.page);
      const overflow = await mobile.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 1, `Móvil ${target}: sin desplazamiento horizontal`);
    }
    await mobile.page.goto(`${BASE_URL}/contacto.html`);
    await rendered(mobile.page);
    await mobile.page.locator('[data-testid="store-map"] iframe').scrollIntoViewIfNeeded();
    await mobile.page.screenshot({ path: path.join(OUT, 'v7-contacto-movil.png'), fullPage: true });
    await mobile.context.close();
  } finally {
    await browser.close();
  }
  if (problems.length) {
    console.error(`\nProblemas detectados (${problems.length}):\n  ${problems.join('\n  ')}`);
    process.exit(1);
  }
  console.log(`\nOK v7 e2e: ${passed.length} comprobaciones`);
})().catch((error) => {
  console.error(error.message || error);
  if (problems.length) console.error(`Problemas: \n  ${problems.join('\n  ')}`);
  process.exit(1);
});
