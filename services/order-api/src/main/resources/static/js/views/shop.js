// Tienda: catálogo con filtros, ficha de producto, carrito, checkout, mis pedidos, perfil y acceso.
// Precios, descuentos, stock, totales, permisos y estados los decide siempre el servidor.

import { ApiError, accountApi, authApi, catalogApi, shopApi } from '../api.js';
import { cart } from '../cart.js';
import {
  customerBadge, emptyState, errorMessage, field, formError, icon, modal, money, orderRef, paginated, productArt,
  progressBar, showFormError, skeletons, submitting,
} from '../components.js';
import { categoryTree, setBreadcrumbs, storeCategories } from '../layout.js';
import { link, rerender, route } from '../router.js';
import { ROLE_LABELS, homeFor, session } from '../session.js';
import { $$, el, formatDateTime, formatRelative, toast } from '../ui.js';
import { newPasswordFields, revealToggle } from '../password-strength.js';
import { addToCart, availabilityNote, badges, priceBlock, productCard, productRail, skeletonCards } from '../store-ui.js';
import { lang, locale, localizeHref, localized, t, td } from '../i18n.js';

const view = (...children) => el('section', { class: 'view view--store' }, ...children);
const head = (eyebrow, title, lead, ...actions) => el('div', { class: 'view__head' },
  el('div', {}, el('p', { class: 'eyebrow', text: eyebrow }), el('h1', { class: 'view__title', text: title }),
    lead ? el('p', { class: 'section__lead', text: lead }) : null),
  actions.filter(Boolean).length ? el('div', { class: 'row-actions' }, actions) : null);

export const DELIVERY_LABELS = { STANDARD: t('Envío estándar'), EXPRESS: t('Envío express'), PICKUP: t('Recojo en tienda') };
export const PAYMENT_LABELS = { CASH_ON_DELIVERY: t('Pago contra entrega'), BANK_TRANSFER: t('Transferencia bancaria'), PAY_IN_STORE: t('Pago en tienda') };
export { productCard };

// ---- Catálogo -------------------------------------------------------------------------------------
const SORTS = [['relevance', t('Más relevantes')], ['price_asc', t('Precio: menor a mayor')], ['price_desc', t('Precio: mayor a menor')],
  ['name', t('Nombre (A-Z)')], ['newest', t('Novedades')], ['popular', t('Más vendidos')], ['discount', t('Mayor descuento')]];
const STATUS_FILTERS = { NEW: t('Nuevos'), FEATURED: t('Destacados'), BESTSELLER: t('Más vendidos'), CLEARANCE: t('Liquidación') };
const PAGE_SIZE = 24;

/** Estado del catálogo ↔ URL (se puede compartir y recargar). */
function readState(query, categories) {
  const value = query.get('categoria');
  const category = value ? categories.find((c) => c.slug === value || String(c.id) === value) || null : null;
  const attrs = {};
  for (const [key, val] of query.entries()) if (key.startsWith('attr.')) (attrs[key.slice(5)] ||= []).push(val);
  return {
    q: query.get('q') || '', category, brands: query.getAll('marca'), min: query.get('min') || '', max: query.get('max') || '',
    available: query.get('disponible') === '1', offers: query.get('ofertas') === '1', status: query.get('estado') || '',
    sort: query.get('orden') || 'relevance', page: Math.max(1, Number(query.get('pagina')) || 1), attrs,
  };
}

function writeState(state) {
  const params = new URLSearchParams();
  if (state.q) params.set('q', state.q);
  if (state.category) params.set('categoria', state.category.slug);
  state.brands.forEach((b) => params.append('marca', b));
  if (state.min) params.set('min', state.min);
  if (state.max) params.set('max', state.max);
  if (state.available) params.set('disponible', '1');
  if (state.offers) params.set('ofertas', '1');
  if (state.status) params.set('estado', state.status);
  if (state.sort && state.sort !== 'relevance') params.set('orden', state.sort);
  if (state.page > 1) params.set('pagina', String(state.page));
  Object.entries(state.attrs).forEach(([key, values]) => values.forEach((v) => params.append(`attr.${key}`, v)));
  const text = params.toString();
  window.history.replaceState(null, '', `/catalogo.html${text ? `?${text}` : ''}`);
}

function apiParams(state) {
  const params = new URLSearchParams();
  if (state.q) params.set('q', state.q);
  if (state.category) params.set('category', String(state.category.id));
  state.brands.forEach((b) => params.append('brand', b));
  if (state.min) params.set('min', state.min);
  if (state.max) params.set('max', state.max);
  if (state.available) params.set('available', 'true');
  if (state.offers) params.set('offers', 'true');
  if (state.status) params.set('status', state.status);
  params.set('sort', state.sort || 'relevance');
  params.set('page', String(state.page));
  params.set('size', String(PAGE_SIZE));
  Object.entries(state.attrs).forEach(([key, values]) => values.forEach((v) => params.append(`attr.${key}`, v)));
  return params;
}

function pager(result, go) {
  const from = result.total ? (result.page - 1) * result.size + 1 : 0;
  const to = Math.min(result.total, result.page * result.size);
  const numbers = [];
  const window_ = new Set([1, result.pages, result.page - 1, result.page, result.page + 1].filter((n) => n >= 1 && n <= result.pages));
  [...window_].sort((a, b) => a - b).forEach((n, i, list) => {
    if (i && n - list[i - 1] > 1) numbers.push(el('span', { class: 'pager__gap', text: '…' }));
    numbers.push(el('button', { type: 'button', class: `btn btn--sm ${n === result.page ? 'btn--primary' : 'btn--ghost'}`, text: String(n),
      'aria-current': n === result.page ? 'page' : null, onclick: () => go(n) }));
  });
  return el('nav', { class: 'pager', 'aria-label': t('Paginación') },
    el('span', { class: 'pager__info', text: t('{0}–{1} de {2} productos', from, to, result.total) }),
    result.pages > 1 ? el('div', { class: 'row-actions' },
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('‹ Anterior'), disabled: result.page <= 1, onclick: () => go(result.page - 1) }),
      ...numbers,
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: t('Siguiente ›'), disabled: result.page >= result.pages, onclick: () => go(result.page + 1) }))
      : null);
}

