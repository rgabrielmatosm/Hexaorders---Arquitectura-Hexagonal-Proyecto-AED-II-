// Componentes comerciales compartidos por la portada, el catálogo, las ofertas y la ficha de producto.
// Los precios, descuentos y la disponibilidad llegan calculados desde el servidor; aquí solo se muestran.

import { cart } from './cart.js';
import { errorMessage, icon, money, productArt } from './components.js';
import { link } from './router.js';
import { el, toast } from './ui.js';

const STATUS_BADGE = { NEW: ['Nuevo', 'new'], FEATURED: ['Destacado', 'featured'], BESTSELLER: ['Más vendido', 'best'], CLEARANCE: ['Liquidación', 'clearance'] };

/** Precio actual, precio anterior tachado y ahorro. */
export function priceBlock(product, { large = false } = {}) {
  return el('div', { class: `price${large ? ' price--large' : ''}` },
    product.previousPrice ? el('span', { class: 'price__old' }, el('span', { class: 'visually-hidden', text: 'Antes: ' }), money(product.previousPrice)) : null,
    el('span', { class: 'price__now' }, product.previousPrice ? el('span', { class: 'visually-hidden', text: 'Ahora: ' }) : null, money(product.price)),
    product.discountPercent > 0 ? el('span', { class: 'price__off', text: `-${product.discountPercent}%` }) : null);
}

export function availabilityNote(product) {
  if (product.stock <= 0) return el('span', { class: 'stock-note stock-note--out', text: 'Agotado' });
  if (product.stock <= 5) return el('span', { class: 'stock-note stock-note--low', text: `¡Últimas ${product.stock} unidades!` });
  return el('span', { class: 'stock-note stock-note--ok', text: 'En stock · envío en 24–72 h' });
}

export function badges(product) {
  const list = [];
  if (product.discountPercent > 0) list.push(el('span', { class: 'tag tag--off', text: `-${product.discountPercent}%` }));
  const status = STATUS_BADGE[product.status];
  if (status) list.push(el('span', { class: `tag tag--${status[1]}`, text: status[0] }));
  if (product.stock <= 0) list.push(el('span', { class: 'tag tag--out', text: 'Agotado' }));
  return list.length ? el('div', { class: 'tags' }, list) : null;
}

/** Agrega al carrito y avisa (si no hay stock suficiente el servidor responde 409 con el motivo). */
export async function addToCart(product, quantity, button) {
  if (button) button.disabled = true;
  try {
    await cart.add(product.id, quantity);
    toast(`${quantity > 1 ? `${quantity} × ` : ''}${product.name} se agregó al carrito`, 'ok', 2600);
    return true;
  } catch (error) {
    toast(errorMessage(error), 'warn');
    return false;
  } finally {
    if (button) button.disabled = product.stock <= 0;
  }
}

export function productCard(product) {
  const add = el('button', { type: 'button', class: 'btn btn--primary btn--sm', disabled: product.stock <= 0,
    'aria-label': product.stock <= 0 ? `${product.name} agotado` : `Agregar ${product.name} al carrito` },
  icon('cart'), el('span', { text: product.stock <= 0 ? 'Agotado' : 'Agregar' }));
  add.addEventListener('click', () => addToCart(product, 1, add));
  const href = link(`/producto/${product.id}`);
  const media = productArt(product, 'product-card__media');
  const tags = badges(product);
  if (tags) media.append(tags);
  return el('article', { class: 'product-card', dataset: { productId: String(product.id) } },
    el('a', { href, 'aria-label': product.name, tabindex: '-1', class: 'product-card__media-link' }, media),
    el('div', { class: 'product-card__body' },
      el('p', { class: 'product-card__brand', text: product.brand }),
      el('h3', { class: 'product-card__title' }, el('a', { href, text: product.name })),
      el('p', { class: 'product-card__specs', text: product.description }),
      priceBlock(product),
      product.promotionName ? el('p', { class: 'product-card__promo' }, icon('percent'), product.promotionName) : null,
      el('div', { class: 'product-card__foot' }, availabilityNote(product), add)));
}

/** Fila horizontal de productos con título y enlace "Ver todo". */
export function productRail(title, products, moreHref, lead = '') {
  if (!products.length) return null;
  return el('section', { class: 'rail' },
    el('div', { class: 'rail__head' }, el('div', {}, el('h2', { class: 'rail__title', text: title }), lead ? el('p', { class: 'muted small', text: lead }) : null),
      moreHref ? el('a', { class: 'rail__more', href: moreHref }, 'Ver todo', icon('chevron')) : null),
    el('div', { class: 'product-grid product-grid--rail' }, products.map(productCard)));
}

export const skeletonCards = (count) => el('div', { class: 'product-grid' },
  Array.from({ length: count }, () => el('div', { class: 'product-card product-card--skeleton' }, el('div', { class: 'skeleton skeleton--media' }),
    el('div', { class: 'skeleton skeleton--row' }), el('div', { class: 'skeleton skeleton--row' }))));
