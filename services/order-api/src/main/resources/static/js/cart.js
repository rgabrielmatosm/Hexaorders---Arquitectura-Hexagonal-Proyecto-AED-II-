// Carrito del portal: solo guarda identificadores y cantidades en el navegador. Los precios,
// el stock y el total los calcula siempre el backend (POST /api/shop/quote).

const KEY = 'hexaorders.cart.v1';
let items = load();

function load() {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || '[]');
    return Array.isArray(parsed) ? parsed.filter((i) => Number.isInteger(i.productId) && Number.isInteger(i.quantity) && i.quantity > 0) : [];
  } catch {
    return [];
  }
}

function save() {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { /* almacenamiento no disponible */ }
  window.dispatchEvent(new CustomEvent('hexa:cart', { detail: cart.count() }));
}

export const cart = {
  items: () => items.map((i) => ({ ...i })),
  count: () => items.reduce((sum, i) => sum + i.quantity, 0),
  add(productId, quantity = 1) {
    const line = items.find((i) => i.productId === productId);
    if (line) line.quantity = Math.min(99, line.quantity + quantity);
    else items.push({ productId, quantity: Math.min(99, quantity) });
    save();
  },
  set(productId, quantity) {
    if (quantity <= 0) items = items.filter((i) => i.productId !== productId);
    else {
      const line = items.find((i) => i.productId === productId);
      if (line) line.quantity = Math.min(99, quantity);
    }
    save();
  },
  remove(productId) { items = items.filter((i) => i.productId !== productId); save(); },
  clear() { items = []; save(); },
};
