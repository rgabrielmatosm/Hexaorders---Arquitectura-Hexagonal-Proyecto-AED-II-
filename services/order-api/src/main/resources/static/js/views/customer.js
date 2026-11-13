// Portal del cliente (CUSTOMER): resumen personal y seguimiento de un pedido. Todo procede de
// PostgreSQL a través de /api/shop (el servidor solo devuelve los pedidos del propio cliente).

import { shopApi } from '../api.js';
import { play } from '../audio.js';
import { cart } from '../cart.js';
import { CUSTOMER_LIFECYCLE, HAPPY_PATH, customerBadge, emptyState, kpi, money, orderRef, progressBar, skeletons } from '../components.js';
import { animatedCheck } from '../motion.js';
import { link, route } from '../router.js';
import { session } from '../session.js';
import { el, formatDateTime, formatRelative, toast } from '../ui.js';
import { paymentBox } from './shop.js';
import { locale, t } from '../i18n.js';

const ACTIVE = ['CREATED', 'RISK_APPROVED', 'RISK_REVIEW', 'CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH', 'IN_TRANSIT'];

route('/cliente', async (root) => {
  const kpis = el('div', { class: 'kpis' }, skeletons(4, 'skeleton--row'));
  const active = el('div', {}, skeletons(3, 'skeleton--row'));
  const recent = el('div', {}, skeletons(3, 'skeleton--row'));
  const user = session.user();
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: t('Mi cuenta') }),
      el('h1', { class: 'view__title', text: t('Hola, {0}', user.displayName.split(' ')[0]) }),
      el('p', { class: 'section__lead', text: t('Resumen de tus compras y del estado de tus pedidos.') })),
    el('div', { class: 'row-actions' }, el('a', { class: 'btn btn--primary btn--sm', href: '/catalogo.html', text: t('Seguir comprando') }),
      cart.count() ? el('a', { class: 'btn btn--ghost btn--sm', href: '/carrito.html', text: t('Carrito ({0})', cart.count()) }) : null)),
    kpis,
    el('div', { class: 'grid-2 card--spaced' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Pedidos en curso') }), active),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Actividad reciente') }), recent))));
  const orders = await shopApi.orders();
  const inProgress = orders.filter((o) => ACTIVE.includes(o.status));
  // La tienda vende en soles; los pedidos anteriores en USD no se suman a este total.
  const spent = orders.filter((o) => !['CANCELLED', 'REJECTED'].includes(o.status) && o.currency === 'PEN').reduce((s, o) => s + Number(o.amount), 0);
  kpis.replaceChildren(kpi(t('Pedidos realizados'), orders.length), kpi(t('En curso'), inProgress.length, inProgress.length ? 'violet' : ''),
    kpi(t('Entregados'), orders.filter((o) => o.status === 'DELIVERED').length, 'ok'), kpi(t('Total comprado'), money(spent), 'blue'));
  active.replaceChildren(inProgress.length ? el('ul', { class: 'order-cards' }, inProgress.slice(0, 5).map((o) => el('li', {},
    el('div', { class: 'order-card__top' }, el('strong', { text: orderRef(o) }), customerBadge(o.status)),
    progressBar(o.status),
    el('div', { class: 'order-card__foot' }, el('span', { class: 'muted small', text: `${money(o.amount, o.currency)} · ${formatRelative(o.createdAt)}` }),
      el('a', { class: 'btn btn--ghost btn--sm', href: link(`/seguimiento/${o.id}`), text: t('Seguimiento') })))))
    : emptyState(t('Nada en curso'), t('Cuando compres algo, verás aquí su progreso.'), 'box', el('a', { class: 'btn btn--primary btn--sm', href: '/catalogo.html', text: t('Explorar catálogo') })));
  recent.replaceChildren(orders.length ? el('ul', { class: 'activity' }, orders.slice(0, 6).map((o) => el('li', {},
    el('a', { href: link(`/mis-pedidos/${o.id}`), text: orderRef(o) }), ' ', customerBadge(o.status),
    el('small', { text: t('{0} · {1} producto(s) · {2}', formatDateTime(o.createdAt), o.lines.reduce((s, l) => s + l.quantity, 0), money(o.amount, o.currency)) }))))
    : emptyState(t('Sin actividad'), t('Todavía no tienes pedidos.'), 'empty'));
}, { roles: ['CUSTOMER'], title: t('Mi cuenta') });

