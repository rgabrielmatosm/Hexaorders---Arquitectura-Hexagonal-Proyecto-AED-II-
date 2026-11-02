// Portal público: inicio, nosotros, contacto y páginas de error. Las cifras del inicio son
// consultas reales (catálogo de PostgreSQL y estadísticas de la API original).

import { catalogApi, contactApi, ordersApi } from '../api.js';
import { emptyState, errorMessage, field, formError, icon, skeletons, submitting } from '../components.js';
import { route } from '../router.js';
import { session, homeFor } from '../session.js';
import { el, formatInt } from '../ui.js';
import { productCard } from './shop.js';

const ROLE_CARDS = [
  ['user', 'Clientes', 'Catálogo, carrito, checkout de demostración y seguimiento de pedidos en tiempo real.', '/cliente/dashboard.html'],
  ['grid', 'Administración', 'Productos, inventario, usuarios y roles, reportes y auditoría completa.', '/admin/dashboard.html'],
  ['list', 'Operaciones', 'Revisión de riesgo, confirmación y preparación con reglas del dominio.', '/operaciones/dashboard.html'],
  ['truck', 'Logística', 'Calendario mensual, semanal y diario con detección de conflictos.', '/logistica/dashboard.html'],
  ['activity', 'Monitoreo', 'Salud de servicios, alertas e indicadores enlazados con Grafana.', '/monitoreo/dashboard.html'],
];

function hexDiagram() {
  // Ilustración propia: núcleo hexagonal con puertos y adaptadores alrededor (decorativa).
  const labels = [['REST', 0], ['PostgreSQL', 60], ['FastAPI', 120], ['Prometheus', 180], ['Seguridad', 240], ['Web', 300]];
  return el('div', { class: 'hero-visual', 'aria-hidden': 'true' },
    el('div', { class: 'hero-visual__core' }, el('span', { text: 'Dominio' }), el('small', { text: 'casos de uso' })),
    ...labels.map(([text, angle]) => {
      const node = el('span', { class: 'hero-visual__node', text });
      node.style.setProperty('--angle', `${angle}deg`);
      return node;
    }));
}