route('/tienda', async (root, { query }) => {
  const categories = await storeCategories();
  const tree = categoryTree(categories);
  const state = readState(query, categories);
  const title = el('h1', { class: 'view__title' });
  const lead = el('p', { class: 'section__lead' });
  const filters = el('aside', { class: 'filters', id: 'catalog-filters', 'aria-label': t('Filtros') });
  const results = el('div', { class: 'catalog-results' }, skeletonCards(8));
  const sort = el('select', { 'aria-label': t('Ordenar por'), class: 'catalog-sort' }, SORTS.map(([value, text]) => el('option', { value, text })));
  sort.value = state.sort;
  const filtersToggle = el('button', { type: 'button', class: 'btn btn--ghost btn--sm filters-toggle', 'aria-controls': 'catalog-filters', 'aria-expanded': 'false' },
    icon('layers'), t(' Filtros'));
  filtersToggle.addEventListener('click', () => {
    const open = !filters.classList.contains('is-open');
    filters.classList.toggle('is-open', open);
    filtersToggle.setAttribute('aria-expanded', String(open));
  });
  let requestId = 0;

  const update = (changes, resetPage = true) => {
    Object.assign(state, changes);
    if (resetPage) state.page = 1;
    writeState(state);
    load();
  };
  sort.addEventListener('change', () => update({ sort: sort.value }));

  function checkbox(label, count, checked, onChange) {
    const input = el('input', { type: 'checkbox', checked });
    input.addEventListener('change', () => onChange(input.checked));
    return el('label', { class: 'filter-option' }, input, el('span', { text: label }), count === undefined ? null : el('small', { text: String(count) }));
  }

  function group(titleText, ...content) {
    return el('details', { class: 'filter-group', open: true }, el('summary', { text: titleText }), el('div', { class: 'filter-group__body' }, ...content.flat()));
  }

  function renderFilters(result) {
    const facets = result.facets;
    const parts = [];
    // Categorías: principales, o subcategorías de la categoría elegida.
    const main = state.category ? (state.category.parentId ? categories.find((c) => c.id === state.category.parentId) : state.category) : null;
    const categoryLinks = [];
    if (main) {
      categoryLinks.push(el('button', { type: 'button', class: 'filter-link', text: t('‹ Todas las categorías'), onclick: () => update({ category: null }) }));
      categoryLinks.push(el('button', { type: 'button', class: `filter-link filter-link--main${state.category.id === main.id ? ' is-active' : ''}`, text: main.name,
        onclick: () => update({ category: main }) }));
      facets.categories.forEach((count) => {
        const sub = categories.find((c) => c.id === count.id);
        if (sub) categoryLinks.push(el('button', { type: 'button', class: `filter-link filter-link--sub${state.category.id === sub.id ? ' is-active' : ''}`,
          onclick: () => update({ category: sub }) }, el('span', { text: sub.name }), el('small', { text: String(count.count) })));
      });
    } else {
      facets.categories.forEach((count) => {
        const category = categories.find((c) => c.id === count.id);
        if (category) categoryLinks.push(el('button', { type: 'button', class: 'filter-link', onclick: () => update({ category }) },
          el('span', { text: category.name }), el('small', { text: String(count.count) })));
      });
    }
    parts.push(group(t('Categoría'), categoryLinks.length ? categoryLinks : el('p', { class: 'muted small', text: t('Sin categorías para esta búsqueda.') })));
    // Marcas
    if (facets.brands.length) {
      const brands = [...facets.brands].sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
      const options = brands.map((b) => checkbox(b.value, b.count, state.brands.includes(b.value), (on) => update({
        brands: on ? [...state.brands, b.value] : state.brands.filter((x) => x !== b.value) })));
      const visible = el('div', {}, options.slice(0, 10));
      const more = options.length > 10 ? el('button', { type: 'button', class: 'filter-link', text: t('Ver {0} marcas más', options.length - 10),
        onclick: (event) => { visible.replaceChildren(...options); event.currentTarget.remove(); } }) : null;
      parts.push(group(t('Marca'), visible, more));
    }
    // Precio
    const min = el('input', { type: 'number', min: '0', step: '1', inputmode: 'numeric', placeholder: t('Mín.'), value: state.min, 'aria-label': t('Precio mínimo en soles') });
    const max = el('input', { type: 'number', min: '0', step: '1', inputmode: 'numeric', placeholder: t('Máx.'), value: state.max, 'aria-label': t('Precio máximo en soles') });
    const apply = el('button', { type: 'submit', class: 'btn btn--ghost btn--sm', text: t('Aplicar') });
    const priceForm = el('form', { class: 'price-filter' }, el('span', { text: 'S/' }), min, el('span', { text: '–' }), max, apply);
    priceForm.addEventListener('submit', (event) => { event.preventDefault(); update({ min: min.value.trim(), max: max.value.trim() }); });
    parts.push(group(t('Precio'), priceForm, facets.minPrice ? el('p', { class: 'muted small', text: t('En esta selección: {0} – {1}', money(facets.minPrice), money(facets.maxPrice)) }) : null));
    // Disponibilidad y ofertas
    parts.push(group(t('Disponibilidad y ofertas'),
      checkbox(t('Solo con stock'), facets.available, state.available, (on) => update({ available: on })),
      checkbox(t('Solo ofertas'), facets.offers, state.offers, (on) => update({ offers: on })),
      el('div', { class: 'filter-chips' }, Object.entries(STATUS_FILTERS).map(([value, text]) => el('button', { type: 'button',
        class: `chip${state.status === value ? ' is-active' : ''}`, text, 'aria-pressed': String(state.status === value),
        onclick: () => update({ status: state.status === value ? '' : value }) })))));
    // Filtros técnicos (solo los que existen en estos resultados)
    facets.attributes.forEach((facet) => {
      const selected = state.attrs[facet.key] || [];
      parts.push(group(facet.label, facet.values.map((v) => checkbox(td(v.value), v.count, selected.includes(v.value), (on) => {
        const next = on ? [...selected, v.value] : selected.filter((x) => x !== v.value);
        const attrs = { ...state.attrs };
        if (next.length) attrs[facet.key] = next; else delete attrs[facet.key];
        update({ attrs });
      }))));
    });
    const active = state.q || state.brands.length || state.min || state.max || state.available || state.offers || state.status || Object.keys(state.attrs).length;
    filters.replaceChildren(el('div', { class: 'filters__head' }, el('strong', { text: t('Filtrar') }),
      active ? el('button', { type: 'button', class: 'filter-link', text: t('Limpiar filtros'), onclick: () => update({ q: '', brands: [], min: '', max: '',
        available: false, offers: false, status: '', attrs: {} }) }) : null), ...parts);
  }

  function chips() {
    const list = [];
    if (state.q) list.push([`«${state.q}»`, () => update({ q: '' })]);
    state.brands.forEach((b) => list.push([b, () => update({ brands: state.brands.filter((x) => x !== b) })]));
    if (state.min || state.max) list.push([`S/ ${state.min || 0} – ${state.max || '…'}`, () => update({ min: '', max: '' })]);
    if (state.available) list.push([t('Con stock'), () => update({ available: false })]);
    if (state.offers) list.push([t('Ofertas'), () => update({ offers: false })]);
    if (state.status) list.push([STATUS_FILTERS[state.status] || state.status, () => update({ status: '' })]);
    Object.entries(state.attrs).forEach(([key, values]) => values.forEach((v) => list.push([td(v), () => {
      const attrs = { ...state.attrs, [key]: values.filter((x) => x !== v) };
      if (!attrs[key].length) delete attrs[key];
      update({ attrs });
    }])));
    return list.length ? el('div', { class: 'active-filters' }, list.map(([text, remove]) => el('button', { type: 'button', class: 'chip is-active',
      'aria-label': t('Quitar filtro {0}', text), onclick: remove }, text, ' ×'))) : null;
  }

  async function load() {
    const current = ++requestId;
    results.setAttribute('aria-busy', 'true');
    try {
      const result = await catalogApi.search(apiParams(state));
      if (current !== requestId) return;
      if (state.page > result.pages && result.total) { update({ page: result.pages }, false); return; }
      const label = state.category ? state.category.name : state.q ? t('Resultados para «{0}»', state.q) : state.offers ? t('Ofertas') : t('Todos los productos');
      title.textContent = label;
      lead.textContent = state.category && state.category.parentId
        ? t('{0} productos en {1}', result.total, (categories.find((c) => c.id === state.category.parentId) || {}).name)
        : t('{0} productos', result.total);
      document.title = t('{0} · POLYMER', label);
      const crumbs = [['/catalogo.html', t('Catálogo')]];
      if (state.category) {
        const parent = state.category.parentId ? categories.find((c) => c.id === state.category.parentId) : null;
        if (parent) crumbs.push([`/catalogo.html?categoria=${parent.slug}`, parent.name]);
        crumbs.push([null, state.category.name]);
      }
      setBreadcrumbs(crumbs);
      renderFilters(result);
      results.replaceChildren(
        chips() || el('span'),
        result.items.length ? el('div', { class: 'product-grid' }, result.items.map(productCard))
          : el('div', { class: 'card' }, emptyState(t('No encontramos productos'), t('Prueba con otras palabras o quita algún filtro.'), 'search',
            el('button', { type: 'button', class: 'btn btn--ghost', text: t('Ver todo el catálogo'), onclick: () => update({ q: '', category: null, brands: [], min: '', max: '',
              available: false, offers: false, status: '', attrs: {} }) }))),
        result.total ? pager(result, (page) => { update({ page }, false); window.scrollTo({ top: 0, behavior: 'smooth' }); }) : null);
    } catch (error) {
      if (current !== requestId) return;
      results.replaceChildren(el('div', { class: 'card' }, emptyState(t('No se pudo cargar el catálogo'), errorMessage(error), 'empty',
        el('button', { type: 'button', class: 'btn btn--ghost', text: t('Reintentar'), onclick: load }))));
    } finally {
      if (current === requestId) results.removeAttribute('aria-busy');
    }
  }

  root.append(view(
    el('div', { class: 'view__head catalog-head' }, el('div', {}, el('p', { class: 'eyebrow', text: t('Catálogo') }), title, lead),
      el('div', { class: 'row-actions' }, filtersToggle, el('label', { class: 'sort-label' }, el('span', { text: t('Ordenar por') }), sort))),
    !state.category && !state.q ? el('div', { class: 'category-chips' }, tree.map(({ main }) => el('button', { type: 'button', class: 'chip', text: main.name,
      onclick: () => update({ category: main }) }))) : null,
    el('div', { class: 'catalog-layout' }, filters, results)));
  await load();
}, { title: t('Catálogo') });

