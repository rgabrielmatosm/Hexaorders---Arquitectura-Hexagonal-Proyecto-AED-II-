// Portal del cliente (CUSTOMER): resumen personal y seguimiento de un pedido. Todo procede de
// PostgreSQL a través de /api/shop (el servidor solo devuelve los pedidos del propio cliente).

import { shopApi } from '../api.js';
import { cart } from '../cart.js';
import { CUSTOMER_LIFECYCLE, HAPPY_PATH, customerBadge, emptyState, kpi, money, orderRef, progressBar, skeletons } from '../components.js';
import { link, route } from '../router.js';
import { session } from '../session.js';
import { el, formatDateTime, formatRelative, toast } from '../ui.js';

const ACTIVE = ['CREATED', 'RISK_APPROVED', 'RISK_REVIEW', 'CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH', 'IN_TRANSIT'];

route('/cliente', async (root) => {
  const kpis = el('div', { class: 'kpis' }, skeletons(4, 'skeleton--row'));
  const active = el('div', {}, skeletons(3, 'skeleton--row'));
  const recent = el('div', {}, skeletons(3, 'skeleton--row'));
  const user = session.user();
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Mi cuenta' }),
      el('h1', { class: 'view__title', text: `Hola, ${user.displayName.split(' ')[0]}` }),
      el('p', { class: 'section__lead', text: 'Resumen de tus compras y del estado de tus pedidos.' })),
    el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary btn--sm', href: '/catalogo.html', text: 'Seguir comprando' }),
      cart.count() ? el('a', { class: 'btn btn--ghost btn--sm', href: '/carrito.html', text: `Carrito (${cart.count()})` }) : null)),
    kpis,
    el('div', { class: 'grid-2 card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos en curso' }), active),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Actividad reciente' }), recent))));
  const orders = await shopApi.orders();
  const inProgress = orders.filter((o) => ACTIVE.includes(o.status));
  // La tienda vende en soles; los pedidos anteriores en USD no se suman a este total.
  const spent = orders.filter((o) => !['CANCELLED', 'REJECTED'].includes(o.status) && o.currency === 'PEN').reduce((s, o) => s + Number(o.amount), 0);
  kpis.replaceChildren(kpi('Pedidos realizados', orders.length), kpi('En curso', inProgress.length, inProgress.length ? 'violet' : ''),
    kpi('Entregados', orders.filter((o) => o.status === 'DELIVERED').length, 'ok'), kpi('Total comprado', money(spent), 'blue'));
  active.replaceChildren(inProgress.length ? el('ul', { class: 'order-cards' }, inProgress.slice(0, 5).map((o) => el('li', {},
    el('div', { class: 'order-card__top' }, el('strong', { text: orderRef(o) }), customerBadge(o.status)),
    progressBar(o.status),
    el('div', { class: 'order-card__foot' }, el('span', { class: 'muted small', text: `${money(o.amount, o.currency)} · ${formatRelative(o.createdAt)}` }),
      el('a', { class: 'btn btn--ghost btn--sm', href: link(`/seguimiento/${o.id}`), text: 'Seguimiento' })))))
    : emptyState('Nada en curso', 'Cuando compres algo, verás aquí su progreso.', 'box', el('a', { class: 'btn btn--primary btn--sm', href: '/catalogo.html', text: 'Explorar catálogo' })));
  recent.replaceChildren(orders.length ? el('ul', { class: 'activity' }, orders.slice(0, 6).map((o) => el('li', {},
    el('a', { href: link(`/mis-pedidos/${o.id}`), text: orderRef(o) }), ' ', customerBadge(o.status),
    el('small', { text: `${formatDateTime(o.createdAt)} · ${o.lines.reduce((s, l) => s + l.quantity, 0)} producto(s) · ${money(o.amount, o.currency)}` }))))
    : emptyState('Sin actividad', 'Todavía no tienes pedidos.', 'empty'));
}, { roles: ['CUSTOMER'], title: 'Mi cuenta' });

route('/seguimiento/:id', async (root, { id, query }) => {
  const details = await shopApi.order(id);
  const { order, history, delivery, shipping } = details;
  const isNew = query.get('nuevo') === '1';
  if (isNew) {
    toast(`¡Gracias por tu compra! Tu número de pedido es ${orderRef(order)}`, 'ok', 6000);
    window.history.replaceState(null, '', `${window.location.pathname}?id=${encodeURIComponent(id)}`);
  }
  const reached = new Set(history.map((h) => h.to));
  const failed = ['REJECTED', 'CANCELLED'].includes(order.status);
  const steps = HAPPY_PATH.map((status) => {
    const change = history.find((h) => h.to === status);
    const cls = reached.has(status) ? 'is-done' : '';
    return el('li', { class: `${cls}${order.status === status ? ' is-current' : ''}` },
      el('span', { class: 'tracker__dot', 'aria-hidden': 'true' }),
      el('strong', { text: CUSTOMER_LIFECYCLE[status] }),
      el('small', { text: change ? formatDateTime(change.at) : 'Pendiente' }));
  });
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Seguimiento' }),
      el('h1', { class: 'view__title', text: orderRef(order) }),
      el('p', { class: 'section__lead', text: `Estado actual: ${CUSTOMER_LIFECYCLE[order.status] || order.status} · actualizado ${formatRelative(history.length ? history[history.length - 1].at : order.createdAt)}` })),
    el('div', { class: 'row-actions' }, customerBadge(order.status), el('a', { class: 'btn btn--ghost btn--sm', href: link(`/mis-pedidos/${order.id}`), text: 'Detalle y productos' }))),
    isNew ? el('div', { class: 'notice-box notice-box--ok order-thanks' }, el('strong', { text: '¡Gracias por tu compra! ' }),
      `Registramos tu pedido ${orderRef(order)} por ${money(order.amount, order.currency)}. Te enviamos la confirmación por correo y aquí puedes seguir cada paso.`) : null,
    failed ? el('div', { class: 'notice-box notice-box--error' }, el('strong', { text: `${CUSTOMER_LIFECYCLE[order.status]}. ` }),
      order.status === 'REJECTED' ? 'No pudimos aprobar este pedido tras la verificación. No se realizó ningún cobro; escríbenos si necesitas ayuda.'
        : 'El pedido fue cancelado. No se realizó ningún cobro.') : null,
    order.status === 'RISK_REVIEW' ? el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: 'Estamos verificando tu pedido. ' }),
      'Por seguridad revisamos algunas compras antes de prepararlas. Te avisaremos por correo en cuanto se confirme.') : null,
    el('div', { class: 'grid-2' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Progreso' }),
        el('ol', { class: `tracker${failed ? ' is-failed' : ''}` }, steps)),
      el('div', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Entrega' }),
          delivery ? el('div', { class: 'notice-box' }, el('strong', { text: 'Programada: ' }),
            `${formatDateTime(delivery.start)} – ${new Date(delivery.end).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}`)
            : el('p', { class: 'muted small', text: 'Programaremos la entrega cuando el pedido esté confirmado.' }),
          shipping ? el('address', { class: 'address-card' }, el('strong', { text: shipping.recipient }), el('span', { text: shipping.address }), el('span', { text: shipping.city }))
            : null),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Historial completo' }),
          el('ol', { class: 'timeline' }, history.map((h, index) => el('li', { class: index === history.length - 1 ? 'is-current' : '' },
            el('strong', {}, customerBadge(h.to)), el('small', { text: formatDateTime(h.at) })))))))));
}, { roles: ['CUSTOMER'], title: 'Seguimiento' });