route('/seguimiento/:id', async (root, { id, query }) => {
  const details = await shopApi.order(id);
  const { order, history, delivery, shipping } = details;
  const isNew = query.get('nuevo') === '1';
  if (isNew) {
    toast(t('¡Gracias por tu compra! Tu número de pedido es {0}', orderRef(order)), 'ok', 6000);
    play('confirm'); // solo si la persona activó los sonidos
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
      el('small', { text: change ? formatDateTime(change.at) : t('Pendiente') }));
  });
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: t('Seguimiento') }),
      el('h1', { class: 'view__title', text: orderRef(order) }),
      el('p', { class: 'section__lead', text: t('Estado actual: {0} · actualizado {1}', CUSTOMER_LIFECYCLE[order.status] || order.status, formatRelative(history.length ? history[history.length - 1].at : order.createdAt)) })),
    el('div', { class: 'row-actions' }, customerBadge(order.status), el('a', { class: 'btn btn--ghost btn--sm', href: link(`/mis-pedidos/${order.id}`), text: t('Detalle y productos') }))),
    isNew ? el('div', { class: 'order-confirmed order-thanks', role: 'status' }, animatedCheck(),
      el('div', {}, el('h2', { text: t('¡Gracias por tu compra!') }),
        el('p', {}, t('Registramos tu pedido '), el('strong', { class: 'order-confirmed__ref', text: orderRef(order) }),
          t(' por {0}. Te enviamos la confirmación por correo y aquí puedes seguir cada paso.', money(order.amount, order.currency))))) : null,
    failed ? el('div', { class: 'notice-box notice-box--error' }, el('strong', { text: `${CUSTOMER_LIFECYCLE[order.status]}. ` }),
      order.status === 'REJECTED' ? t('No pudimos aprobar este pedido tras la verificación. No se realizó ningún cobro; escríbenos si necesitas ayuda.')
        : t('El pedido fue cancelado. No se realizó ningún cobro.')) : null,
    paymentBox(order),
    order.status === 'RISK_REVIEW' ? el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: t('Estamos verificando tu pedido. ') }),
      t('Por seguridad revisamos algunas compras antes de prepararlas. Te avisaremos por correo en cuanto se confirme.')) : null,
    el('div', { class: 'grid-2' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Progreso') }),
        el('ol', { class: `tracker${failed ? ' is-failed' : ''}` }, steps)),
      el('div', { class: 'stack' },
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Entrega') }),
          delivery ? el('div', { class: 'notice-box' }, el('strong', { text: t('Programada: ') }),
            `${formatDateTime(delivery.start)} – ${new Date(delivery.end).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}`)
            : el('p', { class: 'muted small', text: t('Programaremos la entrega cuando el pedido esté confirmado.') }),
          shipping ? el('address', { class: 'address-card' }, el('strong', { text: shipping.recipient }), el('span', { text: shipping.address }), el('span', { text: shipping.city }))
            : null),
        el('article', { class: 'card' }, el('h3', { class: 'card__title', text: t('Historial completo') }),
          el('ol', { class: 'timeline' }, history.map((h, index) => el('li', { class: index === history.length - 1 ? 'is-current' : '' },
            el('strong', {}, customerBadge(h.to)), el('small', { text: formatDateTime(h.at) })))))))));
}, { roles: ['CUSTOMER'], title: t('Seguimiento') });