// ---- Ficha de producto ----------------------------------------------------------------------------
route('/producto/:id', async (root, { id }) => {
  const product = await catalogApi.product(id);
  const categories = await storeCategories();
  const sub = categories.find((c) => c.id === product.categoryId);
  const main = categories.find((c) => c.id === product.mainCategoryId);
  setBreadcrumbs([['/catalogo.html', t('Catálogo')], ...(main && main.id !== (sub && sub.id) ? [[`/catalogo.html?categoria=${main.slug}`, main.name]] : []),
    ...(sub ? [[`/catalogo.html?categoria=${sub.slug}`, sub.name]] : []), [null, product.name]]);
  document.title = t('{0} · POLYMER', product.name);
  let quantity = 1;
  const maxQty = Math.max(1, Math.min(99, product.stock));
  const qty = el('input', { type: 'number', min: '1', max: String(maxQty), value: '1', inputmode: 'numeric', 'aria-label': t('Cantidad'), class: 'qty__input' });
  const setQty = (value) => { quantity = Math.max(1, Math.min(maxQty, Number(value) || 1)); qty.value = String(quantity); };
  qty.addEventListener('change', () => setQty(qty.value));
  const stepper = el('div', { class: 'qty' },
    el('button', { type: 'button', class: 'qty__btn', 'aria-label': t('Quitar una unidad'), text: '−', onclick: () => setQty(quantity - 1) }), qty,
    el('button', { type: 'button', class: 'qty__btn', 'aria-label': t('Agregar una unidad'), text: '+', onclick: () => setQty(quantity + 1) }));
  const soldOut = product.stock <= 0;
  const add = el('button', { type: 'button', class: 'btn btn--ghost btn--lg', disabled: soldOut }, icon('cart'), soldOut ? t(' Agotado') : t(' Agregar al carrito'));
  const buy = el('button', { type: 'button', class: 'btn btn--primary btn--lg', disabled: soldOut, text: t('Comprar ahora') });
  add.addEventListener('click', () => addToCart(product, quantity, add));
  buy.addEventListener('click', async () => {
    if (await addToCart(product, quantity, buy)) window.location.assign(localizeHref(session.has('CUSTOMER') ? '/checkout.html' : '/carrito.html'));
  });
  const art = productArt(product, 'product-detail__art');
  const tagNode = badges(product);
  if (tagNode) art.append(tagNode);
  const relatedHolder = el('div', {}, skeletonCards(4));
  root.append(view(
    el('div', { class: 'product-detail' },
      art,
      el('div', { class: 'product-detail__info' },
        el('p', { class: 'product-card__brand', text: product.brand }),
        el('h1', { class: 'product-detail__title', text: product.name }),
        el('p', { class: 'product-detail__meta' }, t('Modelo: {0}', product.model || '—'), el('span', { 'aria-hidden': 'true', text: ' · ' }), t('SKU: {0}', product.sku),
          el('span', { 'aria-hidden': 'true', text: ' · ' }), sub ? el('a', { href: `/catalogo.html?categoria=${sub.slug}`, text: sub.name }) : product.categoryName),
        priceBlock(product, { large: true }),
        product.previousPrice ? el('p', { class: 'product-detail__savings' }, t('Ahorras {0}', money(Number(product.previousPrice) - Number(product.price))),
          product.promotionName ? t(' con {0}', product.promotionName) : '') : null,
        el('p', { class: 'product-detail__lead', text: product.description }),
        availabilityNote(product),
        el('div', { class: 'product-detail__buy' }, soldOut ? null : stepper, add, buy),
        el('ul', { class: 'product-detail__perks' }, [
          ['truck', t('Envío estándar gratis desde S/ 299 · express en 24–48 h')],
          ['card', t('Pago contra entrega, transferencia o en tienda')],
          ['shield', t('Producto nuevo y sellado con garantía de marca')],
          ['refresh', t('Cambios por fallas de fábrica hasta 7 días')],
        ].map(([ic, text]) => el('li', {}, icon(ic), el('span', { text })))))),
    el('div', { class: 'product-sections' },
      el('section', { class: 'card' }, el('h2', { class: 'card__title', text: t('Descripción') }),
        el('p', { class: 'product-description', text: product.longDescription || product.description, lang: product.longDescriptionLang })),
      el('section', { class: 'card' }, el('h2', { class: 'card__title', text: t('Especificaciones técnicas') }),
        product.specs.length ? el('table', { class: 'spec-table' }, el('tbody', {}, product.specs.map((spec) => el('tr', {},
          el('th', { scope: 'row', text: spec.label }), el('td', { text: spec.value })))))
          : el('p', { class: 'muted small', text: t('Ficha técnica no disponible.') }))),
    relatedHolder,
    el('p', {}, el('a', { class: 'btn btn--ghost', href: sub ? `/catalogo.html?categoria=${sub.slug}` : '/catalogo.html', text: t('‹ Volver al catálogo') }))));
  catalogApi.related(product.id, 8).then((list) => relatedHolder.replaceChildren(productRail(t('Productos relacionados'), list,
    main ? `/catalogo.html?categoria=${main.slug}` : null) || el('span'))).catch(() => relatedHolder.replaceChildren());
}, { title: t('Producto') });

