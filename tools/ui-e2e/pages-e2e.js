// Prueba end-to-end del SITIO MULTIPÁGINA contra el stack real (Java + Python + PostgreSQL y,
// en CI, Prometheus y Grafana). Recorre TODAS las páginas de tools/ui-e2e/pages.json (generado
// por tools/generate_pages.py) como visitante y con cada rol, y comprueba:
//   1. que cada página existe y responde 200 (o redirige al login / muestra 403 según el rol);
//   2. que se renderiza sin errores de JavaScript, sin errores de consola y sin llamadas API rotas;
//   3. que todos los enlaces internos encontrados responden (sin 404);
//   4. que la página funciona tras recargar el navegador;
//   5. formularios reales: contacto, checkout con dirección y seguimiento, panel de administración;
//   6. vista móvil sin desplazamiento horizontal y menú desplegable.
// Uso: DEMO_PASSWORD=… [BASE_URL=…] [SCREENSHOTS_DIR=…] [SKIP_PROMETHEUS=1] node pages-e2e.js
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.SCREENSHOTS_DIR || path.join(__dirname, 'screenshots');
const PASSWORD = process.env.DEMO_PASSWORD;
const PAGES = JSON.parse(fs.readFileSync(path.join(__dirname, 'pages.json'), 'utf8'));
const passed = [];
const problems = [];

function check(condition, description) {
  if (!condition) throw new Error(`FALLO: ${description}`);
  passed.push(description);
  console.log(`  ✔ ${description}`);
}

const ROLE_OF = { cliente: 'CUSTOMER', admin: 'ADMIN', operador: 'ORDER_OPERATOR', logistica: 'LOGISTICS_OPERATOR', observador: 'OBSERVABILITY_VIEWER' };
const allowed = (page, role) => !page.roles.length || page.roles.includes('auth') || page.roles.includes(role);

async function newSession(browser, label, viewport = { width: 1440, height: 900 }) {
  const context = await browser.newContext({ viewport });
  // POLYMER: la intro se prueba en intro-e2e.js; aquí se marca como vista para no tapar la portada.
  await context.addInitScript(() => { try { sessionStorage.setItem('polymer.intro.seen', '1'); } catch { /* sin almacenamiento */ } });
  const page = await context.newPage();
  const failures = [];
  page.on('pageerror', (error) => problems.push(`[${label}] JavaScript: ${error.message} (${page.url()})`));
  page.on('console', (message) => {
    if (message.type() !== 'error') return;
    const text = message.text();
    // Los 401/403/404/409 esperados de la API se comprueban aparte; aquí solo errores inesperados.
    if (/status of (401|403|404|409)/.test(text)) return;
    problems.push(`[${label}] console.error: ${text} (${page.url()})`);
  });
  page.on('response', (response) => {
    const url = response.url();
    if (url.startsWith(`${BASE_URL}/api/`) && response.status() >= 500) failures.push(`${response.status()} ${url}`);
    if (url.startsWith(BASE_URL) && /\.(js|css|svg|png)$/.test(new URL(url).pathname) && response.status() >= 400) failures.push(`${response.status()} ${url}`);
  });
  return { context, page, failures };
}

async function login(session, user) {
  await session.page.goto(`${BASE_URL}/login.html`);
  await session.page.waitForSelector('#view-root form');
  await session.page.fill('#view-root input[autocomplete="username"]', user);
  await session.page.fill('#view-root input[type="password"]', PASSWORD);
  await Promise.all([session.page.waitForURL((url) => !url.pathname.endsWith('/login.html'), { timeout: 15000 }),
    session.page.click('#view-root button[type="submit"]')]);
}

/** Espera a que la vista termine: sin skeleton de carga inicial y sin aria-busy. */
async function rendered(page) {
  await page.waitForFunction(() => {
    const root = document.getElementById('view-root');
    return root && !root.querySelector('.page-loading') && !root.hasAttribute('aria-busy') && root.children.length > 0;
  }, null, { timeout: 20000 });
  await page.waitForTimeout(250);
}

async function viewError(page) {
  return page.evaluate(() => {
    const strong = document.querySelector('#view-root .empty-state strong');
    const text = strong ? strong.textContent : '';
    return /No se pudo|no disponible|Falta el identificador/i.test(text) ? text : '';
  });
}