route('/inicio', async (root) => {
  const stats = el('div', { class: 'hero-stats' }, skeletons(3, 'skeleton--row'));
  const featured = el('div', { class: 'products' }, skeletons(4, 'skeleton--card'));
  const user = session.user();
  root.append(el('div', { class: 'home' },
    el('section', { class: 'hero hero--home' },
      el('div', { class: 'hero__copy' },
        el('p', { class: 'eyebrow', text: 'Order Management & Risk Assessment Platform' }),
        el('h1', { class: 'hero__title' }, 'Pedidos, riesgo y logística sobre una ', el('span', { class: 'gradient-text', text: 'arquitectura hexagonal real' }), '.'),
        el('p', { class: 'hero__lead', text: 'HexaOrders une un portal de compra, portales por rol y observabilidad completa. Cada pedido pasa por casos de uso en Java 21, consulta un motor de riesgo en Python, se guarda en PostgreSQL y queda medido en Prometheus y Grafana.' }),
        el('div', { class: 'hero__actions' },
          el('a', { class: 'btn btn--primary', href: '/catalogo.html', text: 'Explorar el catálogo' }),
          el('a', { class: 'btn btn--ghost', href: '/arquitectura/overview.html', text: 'Ver la arquitectura' }),
          user ? el('a', { class: 'btn btn--ghost', href: homeFor(user), text: 'Ir a mi área' }) : el('a', { class: 'btn btn--ghost', href: '/login.html', text: 'Acceso de equipos' })),
        stats),
      hexDiagram()),
    el('section', { class: 'home-section' },
      el('div', { class: 'feature-row' }, [
        ['shield', 'Reglas en el núcleo', 'Precios, stock, transiciones y permisos se deciden en el dominio y los casos de uso, no en la interfaz.'],
        ['bolt', 'Riesgo antes de guardar', 'El motor FastAPI evalúa cada pedido; si no responde, no se guarda nada (HTTP 503).'],
        ['layers', 'Datos que se conservan', 'Migraciones Flyway no destructivas y volúmenes persistentes de Docker.'],
        ['activity', 'Observabilidad completa', 'Métricas de negocio y técnicas, 7 dashboards y alertas aprovisionadas como código.'],
      ].map(([ic, title, text]) => el('div', { class: 'feature' }, el('div', { class: 'feature__icon' }, icon(ic)), el('strong', { text: title }), el('p', { text }))))),
    el('section', { class: 'home-section', 'aria-labelledby': 'featured-title' },
      el('div', { class: 'section-head' }, el('h2', { class: 'section-title', id: 'featured-title', text: 'Catálogo destacado' }),
        el('a', { class: 'btn btn--ghost btn--sm', href: '/catalogo.html', text: 'Ver todo el catálogo' })),
      featured),
    el('section', { class: 'home-section', 'aria-labelledby': 'how-title' },
      el('h2', { class: 'section-title', id: 'how-title', text: 'Cómo funciona un pedido' }),
      el('ol', { class: 'flow-steps' }, [
        ['Navegador', 'La página llama a la API con sesión y token CSRF.'],
        ['Adaptador REST', 'Spring traduce HTTP a un puerto de entrada.'],
        ['Caso de uso', 'Calcula el precio, valida stock y aplica el dominio.'],
        ['Motor de riesgo', 'Puerto de salida → HTTP → FastAPI devuelve 0–100.'],
        ['PostgreSQL', 'Una transacción: pedido, líneas, stock e historial.'],
        ['Prometheus y Grafana', 'Métricas del pedido, del riesgo y de la API.'],
      ].map(([title, text], i) => el('li', {}, el('span', { class: 'flow-steps__num', text: String(i + 1) }), el('strong', { text: title }), el('p', { text })))),
      el('p', {}, el('a', { href: '/arquitectura/flujo-pedidos.html', text: 'Ver el flujo completo y el ciclo de vida →' }))),
    el('section', { class: 'home-section', 'aria-labelledby': 'roles-title' },
      el('h2', { class: 'section-title', id: 'roles-title', text: 'Un portal para cada rol' }),
      el('div', { class: 'role-grid' }, ROLE_CARDS.map(([ic, title, text, href]) => el('a', { class: 'role-card', href },
        el('div', { class: 'feature__icon' }, icon(ic)), el('strong', { text: title }), el('p', { text }), el('span', { class: 'role-card__cta', text: 'Entrar →' }))))),
    el('section', { class: 'cta-band' },
      el('div', {}, el('h2', { text: 'Pruébalo con datos reales' }), el('p', { text: 'Crea una cuenta de cliente, compra un producto y sigue el pedido mientras operaciones y logística lo procesan.' })),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/registro.html', text: 'Crear cuenta' }),
        el('a', { class: 'btn btn--ghost', href: '/laboratorio.html', text: 'Abrir el laboratorio' })))));
  const [products, categories, statistics] = await Promise.allSettled([catalogApi.products(), catalogApi.categories(), ordersApi.statistics()]);
  const stat = (value, label) => el('div', {}, el('strong', { text: value }), el('span', { text: label }));
  stats.replaceChildren(
    stat(products.status === 'fulfilled' ? formatInt(products.value.length) : '—', 'productos activos'),
    stat(categories.status === 'fulfilled' ? formatInt(categories.value.length) : '—', 'categorías'),
    stat(statistics.status === 'fulfilled' ? formatInt(statistics.value.data.total) : '—', 'pedidos del laboratorio'));
  if (products.status === 'fulfilled') {
    const list = products.value.filter((p) => p.stock > 0).sort((a, b) => b.price - a.price).slice(0, 4);
    featured.replaceChildren(...(list.length ? list.map(productCard) : [emptyState('Catálogo vacío', 'No hay productos disponibles ahora.', 'box')]));
  } else {
    featured.replaceChildren(emptyState('No se pudo cargar el catálogo', errorMessage(products.reason)));
  }
}, { title: 'Inicio' });

route('/nosotros', async (root) => {
  root.append(el('section', { class: 'view view--narrow' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Sobre el proyecto' }),
      el('h1', { class: 'view__title', text: 'Nosotros' }),
      el('p', { class: 'section__lead', text: 'HexaOrders es un proyecto académico de Ingeniería de Sistemas (Algoritmos y Estructuras de Datos II) que demuestra, con una aplicación completa y verificable, cómo la arquitectura hexagonal mantiene las reglas de negocio independientes de la tecnología.' }))),
    el('div', { class: 'grid-2-even' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Misión' }),
        el('p', { text: 'Mostrar que una arquitectura bien separada permite crecer —de un laboratorio de pedidos a una plataforma con portales por rol— sin reescribir el núcleo ni perder lo que ya funcionaba.' })),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Cómo trabajamos' }),
        el('ul', { class: 'check-list' }, ['Conservar → ampliar → integrar → validar.', 'Ninguna funcionalidad, dashboard ni métrica se elimina para simplificar.',
          'Cada regla importante tiene una prueba automática.', 'No se presentan resultados que no se hayan ejecutado.'].map((t) => el('li', { text: t }))))),
    el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Roles de ingeniería que cubre el proyecto' }),
      el('div', { class: 'role-grid role-grid--compact' }, [
        ['hexagon', 'Arquitectura', 'Dominio, puertos, adaptadores y reglas ArchUnit.'], ['server', 'Backend', 'Java 21, Spring Boot 3.5 y Spring Security.'],
        ['bolt', 'Motor de riesgo', 'Python 3.12 y FastAPI con reglas deterministas (no es IA).'], ['layers', 'Datos', 'PostgreSQL 17 con migraciones Flyway.'],
        ['grid', 'Frontend', 'HTML, CSS y JavaScript modular sin frameworks.'], ['activity', 'SRE', 'Prometheus, Grafana, alertas y health checks.'],
        ['shield', 'Seguridad', 'RBAC, BCrypt, CSRF, CSP y auditoría.'], ['box', 'DevOps', 'Docker Compose, PowerShell para Windows y GitHub Actions.'],
      ].map(([ic, title, text]) => el('div', { class: 'role-card role-card--static' }, el('div', { class: 'feature__icon' }, icon(ic)), el('strong', { text: title }), el('p', { text }))))),
    el('div', { class: 'cta-band' }, el('div', {}, el('h2', { text: '¿Quieres ver cómo está construido?' }), el('p', { text: 'Recorre la arquitectura o escríbenos desde la página de contacto.' })),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/arquitectura/overview.html', text: 'Arquitectura' }),
        el('a', { class: 'btn btn--ghost', href: '/contacto.html', text: 'Contacto' })))));
}, { title: 'Nosotros' });