// ---- Carrito ----------------------------------------------------------------------------------------
async function cartView() {
  if (cart.isSaved()) {
    const saved = cart.saved() || await cart.sync();
    if (saved) return saved;
  }
  const items = cart.items();
  const products = await Promise.all(items.map((i) => catalogApi.product(i.productId).catch(() => null)));
  const lines = [];
  const problems = [];
  items.forEach((item, index) => {
    const product = products[index];
    if (!product) { problems.push(t('Un producto ya no está disponible y se quitó del carrito')); cart.remove(item.productId); return; }
    const purchasable = product.stock >= item.quantity;
    if (product.stock <= 0) problems.push(t('{0} está agotado', product.name));
    else if (!purchasable) problems.push(t('Solo quedan {0} unidades de {1}', product.stock, product.name));
    lines.push({ product, quantity: item.quantity, lineTotal: (Number(product.price) * item.quantity).toFixed(2), purchasable });
  });
  const ok = lines.filter((l) => l.purchasable);
  return {
    lines, problems,
    units: ok.reduce((s, l) => s + l.quantity, 0),
    subtotal: ok.reduce((s, l) => s + Number(l.lineTotal), 0).toFixed(2),
    savings: ok.reduce((s, l) => s + (l.product.previousPrice ? (Number(l.product.previousPrice) - Number(l.product.price)) * l.quantity : 0), 0).toFixed(2),
  };
}

function cartLine(line, editable) {
  const { product } = line;
  const qty = el('input', { type: 'number', min: '1', max: '99', value: String(line.quantity), inputmode: 'numeric', class: 'qty__input',
    'aria-label': t('Cantidad de {0}', product.name) });
  const change = async (value) => {
    try {
      await cart.set(product.id, Math.max(0, Math.min(99, value)));
      rerender();
    } catch (error) {
      toast(errorMessage(error), 'warn');
      qty.value = String(line.quantity);
    }
  };
  qty.addEventListener('change', () => change(Number(qty.value) || 0));
  return el('div', { class: `cart-line${line.purchasable ? '' : ' cart-line--problem'}` },
    el('a', { href: link(`/producto/${product.id}`), tabindex: '-1', 'aria-hidden': 'true' }, productArt(product, 'cart-line__thumb')),
    el('div', { class: 'cart-line__info' },
      el('small', { class: 'product-card__brand', text: product.brand }),
      el('a', { class: 'cart-line__name', href: link(`/producto/${product.id}`), text: product.name }),
      priceBlock(product), availabilityNote(product)),
    editable ? el('div', { class: 'qty' },
      el('button', { type: 'button', class: 'qty__btn', 'aria-label': t('Quitar una unidad de {0}', product.name), text: '−', onclick: () => change(line.quantity - 1) }), qty,
      el('button', { type: 'button', class: 'qty__btn', 'aria-label': t('Agregar una unidad de {0}', product.name), text: '+', onclick: () => change(line.quantity + 1) }))
      : el('span', { text: `× ${line.quantity}` }),
    el('div', { class: 'cart-line__total' }, el('strong', { text: money(line.lineTotal) }),
      editable ? el('button', { type: 'button', class: 'btn btn--ghost btn--sm', 'aria-label': t('Quitar {0}', product.name), text: t('Quitar'),
        onclick: async () => { await cart.remove(product.id); rerender(); } }) : null));
}