const linkCache = new Map();
async function checkLinks(session, label) {
  const hrefs = await session.page.$$eval('a[href]', (anchors) => anchors.map((a) => a.href));
  const internal = [...new Set(hrefs.map((h) => h.split('#')[0]).filter((h) => h.startsWith(BASE_URL) && !h.includes('/api/')))];
  for (const href of internal) {
    const key = `${label}|${href}`;
    if (linkCache.has(key)) continue;
    const response = await session.context.request.get(href, { maxRedirects: 0 });
    linkCache.set(key, response.status());
    if (response.status() === 404 || response.status() >= 500) throw new Error(`FALLO: enlace roto ${href} → ${response.status()} (desde ${session.page.url()})`);
  }
}

// v6: lo que ve un visitante o un cliente en la tienda no explica cómo está construida.
const TECH_TERMS = ['hexagonal', 'docker', 'postgres', 'redis', 'rabbitmq', 'grafana', 'prometheus', 'microservicio',
  'idempoten', 'laboratorio', 'arquitectura', 'backend', 'spring', 'motor de riesgo', 'kubernetes', 'caso de uso',
  'hexa' + 'orders']; // la marca anterior tampoco es visible en la tienda
async function checkCommercialText(session, entry) {
  const text = (await session.page.evaluate(() => document.body.innerText)).toLowerCase();
  const found = TECH_TERMS.filter((term) => text.includes(term));
  if (found.length) throw new Error(`FALLO: ${entry.path} muestra texto técnico en la tienda: ${found.join(', ')}`);
}

async function visitAll(session, role, ids) {
  let ok = 0;
  for (const entry of PAGES) {
    if (entry.path.startsWith('/40')) continue; // 403/404 se prueban aparte
    let url = `${BASE_URL}${entry.path}`;
    if (entry.needsId) {
      const id = entry.path === '/producto.html' ? ids.product : ids.order;
      if (!id) continue;
      url += `?id=${encodeURIComponent(id)}`;
    }
    const response = await session.page.goto(url);
    if (!role) {
      if (entry.roles.length) {
        check(new URL(session.page.url()).pathname === '/login.html' && session.page.url().includes('next='), `Visitante: ${entry.path} redirige al login conservando el destino`);
        continue;
      }
    } else if (!allowed(entry, ROLE_OF[role])) {
      await rendered(session.page);
      check(response.status() === 403 && (await session.page.textContent('#view-root')).includes('Acceso restringido'), `${role}: ${entry.path} muestra la página 403`);
      continue;
    }
    check(response.status() === 200, `${role || 'visitante'}: ${entry.path} responde 200`);
    await rendered(session.page);
    const error = await viewError(session.page);
    if (error) throw new Error(`FALLO: ${entry.path} (${role || 'visitante'}) muestra «${error}»`);
    await checkLinks(session, role || 'anon');
    if (!role || role === 'cliente') await checkCommercialText(session, entry);
    ok += 1;
  }
  return ok;
}

