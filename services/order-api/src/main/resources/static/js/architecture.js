// Architecture Explorer. Cada nodo corresponde a una clase o módulo REAL del repositorio.
// Dos vistas distintas:
//  - "dependencies": dependencias del código fuente (las flechas apuntan hacia lo que se usa;
//    en el núcleo todas apuntan hacia dentro: regla de dependencia de la arquitectura hexagonal).
//  - "execution": orden de las llamadas en tiempo de ejecución para POST /api/orders.
// La "solicitud real" usa datos auténticos (HTTP, latencia, respuesta, relectura en PostgreSQL);
// los pasos numerados son el recorrido conceptual del código, no una traza instrumentada.

import { ApiError, ordersApi } from './api.js';
import { $, $$, el, reducedMotion, toast } from './ui.js';

const JAVA = 'services/order-api/src/main/java/com/polymer/';
const PY = 'services/risk-engine/risk_engine/';

const NODES = [
  // Actor y arranque
  { id: 'browser', col: 'actors', kind: 'external', name: 'Navegador', label: 'Actor primario',
    layer: 'Externo · actor que dirige (driver)', file: 'services/order-api/src/main/resources/static/js/orders.js',
    responsibility: 'Interfaz web servida por Spring Boot como recursos estáticos. Envía JSON y muestra respuestas; no contiene reglas de negocio.',
    role: 'Consumidor del adaptador REST.', relation: 'Inicia el caso de uso con POST /api/orders.' },
  { id: 'config', col: 'actors', kind: 'config', name: 'HexagonalConfiguration', label: 'Composition root',
    layer: 'Configuración (Spring)', file: `${JAVA}config/HexagonalConfiguration.java`,
    responsibility: 'Único lugar que elige los adaptadores concretos y construye OrderService con ellos (inyección de dependencias). Define timeouts HTTP de 2 s (conexión) y 3 s (lectura) y el pool de PostgreSQL.',
    role: 'Conecta puertos con adaptadores.', relation: 'Crea OrderService pasándole RiskAssessmentPort, OrderRepository, OrderTelemetry y Clock.' },

  // Adaptadores de entrada
  { id: 'controller', col: 'in', kind: 'adapter', name: 'OrderController', label: 'Adaptador REST',
    layer: 'Adaptador de entrada (driving)', file: `${JAVA}adapter/in/web/OrderController.java`,
    responsibility: 'Traduce HTTP ↔ caso de uso: POST /api/orders, GET /api/orders, GET /api/orders/{id} y GET /api/orders/statistics. Construye OrderDraft y devuelve 201 con cabecera Location.',
    role: 'Usa el puerto de entrada OrderUseCases.', relation: 'Invoca create(), get(), latest() y statistics().' },
  { id: 'handler', col: 'in', kind: 'adapter', name: 'ApiExceptionHandler', label: 'Errores → HTTP',
    layer: 'Adaptador de entrada (driving)', file: `${JAVA}adapter/in/web/ApiExceptionHandler.java`,
    responsibility: 'Convierte excepciones del dominio y de la aplicación en ProblemDetail: 400 (datos inválidos), 404 (no encontrado) y 503 (motor de riesgo o base de datos no disponibles).',
    role: 'Parte del adaptador web.', relation: 'Decide el código HTTP cuando el caso de uso falla.' },

  // Núcleo: puertos de entrada, aplicación, dominio y puertos de salida
  { id: 'useCases', col: 'core', group: 'Puerto de entrada', kind: 'port', name: 'OrderUseCases', label: 'Interfaz',
    layer: 'Aplicación · puerto de entrada', file: `${JAVA}application/port/in/OrderUseCases.java`,
    responsibility: 'Contrato de lo que la aplicación ofrece: create, get, latest y statistics. Los adaptadores de entrada dependen de esta interfaz, no de la implementación.',
    role: 'Puerto de entrada (driving port).', relation: 'Implementado por OrderService.' },
  { id: 'service', col: 'core', group: 'Caso de uso', kind: 'application', name: 'OrderService', label: 'Caso de uso',
    layer: 'Aplicación · servicio', file: `${JAVA}application/service/OrderService.java`,
    responsibility: 'Orquesta el caso de uso en Java puro: primero evalúa el riesgo; solo si hay puntaje válido crea el Order y lo persiste con un único INSERT. Si el riesgo falla, registra el fallo y no guarda nada.',
    role: 'Implementa OrderUseCases; usa tres puertos de salida.', relation: 'Es el caso de uso.' },
  { id: 'draft', col: 'core', group: 'Dominio', kind: 'domain', name: 'OrderDraft', label: 'Valor validado',
    layer: 'Dominio', file: `${JAVA}domain/OrderDraft.java`,
    responsibility: 'Valida la solicitud en lenguaje de negocio: customerId [A-Za-z0-9_-]{1,64}, importe > 0 y ≤ 1 000 000.00 con máximo 2 decimales (sin redondear) y moneda USD.',
    role: 'Entidad/valor del dominio; sin frameworks.', relation: 'Entrada del caso de uso create().' },
  { id: 'order', col: 'core', group: 'Dominio', kind: 'domain', name: 'Order', label: 'Entidad',
    layer: 'Dominio', file: `${JAVA}domain/Order.java`,
    responsibility: 'Pedido creado. Su constructor garantiza que el estado corresponde al puntaje de riesgo.',
    role: 'Entidad del dominio.', relation: 'Lo crea OrderService con Order.create(draft, score, id, instante).' },
  { id: 'status', col: 'core', group: 'Dominio', kind: 'domain', name: 'OrderStatus', label: 'Regla de negocio',
    layer: 'Dominio', file: `${JAVA}domain/OrderStatus.java`,
    responsibility: 'Regla de decisión propiedad del dominio Java: puntaje ≥ 70 → REVIEW; < 70 → APPROVED. Rechaza puntajes fuera de 0..100.',
    role: 'Enumeración con la regla de umbral.', relation: 'Determina el estado del pedido.' },
  { id: 'riskPort', col: 'core', group: 'Puertos de salida', kind: 'port', name: 'RiskAssessmentPort', label: 'Interfaz',
    layer: 'Aplicación · puerto de salida', file: `${JAVA}application/port/out/RiskAssessmentPort.java`,
    responsibility: 'Contrato «dame un puntaje 0..100 para este OrderDraft o lanza RiskUnavailableException». No menciona HTTP ni Python.',
    role: 'Puerto de salida (driven port).', relation: 'OrderService lo usa antes de persistir.' },
  { id: 'repoPort', col: 'core', group: 'Puertos de salida', kind: 'port', name: 'OrderRepository', label: 'Interfaz',
    layer: 'Aplicación · puerto de salida', file: `${JAVA}application/port/out/OrderRepository.java`,
    responsibility: 'Contrato de persistencia: save, findById, latest(limit) y statistics. No menciona SQL ni PostgreSQL.',
    role: 'Puerto de salida (driven port).', relation: 'OrderService guarda y consulta pedidos.' },
  { id: 'telemetryPort', col: 'core', group: 'Puertos de salida', kind: 'port', name: 'OrderTelemetry', label: 'Interfaz',
    layer: 'Aplicación · puerto de salida', file: `${JAVA}application/port/out/OrderTelemetry.java`,
    responsibility: 'Contrato de métricas de negocio: orderCreated(status) y riskFailure(). No menciona Micrometer.',
    role: 'Puerto de salida (driven port).', relation: 'OrderService informa pedidos creados y fallos de riesgo.' },

  // Adaptadores de salida
  { id: 'httpRisk', col: 'out', kind: 'adapter', name: 'HttpRiskAssessmentAdapter', label: 'Cliente HTTP',
    layer: 'Adaptador de salida (driven)', file: `${JAVA}adapter/out/http/HttpRiskAssessmentAdapter.java`,
    responsibility: 'Implementa RiskAssessmentPort con RestClient: POST /score al motor Python. Valida la respuesta y convierte errores de red o respuestas inválidas en RiskUnavailableException.',
    role: 'Implementa RiskAssessmentPort.', relation: 'Proporciona el puntaje al caso de uso.' },
  { id: 'jdbc', col: 'out', kind: 'adapter', name: 'JdbcOrderRepository', label: 'JDBC',
    layer: 'Adaptador de salida (driven)', file: `${JAVA}adapter/out/persistence/JdbcOrderRepository.java`,
    responsibility: 'Implementa OrderRepository con JdbcTemplate: INSERT atómico, SELECT por id, últimos 50 y conteo por estado. Convierte errores de acceso a datos en PersistenceUnavailableException.',
    role: 'Implementa OrderRepository.', relation: 'Persiste el pedido tras la evaluación de riesgo.' },
  { id: 'micrometer', col: 'out', kind: 'adapter', name: 'MicrometerOrderTelemetry', label: 'Micrometer',
    layer: 'Adaptador de salida (driven)', file: `${JAVA}adapter/out/metrics/MicrometerOrderTelemetry.java`,
    responsibility: 'Implementa OrderTelemetry con contadores Micrometer: orders_created_total{status} y orders_risk_failures_total, expuestos en /actuator/prometheus (puerto 9090).',
    role: 'Implementa OrderTelemetry.', relation: 'Hace observable el resultado del caso de uso.' },

  // Servicios externos
  { id: 'pyAdapter', col: 'external', group: 'risk-engine (Python)', kind: 'adapter', name: 'adapters/http.py', label: 'FastAPI · POST /score',
    layer: 'Python · adaptador de entrada', file: `${PY}adapters/http.py`,
    responsibility: 'Adaptador FastAPI: valida JSON con decimales exactos (sin float), invoca el puerto ScoreRiskPort y expone /metrics (risk_evaluations_total, risk_evaluation_duration_seconds).',
    role: 'Adaptador de entrada del segundo hexágono.', relation: 'Recibe la llamada de HttpRiskAssessmentAdapter.' },
  { id: 'pyPort', col: 'external', group: 'risk-engine (Python)', kind: 'port', name: 'ScoreRiskPort', label: 'Protocol',
    layer: 'Python · puerto de entrada', file: `${PY}application/ports.py`,
    responsibility: 'Puerto de entrada (typing.Protocol): execute(Evaluation) → RiskResult. HTTP y tests usan la misma capacidad.',
    role: 'Puerto de entrada.', relation: 'Lo usa el adaptador FastAPI.' },
  { id: 'pyUseCase', col: 'external', group: 'risk-engine (Python)', kind: 'application', name: 'ScoreRisk', label: 'Caso de uso',
    layer: 'Python · aplicación', file: `${PY}application/score_risk.py`,
    responsibility: 'Caso de uso sin estado ni E/S: delega en la política del dominio.',
    role: 'Implementa ScoreRiskPort.', relation: 'Calcula el puntaje que recibe Java.' },
  { id: 'pyDomain', col: 'external', group: 'risk-engine (Python)', kind: 'domain', name: 'domain/risk.py', label: 'assess()',
    layer: 'Python · dominio', file: `${PY}domain/risk.py`,
    responsibility: 'Política determinista y explicable: importe < 500 → 10; 500–999.99 → 40; ≥ 1000 → 75. Solo biblioteca estándar.',
    role: 'Dominio del motor de riesgo.', relation: 'Origen del puntaje.' },
  { id: 'postgres', col: 'external', kind: 'external', name: 'PostgreSQL 17', label: 'Tabla orders',
    layer: 'Infraestructura · base de datos', file: 'services/order-api/src/main/resources/db/migration/V1__create_orders.sql',
    responsibility: 'Persistencia. La migración Flyway crea la tabla orders con restricciones CHECK que repiten las invariantes (estado coherente con el puntaje). Volumen postgres_data.',
    role: 'Sistema externo detrás de OrderRepository.', relation: 'Guarda el pedido aprobado o en revisión.' },
  { id: 'prometheus', col: 'external', kind: 'external', name: 'Prometheus', label: 'Scrape cada 10 s',
    layer: 'Infraestructura · observabilidad', file: 'infra/prometheus/prometheus.yml',
    responsibility: 'Recoge /actuator/prometheus de Java y /metrics de Python; evalúa las reglas de alerta de infra/prometheus/alerts.yml. Grafana lo usa como datasource.',
    role: 'Consumidor de las métricas (pull).', relation: 'Observa el caso de uso desde fuera, sin acoplarlo.' },
];