route('/carrito', async (root) => {
  const data = await cartView();
  if (!data.lines.length) {
    root.append(view(head(t('Carrito'), t('Tu carrito está vacío'), ''), el('div', { class: 'card' },
      emptyState(t('Aún no agregaste productos'), t('Explora el catálogo y agrega lo que necesites.'), 'cart',
        el('a', { class: 'btn btn--primary', href: '/catalogo.html', text: t('Ir al catálogo') })))));
    return;
  }
  const blocked = data.lines.some((l) => !l.purchasable);
  const proceed = cart.isSaved() || !session.isAuthenticated()
    ? el('a', { class: `btn btn--primary btn--block${blocked ? ' is-disabled' : ''}`, href: blocked ? '#' : '/checkout.html', 'aria-disabled': String(blocked),
      text: t('Continuar con la compra'), onclick: (event) => { if (blocked) { event.preventDefault(); toast(t('Ajusta las cantidades marcadas antes de continuar'), 'warn'); } } })
    : el('p', { class: 'notice-box', text: t('Para comprar inicia sesión con una cuenta de cliente.') });
  const summary = el('aside', { class: 'card cart-summary' },
    el('h2', { class: 'card__title', text: t('Resumen') }),
    el('div', { class: 'summary-row' }, el('span', { text: t('Productos ({0})', data.units) }), el('span', { text: money(data.subtotal) })),
    Number(data.savings) > 0 ? el('div', { class: 'summary-row summary-row--savings' }, el('span', { text: t('Ahorras') }), el('span', { text: `-${money(data.savings)}` })) : null,
    el('div', { class: 'summary-row' }, el('span', { text: t('Envío') }), el('span', { text: Number(data.subtotal) >= 299 ? t('Gratis (estándar)') : t('Se calcula en el siguiente paso') })),
    el('div', { class: 'summary-row summary-row--total' }, el('span', { text: t('Total') }), el('span', { text: money(data.subtotal) })),
    el('p', { class: 'muted small', text: t('Precios en soles con IGV incluido. El envío estándar es gratis desde S/ 299.') }),
    proceed,
    el('a', { class: 'btn btn--ghost btn--block', href: '/catalogo.html', text: t('Seguir comprando') }),
    el('button', { type: 'button', class: 'btn btn--ghost btn--block btn--danger-text', text: t('Vaciar carrito'), onclick: async () => {
      await cart.clear();
      rerender();
    } }));
  root.append(view(
    head(t('Carrito'), t('Tu carrito'), `${data.units} ${data.units === 1 ? 'producto' : 'productos'}`),
    data.problems.length ? el('div', { class: 'notice-box notice-box--warn' }, el('strong', { text: t('Revisa tu carrito. ') }), data.problems.join(' · ')) : null,
    el('div', { class: 'cart-layout' }, el('div', { class: 'card cart-lines' }, data.lines.map((l) => cartLine(l, true))), summary)));
}, { title: t('Carrito') });

