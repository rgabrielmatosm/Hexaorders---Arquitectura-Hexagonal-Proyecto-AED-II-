// Carrito de la tienda. Visitantes: identificadores y cantidades en el navegador (localStorage).
// Clientes con sesión: carrito guardado en PostgreSQL (/api/shop/cart), que sobrevive a cierres de
// sesión y cambios de dispositivo; al iniciar sesión, el carrito del visitante se fusiona con él.
// Precios, stock, descuentos y totales los calcula siempre el servidor.

import { shopApi } from './api.js';
import { session } from './session.js';

const KEY = 'hexaorders.cart.v1';
let local = load();
let remote = null; // última vista del carrito del servidor (cliente con sesión)

function load() {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((i) => Number.isInteger(i.productId) && Number.isInteger(i.quantity) && i.quantity > 0) : [];
  } catch {
    return [];
  }
}

function persist() {
  try { localStorage.setItem(KEY, JSON.stringify(local)); } catch { /* almacenamiento no disponible */ }
}

function notify() { window.dispatchEvent(new CustomEvent('hexa:cart', { detail: cart.count() })); }

const isCustomer = () => session.has('CUSTOMER');

function applyRemote(view) {
  remote = view;
  notify();
  return view;
}

export const cart = {
  /** true cuando el carrito se guarda en el servidor (cliente con sesión). */
  isSaved: () => isCustomer(),
  items: () => (isCustomer() && remote
    ? remote.lines.map((l) => ({ productId: l.product.id, quantity: l.quantity }))
    : local.map((i) => ({ ...i }))),
  count: () => (isCustomer() && remote ? remote.lines.reduce((s, l) => s + l.quantity, 0) : local.reduce((s, i) => s + i.quantity, 0)),
  /** Vista del servidor (líneas con precio, problemas de stock) o null para visitantes. */
  saved: () => remote,

  /** Tras cargar la sesión: fusiona el carrito del visitante con el guardado y lo descarga. */
  async sync() {
    if (!isCustomer()) { remote = null; notify(); return null; }
    try {
      const view = local.length ? await shopApi.cartMerge(local) : await shopApi.cart();
      local = [];
      persist();
      return applyRemote(view);
    } catch {
      return null; // sin conexión: se intentará de nuevo en la siguiente página
    }
  },

  async add(productId, quantity = 1) {
    if (isCustomer()) return applyRemote(await shopApi.cartAdd(productId, quantity));
    const line = local.find((i) => i.productId === productId);
    if (line) line.quantity = Math.min(99, line.quantity + quantity);
    else local.push({ productId, quantity: Math.min(99, quantity) });
    persist();
    notify();
    return null;
  },

  async set(productId, quantity) {
    if (isCustomer()) {
      return applyRemote(quantity <= 0 ? await shopApi.cartRemove(productId) : await shopApi.cartSet(productId, quantity));
    }
    if (quantity <= 0) local = local.filter((i) => i.productId !== productId);
    else {
      const line = local.find((i) => i.productId === productId);
      if (line) line.quantity = Math.min(99, quantity);
    }
    persist();
    notify();
    return null;
  },

  async remove(productId) { return cart.set(productId, 0); },

  async clear() {
    if (isCustomer()) { applyRemote(await shopApi.cartClear()); return; }
    local = [];
    persist();
    notify();
  },

  /** Tras confirmar un pedido el servidor ya vació el carrito guardado. */
  forgetLocal() {
    local = [];
    persist();
    if (remote) remote = { ...remote, lines: [], units: 0 };
    notify();
  },
};