// Dependencias del código fuente: [desde, hacia, etiqueta]. "desde" depende de "hacia".
const DEPENDENCIES = [
  ['controller', 'useCases', 'usa'],
  ['controller', 'draft', 'crea'],
  ['service', 'useCases', 'implementa'],
  ['service', 'riskPort', 'usa'],
  ['service', 'repoPort', 'usa'],
  ['service', 'telemetryPort', 'usa'],
  ['service', 'order', 'crea'],
  ['order', 'status', 'usa'],
  ['order', 'draft', 'valida con'],
  ['riskPort', 'draft', 'recibe'],
  ['repoPort', 'order', 'guarda'],
  ['telemetryPort', 'status', 'recibe'],
  ['httpRisk', 'riskPort', 'implementa'],
  ['jdbc', 'repoPort', 'implementa'],
  ['micrometer', 'telemetryPort', 'implementa'],
  ['config', 'service', 'instancia'],
  ['config', 'httpRisk', 'instancia'],
  ['config', 'jdbc', 'instancia'],
  ['config', 'micrometer', 'instancia'],
  ['pyAdapter', 'pyPort', 'usa'],
  ['pyUseCase', 'pyPort', 'implementa'],
  ['pyUseCase', 'pyDomain', 'usa'],
];

// Llamadas en tiempo de ejecución para POST /api/orders (no son dependencias de código).
const STEPS = [
  { text: 'El navegador envía <code>POST /api/orders</code> con customerId, amount y currency.', nodes: ['browser', 'controller'], edges: [['browser', 'controller', 'HTTP']] },
  { text: '<code>OrderController</code> construye <code>OrderDraft</code>: el dominio valida los datos (si son inválidos → 400).', nodes: ['controller', 'draft'], edges: [['controller', 'draft']], fail: 'validation' },
  { text: 'Llama al puerto de entrada <code>OrderUseCases.create()</code>, implementado por <code>OrderService</code>.', nodes: ['controller', 'useCases', 'service'], edges: [['controller', 'useCases'], ['useCases', 'service']] },
  { text: '<code>OrderService</code> pide el puntaje a <code>RiskAssessmentPort</code> sin saber que existe HTTP o Python.', nodes: ['service', 'riskPort'], edges: [['service', 'riskPort']] },
  { text: '<code>HttpRiskAssessmentAdapter</code> envía <code>POST http://risk-engine:8000/score</code> (si falla o tarda → 503, sin guardar).', nodes: ['riskPort', 'httpRisk', 'pyAdapter'], edges: [['riskPort', 'httpRisk'], ['httpRisk', 'pyAdapter', 'HTTP']], fail: 'risk' },
  { text: 'FastAPI valida el JSON y ejecuta <code>ScoreRisk</code> → <code>assess()</code>, que devuelve el puntaje.', nodes: ['pyAdapter', 'pyPort', 'pyUseCase', 'pyDomain'], edges: [['pyAdapter', 'pyPort'], ['pyPort', 'pyUseCase'], ['pyUseCase', 'pyDomain']] },
  { text: '<code>Order.create()</code>: <code>OrderStatus.fromRiskScore</code> decide APPROVED (&lt; 70) o REVIEW (≥ 70).', nodes: ['service', 'order', 'status'], edges: [['service', 'order'], ['order', 'status']] },
  { text: '<code>OrderRepository.save()</code> → <code>JdbcOrderRepository</code> ejecuta un INSERT atómico en PostgreSQL.', nodes: ['service', 'repoPort', 'jdbc', 'postgres'], edges: [['service', 'repoPort'], ['repoPort', 'jdbc'], ['jdbc', 'postgres', 'SQL']], fail: 'persistence' },
  { text: '<code>OrderTelemetry.orderCreated()</code> incrementa <code>orders_created_total{status}</code>.', nodes: ['service', 'telemetryPort', 'micrometer'], edges: [['service', 'telemetryPort'], ['telemetryPort', 'micrometer']] },
  { text: '<code>OrderController</code> responde <code>201 Created</code> con la cabecera Location y el pedido en JSON.', nodes: ['controller', 'browser'], edges: [['controller', 'browser', 'HTTP']] },
];