// ---- Checkout -----------------------------------------------------------------------------------------
route('/checkout', async (root) => {
  const data = await cartView();
  const items = data.lines.filter((l) => l.purchasable).map((l) => ({ productId: l.product.id, quantity: l.quantity }));
  if (!items.length) {
    root.append(view(head(t('Checkout'), t('No hay productos para comprar'), ''), el('div', { class: 'card' },
      emptyState(t('Tu carrito está vacío o sin stock'), t('Agrega productos disponibles para continuar.'), 'cart',
        el('a', { class: 'btn btn--primary', href: '/carrito.html', text: t('Volver al carrito') })))));
    return;
  }
  const options = await shopApi.options();
  const user = session.user();
  const state = { delivery: 'STANDARD', payment: 'CASH_ON_DELIVERY' };
  const recipient = el('input', { autocomplete: 'name', maxlength: '80', required: true, value: user.displayName });
  const phone = el('input', { type: 'tel', autocomplete: 'tel', maxlength: '20', placeholder: '+51 999 999 999' });
  const address = el('input', { autocomplete: 'street-address', maxlength: '160', required: true, placeholder: t('Av. Ejemplo 123, dpto. 4, distrito') });
  const city = el('input', { autocomplete: 'address-level2', maxlength: '60', required: true, placeholder: 'Lima' });
  const instructions = el('textarea', { maxlength: '300', rows: '2', placeholder: t('Referencias para el repartidor (opcional)') });
  const addressFields = el('div', { class: 'form-grid' }, field(t('Destinatario'), recipient), field(t('Teléfono'), phone),
    (() => { const f = field(t('Dirección'), address); f.classList.add('field--full'); return f; })(), field(t('Ciudad'), city),
    (() => { const f = field(t('Referencias'), instructions); f.classList.add('field--full'); return f; })());
  const pickupNote = el('div', { class: 'notice-box', hidden: true }, el('strong', { text: t('Recojo en tienda: ') }),
    t('Av. Tecnología 123, Lima. Lunes a sábado de 9:00 a 19:00. Te avisaremos por correo cuando tu pedido esté listo.'));
  const deliveryGroup = el('div', { class: 'choice-list', role: 'radiogroup', 'aria-label': t('Método de entrega') });
  const paymentGroup = el('div', { class: 'choice-list', role: 'radiogroup', 'aria-label': t('Método de pago') });
  const totals = el('div', {}, skeletons(3, 'skeleton--row'));
  const errorNode = formError();
  const confirm = el('button', { type: 'button', class: 'btn btn--primary btn--block btn--lg', text: t('Confirmar pedido') });
  // Una clave por intento de compra: si la red falla y el cliente reintenta, no se duplica el pedido.
  const idempotencyKey = `web-${crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2)}`.slice(0, 64);

  function choice(groupName, option, checked, detail, onPick, disabled = false) {
    const input = el('input', { type: 'radio', name: groupName, value: option.code, checked, disabled });
    input.addEventListener('change', () => onPick(option.code));
    return el('label', { class: `choice${checked ? ' is-checked' : ''}${disabled ? ' is-disabled' : ''}` }, input,
      el('span', { class: 'choice__body' }, el('strong', { text: option.label }), el('small', { text: detail })));
  }

  function renderChoices(subtotal) {
    deliveryGroup.replaceChildren(...options.delivery.map((d) => {
      const free = d.freeFrom && Number(subtotal) >= Number(d.freeFrom);
      const cost = Number(d.cost) === 0 || free ? t('Gratis') : money(d.cost);
      return choice('delivery', d, state.delivery === d.code, `${d.eta} · ${cost}${d.freeFrom && !free ? t(' (gratis desde {0})', money(d.freeFrom)) : ''}`, (code) => {
        state.delivery = code;
        const allowed = options.payment.filter((p) => p.deliveries.includes(code));
        if (!allowed.some((p) => p.code === state.payment)) state.payment = allowed[0].code;
        refresh();
      });
    }));
    paymentGroup.replaceChildren(...options.payment.map((p) => {
      const allowed = p.deliveries.includes(state.delivery);
      return choice('payment', p, state.payment === p.code, allowed ? p.instructions : t('No disponible con {0}', DELIVERY_LABELS[state.delivery].toLowerCase()),
        (code) => { state.payment = code; refresh(); }, !allowed);
    }));
    const pickup = state.delivery === 'PICKUP';
    addressFields.hidden = pickup;
    pickupNote.hidden = !pickup;
  }

  async function refresh() {
    try {
      const summary = await shopApi.summary(items, state.delivery, state.payment);
      renderChoices(summary.subtotal);
      totals.replaceChildren(
        ...summary.lines.map((l) => el('div', { class: 'summary-row' }, el('span', { text: `${l.quantity} × ${l.productName}` }), el('span', { text: money(l.lineTotal) }))),
        el('hr', { class: 'divider' }),
        el('div', { class: 'summary-row' }, el('span', { text: t('Subtotal') }), el('span', { text: money(summary.subtotal) })),
        Number(summary.savings) > 0 ? el('div', { class: 'summary-row summary-row--savings' }, el('span', { text: t('Ahorro en promociones') }), el('span', { text: `-${money(summary.savings)}` })) : null,
        el('div', { class: 'summary-row' }, el('span', { text: summary.delivery.label }), el('span', { text: Number(summary.shippingCost) ? money(summary.shippingCost) : t('Gratis') })),
        el('div', { class: 'summary-row summary-row--total' }, el('span', { text: t('Total a pagar') }), el('span', { text: money(summary.total) })));
    } catch (error) {
      totals.replaceChildren(el('div', { class: 'notice-box notice-box--error', text: errorMessage(error) }));
    }
  }

  confirm.addEventListener('click', async () => {
    const pickup = state.delivery === 'PICKUP';
    const shipping = { recipient: recipient.value.trim(), address: address.value.trim(), city: city.value.trim(), phone: phone.value.trim(), instructions: instructions.value.trim() };
    if (!pickup && (shipping.recipient.length < 2 || shipping.address.length < 5 || shipping.city.length < 2)) {
      showFormError(errorNode, new Error(t('Completa destinatario, dirección (mínimo 5 caracteres) y ciudad.')));
      address.focus();
      return;
    }
    try {
      const payload = { items, deliveryMethod: state.delivery, paymentMethod: state.payment, shipping: pickup ? null : shipping };
      const result = await submitting(confirm, errorNode, () => shopApi.checkout(payload, idempotencyKey));
      cart.forgetLocal();
      window.location.assign(localizeHref(`/cliente/seguimiento.html?id=${encodeURIComponent(result.data.id)}&nuevo=1`));
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) toast(t('El stock cambió: revisa tu carrito'), 'warn');
    }
  });

  root.append(view(
    head(t('Checkout'), t('Finaliza tu compra'), t('Comprando como {0}', user.displayName)),
    el('ol', { class: 'checkout-steps', 'aria-label': t('Pasos de la compra') },
      el('li', { class: 'is-done', text: t('Carrito') }), el('li', { class: 'is-current', text: t('Entrega y pago') }), el('li', { text: t('Confirmación') })),
    el('div', { class: 'cart-layout' },
      el('div', { class: 'stack' },
        el('section', { class: 'card' }, el('h2', { class: 'card__title', text: t('1. Método de entrega') }), deliveryGroup),
        el('form', { class: 'card form', novalidate: true, 'aria-labelledby': 'shipping-title', onsubmit: (event) => event.preventDefault() },
          el('h2', { class: 'card__title', id: 'shipping-title', text: t('2. Datos de entrega') }), addressFields, pickupNote),
        el('section', { class: 'card' }, el('h2', { class: 'card__title', text: t('3. Método de pago') }), paymentGroup,
          el('p', { class: 'muted small', text: t('No solicitamos datos de tarjeta ni bancarios en la web.') }))),
      el('aside', { class: 'card cart-summary' }, el('h2', { class: 'card__title', text: t('Resumen del pedido') }), totals, confirm, errorNode,
        el('a', { class: 'btn btn--ghost btn--block', href: '/carrito.html', text: t('Volver al carrito') })))));
  renderChoices(data.subtotal);
  await refresh();
}, { roles: ['CUSTOMER'], title: t('Checkout') });

// ---- Mis pedidos --------------------------------------------------------------------------------------
route('/mis-pedidos', async (root) => {
  const list = el('div', { class: 'card' }, skeletons(4, 'skeleton--row'));
  root.append(view(head(t('Mi cuenta'), t('Mis pedidos'), t('Historial y seguimiento de tus compras.')), list));
  try {
    const orders = await shopApi.orders();
    if (!orders.length) {
      list.replaceChildren(emptyState(t('Todavía no tienes pedidos'), t('Cuando compres algo aparecerá aquí con su seguimiento.'), 'box',
        el('a', { class: 'btn btn--primary', href: '/catalogo.html', text: t('Ir al catálogo') })));
      return;
    }
    list.replaceChildren(paginated(orders, (page) => el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
      el('thead', {}, el('tr', {}, [t('Pedido'), t('Fecha'), t('Productos'), t('Total'), t('Estado'), ''].map((h) => el('th', { scope: 'col', text: h })))),
      el('tbody', {}, page.map((o) => el('tr', {},
        el('td', {}, el('span', { class: 'table__id', text: orderRef(o) })),
        el('td', { text: formatDateTime(o.createdAt) }),
        el('td', { text: String(o.lines.reduce((s, l) => s + l.quantity, 0)) }),
        el('td', { class: 'mono', text: money(o.amount, o.currency) }),
        el('td', {}, customerBadge(o.status)),
        el('td', {}, el('div', { class: 'row-actions' },
          el('a', { class: 'btn btn--ghost btn--sm', href: link(`/mis-pedidos/${o.id}`), text: t('Detalle') }),
          el('a', { class: 'btn btn--ghost btn--sm', href: link(`/seguimiento/${o.id}`), text: t('Seguimiento') })))))))), { pageSize: 10, label: t('pedidos') }));
  } catch (error) {
    list.replaceChildren(emptyState(t('No se pudieron cargar tus pedidos'), errorMessage(error)));
  }
}, { roles: ['CUSTOMER'], title: t('Mis pedidos') });