(async () => {
  if (!PASSWORD) throw new Error('Define DEMO_PASSWORD con la contraseña de las cuentas de demostración.');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const failuresAll = [];
  try {
    // ---- Visitante ----------------------------------------------------------------------
    console.log('Visitante');
    const anon = await newSession(browser, 'visitante');
    const products = await (await anon.context.request.get(`${BASE_URL}/api/catalog/products`)).json();
    const ids = { product: products.find((p) => p.stock > 0).id };
    const shown = await visitAll(anon, null, ids);
    check(shown >= 10, `Visitante: ${shown} páginas públicas renderizadas con datos reales y enlaces válidos`);
    const notFound = await anon.page.goto(`${BASE_URL}/no-existe-${Date.now()}.html`);
    await rendered(anon.page);
    check(notFound.status() === 404 && (await anon.page.textContent('h1')).includes('no encontrada'), 'Una URL inexistente muestra la página 404 diseñada');
    await anon.page.goto(`${BASE_URL}/#/tienda`);
    await anon.page.waitForURL(/\/catalogo\.html/);
    check(true, 'La URL antigua /#/tienda redirige a /catalogo.html (compatibilidad v3)');
    await anon.page.goto(`${BASE_URL}/#panel`);
    await anon.page.waitForURL(/\/login\.html\?next=%2Flaboratorio/);
    check(true, 'El ancla antigua /#panel lleva al laboratorio interno, que exige iniciar sesión (personal autorizado)');
    // Catálogo: filtros reales en la URL y recarga.
    await anon.page.goto(`${BASE_URL}/catalogo.html?categoria=computadoras`);
    await rendered(anon.page);
    const stockFilter = '.filters .filter-option:has-text("Solo con stock") input';
    await anon.page.waitForSelector(stockFilter);
    await anon.page.check(stockFilter);
    await anon.page.waitForFunction(() => window.location.search.includes('disponible=1'));
    await anon.page.reload();
    await rendered(anon.page);
    await anon.page.waitForSelector(stockFilter);
    check(anon.page.url().includes('disponible=1') && anon.page.url().includes('categoria=computadoras') && await anon.page.isChecked(stockFilter),
      'Los filtros del catálogo sobreviven a la recarga (estado en la URL)');
    const brandBox = '.filters .filter-group:has(summary:has-text("Marca")) .filter-option input';
    await anon.page.check(brandBox);
    await anon.page.waitForFunction(() => window.location.search.includes('marca='));
    await rendered(anon.page);
    const brandName = new URL(anon.page.url()).searchParams.get('marca');
    const brands = await anon.page.$$eval('.product-card .product-card__brand', (nodes) => nodes.map((n) => n.textContent.trim().toLowerCase()));
    check(brands.length > 0 && brands.every((b) => b === brandName.toLowerCase()), `El filtro de marca (${brandName}) cambia los resultados del servidor`);
    await anon.page.screenshot({ path: path.join(OUT, 'v4-catalogo.png') });
    await anon.page.goto(`${BASE_URL}/`);
    await rendered(anon.page);
    await anon.page.screenshot({ path: path.join(OUT, 'v4-inicio.png') });
    // Contacto: formulario real persistido.
    await anon.page.goto(`${BASE_URL}/contacto.html`);
    await rendered(anon.page);
    const stamp = `E2E ${Date.now()}`;
    await anon.page.fill('#view-root input[autocomplete="name"]', 'Prueba E2E');
    await anon.page.fill('#view-root input[type="email"]', 'e2e@example.com');
    await anon.page.fill('#view-root textarea', `${stamp}: mensaje enviado por la prueba multipágina.`);
    await anon.page.click('#view-root button[type="submit"]');
    await anon.page.waitForSelector('.notice-box--ok');
    check(true, 'El formulario de contacto guarda el mensaje (POST /api/contact con CSRF)');
    failuresAll.push(...anon.failures);

    // ---- Cliente: compra con dirección y seguimiento --------------------------------------
    console.log('Cliente');
    const customer = await newSession(browser, 'cliente');
    await login(customer, 'cliente');
    check(new URL(customer.page.url()).pathname === '/cliente/dashboard.html', 'El cliente entra a su resumen tras iniciar sesión');
    await customer.page.goto(`${BASE_URL}/producto.html?id=${ids.product}`);
    await rendered(customer.page);
    check((await customer.page.locator('.spec-table tr').count()) >= 3, 'La ficha del producto muestra sus especificaciones técnicas');
    // «Comprar ahora» agrega al carrito guardado y lleva al checkout.
    await Promise.all([customer.page.waitForURL(/\/checkout\.html/, { timeout: 15000 }), customer.page.click('.product-detail .btn--primary')]);
    await rendered(customer.page);
    await customer.page.fill('#view-root input[autocomplete="street-address"]', 'Av. Prueba 742, dpto. 3');
    await customer.page.fill('#view-root input[autocomplete="address-level2"]', 'Arequipa');
    await customer.page.screenshot({ path: path.join(OUT, 'v4-checkout.png') });
    await Promise.all([customer.page.waitForURL(/\/cliente\/seguimiento\.html\?id=/, { timeout: 20000 }), customer.page.click('button:has-text("Confirmar pedido")')]);
    await rendered(customer.page);
    ids.order = new URL(customer.page.url()).searchParams.get('id');
    check((await customer.page.textContent('#view-root')).includes('Arequipa'), 'Checkout desde la interfaz: el seguimiento muestra la dirección guardada en PostgreSQL');
    await customer.page.reload();
    await rendered(customer.page);
    check((await customer.page.locator('.tracker li.is-done').count()) >= 2, 'El seguimiento funciona tras recargar (estado e historial reales)');
    await customer.page.screenshot({ path: path.join(OUT, 'v4-seguimiento.png') });
    const customerPages = await visitAll(customer, 'cliente', ids);
    check(customerPages >= 12, `Cliente: ${customerPages} páginas permitidas renderizadas; las demás devuelven 403`);
    failuresAll.push(...customer.failures);

    // ---- Equipos ---------------------------------------------------------------------------
    for (const user of ['admin', 'operador', 'logistica', 'observador']) {
      console.log(user);
      const staff = await newSession(browser, user);
      await login(staff, user);
      const count = await visitAll(staff, user, ids);
      check(count >= 8, `${user}: ${count} páginas permitidas renderizadas; las de otros roles devuelven 403`);
      if (user === 'admin') {
        await staff.page.goto(`${BASE_URL}/admin/dashboard.html`);
        await rendered(staff.page);
        await staff.page.waitForSelector('text=Mensajes de la página de contacto');
        await staff.page.waitForFunction((s) => document.body.textContent.includes(s), stamp, { timeout: 15000 });
        check(true, 'El administrador ve el mensaje de contacto recién enviado');
        await staff.page.screenshot({ path: path.join(OUT, 'v4-admin-dashboard.png') });
        await staff.page.goto(`${BASE_URL}/admin/inventario.html`);
        await rendered(staff.page);
        await staff.page.screenshot({ path: path.join(OUT, 'v4-admin-inventario.png') });
      }
      if (user === 'operador') {
        await staff.page.goto(`${BASE_URL}/operaciones/pedidos.html`);
        await rendered(staff.page);
        await staff.page.screenshot({ path: path.join(OUT, 'v4-operaciones.png') });
      }
      if (user === 'logistica') {
        await staff.page.goto(`${BASE_URL}/logistica/calendario.html`);
        await staff.page.waitForSelector('.cal-month .cal-day');
        check((await staff.page.locator('.cal-month .cal-day').count()) === 42, 'Calendario logístico mensual conectado a PostgreSQL');
        await staff.page.screenshot({ path: path.join(OUT, 'v4-logistica-calendario.png') });
        await staff.page.goto(`${BASE_URL}/logistica/asignaciones.html`);
        await rendered(staff.page);
        await staff.page.screenshot({ path: path.join(OUT, 'v4-logistica-asignaciones.png') });
      }
      if (user === 'observador') {
        await staff.page.goto(`${BASE_URL}/monitoreo/servicios.html`);
        await rendered(staff.page);
        if (!process.env.SKIP_PROMETHEUS) {
          await staff.page.waitForFunction(() => document.querySelectorAll('#view-root tbody tr').length >= 3, null, { timeout: 30000 });
          const ups = await staff.page.locator('#view-root tbody .badge--ok').count();
          check(ups >= 3, `Monitoreo: order-api, risk-engine y prometheus en UP leídos de Prometheus por el backend (${ups})`);
          await staff.page.goto(`${BASE_URL}/monitoreo/alertas.html`);
          await rendered(staff.page);
          check((await staff.page.locator('#view-root tbody tr').count()) >= 9, 'Monitoreo: las 9 reglas de alerta aparecen con su estado');
        } else {
          check((await staff.page.textContent('#view-root')).includes('Prometheus no responde'), 'Monitoreo sin Prometheus: la página lo explica sin romperse');
        }
        await staff.page.screenshot({ path: path.join(OUT, 'v4-monitoreo.png') });
      }
      failuresAll.push(...staff.failures);
      await staff.context.close();
    }

    // ---- Móvil ------------------------------------------------------------------------------
    const mobile = await newSession(browser, 'móvil', { width: 390, height: 844 });
    for (const target of ['/', '/catalogo.html', `/producto.html?id=${ids.product}`, '/ofertas.html', '/ayuda.html', '/contacto.html']) {
      await mobile.page.goto(`${BASE_URL}${target}`);
      await rendered(mobile.page);
      check(!(await mobile.page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)), `Móvil (390 px): ${target} sin desplazamiento horizontal`);
    }
    await mobile.page.click('.nav-toggle');
    check(await mobile.page.isVisible('#primary-nav a[href="/ofertas.html"]'), 'Móvil: el menú desplegable muestra la navegación');
    await mobile.page.screenshot({ path: path.join(OUT, 'v4-movil-menu.png') });
    failuresAll.push(...mobile.failures);
  } finally {
    await browser.close();
  }
  check(failuresAll.length === 0, `Sin llamadas API con 5xx ni recursos rotos${failuresAll.length ? `: ${failuresAll.join(' | ')}` : ''}`);
  check(problems.length === 0, `Sin errores de JavaScript ni de consola${problems.length ? `: ${problems.slice(0, 8).join(' | ')}` : ''}`);
  console.log(`\nPáginas E2E correctas: ${passed.length} comprobaciones. Capturas en ${OUT}`);
})().catch((error) => {
  console.error(`\n${error.message}`);
  if (problems.length) console.error(`Problemas registrados: ${problems.slice(0, 10).join(' | ')}`);
  process.exit(1);
});