const MODE_HELP = {
  dependencies: 'Dependencias del código: cada flecha apunta a lo que el componente usa o implementa. En el núcleo todas apuntan hacia dentro; el dominio no depende de nada externo. Lo verifica ArchUnit (Java) y un test AST (Python).',
  execution: 'Flujo de ejecución de POST /api/orders: el orden real de las llamadas. Fíjate en que la llamada va del caso de uso hacia el adaptador, aunque la dependencia del código va del adaptador hacia el puerto (inversión de dependencias).',
};

let mode = 'dependencies';
let selected = null;
let running = false;
const byId = new Map(NODES.map((node) => [node.id, node]));

const nodeButton = (id) => $(`.node[data-node="${id}"]`);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const kindLabel = { external: 'Externo', config: 'Configuración', adapter: 'Adaptador', port: 'Puerto', application: 'Aplicación', domain: 'Dominio' };

function buildDiagram() {
  const groups = new Map();
  for (const node of NODES) {
    const column = $(`.arch__col[data-col="${node.col}"]`);
    const button = el('button', { type: 'button', class: 'node', dataset: { node: node.id, kind: node.kind },
      'aria-pressed': 'false', 'aria-label': `${node.name}, ${node.layer}` },
    el('span', { class: 'node__name', text: node.name.replace(/([a-z])([A-Z])/g, '$1\u200B$2').replace(/\//g, '/\u200B') }),
    el('span', { class: 'node__kind', text: node.label }));
    button.addEventListener('click', () => select(node.id));
    if (node.group) {
      const key = `${node.col}:${node.group}`;
      if (!groups.has(key)) {
        const group = el('div', { class: 'arch__group' }, el('span', { class: 'arch__group-title', text: node.group }));
        groups.set(key, group);
        column.append(group);
      }
      groups.get(key).append(button);
    } else {
      column.append(button);
    }
  }
}

function edgesForMode() {
  if (mode === 'dependencies') return DEPENDENCIES.map(([from, to, label]) => ({ from, to, label }));
  const seen = new Set();
  const list = [];
  for (const step of STEPS) {
    for (const [from, to, label] of step.edges) {
      const key = `${from}>${to}`;
      if (!seen.has(key)) { seen.add(key); list.push({ from, to, label }); }
    }
  }
  list.push({ from: 'prometheus', to: 'micrometer', label: 'scrape' });
  list.push({ from: 'prometheus', to: 'pyAdapter', label: 'scrape' });
  return list;
}

function anchor(rect, base, towardsRight) {
  return { x: (towardsRight ? rect.right : rect.left) - base.left, y: rect.top + rect.height / 2 - base.top };
}

function drawEdges(highlight = new Set(), activeEdges = new Set()) {
  const svg = $('#arch-edges');
  const diagram = $('#arch-diagram');
  if (!svg || getComputedStyle(svg).display === 'none') return;
  const base = diagram.getBoundingClientRect();
  svg.setAttribute('viewBox', `0 0 ${base.width} ${base.height}`);
  const ns = 'http://www.w3.org/2000/svg';
  svg.replaceChildren();
  const defs = document.createElementNS(ns, 'defs');
  for (const [id, color] of [['arrow', 'rgba(148,163,184,0.45)'], ['arrow-related', '#A6B8FF'], ['arrow-active', '#10B981']]) {
    const marker = document.createElementNS(ns, 'marker');
    marker.setAttribute('id', id);
    marker.setAttribute('viewBox', '0 0 10 10');
    marker.setAttribute('refX', '9');
    marker.setAttribute('refY', '5');
    marker.setAttribute('markerWidth', '7');
    marker.setAttribute('markerHeight', '7');
    marker.setAttribute('orient', 'auto-start-reverse');
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', 'M0,0 L10,5 L0,10 z');
    path.setAttribute('fill', color);
    marker.append(path);
    defs.append(marker);
  }
  svg.append(defs);
  for (const edge of edgesForMode()) {
    const a = nodeButton(edge.from);
    const b = nodeButton(edge.to);
    if (!a || !b) continue;
    const ra = a.getBoundingClientRect();
    const rb = b.getBoundingClientRect();
    const sameColumn = Math.abs(ra.left - rb.left) < 8;
    let p1;
    let p2;
    let d;
    if (sameColumn) {
      // Conexión dentro de una columna: arco por el lateral derecho.
      p1 = anchor(ra, base, true);
      p2 = anchor(rb, base, true);
      const bulge = 26 + Math.min(40, Math.abs(p2.y - p1.y) / 6);
      d = `M${p1.x},${p1.y} C${p1.x + bulge},${p1.y} ${p2.x + bulge},${p2.y} ${p2.x + 2},${p2.y}`;
    } else {
      const rightward = rb.left > ra.left;
      p1 = anchor(ra, base, rightward);
      p2 = anchor(rb, base, !rightward);
      const dx = Math.max(30, Math.abs(p2.x - p1.x) / 2);
      d = `M${p1.x},${p1.y} C${p1.x + (rightward ? dx : -dx)},${p1.y} ${p2.x + (rightward ? -dx : dx)},${p2.y} ${p2.x},${p2.y}`;
    }
    const key = `${edge.from}>${edge.to}`;
    const isActive = activeEdges.has(key);
    const isRelated = !isActive && (highlight.has(edge.from) && highlight.has(edge.to));
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', d);
    path.setAttribute('class', `edge${isActive ? ' edge--active' : isRelated ? ' edge--related' : ''}`);
    path.setAttribute('marker-end', `url(#${isActive ? 'arrow-active' : isRelated ? 'arrow-related' : 'arrow'})`);
    if (mode === 'execution' && !isActive) path.setAttribute('stroke-dasharray', '4 4');
    svg.append(path);
  }
}

function relationsOf(id) {
  const edges = edgesForMode();
  return {
    outgoing: edges.filter((edge) => edge.from === id),
    incoming: edges.filter((edge) => edge.to === id),
  };
}

function renderDetail(node) {
  const panel = $('#arch-detail');
  const { outgoing, incoming } = relationsOf(node.id);
  const verbOut = mode === 'dependencies' ? 'Depende de' : 'Llama a';
  const verbIn = mode === 'dependencies' ? 'Dependen de este componente' : 'Recibe llamadas de';
  const relationList = (edges, pick) => (edges.length
    ? el('ul', { class: 'detail__relations' }, edges.map((edge) => el('li', {},
      `${edge.label ? `${edge.label} · ` : ''}${byId.get(pick(edge)).name}`)))
    : el('p', { class: 'muted small', text: 'Ninguna en esta vista.' }));
  panel.replaceChildren(el('div', { class: 'detail' },
    el('div', { class: 'detail__layer' }, el('span', { class: `badge ${node.kind === 'domain' ? 'badge--ok' : node.kind === 'port' ? 'badge--warn' : 'badge--neutral'}`, text: kindLabel[node.kind] })),
    el('h3', { class: 'detail__name', text: node.name }),
    el('code', { class: 'detail__file', title: 'Archivo real en el repositorio', text: node.file }),
    el('dl', {},
      el('div', {}, el('dt', { text: 'Capa' }), el('dd', { text: node.layer })),
      el('div', {}, el('dt', { text: 'Responsabilidad' }), el('dd', { text: node.responsibility })),
      el('div', {}, el('dt', { text: 'Puerto o adaptador' }), el('dd', { text: node.role })),
      el('div', {}, el('dt', { text: 'Relación con el caso de uso' }), el('dd', { text: node.relation })),
      el('div', {}, el('dt', { text: verbOut }), el('dd', {}, relationList(outgoing, (edge) => edge.to))),
      el('div', {}, el('dt', { text: verbIn }), el('dd', {}, relationList(incoming, (edge) => edge.from))))));
}

function select(id) {
  if (running) return;
  selected = id;
  const node = byId.get(id);
  const { outgoing, incoming } = relationsOf(id);
  const related = new Set([id, ...outgoing.map((edge) => edge.to), ...incoming.map((edge) => edge.from)]);
  $$('.node').forEach((button) => {
    const nodeId = button.dataset.node;
    button.classList.toggle('is-selected', nodeId === id);
    button.classList.toggle('is-related', nodeId !== id && related.has(nodeId));
    button.classList.toggle('is-dimmed', !related.has(nodeId));
    button.setAttribute('aria-pressed', String(nodeId === id));
  });
  drawEdges(related);
  renderDetail(node);
}

function clearHighlights() {
  $$('.node').forEach((button) => button.classList.remove('is-selected', 'is-related', 'is-dimmed', 'is-active', 'is-failed'));
}

function setMode(next) {
  mode = next;
  $$('[data-arch-mode]').forEach((button) => {
    const isActive = button.dataset.archMode === next;
    button.classList.toggle('is-active', isActive);
    button.setAttribute('aria-pressed', String(isActive));
  });
  $('#arch-mode-help').textContent = MODE_HELP[next];
  if (selected) select(selected); else drawEdges();
}

// ---- Solicitud real ---------------------------------------------------------

function renderSteps() {
  $('#run-steps').replaceChildren(...STEPS.map((step) => {
    const item = el('li');
    item.innerHTML = step.text; // texto estático definido en este archivo, nunca datos del usuario o del API
    return item;
  }));
}

function evidenceRow(label, value) {
  return el('div', { class: 'evidence__row' }, el('span', { text: label }), el('strong', { text: value }));
}

/** Índice del último paso alcanzado y si falló, según el resultado REAL de la petición. */
function outcomeFor(error) {
  if (!error) return { reached: STEPS.length - 1, failed: false };
  if (!(error instanceof ApiError) || error.status === 0) return { reached: 0, failed: true };
  if (error.status === 400) return { reached: STEPS.findIndex((step) => step.fail === 'validation'), failed: true };
  if (error.status === 503 && /persist/i.test(error.title)) return { reached: STEPS.findIndex((step) => step.fail === 'persistence'), failed: true };
  if (error.status === 503) return { reached: STEPS.findIndex((step) => step.fail === 'risk'), failed: true };
  return { reached: 0, failed: true };
}

async function animateOutcome({ reached, failed }) {
  const items = $$('#run-steps li');
  const delay = reducedMotion() ? 0 : 420;
  const traversed = new Set();
  clearHighlights();
  for (let index = 0; index < STEPS.length; index += 1) {
    const item = items[index];
    if (index < reached || (index === reached && !failed)) {
      item.className = 'is-done';
      STEPS[index].nodes.forEach((id) => nodeButton(id)?.classList.add('is-active'));
      STEPS[index].edges.forEach(([from, to]) => traversed.add(`${from}>${to}`));
      drawEdges(new Set(), traversed);
    } else if (index === reached && failed) {
      item.className = 'is-failed';
      STEPS[index].nodes.forEach((id) => nodeButton(id)?.classList.add('is-failed'));
      nodeButton('handler')?.classList.add('is-failed');
    } else {
      item.className = 'is-skipped';
    }
    if (delay && index <= reached) await sleep(delay);
  }
}

async function runRequest() {
  if (running) return;
  running = true;
  const button = $('#run-request');
  const amount = $('#run-amount').value;
  const evidence = $('#run-evidence');
  button.disabled = true;
  if (mode !== 'execution') setMode('execution');
  renderSteps();
  const requestBody = { customerId: 'explorer_demo', amount: Number(amount), currency: 'USD' };
  evidence.replaceChildren(el('p', { class: 'muted small', text: 'Enviando solicitud real…' }));
  let response = null;
  let failure = null;
  try {
    response = await ordersApi.create(requestBody.customerId, amount);
  } catch (error) {
    failure = error;
  }

  const rows = [
    el('p', { class: 'muted small', text: 'Observado en esta ejecución (datos reales):' }),
    evidenceRow('Solicitud', 'POST /api/orders'),
    el('pre', { text: JSON.stringify(requestBody, null, 2) }),
  ];
  if (response) {
    const order = response.data;
    rows.push(evidenceRow('Estado HTTP', `${response.status} Created`));
    rows.push(evidenceRow('Latencia (navegador)', `${response.ms.toFixed(0)} ms`));
    rows.push(evidenceRow('Location', response.headers.get('Location') || '—'));
    rows.push(evidenceRow('Puntaje devuelto por Python', `${order.riskScore}/100`));
    rows.push(evidenceRow('Decisión del dominio Java', order.status));
    rows.push(el('pre', { text: JSON.stringify(order, null, 2) }));
    try {
      const reread = await ordersApi.get(order.id);
      const same = JSON.stringify(reread.data) === JSON.stringify(order);
      rows.push(evidenceRow('Relectura GET /api/orders/{id}', same ? 'idéntica: persistido en PostgreSQL' : 'difiere de la respuesta'));
    } catch (error) {
      rows.push(evidenceRow('Relectura GET /api/orders/{id}', `falló: ${error.message}`));
    }
    window.dispatchEvent(new CustomEvent('polymer:orders-changed'));
  } else {
    rows.push(evidenceRow('Estado HTTP', failure.status ? String(failure.status) : 'sin respuesta'));
    if (failure.title) rows.push(evidenceRow('Título (ProblemDetail)', failure.title));
    rows.push(evidenceRow('Detalle', failure.message));
    rows.push(el('p', { class: 'muted small', text: failure.status === 400 || failure.status === 503 ? 'No se guardó ningún pedido: el caso de uso se interrumpió antes del INSERT.' : '' }));
  }
  evidence.replaceChildren(...rows);
  await animateOutcome(outcomeFor(failure));
  if (selected) renderDetail(byId.get(selected)); // relaciones de la vista actual (ejecución)
  if (failure) toast(`Resultado real: ${failure.status || 'sin conexión'} · ${failure.message}`, failure.status === 400 ? 'warn' : 'error');
  button.disabled = false;
  running = false;
}

export function initArchitecture() {
  buildDiagram();
  renderSteps();
  $$('[data-arch-mode]').forEach((button) => button.addEventListener('click', () => {
    clearHighlights();
    setMode(button.dataset.archMode);
  }));
  $('#run-request').addEventListener('click', runRequest);
  setMode('dependencies');
  let timer = 0;
  const redraw = () => { clearTimeout(timer); timer = setTimeout(() => (selected ? select(selected) : drawEdges()), 100); };
  window.addEventListener('resize', redraw);
  // El diagrama puede cambiar de tamaño al cargar fuentes o al hacerse visible.
  if ('ResizeObserver' in window) new ResizeObserver(redraw).observe($('#arch-diagram'));
  select('service');
}