route('/mis-pedidos/:id', async (root, { id }) => {
  const details = await shopApi.order(id);
  const { order, history, delivery, allowedTransitions, shipping, checkout } = details;
  const currency = order.currency;
  const cancelButton = allowedTransitions.includes('CANCELLED')
    ? el('button', { type: 'button', class: 'btn btn--ghost', text: t('Cancelar pedido') }) : null;
  if (cancelButton) {
    cancelButton.addEventListener('click', () => {
      const errorNode = formError();
      const confirm = el('button', { type: 'button', class: 'btn btn--primary', text: t('Sí, cancelar') });
      const dialog = modal(t('Cancelar pedido'), el('div', {}, el('p', { class: 'muted', text: t('¿Seguro que quieres cancelar este pedido? Esta acción no se puede deshacer.') }), errorNode),
        { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: t('Volver'), onclick: () => dialog.close() }), confirm] });
      confirm.addEventListener('click', async () => {
        await submitting(confirm, errorNode, () => shopApi.cancel(id), t('Pedido cancelado'));
        dialog.close();
        rerender();
      });
    });
  }
  const lineSavings = order.lines.reduce((s, l) => s + (l.listUnitPrice ? (Number(l.listUnitPrice) - Number(l.unitPrice)) * l.quantity : 0), 0);
  root.append(view(
    head(t('Detalle del pedido'), orderRef(order), t('Realizado {0} · {1}', formatRelative(order.createdAt), formatDateTime(order.createdAt)),
      customerBadge(order.status), el('a', { class: 'btn btn--primary btn--sm', href: link(`/seguimiento/${order.id}`), text: t('Ver seguimiento') }), cancelButton),
    el('div', { class: 'grid-2' },
      el('div', { class: 'card' },
        el('h3', { class: 'card__title', text: t('Progreso') }), progressBar(order.status),
        delivery ? el('div', { class: 'notice-box' }, el('strong', { text: t('Entrega programada: ') }),
          `${formatDateTime(delivery.start)} – ${new Date(delivery.end).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}`)
          : el('p', { class: 'muted small', text: t('Programaremos la entrega cuando el pedido esté confirmado.') }),
        el('h3', { class: 'card__title card__title--spaced', text: t('Historial') }),
        el('ol', { class: 'timeline' }, history.map((h, index) => el('li', { class: index === history.length - 1 ? 'is-current' : '' },
          el('strong', {}, customerBadge(h.to)), el('small', { text: formatDateTime(h.at) }))))),
      el('div', { class: 'card' },
        el('h3', { class: 'card__title', text: t('Productos') }),
        order.lines.map((l) => el('div', { class: 'summary-row' }, el('span', { text: `${l.quantity} × ${l.productName}` }), el('span', { text: money(l.lineTotal, currency) }))),
        checkout ? el('div', { class: 'summary-row' }, el('span', { text: t('Subtotal') }), el('span', { text: money(checkout.subtotal, currency) })) : null,
        lineSavings > 0 ? el('div', { class: 'summary-row summary-row--savings' }, el('span', { text: t('Ahorro en promociones') }), el('span', { text: `-${money(lineSavings, currency)}` })) : null,
        checkout ? el('div', { class: 'summary-row' }, el('span', { text: DELIVERY_LABELS[checkout.delivery] }), el('span', { text: Number(checkout.shippingCost) ? money(checkout.shippingCost, currency) : t('Gratis') })) : null,
        el('div', { class: 'summary-row summary-row--total' }, el('span', { text: t('Total') }), el('span', { text: money(order.amount, currency) })),
        checkout && checkout.payment ? el('p', { class: 'small' }, el('strong', { text: t('Pago: ') }), PAYMENT_LABELS[checkout.payment]) : null,
        el('h3', { class: 'card__title card__title--spaced', text: t('Entrega') }),
        checkout && checkout.delivery === 'PICKUP' ? el('p', { class: 'small', text: t('Recojo en tienda · Av. Tecnología 123, Lima.') })
          : shipping ? el('address', { class: 'address-card' }, el('strong', { text: shipping.recipient }), el('span', { text: shipping.address }),
            el('span', { text: shipping.city }), shipping.phone ? el('span', { text: shipping.phone }) : null,
            shipping.instructions ? el('small', { class: 'muted', text: shipping.instructions }) : null)
            : el('p', { class: 'muted small', text: t('Sin dirección registrada.') })))));
}, { roles: ['CUSTOMER'], title: t('Detalle del pedido') });

