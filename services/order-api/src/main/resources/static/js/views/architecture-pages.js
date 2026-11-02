// Páginas de arquitectura: visión general (con el explorador interactivo real del laboratorio) y
// flujo de pedidos (secuencia del checkout y máquina de estados del ciclo de vida).

import { ordersApi } from '../api.js';
import { initArchitecture } from '../architecture.js';
import { LIFECYCLE, icon, lifecycleBadge } from '../components.js';
import { route } from '../router.js';
import { el, formatInt } from '../ui.js';

/**
 * Copia para documentación de domain/fulfillment/TransitionPolicy.java (el servidor es la autoridad).
 * tools/check_frontend.py falla si esta tabla deja de coincidir con la del dominio.
 */
export const TRANSITIONS = [
  ['CREATED', 'RISK_APPROVED', ['SYSTEM']],
  ['CREATED', 'RISK_REVIEW', ['SYSTEM']],
  ['RISK_APPROVED', 'CONFIRMED', ['ORDER_OPERATOR', 'ADMIN']],
  ['RISK_APPROVED', 'CANCELLED', ['CUSTOMER', 'ORDER_OPERATOR', 'ADMIN']],
  ['RISK_REVIEW', 'CONFIRMED', ['ORDER_OPERATOR', 'ADMIN']],
  ['RISK_REVIEW', 'REJECTED', ['ORDER_OPERATOR', 'ADMIN']],
  ['RISK_REVIEW', 'CANCELLED', ['CUSTOMER', 'ADMIN']],
  ['CONFIRMED', 'PREPARING', ['ORDER_OPERATOR', 'ADMIN']],
  ['CONFIRMED', 'CANCELLED', ['CUSTOMER', 'ORDER_OPERATOR', 'ADMIN']],
  ['PREPARING', 'READY_FOR_DISPATCH', ['ORDER_OPERATOR', 'ADMIN']],
  ['PREPARING', 'CANCELLED', ['ORDER_OPERATOR', 'ADMIN']],
  ['READY_FOR_DISPATCH', 'IN_TRANSIT', ['LOGISTICS_OPERATOR', 'ADMIN']],
  ['IN_TRANSIT', 'DELIVERED', ['LOGISTICS_OPERATOR', 'ADMIN']],
];

const LAYERS = [
  ['Dominio', 'hexagon', 'Reglas puras sin frameworks: Order, CommerceOrder, TransitionPolicy, ShippingAddress, Delivery, Incident, UserAccount.', 'domain/'],
  ['Aplicación', 'layers', 'Casos de uso y puertos: CommerceService, OperationsService, LogisticsService, MonitoringService, ContactService…', 'application/'],
  ['Adaptadores de entrada', 'server', 'Controladores REST y Spring Security: ShopController, OperationsController, LogisticsController, OpsController…', 'adapter/in/web/'],
  ['Adaptadores de salida', 'box', 'JDBC (PostgreSQL), HTTP al motor de riesgo, Prometheus HTTP API, Micrometer y BCrypt.', 'adapter/out/'],
];

route('/arquitectura/overview', async (root) => {
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Arquitectura' }),
      el('h1', { class: 'view__title', text: 'Arquitectura hexagonal' }),
      el('p', { class: 'section__lead', text: 'Las dependencias del código apuntan hacia el núcleo. El dominio no conoce Spring, HTTP, PostgreSQL ni Prometheus; ArchUnit (Java) y un análisis AST (Python) lo comprueban en cada build.' }))),
    el('div', { class: 'layer-grid' }, LAYERS.map(([title, ic, text, path]) => el('article', { class: 'card layer-card' },
      el('div', { class: 'feature__icon' }, icon(ic)), el('strong', { text: title }), el('p', { class: 'muted small', text }), el('code', { text: path })))),
    el('div', { class: 'notice-box card--spaced' }, el('strong', { text: 'Regla de dependencias: ' }),
      'adaptador de entrada → puerto de entrada → caso de uso → dominio; el caso de uso usa puertos de salida que implementan los adaptadores de salida. Ninguna página HTML habla con PostgreSQL: todo pasa por la API.')));
  // El explorador interactivo es el mismo componente del laboratorio, con clases y archivos reales.
  if (document.getElementById('arch-diagram')) initArchitecture();
}, { title: 'Arquitectura' });

route('/arquitectura/flujo-pedidos', async (root) => {
  const stats = el('div', { class: 'kpis' });
  const byFrom = new Map();
  TRANSITIONS.forEach(([from, to, actors]) => { if (!byFrom.has(from)) byFrom.set(from, []); byFrom.get(from).push([to, actors]); });
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Arquitectura' }),
      el('h1', { class: 'view__title', text: 'Flujo de pedidos' }),
      el('p', { class: 'section__lead', text: 'Secuencia del checkout, integración Java → Python y ciclo de vida completo del pedido.' }))),
    el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Secuencia del checkout (POST /api/shop/orders)' }),
      el('ol', { class: 'sequence' }, [
        ['Navegador → ShopController', 'Sesión, cabecera X-CSRF-TOKEN e Idempotency-Key. Spring Security exige el rol CUSTOMER.'],
        ['ShopController → CommerceUseCases', 'El adaptador traduce JSON a CartItem y ShippingAddress (validados en el dominio).'],
        ['CommerceService → CommerceOrderRepository', '¿Clave ya usada? Devuelve el pedido original (200 + Idempotent-Replay).'],
        ['CommerceService → CatalogRepository', 'Precios y stock del catálogo: el total se calcula en el servidor (Quote).'],
        ['CommerceService → RiskAssessmentPort → FastAPI', 'POST /score con timeouts, antes de abrir la transacción. Si falla: 503 y nada se guarda.'],
        ['CommerceService → PostgreSQL (una transacción)', 'Pedido + líneas + dirección + stock condicional (stock ≥ cantidad) + historial + auditoría.'],
        ['Métricas', 'orders_created, hexaorders_shop_orders_placed y la latencia HTTP llegan a Prometheus y Grafana.'],
      ].map(([title, text], i) => el('li', {}, el('span', { class: 'flow-steps__num', text: String(i + 1) }), el('div', {}, el('strong', { text: title }), el('p', { class: 'muted small', text })))))),
    el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Ciclo de vida y quién puede ejecutar cada transición' }),
      el('div', { class: 'lifecycle-map' }, Object.keys(LIFECYCLE).map((status) => el('div', { class: 'lifecycle-map__row' },
        el('div', { class: 'lifecycle-map__from' }, lifecycleBadge(status)),
        el('div', { class: 'lifecycle-map__to' }, (byFrom.get(status) || []).length
          ? (byFrom.get(status) || []).map(([to, actors]) => el('span', { class: 'lifecycle-map__edge' }, '→ ', lifecycleBadge(to), el('small', { text: ` ${actors.join(', ')}` })))
          : el('small', { class: 'muted', text: 'Estado final' })))))),
    el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Datos reales de la API original (laboratorio)' }), stats,
      el('p', { class: 'muted small', text: 'GET /api/orders/statistics es público y conserva su contrato de la versión 1.' }))));
  try {
    const { data } = await ordersApi.statistics();
    stats.replaceChildren(...[['Pedidos', data.total, ''], ['Aprobados', data.approved, 'ok'], ['En revisión', data.review, 'warn']]
      .map(([label, value, tone]) => el('div', { class: `kpi${tone ? ` kpi--${tone}` : ''}` }, el('span', { text: label }), el('strong', { text: formatInt(value) }))));
  } catch (error) {
    stats.replaceChildren(el('p', { class: 'muted small', text: error.message }));
  }
}, { title: 'Flujo de pedidos' });
