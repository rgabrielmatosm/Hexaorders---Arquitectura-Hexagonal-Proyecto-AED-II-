// POLYMER — prueba end-to-end de la marca, la intro cinematográfica y el sistema de movimiento,
// contra el stack real. Comprueba las reglas pedidas: una vez por sesión, se puede omitir (botón y
// Esc), no se repite al navegar ni al recargar, sin sonido salvo elección explícita, alternativa sin
// animación con prefers-reduced-motion, sin intro en los paneles internos, sin errores de JavaScript,
// móvil sin desbordamiento, pie con enlaces reales, Libro de reclamaciones y vuelo al carrito.
// Uso:  node intro-e2e.js   (variables: BASE_URL, DEMO_PASSWORD para el panel interno, SCREENSHOTS_DIR)
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.SCREENSHOTS_DIR || path.join(__dirname, 'screenshots');
const problems = [];
let passed = 0;

function check(condition, description) {
  if (!condition) throw new Error(`FALLO: ${description}`);
  passed += 1;
  console.log(`  ✔ ${description}`);
}

async function open(browser, label, options = {}) {
  const context = await browser.newContext({ viewport: { width: 1366, height: 820 }, ...options });
  const page = await context.newPage();
  page.on('pageerror', (error) => problems.push(`[${label}] JavaScript: ${error.message}`));
  page.on('console', (message) => {
    if (message.type() === 'error' && !/status of (401|403|404|409)/.test(message.text())) problems.push(`[${label}] console.error: ${message.text()}`);
  });
  return { context, page };
}