// ---- Perfil ---------------------------------------------------------------------------------------------
route('/perfil', async (root) => {
  const me = await accountApi.me();
  const name = el('input', { value: me.displayName, maxlength: '80', autocomplete: 'name' });
  const email = el('input', { type: 'email', value: me.email, maxlength: '254', autocomplete: 'email' });
  const profileError = formError();
  const saveProfile = el('button', { type: 'submit', class: 'btn btn--primary', text: t('Guardar perfil') });
  const current = el('input', { type: 'password', autocomplete: 'current-password' });
  const next = newPasswordFields({ label: t('Nueva contraseña'), personal: () => [me.username, name.value, email.value] });
  const passError = formError();
  const savePass = el('button', { type: 'submit', class: 'btn btn--ghost', text: t('Cambiar contraseña') });
  const profileForm = el('form', { class: 'card form', novalidate: true }, el('h3', { class: 'card__title', text: t('Datos personales') }),
    field(t('Nombre'), name), field(t('Correo electrónico'), email), saveProfile, profileError);
  const passForm = el('form', { class: 'card form', novalidate: true }, el('h3', { class: 'card__title', text: t('Seguridad') }),
    (() => { const f = field(t('Contraseña actual'), current); const wrap = el('div', { class: 'pw-input' }); current.replaceWith(wrap); wrap.append(current, revealToggle(current)); return f; })(),
    next.element, savePass, passError);
  profileForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    await submitting(saveProfile, profileError, () => accountApi.profile(name.value, email.value), t('Perfil actualizado')).catch(() => {});
  });
  passForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const problem = next.check();
    if (problem) { showFormError(passError, new Error(problem)); next.input.focus(); return; }
    await submitting(savePass, passError, () => accountApi.password(current.value, next.input.value), t('Contraseña actualizada'))
      .then(() => { current.value = ''; next.reset(); }).catch(() => {});
  });
  root.append(view(head(t('Mi cuenta'), t('Perfil y seguridad'), `@${me.username} · ${me.roles.map((r) => ROLE_LABELS[r] || r).join(', ')}`),
    el('div', { class: 'grid-2-even' }, profileForm, passForm)));
}, { roles: ['auth'], title: t('Perfil') });

// ---- Acceso -------------------------------------------------------------------------------------------
const DEMO = [
  ['cliente', 'CUSTOMER'], ['admin', 'ADMIN'], ['operador', 'ORDER_OPERATOR'],
  ['logistica', 'LOGISTICS_OPERATOR'], ['logistica2', 'LOGISTICS_OPERATOR'], ['observador', 'OBSERVABILITY_VIEWER'],
];

route('/entrar', async (root, { query }) => {
  const nextPath = query.get('next');
  const safeNext = nextPath && nextPath.startsWith('/') && !nextPath.startsWith('//') ? nextPath : null;
  if (session.isAuthenticated()) { window.location.replace(safeNext || localizeHref(homeFor(session.user()))); return; }
  const username = el('input', { autocomplete: 'username', required: true, maxlength: '32' });
  const password = el('input', { type: 'password', autocomplete: 'current-password', required: true });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block', text: t('Iniciar sesión') });
  const form = el('form', { class: 'card form', novalidate: true },
    el('h2', { class: 'card__title', text: t('Iniciar sesión') }), field(t('Usuario'), username), field(t('Contraseña'), password), submit, errorNode,
    el('p', { class: 'muted small' }, t('¿Eres nuevo? '), el('a', { href: '/registro.html', text: t('Crea tu cuenta') })));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      const user = await submitting(submit, errorNode, () => session.login(username.value.trim(), password.value));
      if (localized) accountApi.locale(lang).catch(() => {}); // idioma de sus correos = el de esta página
      toast(t('Hola, {0}', user.displayName), 'ok');
      await cart.sync(); // tu carrito de visitante pasa a tu cuenta
      // Solo rutas internas: evita redirecciones abiertas a otros dominios.
      window.location.assign(safeNext ? link(safeNext) : localizeHref(homeFor(user)));
    } catch { /* el error ya se muestra */ }
  });
  root.append(view(el('div', { class: 'auth' },
    el('div', { class: 'auth__aside' },
      el('img', { src: '/img/logo.svg', alt: '', width: '56', height: '56' }),
      el('h2', { text: t('Bienvenido a POLYMER') }),
      el('p', { text: t('Inicia sesión para comprar, guardar tu carrito y seguir tus pedidos.') }),
      el('details', { class: 'demo-accounts-box' }, el('summary', { text: t('Cuentas de demostración') }),
        el('div', { class: 'demo-accounts' }, DEMO.map(([user, role]) => el('button', { type: 'button', onclick: () => { username.value = user; password.focus(); } },
          el('span', {}, el('strong', { text: user }), el('small', { text: ` · ${ROLE_LABELS[role]}` })), el('small', { text: 'usar' })))))),
    form)));
  username.focus();
}, { title: t('Iniciar sesión') });

route('/registro', async (root) => {
  const username = el('input', { autocomplete: 'username', maxlength: '32' });
  const displayName = el('input', { autocomplete: 'name', maxlength: '80' });
  const email = el('input', { type: 'email', autocomplete: 'email', maxlength: '254' });
  const password = newPasswordFields({ personal: () => [username.value, displayName.value, email.value] });
  const errorNode = formError();
  const submit = el('button', { type: 'submit', class: 'btn btn--primary btn--block', text: t('Crear cuenta') });
  const form = el('form', { class: 'card form', novalidate: true }, el('h2', { class: 'card__title', text: t('Crea tu cuenta') }),
    field(t('Usuario'), username, t('3 a 32 letras, dígitos, guion o guion bajo.')), field(t('Nombre'), displayName),
    field(t('Correo electrónico'), email), password.element, submit, errorNode,
    el('p', { class: 'muted small' }, t('¿Ya tienes cuenta? '), el('a', { href: '/login.html', text: t('Inicia sesión') })));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const problem = password.check();
    if (problem) { showFormError(errorNode, new Error(problem)); password.input.focus(); return; }
    try {
      await submitting(submit, errorNode, async () => {
        await authApi.register({ username: username.value.trim(), displayName: displayName.value.trim(), email: email.value.trim(), password: password.input.value });
        const logged = await session.login(username.value.trim(), password.input.value);
        if (localized) await accountApi.locale(lang).catch(() => {});
        return logged;
      }, t('Cuenta creada'));
      await cart.sync();
      window.location.assign(localizeHref(cart.count() ? '/carrito.html' : '/cliente/dashboard.html'));
    } catch { /* el error ya se muestra */ }
  });
  root.append(view(el('div', { class: 'auth' },
    el('div', { class: 'auth__aside' }, el('img', { src: '/img/logo.svg', alt: '', width: '56', height: '56' }),
      el('h2', { text: t('Tu cuenta POLYMER') }),
      el('ul', { class: 'auth__perks' }, [t('Guarda tu carrito en todos tus dispositivos'), t('Sigue tus pedidos en tiempo real'), t('Compra más rápido con tus datos guardados')]
        .map((text) => el('li', {}, icon('sparkle'), el('span', { text }))))),
    form)));
  username.focus();
}, { title: t('Registro') });

export function showError(node, error) { showFormError(node, error); }
void $$;
