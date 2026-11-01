// Prueba end-to-end de la interfaz contra el stack REAL levantado con Docker Compose
// (Java + Python + PostgreSQL + Prometheus + Grafana). No usa datos simulados.
// Uso:  npm install && node ui-e2e.js   (variables opcionales: BASE_URL, SCREENSHOTS_DIR)
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const BASE_URL = process.env.BASE_URL || 'http://127.0.0.1:8080';
const OUT = process.env.SCREENSHOTS_DIR || path.join(__dirname, 'screenshots');
const problems = [];
const passed = [];

function check(condition, description) {
  if (!condition) throw new Error(`FALLO: ${description}`);
  passed.push(description);
  console.log(`  ✔ ${description}`);
}

async function apiJson(page, url) {
  return page.evaluate(async (target) => (await fetch(target, { cache: 'no-store' })).json(), url);
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    page.on('pageerror', (error) => problems.push(`Error de JavaScript: ${error.message}`));
    page.on('console', (message) => {
      // El 400 de la prueba de validación del explorador es intencionado.
      if (message.type() === 'error' && !/status of 400/.test(message.text())) problems.push(`console.error: ${message.text()}`);
    });

    console.log(`Abriendo ${BASE_URL}`);
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#splash');
    await page.waitForFunction(() => document.querySelector('#splash [data-check="api"] em').textContent !== 'comprobando…', null, { timeout: 15000 });
    check((await page.textContent('#splash [data-check="api"] em')).includes('disponible'), 'La bienvenida confirma la API con una consulta real');
    await page.screenshot({ path: path.join(OUT, '01-bienvenida.png') });
    await page.keyboard.press('Enter');
    await page.waitForSelector('#splash', { state: 'hidden' });

    // Panel con datos reales de PostgreSQL
    await page.waitForFunction(() => !document.querySelector('#stat-total').hasAttribute('data-loading'), null, { timeout: 15000 });
    const stats = await apiJson(page, '/api/orders/statistics');
    const shownTotal = Number((await page.textContent('#stat-total')).replace(/\D/g, ''));
    check(shownTotal === stats.total, `El panel muestra el total real de PostgreSQL (${stats.total})`);
    check((await page.textContent('#api-status')).includes('API conectada'), 'El indicador de la barra superior marca la API como conectada');
    await page.screenshot({ path: path.join(OUT, '02-inicio.png') });

    // Validación en el navegador (no llega al backend)
    await page.fill('#amount', '10.001');
    await page.click('#order-submit');
    check(await page.isVisible('#amount-error'), 'El formulario rechaza importes con 3 decimales antes de enviar');

    // Alta real de un pedido en revisión
    const customer = `e2e_${Date.now()}`;
    await page.fill('#customer', customer);
    await page.fill('#amount', '1500.00');
    await page.click('#order-submit');
    await page.waitForSelector('#order-result:not([hidden])', { timeout: 15000 });
    const result = await page.textContent('#order-result');
    check(result.includes('requiere revisión') && result.includes('75/100'), 'Pedido de 1500.00 USD creado en revisión con riesgo 75 (Java → Python → PostgreSQL)');
    await page.waitForFunction((name) => Array.from(document.querySelectorAll('#orders-body tr')).some((row) => row.textContent.includes(name)), customer, { timeout: 15000 });
    check(true, 'El pedido nuevo aparece en el listado leído de PostgreSQL');
    const after = await apiJson(page, '/api/orders/statistics');
    check(after.review === stats.review + 1, 'Las estadísticas persistentes aumentan en un pedido en revisión');
    await page.locator('#panel').scrollIntoViewIfNeeded();
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, '03-panel.png') });
    await page.locator('#pedidos').scrollIntoViewIfNeeded();
    await page.waitForTimeout(800);
    await page.screenshot({ path: path.join(OUT, '04-pedidos.png') });

    // Consulta por ID (rellenada automáticamente con el último pedido creado)
    await page.click('#lookup-form button[type="submit"]');
    await page.waitForSelector('#lookup-result:not([hidden])');
    check((await page.textContent('#lookup-result')).includes('encontrado en PostgreSQL'), 'GET /api/orders/{id} encuentra el pedido creado');

    // Explorador de arquitectura
    await page.locator('#arquitectura').scrollIntoViewIfNeeded();
    await page.click('.node[data-node="httpRisk"]');
    check((await page.textContent('#arch-detail')).includes('adapter/out/http/HttpRiskAssessmentAdapter.java'), 'El explorador muestra el archivo real del adaptador HTTP');
    await page.screenshot({ path: path.join(OUT, '05-arquitectura.png') });
    await page.selectOption('#run-amount', '125.50');
    await page.click('#run-request');
    await page.waitForFunction(() => document.querySelectorAll('#run-steps li.is-done').length === 10, null, { timeout: 20000 });
    const evidence = await page.textContent('#run-evidence');
    check(evidence.includes('201 Created') && evidence.includes('idéntica'), 'Solicitud real: 201 y relectura idéntica desde PostgreSQL');
    await page.locator('#arch-diagram').scrollIntoViewIfNeeded();
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(OUT, '06-flujo-ejecucion.png') });
    await page.selectOption('#run-amount', '0');
    await page.click('#run-request');
    await page.waitForSelector('#run-steps li.is-failed', { timeout: 20000 });
    check((await page.textContent('#run-evidence')).includes('400'), 'Solicitud real inválida: el dominio responde 400 y no se guarda nada');

    // Observabilidad con Prometheus y Grafana reales
    await page.locator('#observabilidad').scrollIntoViewIfNeeded();
    await page.waitForFunction(() => {
      const rows = Array.from(document.querySelectorAll('#targets-body tr'));
      return rows.length >= 3 && rows.filter((row) => row.textContent.includes('up')).length >= 3;
    }, null, { timeout: 45000 });
    check(true, 'Prometheus informa los 3 targets (order-api, risk-engine, prometheus) en estado up');
    check(await page.isHidden('#obs-warning'), 'Sin avisos: Prometheus y Grafana accesibles desde el navegador');
    await page.waitForFunction(() => !document.querySelector('#metrics-list').textContent.includes('no disponibles'), null, { timeout: 20000 });
    check((await page.textContent('#metrics-list')).includes('Evaluaciones en Python'), 'Las métricas reales de Prometheus se muestran');
    await page.screenshot({ path: path.join(OUT, '07-observabilidad.png') });

    // Móvil
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
    mobile.on('pageerror', (error) => problems.push(`Error de JavaScript (móvil): ${error.message}`));
    await mobile.goto(BASE_URL);
    await mobile.keyboard.press('Escape');
    await mobile.waitForTimeout(800);
    check(!(await mobile.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)), 'Sin desplazamiento horizontal en móvil (390 px)');
    await mobile.screenshot({ path: path.join(OUT, '08-movil.png') });
  } finally {
    await browser.close();
  }
  check(problems.length === 0, `Sin errores de JavaScript ni de consola${problems.length ? `: ${problems.join(' | ')}` : ''}`);
  console.log(`\nUI E2E correcta: ${passed.length} comprobaciones. Capturas en ${OUT}`);
})().catch((error) => {
  console.error(`\n${error.message}`);
  if (problems.length) console.error(`Problemas registrados: ${problems.join(' | ')}`);
  process.exit(1);
});
