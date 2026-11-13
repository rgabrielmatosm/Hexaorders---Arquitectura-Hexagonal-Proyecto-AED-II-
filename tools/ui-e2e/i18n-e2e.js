// POLYMER — prueba end-to-end del sitio multilingüe (es, en, fr, it, pt, de) y de la seguridad de
// contraseñas, contra el stack real. Comprueba:
//   1. rutas localizadas reales (/es/productos, /en/products, /fr/produits…): acceso directo, recarga,
//      html lang, título traducido, canonical, hreflang ×6 + x-default, sitemap y 404 localizado;
//   2. que la interfaz se traduce (navegación, catálogo, pie) y que los enlaces internos conservan el idioma;
//   3. el selector de idioma (pie y cabecera): misma página equivalente, filtros, carrito y sesión
//      conservados, sin repetir la intro, y preferencia guardada en la cuenta;
//   4. permisos por idioma: visitante → login localizado con «next»; cliente → 403 localizado;
//   5. carrito y checkout completos en inglés (pedido real en PostgreSQL) y seguimiento localizado;
//   6. contraseñas: indicador débil/aceptable/fuerte, mostrar/ocultar, contraseña común, confirmación,
//      y rechazo en el servidor (WEAK_PASSWORD) aunque se salte la interfaz;
//   7. vista móvil sin desplazamiento horizontal con el selector compacto.
// Uso: DEMO_PASSWORD=… [BASE_URL=…] [SCREENSHOTS_DIR=…] node i18n-e2e.js
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const { completeVerification } = require('./mail-code');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.SCREENSHOTS_DIR || path.join(__dirname, 'screenshots');
const PASSWORD = process.env.DEMO_PASSWORD;
const ROUTES = JSON.parse(fs.readFileSync(path.join(__dirname, '../../services/order-api/src/main/resources/static/i18n/routes.json'), 'utf8'));
const LANGS = ROUTES.languages;
const slug = (lang, file) => ROUTES.pages[file][lang];
const url = (lang, file, query = '') => `${BASE_URL}/${lang}/${slug(lang, file)}${query}`;
const passed = [];
const problems = [];

// Una palabra de la navegación por idioma: demuestra que el catálogo de traducción se aplicó.
const CART_WORD = { es: 'Carrito', en: 'Cart', fr: 'Panier', it: 'Carrello', pt: 'Carrinho', de: 'Warenkorb' };

function check(condition, description) {
  if (!condition) throw new Error(`FALLO: ${description}`);
  passed.push(description);
  console.log(`  ✔ ${description}`);
}

async function open(browser, label, { viewport = { width: 1440, height: 900 }, skipIntro = true } = {}) {
  const context = await browser.newContext({ viewport });
  if (skipIntro) await context.addInitScript(() => { try { sessionStorage.setItem('polymer.intro.seen', '1'); } catch { /* sin almacenamiento */ } });
  const page = await context.newPage();
  page.on('pageerror', (error) => problems.push(`[${label}] JavaScript: ${error.message} (${page.url()})`));
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    const text = message.text();
    if (/status of (400|401|403|404|409)/.test(text)) return; // respuestas esperadas, comprobadas aparte
    problems.push(`[${label}] console.error: ${text} (${page.url()})`);
  });
  page.on('response', (response) => {
    const u = response.url();
    if (u.startsWith(BASE_URL) && /\/i18n\/|\.(js|css)$/.test(new URL(u).pathname) && response.status() >= 400) problems.push(`[${label}] ${response.status()} ${u}`);
    if (u.startsWith(`${BASE_URL}/api/`) && response.status() >= 500) problems.push(`[${label}] ${response.status()} ${u}`);
  });
  return { context, page };
}

async function rendered(page) {
  await page.waitForFunction(() => {
    const root = document.getElementById('view-root');
    return root && !root.querySelector('.page-loading') && !root.hasAttribute('aria-busy') && root.children.length > 0;
  }, null, { timeout: 20000 });
  await page.waitForTimeout(250);
}