route('/contacto', async (root) => {
  const name = el('input', { autocomplete: 'name', maxlength: '80', required: true });
  const email = el('input', { type: 'email', autocomplete: 'email', maxlength: '254', required: true });
  const topic = el('select', {}, [['DEMO', 'Solicitar una demostración'], ['SUPPORT', 'Soporte técnico'], ['ACADEMIC', 'Consulta académica'], ['OTHER', 'Otro tema']]
    .map(([value, text]) => el('option', { value, text })));
  const message = el('textarea', { maxlength: '2000', rows: '6', required: true });
  const user = session.user();
  if (user) name.value = user.displayName;
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary', text: 'Enviar mensaje' });
  const result = el('div', { 'aria-live': 'polite' });
  const form = el('form', { class: 'card form', novalidate: true }, el('h2', { class: 'card__title', text: 'Escríbenos' }),
    el('div', { class: 'form-grid' }, field('Nombre', name), field('Correo electrónico', email), (() => { const f = field('Tema', topic); f.classList.add('field--full'); return f; })(),
      (() => { const f = field('Mensaje', message, 'Entre 10 y 2000 caracteres.'); f.classList.add('field--full'); return f; })()),
    submit, errorNode, result);
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const receipt = await submitting(submit, errorNode, () => contactApi.send({ name: name.value, email: email.value, topic: topic.value, message: message.value }), 'Mensaje enviado');
      form.reset();
      if (user) name.value = user.displayName;
      result.replaceChildren(el('div', { class: 'notice-box notice-box--ok' }, el('strong', { text: 'Recibido. ' }),
        `Tu mensaje quedó registrado con el número ${receipt.id}. El equipo de administración lo verá en su panel.`));
    } catch { /* el error ya se muestra en el formulario */ }
  });
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Contacto' }), el('h1', { class: 'view__title', text: 'Hablemos' }),
      el('p', { class: 'section__lead', text: 'Solicita una demostración guiada, reporta un problema o pregunta por el proyecto. Los mensajes se guardan en PostgreSQL y los lee el rol de administración.' }))),
    el('div', { class: 'contact-layout' }, form,
      el('aside', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Repositorio' }),
          el('p', { class: 'muted small', text: 'Código, documentación, ADR y guía para Windows con Docker Desktop.' }),
          el('a', { class: 'btn btn--ghost btn--sm', href: 'https://github.com/rgabrielmatosm/Hexaorders---Arquitectura-Hexagonal-Proyecto-AED-II-', target: '_blank', rel: 'noopener', text: 'Abrir en GitHub ↗' })),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Privacidad' }),
          el('p', { class: 'muted small', text: 'Solo guardamos lo que escribes en este formulario. Tu correo no aparece en métricas ni en la auditoría.' }))))));
}, { title: 'Contacto' });

function errorPage(code, title, text) {
  return async (root) => {
    const user = session.user();
    root.append(el('section', { class: 'view view--narrow' }, el('div', { class: 'card error-page' },
      el('p', { class: 'error-page__code gradient-text', text: code }), el('h1', { text: title }), el('p', { class: 'muted', text }),
      el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary', href: '/', text: 'Ir al inicio' }),
        el('a', { class: 'btn btn--ghost', href: '/catalogo.html', text: 'Catálogo' }),
        user ? el('a', { class: 'btn btn--ghost', href: homeFor(user), text: 'Mi área' }) : el('a', { class: 'btn btn--ghost', href: '/login.html', text: 'Iniciar sesión' })))));
  };
}

route('/error/403', errorPage('403', 'Acceso restringido', 'Tu rol no tiene permiso para abrir esta página. El servidor lo comprueba en cada petición, no solo en los menús.'), { title: 'Acceso restringido' });
route('/error/404', errorPage('404', 'Página no encontrada', 'La dirección no existe o ha cambiado. Las URLs antiguas con #/ se redirigen automáticamente a su página nueva.'), { title: 'Página no encontrada' });