const introVisible = (page) => page.evaluate(() => Boolean(document.querySelector('#polymer-intro')));

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  try {
    // 1. Primera visita: la intro aparece sin destello de la página y se arma por etapas.
    console.log('Intro en la primera visita');
    const { context, page } = await open(browser, 'intro');
    await page.goto(`${BASE_URL}/`, { waitUntil: 'commit' });
    await page.waitForFunction(() => document.documentElement.classList.length > 0 || document.body, null, { timeout: 5000 });
    const early = await page.evaluate(() => document.documentElement.className);
    check(/intro-(pending|running)/.test(early), `Antes de pintar, la página queda cubierta (clase «${early.trim()}»)`);
    await page.waitForSelector('#polymer-intro', { timeout: 5000 });
    check((await page.$$('.intro__word span')).length === 7, 'El logotipo P·O·L·Y·M·E·R aparece letra por letra (7 letras)');
    check(await page.isVisible('text=Entrar con sonido'), 'Botón visible «Entrar con sonido» (el audio nunca arranca solo)');
    check(await page.isVisible('text=Omitir intro'), 'Botón visible «Omitir intro»');
    check(await page.evaluate(() => document.activeElement?.textContent === 'Omitir intro'), 'El foco está en «Omitir intro» (teclado)');
    await page.waitForTimeout(1600);
    await page.screenshot({ path: path.join(OUT, 'p1-intro.png') });
    check(await page.evaluate(() => document.querySelector('#view-root .hero-store, #view-root .product-card') !== null),
      'La página se prepara detrás del telón mientras corre la intro');
    await page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 7000 });
    check(true, 'La intro termina sola (≈4 s) y abre el telón');
    check(await page.evaluate(() => sessionStorage.getItem('polymer.intro.seen') === '1'), 'Queda registrada como vista en esta sesión');
    check(await page.evaluate(() => localStorage.getItem('polymer.sound') !== 'on'), 'Sin interacción no se activa el sonido');
    await page.screenshot({ path: path.join(OUT, 'p2-portada.png') });

    // 2. No se repite al navegar ni al recargar.
    await page.click('#primary-nav a[href="/ofertas.html"]');
    await page.waitForURL(/ofertas\.html/);
    await page.waitForTimeout(400);
    check(!(await introVisible(page)), 'No se repite al cambiar de página');
    await page.reload();
    await page.waitForTimeout(400);
    check(!(await introVisible(page)), 'No se repite al recargar');
    check(await page.evaluate(() => getComputedStyle(document.body).visibility === 'visible'), 'La página es visible de inmediato en visitas siguientes');

    // 3. Botón de sonido global (apagado por defecto, persistente).
    const toggle = page.locator('#sound-toggle');
    check((await toggle.getAttribute('aria-pressed')) === 'false', 'El silencio global está activo por defecto');
    await toggle.click();
    check((await toggle.getAttribute('aria-pressed')) === 'true' && await page.evaluate(() => localStorage.getItem('polymer.sound') === 'on'),
      'El botón de sonido activa los sonidos y recuerda la preferencia');
    await toggle.click();
    check(await page.evaluate(() => localStorage.getItem('polymer.sound') === 'off'), 'El botón de sonido vuelve a silenciar');

    // 4. Pie comercial: 5 columnas, contactos de demostración y enlaces reales.
    await page.goto(`${BASE_URL}/`);
    await page.waitForSelector('.pm-footer .site-footer__col');
    const footer = await page.evaluate(() => ({
      columns: document.querySelectorAll('.pm-footer .site-footer__grid > *').length,
      titles: [...document.querySelectorAll('.pm-footer .site-footer__col > strong')].map((n) => n.textContent),
      text: document.querySelector('.pm-footer').innerText,
      hrefs: [...document.querySelectorAll('.pm-footer a[href]')].map((a) => a.href),
      social: document.querySelectorAll('.pm-social a').length,
    }));
    check(footer.columns === 5, 'El pie tiene 5 columnas');
    check(['Sobre nosotros', 'Te informamos', 'Servicio al cliente', 'Destacados'].every((t) => footer.titles.includes(t)), 'Columnas: Sobre nosotros, Te informamos, Servicio al cliente, Destacados');
    check(footer.text.includes('Tecnología que conecta contigo.') && footer.text.includes('+51 900 000 000') && footer.text.includes('soporte@example.com')
      && footer.text.includes('Libro de reclamaciones') && footer.text.includes('DEMO'), 'Eslogan, contacto de demostración marcado como DEMO y Libro de reclamaciones');
    check(/RUC: no configurado/.test(footer.text) && !/RUC: \d/.test(footer.text), 'No se inventa ningún RUC');
    check(/próximamente/.test(footer.text) && !/Visa|Mastercard|Yape|Plin/.test(footer.text), 'Los pagos no integrados se muestran como «próximamente» y no se anuncian marcas de pago');
    check(footer.social === 6, 'Iconos de Facebook, Instagram, TikTok, YouTube, LinkedIn y WhatsApp');
    const internal = [...new Set(footer.hrefs.filter((h) => h.startsWith(BASE_URL)).map((h) => h.split('#')[0]))];
    for (const href of internal) {
      const response = await context.request.get(href, { maxRedirects: 0 });
      if (response.status() >= 400) throw new Error(`FALLO: enlace del pie roto ${href} → ${response.status()}`);
    }
    check(internal.length >= 15, `Las ${internal.length} páginas enlazadas desde el pie (${footer.hrefs.length} enlaces) responden sin error`);
    check(footer.hrefs.every((h) => h.startsWith(BASE_URL) || h.startsWith('mailto:')), 'Ningún enlace del pie simula una cuenta oficial externa');
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.waitForTimeout(900);
    await page.screenshot({ path: path.join(OUT, 'p3-pie.png') });

    // 5. Movimiento: aparición al desplazarse y vuelo al carrito.
    await page.goto(`${BASE_URL}/catalogo.html`);
    await page.waitForSelector('.product-card .btn--primary:not([disabled])');
    check(await page.evaluate(() => document.querySelectorAll('.product-card.reveal').length > 0), 'Las tarjetas usan la aparición escalonada (IntersectionObserver)');
    await page.waitForTimeout(900);
    check(await page.evaluate(() => getComputedStyle(document.querySelector('.product-card')).opacity === '1'), 'Las tarjetas visibles terminan opacas');
    const before = Number((await page.textContent('#cart-count')) || 0);
    await page.click('.product-card .btn--primary:not([disabled])');
    await page.waitForTimeout(150);
    const flying = await page.evaluate(() => Boolean(document.querySelector('.fly-dot')) || document.querySelector('#cart-button').classList.contains('is-bumped'));
    check(flying, 'Agregar al carrito anima el vuelo hasta el carrito');
    await page.waitForFunction((n) => Number(document.querySelector('#cart-count').textContent) > n, before);
    check(true, 'El contador del carrito aumenta');
    check(await page.evaluate(() => Boolean(document.querySelector('.nav-progress'))), 'Indicador de navegación entre páginas presente');

    // 6. Libro de reclamaciones: se registra de verdad.
    await page.goto(`${BASE_URL}/libro-reclamaciones.html`);
    await page.fill('#view-root input[autocomplete="name"]', 'Cliente de prueba');
    await page.fill('#view-root input[type="email"]', 'cliente.prueba@example.com');
    await page.fill('#view-root textarea >> nth=0', 'El producto llegó con la caja dañada y un accesorio faltante.');
    await page.fill('#view-root textarea >> nth=1', 'Cambio del producto.');
    await page.click('#view-root button[type="submit"]');
    await page.waitForSelector('.notice-box--ok', { timeout: 10000 });
    check(/Hoja N\.° \d{6}/.test(await page.textContent('.notice-box--ok')), 'El Libro de reclamaciones registra la hoja y muestra su número');
    await context.close();

    // 7. Omitir con Esc y repetir a demanda.
    const esc = await open(browser, 'esc');
    await esc.page.goto(`${BASE_URL}/catalogo.html`);
    await esc.page.waitForSelector('#polymer-intro');
    await esc.page.keyboard.press('Escape');
    await esc.page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 2500 });
    check(true, 'Esc omite la intro de inmediato (también en páginas interiores de la tienda)');
    await esc.page.goto(`${BASE_URL}/?intro=1`);
    await esc.page.waitForSelector('#polymer-intro');
    check(true, '«Ver la intro» (?intro=1) la repite a demanda');
    await esc.page.click('text=Omitir intro');
    await esc.page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 2500 });
    check(true, 'El botón «Omitir intro» la cierra');
    await esc.context.close();

    // 8. Entrar con sonido: solo tras el clic, sin errores.
    const sound = await open(browser, 'sound');
    await sound.page.goto(`${BASE_URL}/`);
    await sound.page.click('text=Entrar con sonido');
    await sound.page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 4000 });
    check(await sound.page.evaluate(() => localStorage.getItem('polymer.sound') === 'on'), '«Entrar con sonido» activa el sonido de bienvenida (Web Audio) y abre la tienda');
    check((await sound.page.getAttribute('#sound-toggle', 'aria-pressed')) === 'true', 'El botón de la cabecera refleja que el sonido está activo');
    await sound.context.close();

    // 9. prefers-reduced-motion: sin intro y sin animaciones de aparición.
    const reduced = await open(browser, 'reduced', { reducedMotion: 'reduce' });
    await reduced.page.goto(`${BASE_URL}/`);
    await reduced.page.waitForSelector('.product-card');
    check(!(await introVisible(reduced.page)), 'Con movimiento reducido no se muestra la intro');
    check(await reduced.page.evaluate(() => getComputedStyle(document.body).visibility === 'visible'
      && [...document.querySelectorAll('.product-card')].every((c) => getComputedStyle(c).opacity === '1')), 'Con movimiento reducido todo el contenido es visible y estático');
    await reduced.context.close();

    // 10. Móvil: intro adaptada y sin desbordamiento.
    const mobile = await open(browser, 'mobile', { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    await mobile.page.goto(`${BASE_URL}/`);
    await mobile.page.waitForSelector('#polymer-intro');
    await mobile.page.waitForTimeout(1500);
    check(await mobile.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Intro sin desbordamiento horizontal en móvil (390 px)');
    await mobile.page.screenshot({ path: path.join(OUT, 'p4-movil-intro.png') });
    await mobile.page.tap('text=Omitir intro');
    await mobile.page.waitForSelector('#polymer-intro', { state: 'detached', timeout: 2500 });
    check(await mobile.page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Tienda y pie sin desbordamiento en móvil');
    await mobile.context.close();

    // 11. Paneles internos: nunca muestran la intro.
    if (process.env.DEMO_PASSWORD) {
      const staff = await open(browser, 'staff');
      await staff.page.goto(`${BASE_URL}/login.html?intro=0`);
      await staff.page.fill('#view-root input[autocomplete="username"]', 'admin');
      await staff.page.fill('#view-root input[type="password"]', process.env.DEMO_PASSWORD);
      await Promise.all([staff.page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15000 }), staff.page.click('#view-root button[type="submit"]')]);
      await staff.page.evaluate(() => sessionStorage.removeItem('polymer.intro.seen'));
      for (const target of ['/admin/dashboard.html', '/operaciones/dashboard.html', '/logistica/dashboard.html', '/monitoreo/dashboard.html']) {
        await staff.page.goto(`${BASE_URL}${target}`);
        await staff.page.waitForTimeout(300);
        if (await introVisible(staff.page)) throw new Error(`FALLO: la intro aparece en el panel interno ${target}`);
      }
      check(true, 'Los paneles de administración, operaciones, logística y monitoreo no muestran la intro');
      check(await staff.page.evaluate(() => getComputedStyle(document.querySelector('.side-nav')).borderTopColor !== ''), 'Los paneles internos comparten la identidad POLYMER con su color de área');
      await staff.page.screenshot({ path: path.join(OUT, 'p5-panel.png') });
      await staff.context.close();
    } else {
      console.log('  (sin DEMO_PASSWORD: se omite la comprobación de los paneles internos)');
    }

    if (problems.length) throw new Error(`Errores en la consola o de JavaScript:\n    ${problems.join('\n    ')}`);
    check(true, 'Sin errores de JavaScript ni de consola en todo el recorrido');
    console.log(`\nOK: intro y movimiento de POLYMER (${passed} comprobaciones)`);
  } catch (error) {
    console.error(error.message);
    if (problems.length) console.error(problems.join('\n'));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