const head = (page) => page.evaluate(() => ({
  lang: document.documentElement.lang,
  title: document.title,
  canonical: document.querySelector('link[rel=canonical]')?.href,
  alternates: [...document.querySelectorAll('link[rel=alternate][hreflang]')].map((l) => [l.hreflang, new URL(l.href).pathname]),
  description: document.querySelector('meta[name=description]')?.content,
}));

async function login(session, lang, user, password = PASSWORD) {
  await session.page.goto(url(lang, '/login.html'));
  await rendered(session.page);
  await session.page.fill('#view-root input[autocomplete="username"]', user);
  await session.page.fill('#view-root input[type="password"]', password);
  await Promise.all([session.page.waitForURL((u) => !u.pathname.endsWith(`/${slug(lang, '/login.html')}`), { timeout: 15000 }),
    session.page.click('#view-root button[type="submit"]')]);
}

(async () => {
  if (!PASSWORD) throw new Error('Define DEMO_PASSWORD con la contraseña de las cuentas de demostración.');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  try {
    const anon = await open(browser, 'visitante');
    const products = await (await anon.context.request.get(`${BASE_URL}/api/catalog/products`)).json();
    const product = products.find((p) => p.stock > 5);

    // ---- 1 y 2. Rutas, SEO y traducción en los seis idiomas -----------------------------------
    console.log('Rutas localizadas');
    check(LANGS.join(',') === 'es,en,fr,it,pt,de', 'Seis idiomas declarados en routes.json (español por defecto)');
    for (const lang of LANGS) {
      const response = await anon.page.goto(url(lang, '/catalogo.html'));
      await rendered(anon.page);
      const meta = await head(anon.page);
      check(response.status() === 200 && meta.lang === lang, `${lang}: /${lang}/${slug(lang, '/catalogo.html')} responde 200 con <html lang="${lang}">`);
      check(meta.canonical === url(lang, '/catalogo.html'), `${lang}: canonical apunta a la ruta del idioma`);
      const expected = LANGS.map((l) => [l, `/${l}/${slug(l, '/catalogo.html')}`]).concat([['x-default', `/es/${slug('es', '/catalogo.html')}`]]);
      check(JSON.stringify(meta.alternates) === JSON.stringify(expected), `${lang}: hreflang de los 6 idiomas + x-default`);
      const nav = await anon.page.textContent('header');
      check(nav.includes(CART_WORD[lang]), `${lang}: la cabecera está traducida («${CART_WORD[lang]}»)`);
      const links = await anon.page.$$eval('header a[href^="/"]:not([hreflang]), footer a[href^="/"]:not([hreflang])', (as) => as.map((a) => a.getAttribute('href')));
      const foreign = links.filter((h) => !h.startsWith(`/${lang}/`) && h !== `/${lang}` && !/^\/(api|img|css|js|i18n)\//.test(h) && !/\.(xml|txt|svg|png|pdf)$/.test(h));
      check(foreign.length === 0, `${lang}: los enlaces internos de cabecera y pie conservan el idioma (${links.length} enlaces)`);
      await anon.page.reload();
      await rendered(anon.page);
      check((await anon.page.evaluate(() => document.documentElement.lang)) === lang && (await anon.page.locator('.product-card').count()) > 0,
        `${lang}: la página funciona tras recargar el navegador`);
      const detail = await anon.page.goto(url(lang, '/producto.html', `?id=${product.id}`));
      await rendered(anon.page);
      check(detail.status() === 200 && (await anon.page.locator('.spec-table tr').count()) >= 3, `${lang}: ficha de producto con especificaciones`);
    }
    const english = await anon.page.goto(url('en', '/catalogo.html'));
    await rendered(anon.page);
    const englishText = await anon.page.textContent('#view-root');
    check(english.ok() && (await anon.page.title()) === 'All products · POLYMER' && englishText.includes('Sort by') && englishText.includes('In stock only')
      && !/Ordenar por|Todos los productos|Solo con stock|Agregar al carrito/.test(englishText), 'en: catálogo con la interfaz en inglés (sin textos en español)');
    const specs = await anon.page.evaluate(() => [...document.querySelectorAll('.product-card')].map((c) => c.textContent).join(' '));
    check(!/\bHasta \d/.test(specs) && /Up to \d/.test(specs), 'en: valores de ficha técnica traducidos con plantillas («Hasta 22 h» → «Up to 22 h»)');
    const sitemap = await (await anon.context.request.get(`${BASE_URL}/sitemap.xml`)).text();
    check(sitemap.includes('/en/products') && sitemap.includes('/de/produkte') && sitemap.includes('hreflang="fr"') && !sitemap.includes('/admin'),
      'sitemap.xml multilingüe con alternates y sin páginas internas');
    const missing = await anon.page.goto(`${BASE_URL}/fr/page-inexistante`);
    await rendered(anon.page);
    check(missing.status() === 404 && (await anon.page.evaluate(() => document.documentElement.lang)) === 'fr', 'Ruta inexistente: 404 localizado (fr)');
    check((await (await anon.context.request.get(`${BASE_URL}/xx/products`)).status()) === 404, 'Idioma no soportado: 404');

    // ---- 3. Selector de idioma: página equivalente, filtros, carrito y sin repetir la intro --------
    console.log('Selector de idioma');
    const guest = await open(browser, 'selector', { skipIntro: false });
    await guest.page.goto(url('es', '/producto.html', `?id=${product.id}`));
    await guest.page.waitForSelector('#polymer-intro', { timeout: 5000 });
    await guest.page.keyboard.press('Escape'); // v9: la intro no tiene botones; Esc o un clic la abren
    await guest.page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 4000 });
    await rendered(guest.page);
    await guest.page.click('.product-detail .btn--ghost');
    await guest.page.waitForFunction(() => document.querySelector('#cart-count')?.textContent === '1', null, { timeout: 10000 });
    await guest.page.goto(url('es', '/catalogo.html', '?marca=Lenovo&orden=price-asc'));
    await rendered(guest.page);
    await guest.page.click('footer .lang-menu summary');
    await Promise.all([guest.page.waitForURL(/\/en\/products\?/), guest.page.click('footer .lang-menu a[hreflang="en"]')]);
    await rendered(guest.page);
    const switched = new URL(guest.page.url());
    check(switched.searchParams.get('marca') === 'Lenovo' && switched.searchParams.get('orden') === 'price-asc', 'Pie: al cambiar a inglés se conserva la página y los filtros');
    check((await guest.page.textContent('#cart-count')) === '1', 'El carrito se conserva al cambiar de idioma');
    check(!(await guest.page.evaluate(() => Boolean(document.querySelector('#polymer-intro')))), 'La intro no se repite al cambiar de idioma');
    await Promise.all([guest.page.waitForURL(/\/de\/produkte\?/), guest.page.selectOption('header .lang-select', 'de')]);
    await rendered(guest.page);
    check((await guest.page.evaluate(() => document.documentElement.lang)) === 'de' && new URL(guest.page.url()).searchParams.get('marca') === 'Lenovo',
      'Cabecera: el selector compacto cambia a alemán en la misma página');
    await guest.page.goto(url('de', '/carrito.html'));
    await rendered(guest.page);
    check((await guest.page.textContent('#view-root')).includes('Warenkorb') && (await guest.page.locator('.cart-line, .cart-item, [data-product-id]').count()) >= 1,
      'de: el carrito muestra el producto agregado en español');
    await guest.page.screenshot({ path: path.join(OUT, 'i18n-de-carrito.png') });
    await guest.context.close();

    // ---- 4. Permisos por idioma -------------------------------------------------------------------
    console.log('Permisos');
    const redirect = await anon.context.request.get(url('it', '/checkout.html'), { maxRedirects: 0 });
    check(redirect.status() === 302 && redirect.headers().location.includes(`/it/${slug('it', '/login.html')}?next=`), 'Visitante en /it/checkout → login en italiano con «next»');

    // ---- 5. Cliente: sesión, carrito y checkout en inglés -----------------------------------------
    console.log('Cliente en inglés');
    const customer = await open(browser, 'cliente');
    await login(customer, 'en', 'cliente');
    check(new URL(customer.page.url()).pathname.startsWith('/en/'), 'Tras iniciar sesión en inglés, el cliente sigue en rutas /en/');
    const forbidden = await customer.page.goto(url('en', '/admin/dashboard.html'));
    await rendered(customer.page);
    check(forbidden.status() === 403 && (await customer.page.evaluate(() => document.documentElement.lang)) === 'en', 'Cliente en /en/admin → 403 localizado');
    await customer.page.goto(url('en', '/producto.html', `?id=${product.id}`));
    await rendered(customer.page);
    await Promise.all([customer.page.waitForURL(/\/en\/checkout/, { timeout: 15000 }), customer.page.click('.product-detail .btn--primary')]);
    await rendered(customer.page);
    const checkoutText = await customer.page.textContent('#view-root');
    check(checkoutText.includes('Delivery method') && checkoutText.includes('Payment method') && checkoutText.includes('Order summary'), 'Checkout en inglés');
    await customer.page.fill('#view-root input[autocomplete="street-address"]', '742 Test Ave., apt. 3');
    await customer.page.fill('#view-root input[autocomplete="address-level2"]', 'Arequipa');
    await customer.page.screenshot({ path: path.join(OUT, 'i18n-en-checkout.png') });
    await Promise.all([customer.page.waitForURL(/\/en\/tracking\?id=/, { timeout: 20000 }), customer.page.click('button:has-text("Confirm order")')]);
    await rendered(customer.page);
    check((await customer.page.textContent('#view-root')).includes('Arequipa') && (await customer.page.evaluate(() => document.documentElement.lang)) === 'en',
      'Pedido real creado desde el checkout en inglés; seguimiento localizado');
    await customer.page.reload();
    await rendered(customer.page);
    check((await customer.page.locator('.tracker li.is-done').count()) >= 2, 'El seguimiento en inglés funciona tras recargar');
    // Preferencia guardada en la cuenta (los correos se envían en ese idioma).
    const [saved] = await Promise.all([
      customer.page.waitForResponse((r) => r.url().endsWith('/api/account/locale') && r.request().method() === 'PUT'),
      customer.page.click('footer .lang-menu summary').then(() => customer.page.click('footer .lang-menu a[hreflang="pt"]')),
    ]);
    await rendered(customer.page);
    check(saved.ok() && (await customer.page.evaluate(() => document.documentElement.lang)) === 'pt', 'Con sesión: el cambio a portugués se guarda en la cuenta');
    const me = await (await customer.context.request.get(`${BASE_URL}/api/account/me`)).json();
    check(me.username === 'cliente', 'La sesión se conserva tras cambiar de idioma');
    await customer.page.goto(url('pt', '/catalogo.html'));
    await rendered(customer.page);
    await customer.page.click('footer .lang-menu summary');
    await Promise.all([
      customer.page.waitForResponse((r) => r.url().endsWith('/api/account/locale')),
      customer.page.click('footer .lang-menu a[hreflang="es"]'),
    ]);
    await rendered(customer.page);
    check((await customer.page.evaluate(() => document.documentElement.lang)) === 'es', 'La preferencia del cliente de demostración vuelve a español');
    await customer.context.close();

    // ---- 6. Contraseñas ---------------------------------------------------------------------------
    console.log('Contraseñas');
    const signup = await open(browser, 'registro');
    await signup.page.goto(url('en', '/registro.html'));
    await rendered(signup.page);
    const level = () => signup.page.getAttribute('.pw-meter', 'data-level');
    const pw = signup.page.locator('.pw-group input').nth(0);
    const pw2 = signup.page.locator('.pw-group input').nth(1);
    await signup.page.fill('#view-root input[autocomplete="username"]', `e2e${Date.now().toString(36)}`);
    await signup.page.fill('#view-root input[autocomplete="name"]', 'Prueba Idiomas');
    const signupEmail = `i18n.${Date.now()}@example.com`;
    await signup.page.fill('#view-root input[type="email"]', signupEmail);
    await pw.fill('corta');
    check((await level()) === 'WEAK' && (await signup.page.textContent('.pw-tips')).includes('12'), 'Indicador: contraseña corta → débil, con el mínimo de 12');
    await pw.fill('password1234');
    check((await level()) === 'WEAK' && /common|known/i.test(await signup.page.textContent('.pw-tips')), 'Indicador: contraseña común detectada (lista local)');
    await pw.fill('Lima2026river');
    check(['FAIR', 'STRONG'].includes(await level()), `Indicador: contraseña aceptable (${await level()})`);
    const strong = 'ancla verde cobre lluvia 2026';
    await pw.fill(strong);
    check((await level()) === 'STRONG', 'Indicador: frase de contraseña larga → fuerte (se permiten espacios)');
    const reveal = signup.page.locator('.pw-reveal').first();
    await reveal.click();
    check((await pw.getAttribute('type')) === 'text' && (await reveal.getAttribute('aria-pressed')) === 'true', 'Mostrar contraseña');
    await reveal.click();
    check((await pw.getAttribute('type')) === 'password', 'Ocultar contraseña');
    await pw2.fill(`${strong}x`);
    check((await signup.page.getAttribute('.pw-match', 'data-ok')) === 'false', 'Confirmación distinta: aviso «no coinciden»');
    await signup.page.click('#view-root button[type="submit"]');
    check(new URL(signup.page.url()).pathname === `/en/${slug('en', '/registro.html')}`, 'El formulario no se envía si la confirmación no coincide');
    await pw2.fill(strong);
    check((await signup.page.getAttribute('.pw-match', 'data-ok')) === 'true', 'Confirmación igual: «coinciden»');
    await signup.page.screenshot({ path: path.join(OUT, 'i18n-en-registro.png') });
    // El servidor valida aunque se salte la interfaz.
    const csrf = (await (await signup.context.request.get(`${BASE_URL}/api/auth/csrf`)).json()).token;
    for (const weak of ['password1234', 'corta2026', 'aaaaaaaaaaaaaa']) {
      const response = await signup.context.request.post(`${BASE_URL}/api/auth/register`, {
        headers: { 'X-CSRF-TOKEN': csrf },
        data: { username: `weak${Date.now().toString(36)}`, displayName: 'Prueba', email: `weak.${Date.now()}@example.com`, password: weak },
      });
      const body = await response.json().catch(() => ({}));
      check(response.status() === 400 && body.code === 'WEAK_PASSWORD' && Array.isArray(body.problems) && body.problems.length > 0,
        `Servidor: rechaza una contraseña débil de ${weak.length} caracteres (${response.status()} ${body.code}: ${(body.problems || []).join(', ')})`);
    }
    // v7: con servidor de correo, la cuenta nueva confirma su correo con el código de 6 dígitos (en inglés).
    const signupStarted = Date.now();
    const landed = signup.page.waitForURL(new RegExp(`/en/(${slug("en", "/cliente/dashboard.html")}|${slug("en", "/carrito.html")})$`), { timeout: 40000 });
    await signup.page.click('#view-root button[type="submit"]');
    const verified = await completeVerification(signup.page, signupEmail, signupStarted);
    if (verified) check(true, 'Registro: el paso de verificación con el código de 6 dígitos aparece en inglés y acepta el código del correo');
    await landed;
    check(true, 'Registro con frase de contraseña fuerte: cuenta creada y sesión iniciada en inglés');
    await signup.context.close();

    // ---- 7. Móvil ---------------------------------------------------------------------------------
    console.log('Móvil');
    const mobile = await open(browser, 'móvil', { viewport: { width: 390, height: 844 } });
    for (const lang of ['de', 'fr']) {
      await mobile.page.goto(url(lang, '/catalogo.html'));
      await rendered(mobile.page);
      const overflow = await mobile.page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 1 && (await mobile.page.isVisible('header .lang-select')), `Móvil (${lang}): sin desplazamiento horizontal y con selector de idioma visible`);
    }
    await mobile.page.screenshot({ path: path.join(OUT, 'i18n-fr-movil.png') });
    await mobile.context.close();
    await anon.context.close();
  } finally {
    await browser.close();
  }
  if (problems.length) {
    console.error(`\nProblemas detectados (${problems.length}):\n  ${problems.join('\n  ')}`);
    process.exit(1);
  }
  console.log(`\nOK i18n e2e: ${passed.length} comprobaciones`);
})().catch((error) => {
  console.error(error.message || error);
  if (problems.length) console.error(`Además:\n  ${problems.join('\n  ')}`);
  process.exit(1);
});
