// Administración (ADMIN): panel, productos, inventario, pedidos, usuarios y roles, incidencias,
// auditoría y reportes. Todas las cifras proceden de PostgreSQL a través de la API.

import { adminApi, incidentsApi, operationsApi } from '../api.js';
import {
  LIFECYCLE, TRANSITION_VERBS, barChart, channelBadge, downloadCsv, emptyState, errorMessage, field, formError, kpi as kpiTile,
  lifecycleBadge, modal, money, paginated, searchBox, skeletons, submitting,
} from '../components.js';
import { link, rerender, route } from '../router.js';
import { ROLE_LABELS, session } from '../session.js';
import { el, formatDateTime, formatInt, formatRelative, toast } from '../ui.js';

const TITLES = {
  panel: ['Centro de control', 'Ventas, pedidos, stock e incidencias de la tienda.'],
  productos: ['Productos', 'Alta y edición del catálogo: ficha técnica, precios, estado comercial y visibilidad.'],
  categorias: ['Categorías', 'Categorías principales y subcategorías de la tienda.'],
  inventario: ['Inventario', 'Stock por producto, alertas de stock bajo y ajustes auditados.'],
  promociones: ['Promociones', 'Campañas con vigencia, descuento y productos o categorías asociadas.'],
  pedidos: ['Pedidos', 'Todos los pedidos con su ciclo de vida.'],
  usuarios: ['Usuarios y roles', 'Roles, activación de cuentas y protección contra el autobloqueo.'],
  incidencias: ['Incidencias', 'Registro y resolución de incidencias operativas.'],
  reportes: ['Reportes comerciales', 'Ventas por categoría y productos más vendidos, exportables a CSV.'],
  auditoria: ['Auditoría', 'Quién hizo qué y cuándo.'],
};
const CONTACT_TOPICS = { ORDERS: 'Pedidos y envíos', SUPPORT: 'Soporte técnico', WARRANTY: 'Garantías', SALES: 'Ventas corporativas', CLAIM: 'Libro de reclamaciones',
  OTHER: 'Otro', DEMO: 'Demostración', ACADEMIC: 'Consulta' };
const STATUS_OPTIONS = [['STANDARD', 'Estándar'], ['NEW', 'Nuevo'], ['FEATURED', 'Destacado'], ['BESTSELLER', 'Más vendido'], ['CLEARANCE', 'Liquidación']];
const slugify = (text) => text.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

/** Select de subcategoría agrupado por categoría principal (solo categorías visibles). */
function categorySelect(categories, selected) {
  const mains = categories.filter((c) => c.parentId === null && c.active).sort((a, b) => a.position - b.position);
  const select = el('select', {});
  mains.forEach((main) => {
    const subs = categories.filter((c) => c.parentId === main.id).sort((a, b) => a.position - b.position);
    const group = el('optgroup', { label: main.name });
    (subs.length ? subs : [main]).forEach((c) => group.append(el('option', { value: String(c.id), text: c.name, selected: c.id === selected })));
    select.append(group);
  });
  return select;
}

const table = (headers, rows) => el('div', { class: 'table-scroll' }, el('table', { class: 'table' },
  el('thead', {}, el('tr', {}, headers.map((h) => el('th', { scope: 'col', text: h })))), el('tbody', {}, rows)));

// En el sitio multipágina la navegación entre secciones la da la barra lateral (layout.js).
function shell(root, active, ...content) {
  const [title, lead] = TITLES[active] || TITLES.panel;
  root.append(el('section', { class: 'view' },
    el('div', { class: 'view__head' }, el('div', {}, el('p', { class: 'eyebrow', text: 'Administración' }),
      el('h1', { class: 'view__title', text: title }),
      el('p', { class: 'section__lead', text: `${lead} Sesión: ${session.user().displayName}.` }))),
    ...content));
}

// ---- Panel ------------------------------------------------------------------------
async function panel(root) {
  const body = el('div', {}, el('div', { class: 'kpis' }, skeletons(8, 'skeleton--row')));
  shell(root, 'panel', body);
  const d = await adminApi.dashboard();
  const s = d.ordersByStatus;
  const kpi = (label, value, tone = '') => el('div', { class: `kpi${tone ? ` kpi--${tone}` : ''}` }, el('span', { text: label }), el('strong', { text: value }));
  const alerts = [];
  if (s.RISK_REVIEW > 0) alerts.push(['warn', `${s.RISK_REVIEW} pedido(s) esperan revisión de riesgo`, '/admin/pedidos.html']);
  if (d.lowStock.length) alerts.push(['warn', `${d.lowStock.length} producto(s) con stock ≤ 5: ${d.lowStock.slice(0, 3).map((p) => p.details.name).join(', ')}`, '/admin/inventario.html']);
  if (d.openIncidents > 0) alerts.push(['error', `${d.openIncidents} incidencia(s) abierta(s)`, '/admin/incidencias.html']);
  if (!alerts.length) alerts.push(['ok', 'Sin alertas operativas: no hay revisiones pendientes, stock bajo ni incidencias abiertas.', null]);
  body.replaceChildren(
    el('div', { class: 'kpis' },
      kpi('Pedidos totales', formatInt(d.totalOrders)),
      kpi('Ventas de la tienda', money(d.shopSales), 'violet'),
      Number(d.legacyUsdSales) > 0 ? kpi('Ventas anteriores (USD)', money(d.legacyUsdSales, 'USD')) : null,
      kpi('Pendientes de revisión', formatInt(s.RISK_REVIEW || 0), s.RISK_REVIEW ? 'warn' : ''),
      kpi('Aprobados por riesgo', formatInt(s.RISK_APPROVED || 0), 'ok'),
      kpi('En preparación', formatInt((s.CONFIRMED || 0) + (s.PREPARING || 0) + (s.READY_FOR_DISPATCH || 0))),
      kpi('En tránsito', formatInt(s.IN_TRANSIT || 0)),
      kpi('Entregados', formatInt(s.DELIVERED || 0), 'ok'),
      kpi('Rechazados / cancelados', formatInt((s.REJECTED || 0) + (s.CANCELLED || 0)), (s.REJECTED || s.CANCELLED) ? 'error' : ''),
      kpi('Productos', formatInt(d.products)),
      kpi('Incidencias abiertas', formatInt(d.openIncidents), d.openIncidents ? 'error' : '')),
    el('div', { class: 'grid-2' },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos por día (últimos 14 días)' }),
        barChart(d.ordersPerDay, { label: (k) => k.slice(8, 10) + '/' + k.slice(5, 7) })),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Alertas operativas' }),
        el('ul', { class: 'alerts-list' }, alerts.map(([tone, text, href]) => el('li', { class: `is-${tone}` },
          el('span', { class: `dot dot--${tone === 'ok' ? 'ok' : tone === 'warn' ? 'warn' : 'error'}` }),
          href ? el('a', { href, text }) : el('span', { text })))))),
    el('div', { class: 'grid-2-even', style: null },
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Pedidos por estado' }),
        table(['Estado', 'Pedidos'], Object.entries(s).map(([status, count]) => el('tr', {}, el('td', {}, lifecycleBadge(status)), el('td', { class: 'mono', text: String(count) }))))),
      el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Actividad reciente' }),
        d.recentActivity.length ? el('ul', { class: 'activity' }, d.recentActivity.map((a) => el('li', {},
          el('strong', { text: a.action }), ` · ${a.actor} → ${a.target}`, el('small', { text: `${formatRelative(a.at)}${a.details ? ` · ${a.details}` : ''}` }))))
          : emptyState('Sin actividad', 'Todavía no hay eventos auditados.'))));
  body.lastElementChild.style.marginTop = '16px';
  const messages = el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Mensajes de la página de contacto' }),
    el('div', {}, skeletons(2, 'skeleton--row')));
  body.append(messages);
  try {
    const list = await adminApi.contactMessages(20);
    messages.lastElementChild.replaceChildren(list.length ? paginated(list, (page) => table(['Fecha', 'Nombre', 'Correo', 'Tema', 'Mensaje'],
      page.map((m) => el('tr', {}, el('td', { text: formatRelative(m.createdAt) }), el('td', { text: m.name }),
        el('td', {}, el('a', { href: `mailto:${m.email}`, text: m.email })), el('td', {}, el('span', { class: 'badge badge--neutral', text: CONTACT_TOPICS[m.topic] || m.topic })),
        el('td', { class: 'small', text: m.message })))), { pageSize: 5, label: 'mensajes' })
      : emptyState('Sin mensajes', 'Los mensajes enviados desde /contacto.html aparecerán aquí.', 'mail'));
  } catch (error) {
    messages.lastElementChild.replaceChildren(emptyState('No se pudieron cargar los mensajes', errorMessage(error)));
  }
}

// ---- Productos e inventario --------------------------------------------------------
function productForm(categories, product = null) {
  const firstSub = categories.find((c) => c.parentId !== null && c.active);
  const v = product || { sku: '', name: '', brand: '', model: '', description: '', longDescription: '', categoryId: firstSub && firstSub.id,
    listPrice: '', compareAtPrice: '', active: true, hue: 260, stock: 0, status: 'STANDARD', specs: [], attributes: {} };
  const sku = el('input', { value: v.sku, maxlength: '32', placeholder: 'CMP-01-99' });
  const name = el('input', { value: v.name, maxlength: '120' });
  const brand = el('input', { value: v.brand || '', maxlength: '60', placeholder: 'Lenovo' });
  const model = el('input', { value: v.model || '', maxlength: '80', placeholder: 'IdeaPad Slim 3' });
  const description = el('textarea', { maxlength: '1000', rows: '2' });
  description.value = v.description;
  const longDescription = el('textarea', { maxlength: '4000', rows: '4' });
  longDescription.value = v.longDescription || '';
  const category = categorySelect(categories, v.categoryId);
  // En la tabla de administración "price" es el precio regular (listPrice); las promociones se aplican aparte.
  const price = el('input', { value: String(v.listPrice ?? v.price ?? ''), inputmode: 'decimal', placeholder: '1299.00' });
  const compare = el('input', { value: v.compareAtPrice ? String(v.compareAtPrice) : '', inputmode: 'decimal', placeholder: 'Opcional, mayor que el precio' });
  const status = el('select', {}, STATUS_OPTIONS.map(([value, text]) => el('option', { value, text, selected: value === v.status })));
  const specs = el('textarea', { rows: '6', placeholder: 'Procesador: Intel Core i5-1335U\nRAM: 16 GB DDR4' });
  specs.value = (v.specs || []).map((s) => `${s.label}: ${s.value}`).join('\n');
  const attributes = el('textarea', { rows: '3', placeholder: 'ram=16 GB\nprocesador=Intel' });
  attributes.value = Object.entries(v.attributes || {}).map(([k, val]) => `${k}=${val}`).join('\n');
  const hue = el('input', { type: 'range', min: '0', max: '359', value: String(v.hue) });
  const swatch = el('span', { class: 'swatch' });
  const paint = () => { swatch.style.background = `linear-gradient(135deg, hsl(${hue.value} 70% 40%), hsl(${(Number(hue.value) + 40) % 360} 75% 24%))`; };
  hue.addEventListener('input', paint); paint();
  const active = el('input', { type: 'checkbox', checked: v.active });
  const stock = el('input', { type: 'number', min: '0', max: '100000', value: String(v.stock) });
  const full = (f) => { f.classList.add('field--full'); return f; };
  const grid = el('div', { class: 'form-grid' },
    field('SKU', sku), field('Nombre', name), field('Marca', brand), field('Modelo', model),
    full(field('Descripción breve', description, 'Se muestra en las tarjetas del catálogo.')),
    full(field('Descripción detallada', longDescription)),
    field('Subcategoría', category), field('Estado comercial', status),
    field('Precio (S/)', price), field('Precio anterior (S/)', compare, 'Déjalo vacío para no mostrar precio tachado.'),
    full(field('Especificaciones técnicas', specs, 'Una por línea, con el formato «Etiqueta: valor».')),
    full(field('Filtros técnicos', attributes, 'Una por línea, «clave=valor». Claves: procesador, ram, almacenamiento, gpu, pantalla, resolucion, frecuencia, conectividad, bateria, capacidad, potencia, formato.')),
    field('Tono de la tarjeta', hue), el('div', { class: 'field' }, el('span', { class: 'field__help', text: 'Vista previa' }), swatch),
    product ? null : field('Stock inicial', stock),
    el('label', { class: 'switch' }, active, 'Visible en la tienda'));
  const parseSpecs = () => specs.value.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const index = line.indexOf(':');
    return index > 0 ? { label: line.slice(0, index).trim(), value: line.slice(index + 1).trim() } : { label: line, value: '' };
  });
  const parseAttributes = () => Object.fromEntries(attributes.value.split('\n').map((line) => line.trim()).filter(Boolean)
    .map((line) => line.split('=').map((part) => part.trim())).filter(([key, val]) => key && val));
  const read = () => ({ sku: sku.value.trim(), name: name.value.trim(), brand: brand.value.trim(), model: model.value.trim(),
    description: description.value.trim(), longDescription: longDescription.value.trim(), categoryId: Number(category.value),
    price: price.value.trim().replace(',', '.'), compareAtPrice: compare.value.trim() ? compare.value.trim().replace(',', '.') : 0,
    status: status.value, specs: parseSpecs(), attributes: parseAttributes(), active: active.checked, hue: Number(hue.value), stock: Number(stock.value) });
  return { grid, read };
}

function categoryModal(categories) {
  const name = el('input', { maxlength: '80' });
  const slug = el('input', { maxlength: '48', placeholder: 'mi-categoria' });
  name.addEventListener('input', () => { slug.value = slugify(name.value); });
  const parent = el('select', {}, el('option', { value: '', text: '— Categoría principal —' }),
    categories.filter((c) => c.parentId === null && c.active).map((c) => el('option', { value: String(c.id), text: c.name })));
  const errorNode = formError();
  const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Crear categoría' });
  const dialog = modal('Nueva categoría', el('div', {}, field('Nombre', name), field('Slug (URL)', slug),
    field('Pertenece a', parent, 'Elige una categoría principal para crear una subcategoría.'), errorNode),
  { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => adminApi.createCategory(slug.value, name.value, parent.value ? Number(parent.value) : null), 'Categoría creada')
      .then(() => { try { sessionStorage.removeItem('hexaorders.categories.v6'); } catch { /* sin almacenamiento */ } dialog.close(); rerender(); }).catch(() => {});
  });
}

async function products(root) {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  const create = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Nuevo producto' });
  const newCategory = el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Nueva categoría' });
  shell(root, 'productos', el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Catálogo' }),
    el('div', { class: 'row-actions' }, newCategory, create)), body));
  const [list, categories] = await Promise.all([adminApi.products(), adminApi.categories()]);
  const reload = () => rerender();
  const openForm = (product) => {
    const form = productForm(categories, product);
    const errorNode = formError();
    const save = el('button', { type: 'button', class: 'btn btn--primary', text: product ? 'Guardar cambios' : 'Crear producto' });
    const dialog = modal(product ? `Editar ${product.name}` : 'Nuevo producto', el('div', {}, form.grid, errorNode),
      { wide: true, actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
    save.addEventListener('click', async () => {
      const value = form.read();
      await submitting(save, errorNode, () => (product ? adminApi.updateProduct(product.id, value) : adminApi.createProduct(value)),
        product ? 'Producto actualizado' : 'Producto creado').then(() => { dialog.close(); reload(); }).catch(() => {});
    });
  };
  const openStock = (product) => {
    const delta = el('input', { type: 'number', value: '10', step: '1' });
    const reason = el('input', { maxlength: '200', value: 'Reposición de proveedor' });
    const errorNode = formError();
    const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Aplicar ajuste' });
    const dialog = modal(`Ajustar stock · ${product.name}`, el('div', {}, el('p', { class: 'muted small', text: `Stock actual: ${product.stock}. Usa valores negativos para mermas.` }),
      field('Cantidad (+/−)', delta), field('Motivo (queda auditado)', reason), errorNode),
      { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
    save.addEventListener('click', async () => {
      await submitting(save, errorNode, () => adminApi.adjustStock(product.id, Number(delta.value), reason.value), 'Stock actualizado')
        .then(() => { dialog.close(); rerender(); }).catch(() => {});
    });
  };
  create.addEventListener('click', () => openForm(null));
  newCategory.addEventListener('click', () => categoryModal(categories));
  const draw = (filter) => body.replaceChildren(paginated(list.filter((p) => !filter || `${p.name} ${p.sku} ${p.brand} ${p.model} ${p.categoryName}`.toLowerCase().includes(filter)),
    (page) => table(['Producto', 'Categoría', 'Precio', 'Stock', 'Estado', ''], page.map((p) => el('tr', {},
    el('td', {}, el('strong', { text: p.name }), el('span', { class: 'table__id', text: `${p.sku} · ${p.brand}` })),
    el('td', { text: p.categoryName }),
    el('td', { class: 'mono' }, money(p.listPrice), p.compareAtPrice ? el('small', { class: 'price__old', text: ` ${money(p.compareAtPrice)}` }) : null),
    el('td', {}, el('span', { class: p.stock <= 5 ? 'badge badge--warn' : 'badge badge--neutral', text: String(p.stock) })),
    el('td', {}, el('span', { class: p.active ? 'badge badge--ok' : 'badge badge--neutral', text: p.active ? 'Activo' : 'Oculto' })),
    el('td', {}, el('div', { class: 'row-actions' },
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Stock', onclick: () => openStock(p) }),
      el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Editar', onclick: () => openForm(p) })))))), { pageSize: 10, label: 'productos' }));
  body.before(el('div', { class: 'catalog-toolbar' }, searchBox('Buscar por nombre, SKU, marca, modelo o categoría', draw)));
  draw('');
}

// ---- Categorías -------------------------------------------------------------------------
async function categoriesPage(root) {
  const body = el('div', {}, skeletons(4, 'skeleton--row'));
  const create = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Nueva categoría' });
  shell(root, 'categorias', el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Categorías de la tienda' }), create), body));
  const [categories, list] = await Promise.all([adminApi.categories(), adminApi.products()]);
  create.addEventListener('click', () => categoryModal(categories));
  const mains = categories.filter((c) => c.parentId === null).sort((a, b) => Number(b.active) - Number(a.active) || a.position - b.position);
  const stats = (ids) => {
    const items = list.filter((p) => ids.includes(p.categoryId));
    return [items.length, items.filter((p) => p.active).length, items.reduce((s, p) => s + p.stock, 0)];
  };
  const rows = [];
  mains.forEach((main) => {
    const subs = categories.filter((c) => c.parentId === main.id).sort((a, b) => a.position - b.position);
    const [count, active, units] = stats([main.id, ...subs.map((s) => s.id)]);
    rows.push(el('tr', { class: 'row--group' },
      el('td', {}, el('strong', { text: main.name }), main.active ? null : el('span', { class: 'badge badge--neutral', text: ' Oculta' })),
      el('td', { class: 'mono small', text: main.slug }), el('td', { class: 'mono', text: String(subs.length) }),
      el('td', { class: 'mono', text: String(count) }), el('td', { class: 'mono', text: String(active) }), el('td', { class: 'mono', text: String(units) }),
      el('td', {}, main.active ? el('a', { class: 'btn btn--ghost btn--sm', href: `/catalogo.html?categoria=${main.slug}`, text: 'Ver en la tienda' }) : null)));
    subs.forEach((sub) => {
      const [subCount, subActive, subUnits] = stats([sub.id]);
      rows.push(el('tr', {}, el('td', { class: 'cell--indent', text: `↳ ${sub.name}` }), el('td', { class: 'mono small', text: sub.slug }), el('td', { text: '' }),
        el('td', { class: 'mono', text: String(subCount) }), el('td', { class: 'mono', text: String(subActive) }), el('td', { class: 'mono', text: String(subUnits) }),
        el('td', {}, el('a', { class: 'btn btn--ghost btn--sm', href: `/catalogo.html?categoria=${sub.slug}`, text: 'Ver' }))));
    });
  });
  body.replaceChildren(el('p', { class: 'muted small', text: `${mains.filter((m) => m.active).length} categorías principales y ${categories.filter((c) => c.parentId !== null).length} subcategorías. Las categorías ocultas conservan sus productos y pedidos anteriores.` }),
    table(['Categoría', 'Slug', 'Subcategorías', 'Productos', 'Activos', 'Unidades en stock', ''], rows));
}

// ---- Promociones ------------------------------------------------------------------------
const PROMO_STATE = { RUNNING: ['Vigente', 'ok'], SCHEDULED: ['Programada', 'warn'], EXPIRED: ['Finalizada', 'neutral'], INACTIVE: ['Desactivada', 'neutral'] };
const toLocalInput = (iso) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };

function promotionModal(categories, products, promotion = null) {
  const now = new Date();
  const v = promotion || { name: '', slug: '', description: '', discountPercent: 10, startsAt: now.toISOString(),
    endsAt: new Date(now.getTime() + 14 * 86400000).toISOString(), active: true, categoryIds: [], productIds: [] };
  const name = el('input', { maxlength: '80', value: v.name });
  const slug = el('input', { maxlength: '48', value: v.slug, placeholder: 'semana-gaming' });
  if (!promotion) name.addEventListener('input', () => { slug.value = slugify(name.value); });
  const description = el('input', { maxlength: '300', value: v.description });
  const percent = el('input', { type: 'number', min: '1', max: '90', value: String(v.discountPercent) });
  const starts = el('input', { type: 'datetime-local', value: toLocalInput(v.startsAt) });
  const ends = el('input', { type: 'datetime-local', value: toLocalInput(v.endsAt) });
  const active = el('input', { type: 'checkbox', checked: v.active });
  const mains = categories.filter((c) => c.parentId === null && c.active).sort((a, b) => a.position - b.position);
  const boxes = mains.map((main) => {
    const subs = categories.filter((c) => c.parentId === main.id).sort((a, b) => a.position - b.position);
    const option = (c, cls) => el('label', { class: `filter-option ${cls}` }, el('input', { type: 'checkbox', value: String(c.id), checked: v.categoryIds.includes(c.id) }), el('span', { text: c.name }));
    return el('details', { class: 'filter-group', open: v.categoryIds.some((id) => id === main.id || subs.some((s) => s.id === id)) },
      el('summary', { text: main.name }), el('div', { class: 'filter-group__body' }, option(main, 'filter-option--main'), subs.map((s) => option(s, ''))));
  });
  const skus = el('textarea', { rows: '2', placeholder: 'SKU separados por comas (opcional)' });
  skus.value = products.filter((p) => v.productIds.includes(p.id)).map((p) => p.sku).join(', ');
  const errorNode = formError();
  const save = el('button', { type: 'button', class: 'btn btn--primary', text: promotion ? 'Guardar cambios' : 'Crear promoción' });
  const full = (f) => { f.classList.add('field--full'); return f; };
  const dialog = modal(promotion ? `Editar ${promotion.name}` : 'Nueva promoción', el('div', {},
    el('div', { class: 'form-grid' }, field('Nombre', name), field('Identificador (URL)', slug), full(field('Descripción', description)),
      field('Descuento (%)', percent), el('label', { class: 'switch' }, active, 'Activa'), field('Inicio', starts), field('Fin', ends),
      full(el('div', { class: 'field' }, el('span', { class: 'field__label', text: 'Categorías incluidas (las principales incluyen sus subcategorías)' }),
        el('div', { class: 'promo-targets' }, boxes))),
      full(field('Productos específicos', skus))), errorNode),
  { wide: true, actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
  save.addEventListener('click', async () => {
    const categoryIds = [...dialog.root.querySelectorAll('.promo-targets input:checked')].map((input) => Number(input.value));
    const wanted = skus.value.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean);
    const productIds = products.filter((p) => wanted.includes(p.sku)).map((p) => p.id);
    const missing = wanted.filter((sku) => !products.some((p) => p.sku === sku));
    const payload = { slug: slug.value.trim(), name: name.value.trim(), description: description.value.trim(), discountPercent: Number(percent.value),
      startsAt: new Date(starts.value).toISOString(), endsAt: new Date(ends.value).toISOString(), active: active.checked, categoryIds, productIds };
    await submitting(save, errorNode, async () => {
      if (missing.length) throw new Error(`SKU no encontrado: ${missing.join(', ')}`);
      return promotion ? adminApi.updatePromotion(promotion.id, payload) : adminApi.createPromotion(payload);
    }, promotion ? 'Promoción actualizada' : 'Promoción creada').then(() => { dialog.close(); rerender(); }).catch(() => {});
  });
}

async function promotionsPage(root) {
  const body = el('div', {}, skeletons(4, 'skeleton--row'));
  const create = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Nueva promoción' });
  shell(root, 'promociones', el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Campañas comerciales' }), create), body));
  const [promotions, categories, products] = await Promise.all([adminApi.promotions(), adminApi.categories(), adminApi.products()]);
  create.addEventListener('click', () => promotionModal(categories, products));
  const names = (ids) => ids.map((id) => (categories.find((c) => c.id === id) || {}).name).filter(Boolean).join(', ');
  body.replaceChildren(promotions.length ? table(['Promoción', 'Descuento', 'Vigencia', 'Aplica a', 'Productos', 'Estado', ''], promotions.map((p) => {
    const [label, tone] = PROMO_STATE[p.state] || [p.state, 'neutral'];
    return el('tr', {},
      el('td', {}, el('strong', { text: p.name }), el('span', { class: 'table__id', text: p.slug })),
      el('td', { class: 'mono', text: `${p.discountPercent}%` }),
      el('td', { class: 'small', text: `${formatDateTime(p.startsAt)} → ${formatDateTime(p.endsAt)}` }),
      el('td', { class: 'small', text: [names(p.categoryIds), p.productIds.length ? `${p.productIds.length} producto(s)` : ''].filter(Boolean).join(' · ') }),
      el('td', { class: 'mono', text: String(p.products) }),
      el('td', {}, el('span', { class: `badge badge--${tone}`, text: label })),
      el('td', {}, el('div', { class: 'row-actions' },
        el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Editar', onclick: () => promotionModal(categories, products, p) }),
        el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: p.active ? 'Desactivar' : 'Activar', onclick: async () => {
          try { await adminApi.setPromotionActive(p.id, !p.active); toast(p.active ? 'Promoción desactivada' : 'Promoción activada', 'ok'); rerender(); }
          catch (error) { toast(errorMessage(error), 'warn'); }
        } }))));
  })) : emptyState('Sin promociones', 'Crea la primera campaña con el botón «Nueva promoción».', 'percent'));
}

// ---- Inventario -------------------------------------------------------------------------
async function inventoryPage(root) {
  const summary = el('div', { class: 'kpis' }, skeletons(4, 'skeleton--row'));
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  const filter = el('select', { 'aria-label': 'Filtrar inventario' }, el('option', { value: 'all', text: 'Todos los productos' }),
    el('option', { value: 'low', text: 'Stock bajo (≤ 5)' }), el('option', { value: 'out', text: 'Agotados' }));
  filter.style.width = 'auto';
  shell(root, 'inventario', summary, el('article', { class: 'card card--spaced' },
    el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Existencias' }), filter), body));
  const list = await adminApi.products();
  summary.replaceChildren(
    kpiTile('Productos', list.length), kpiTile('Unidades en stock', list.reduce((s, p) => s + p.stock, 0), 'violet'),
    kpiTile('Stock bajo (≤ 5)', list.filter((p) => p.stock > 0 && p.stock <= 5).length, 'warn'),
    kpiTile('Agotados', list.filter((p) => p.stock === 0).length, list.some((p) => p.stock === 0) ? 'error' : ''),
    kpiTile('Valor del inventario', money(list.reduce((s, p) => s + Number(p.listPrice) * p.stock, 0)), 'ok'));
  const adjust = (product) => {
    const delta = el('input', { type: 'number', value: '10', step: '1' });
    const reason = el('input', { maxlength: '200', value: 'Reposición de proveedor' });
    const errorNode = formError();
    const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Aplicar ajuste' });
    const dialog = modal(`Ajustar stock · ${product.name}`, el('div', {}, el('p', { class: 'muted small', text: `Stock actual: ${product.stock}. Usa valores negativos para mermas; el stock nunca queda negativo.` }),
      field('Cantidad (+/−)', delta), field('Motivo (queda auditado)', reason), errorNode),
      { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
    save.addEventListener('click', async () => {
      await submitting(save, errorNode, () => adminApi.adjustStock(product.id, Number(delta.value), reason.value), 'Stock actualizado')
        .then(() => { dialog.close(); rerender(); }).catch(() => {});
    });
  };
  const draw = () => {
    const rows = list.filter((p) => filter.value === 'all' || (filter.value === 'low' ? p.stock <= 5 : p.stock === 0))
      .sort((a, b) => a.stock - b.stock);
    body.replaceChildren(rows.length ? paginated(rows, (page) => table(['Producto', 'SKU', 'Stock', 'Nivel', ''], page.map((p) => el('tr', {},
      el('td', {}, el('strong', { text: p.name }), el('span', { class: 'table__id', text: p.categoryName })),
      el('td', { class: 'mono small', text: p.sku }),
      el('td', { class: 'mono', text: String(p.stock) }),
      el('td', {}, el('div', { class: 'meter', role: 'img', 'aria-label': `${p.stock} unidades` },
        (() => { const bar = el('span', { class: p.stock === 0 ? 'is-error' : p.stock <= 5 ? 'is-warn' : '' }); bar.style.width = `${Math.min(100, p.stock * 2)}%`; return bar; })())),
      el('td', {}, el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Ajustar', onclick: () => adjust(p) }))))), { pageSize: 12, label: 'productos' })
      : emptyState('Nada que mostrar', 'No hay productos en este filtro.', 'box'));
  };
  filter.addEventListener('change', draw);
  draw();
}

// ---- Pedidos ---------------------------------------------------------------------
export function orderDetailModal(id, api, onChanged) {
  const body = el('div', {}, skeletons(3, 'skeleton--row'));
  const dialog = modal('Detalle del pedido', body, { wide: true });
  (async () => {
    try {
      const { order, history, delivery, allowedTransitions } = await api.order(id);
      const note = el('input', { maxlength: '500', placeholder: 'Nota opcional (queda en el historial)' });
      const errorNode = formError();
      const buttons = allowedTransitions.map((target) => {
        const button = el('button', { type: 'button', class: target === 'REJECTED' || target === 'CANCELLED' ? 'btn btn--ghost btn--sm' : 'btn btn--primary btn--sm', text: TRANSITION_VERBS[target] || target });
        button.addEventListener('click', async () => {
          await submitting(button, errorNode, () => api.transition(id, target, note.value), `Pedido: ${LIFECYCLE[target].label}`)
            .then(() => { dialog.close(); if (onChanged) onChanged(); }).catch(() => {});
        });
        return button;
      });
      body.replaceChildren(
        el('div', { class: 'row-actions', style: null }, lifecycleBadge(order.status), channelBadge(order.channel),
          el('span', { class: 'badge badge--neutral', text: `Riesgo ${order.riskScore}/100 · ${order.riskDecision}` })),
        el('p', { class: 'muted small', text: `${order.id} · cliente ${order.customerId} · ${formatDateTime(order.createdAt)} · versión ${order.version}` }),
        order.lines.length ? table(['Artículo', 'Cant.', 'Total'], order.lines.map((l) => el('tr', {}, el('td', { text: l.productName }), el('td', { text: String(l.quantity) }), el('td', { class: 'mono', text: money(l.lineTotal) }))))
          : el('p', { class: 'muted small', text: 'Pedido de la API original (laboratorio): sin líneas de producto.' }),
        el('div', { class: 'summary-row summary-row--total' }, el('span', { text: 'Total' }), el('span', { text: money(order.amount) })),
        delivery ? el('div', { class: 'notice-box' }, el('strong', { text: 'Entrega: ' }), `${formatDateTime(delivery.start)} · ${delivery.assigneeName}`) : null,
        el('h3', { class: 'card__title card__title--spaced', text: 'Historial' }),
        el('ol', { class: 'timeline' }, history.length
          ? history.map((h, i) => el('li', { class: i === history.length - 1 ? 'is-current' : '' },
            el('strong', {}, lifecycleBadge(h.to)), el('small', { text: `${formatDateTime(h.at)} · ${h.actor}${h.note ? ` · ${h.note}` : ''}` })))
          // Pedidos de la API original creados sin historial de transiciones: se muestra su estado actual real.
          : [el('li', { class: 'is-current' }, el('strong', {}, lifecycleBadge(order.status)),
            el('small', { text: `${formatDateTime(order.createdAt)} · estado actual (pedido de la API original, sin transiciones registradas todavía)` }))]),
        buttons.length ? el('div', {}, el('h3', { class: 'card__title card__title--spaced', text: 'Acciones permitidas para tu rol' }), note,
          el('div', { class: 'row-actions', style: null }, buttons), errorNode)
          : el('p', { class: 'muted small', text: 'No hay acciones disponibles para tu rol en este estado.' }));
      body.querySelectorAll('.row-actions').forEach((r) => { r.style.justifyContent = 'flex-start'; r.style.marginTop = '10px'; });
    } catch (error) {
      body.replaceChildren(emptyState('No se pudo cargar el pedido', errorMessage(error)));
    }
  })();
  return dialog;
}

export function ordersTable(orders, api, onChanged) {
  if (!orders.length) return emptyState('Sin pedidos en este filtro', 'Cambia el filtro de estado.', 'box');
  return table(['Pedido', 'Cliente', 'Canal', 'Total', 'Riesgo', 'Estado', 'Creado', ''], orders.map((o) => el('tr', {},
    el('td', {}, el('span', { class: 'table__id', text: o.id.slice(0, 8) })),
    el('td', { text: o.customerId }),
    el('td', {}, channelBadge(o.channel)),
    el('td', { class: 'mono', text: money(o.amount) }),
    el('td', { class: 'mono', text: `${o.riskScore}/100` }),
    el('td', {}, lifecycleBadge(o.status)),
    el('td', { title: formatDateTime(o.createdAt), text: formatRelative(o.createdAt) }),
    el('td', {}, el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Gestionar', onclick: () => orderDetailModal(o.id, api, onChanged) })))));
}

export function statusFilter(onChange, initial = []) {
  const select = el('select', { 'aria-label': 'Filtrar por estado' },
    el('option', { value: '', text: 'Todos los estados' }),
    el('option', { value: 'RISK_REVIEW,RISK_APPROVED', text: 'Pendientes de confirmar' }),
    el('option', { value: 'CONFIRMED,PREPARING', text: 'En preparación' }),
    el('option', { value: 'READY_FOR_DISPATCH,IN_TRANSIT', text: 'Despacho' }),
    ...Object.entries(LIFECYCLE).map(([k, v]) => el('option', { value: k, text: v.label })));
  select.value = initial.join(',');
  select.style.width = 'auto';
  select.addEventListener('change', () => onChange(select.value ? select.value.split(',') : []));
  return select;
}

async function orders(root) {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  let statuses = [];
  const load = async () => {
    body.replaceChildren(...skeletons(6, 'skeleton--row'));
    try { body.replaceChildren(ordersTable(await operationsApi.queue(statuses), operationsApi, load)); }
    catch (error) { body.replaceChildren(emptyState('No se pudieron cargar los pedidos', errorMessage(error))); }
  };
  shell(root, 'pedidos', el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Todos los pedidos (portal y laboratorio)' }),
    statusFilter((value) => { statuses = value; load(); })), body));
  await load();
}

// ---- Usuarios y roles ------------------------------------------------------------
async function users(root) {
  const body = el('div', {}, skeletons(6, 'skeleton--row'));
  shell(root, 'usuarios', el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Usuarios, roles y acceso' }),
    el('p', { class: 'muted small', text: 'Los cambios de rol se aplican en el siguiente inicio de sesión del usuario y quedan auditados.' }), body));
  const list = await adminApi.users();
  const roleFilter = el('select', { 'aria-label': 'Filtrar por rol' }, el('option', { value: '', text: 'Todos los roles' }),
    ...Object.entries(ROLE_LABELS).map(([value, label]) => el('option', { value, text: label })));
  roleFilter.style.width = 'auto';
  let text = '';
  const draw = () => body.replaceChildren(paginated(list.filter((u) => (!roleFilter.value || u.roles.includes(roleFilter.value))
    && (!text || `${u.username} ${u.displayName} ${u.email}`.toLowerCase().includes(text))), (page) => usersTable(page), { pageSize: 10, label: 'usuarios' }));
  body.before(el('div', { class: 'catalog-toolbar' }, searchBox('Buscar usuario, nombre o correo', (value) => { text = value; draw(); }), roleFilter));
  roleFilter.addEventListener('change', draw);
  draw();
}

function usersTable(list) {
  return table(['Usuario', 'Correo', 'Roles', 'Activo', 'Alta'], list.map((u) => {
    const checks = el('div', { class: 'role-checks' }, Object.keys(ROLE_LABELS).map((role) => {
      const input = el('input', { type: 'checkbox', checked: u.roles.includes(role), 'aria-label': `${ROLE_LABELS[role]} para ${u.username}` });
      input.addEventListener('change', async () => {
        const roles = Array.from(checks.querySelectorAll('input')).map((i, idx) => (i.checked ? Object.keys(ROLE_LABELS)[idx] : null)).filter(Boolean);
        try { await adminApi.roles(u.id, roles); toast(`Roles de ${u.username} actualizados`, 'ok'); }
        catch (error) { input.checked = !input.checked; toast(errorMessage(error), 'error'); }
      });
      return el('label', {}, input, ROLE_LABELS[role]);
    }));
    const enabled = el('input', { type: 'checkbox', checked: u.enabled, 'aria-label': `Cuenta activa de ${u.username}` });
    enabled.addEventListener('change', async () => {
      try { await adminApi.enabled(u.id, enabled.checked); toast(`${u.username}: ${enabled.checked ? 'activada' : 'desactivada'}`, 'ok'); }
      catch (error) { enabled.checked = !enabled.checked; toast(errorMessage(error), 'error'); }
    });
    return el('tr', {},
      el('td', {}, el('strong', { text: u.displayName }), el('span', { class: 'table__id', text: `@${u.username}` })),
      el('td', { text: u.email }), el('td', {}, checks),
      el('td', {}, el('label', { class: 'switch' }, enabled, u.enabled ? 'Sí' : 'No')),
      el('td', { text: formatRelative(u.createdAt) }));
  }));
}

// ---- Incidencias -------------------------------------------------------------------
export async function incidentsPanel(container, canResolve = true) {
  const list = el('div', {}, skeletons(4, 'skeleton--row'));
  const filter = el('select', { 'aria-label': 'Estado' }, el('option', { value: 'OPEN', text: 'Abiertas' }), el('option', { value: 'RESOLVED', text: 'Resueltas' }), el('option', { value: '', text: 'Todas' }));
  filter.style.width = 'auto';
  const report = el('button', { type: 'button', class: 'btn btn--primary btn--sm', text: 'Registrar incidencia' });
  container.append(el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Incidencias' }),
    el('div', { class: 'row-actions' }, filter, report)), list));
  const load = async () => {
    try {
      const items = await incidentsApi.list(filter.value || undefined);
      list.replaceChildren(items.length ? table(['Categoría', 'Descripción', 'Pedido', 'Reportó', 'Estado', ''], items.map((i) => el('tr', {},
        el('td', {}, el('span', { class: 'badge badge--warn', text: i.category })),
        el('td', { text: i.description, style: null }),
        el('td', {}, i.orderId ? el('span', { class: 'table__id', text: i.orderId.slice(0, 8) }) : '—'),
        el('td', { text: `${i.reportedBy} · ${formatRelative(i.createdAt)}` }),
        el('td', {}, el('span', { class: i.status === 'OPEN' ? 'badge badge--error' : 'badge badge--ok', text: i.status === 'OPEN' ? 'Abierta' : 'Resuelta' })),
        el('td', {}, i.status === 'OPEN' && canResolve ? el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Resolver', onclick: () => {
          const resolution = el('textarea', { maxlength: '500' });
          const errorNode = formError();
          const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Marcar resuelta' });
          const dialog = modal('Resolver incidencia', el('div', {}, el('p', { class: 'muted small', text: i.description }), field('Resolución', resolution), errorNode),
            { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
          save.addEventListener('click', async () => { await submitting(save, errorNode, () => incidentsApi.resolve(i.id, resolution.value), 'Incidencia resuelta').then(() => { dialog.close(); load(); }).catch(() => {}); });
        } }) : (i.resolution || '')))))
        : emptyState('Sin incidencias', 'No hay incidencias en este filtro.', 'shield'));
    } catch (error) {
      list.replaceChildren(emptyState('No se pudieron cargar las incidencias', errorMessage(error)));
    }
  };
  filter.addEventListener('change', load);
  report.addEventListener('click', () => reportIncidentModal(null, load));
  await load();
}

export function reportIncidentModal(orderId, onDone) {
  const order = el('input', { value: orderId || '', placeholder: 'UUID del pedido (opcional)', maxlength: '36' });
  const category = el('select', {}, [['DELAY', 'Retraso'], ['DAMAGED', 'Daño'], ['ADDRESS', 'Dirección'], ['STOCK', 'Stock'], ['OTHER', 'Otra']].map(([v, t]) => el('option', { value: v, text: t })));
  const description = el('textarea', { maxlength: '1000' });
  const errorNode = formError();
  const save = el('button', { type: 'button', class: 'btn btn--primary', text: 'Registrar' });
  const dialog = modal('Registrar incidencia', el('div', {}, field('Pedido', order), field('Categoría', category), field('Descripción', description), errorNode),
    { actions: [el('button', { type: 'button', class: 'btn btn--ghost', text: 'Cancelar', onclick: () => dialog.close() }), save] });
  save.addEventListener('click', async () => {
    await submitting(save, errorNode, () => incidentsApi.report(order.value.trim() || null, category.value, description.value), 'Incidencia registrada')
      .then(() => { dialog.close(); if (onDone) onDone(); }).catch(() => {});
  });
}

async function incidents(root) {
  const container = el('div', {});
  shell(root, 'incidencias', container);
  await incidentsPanel(container);
}

// ---- Auditoría y reportes ------------------------------------------------------------
async function audit(root) {
  const body = el('div', {}, skeletons(8, 'skeleton--row'));
  shell(root, 'auditoria', el('article', { class: 'card' }, el('h3', { class: 'card__title', text: 'Registro de auditoría (últimos 200 eventos)' }), body));
  const events = await adminApi.audit(200);
  const actions = [...new Set(events.map((e) => e.action))].sort();
  const actionFilter = el('select', { 'aria-label': 'Filtrar por acción' }, el('option', { value: '', text: 'Todas las acciones' }),
    ...actions.map((a) => el('option', { value: a, text: a })));
  actionFilter.style.width = 'auto';
  let text = '';
  const draw = () => body.replaceChildren(paginated(events.filter((e) => (!actionFilter.value || e.action === actionFilter.value)
    && (!text || `${e.actor} ${e.target} ${e.details}`.toLowerCase().includes(text))),
  (page) => table(['Fecha', 'Actor', 'Acción', 'Objetivo', 'Detalle'], page.map((e) => el('tr', {},
    el('td', { text: formatDateTime(e.at) }), el('td', { text: e.actor }),
    el('td', {}, el('span', { class: 'badge badge--neutral', text: e.action })), el('td', { class: 'mono small', text: e.target }),
    el('td', { class: 'small', text: e.details })))), { pageSize: 15, label: 'eventos' }));
  body.before(el('div', { class: 'catalog-toolbar' }, searchBox('Buscar actor, objetivo o detalle', (value) => { text = value; draw(); }), actionFilter));
  actionFilter.addEventListener('change', draw);
  draw();
}

async function reports(root) {
  const body = el('div', {}, skeletons(4, 'skeleton--row'));
  const top = el('div', {}, skeletons(4, 'skeleton--row'));
  const exportBtn = el('button', { type: 'button', class: 'btn btn--ghost btn--sm', text: 'Exportar CSV' });
  shell(root, 'reportes',
    el('article', { class: 'card' }, el('div', { class: 'card__head' }, el('h3', { class: 'card__title', text: 'Ventas por categoría (sin pedidos cancelados ni rechazados)' }), exportBtn), body),
    el('article', { class: 'card card--spaced' }, el('h3', { class: 'card__title', text: 'Productos más vendidos' }), top));
  const d = await adminApi.dashboard();
  const totals = {};
  d.salesByCategory.forEach((c) => { totals[c.currency] = (totals[c.currency] || 0) + Number(c.revenue); });
  body.replaceChildren(d.salesByCategory.length
    ? table(['Categoría', 'Moneda', 'Unidades', 'Ingresos', '% del total'], d.salesByCategory.map((c) => el('tr', {},
      el('td', { text: c.category }), el('td', { class: 'mono small', text: c.currency }), el('td', { class: 'mono', text: String(c.units) }),
      el('td', { class: 'mono', text: money(c.revenue, c.currency) }),
      el('td', { class: 'mono', text: totals[c.currency] ? `${((Number(c.revenue) / totals[c.currency]) * 100).toFixed(1)} %` : '—' }))))
    : emptyState('Sin ventas todavía', 'Los reportes se llenan con los pedidos de la tienda.', 'chart'));
  top.replaceChildren(d.topProducts.length
    ? table(['Producto', 'Unidades', 'Ingresos'], d.topProducts.map((p) => el('tr', {},
      el('td', {}, el('a', { href: link(`/producto/${p.productId}`), text: p.name })), el('td', { class: 'mono', text: String(p.units) }),
      el('td', { class: 'mono', text: money(p.revenue, p.currency) }))))
    : emptyState('Sin ventas todavía', 'Aquí verás los productos con más unidades vendidas.', 'chart'));
  exportBtn.addEventListener('click', () => downloadCsv('hexaorders-ventas-por-categoria.csv',
    [['categoria', 'moneda', 'unidades', 'ingresos'], ...d.salesByCategory.map((c) => [c.category, c.currency, c.units, c.revenue])]));
}

const RENDER = {
  panel, productos: products, categorias: categoriesPage, inventario: inventoryPage, promociones: promotionsPage, pedidos: orders, usuarios: users,
  incidencias: incidents, auditoria: audit, reportes: reports,
};
void link;
route('/admin', (root) => panel(root), { roles: ['ADMIN'], title: 'Administración' });
route('/admin/:tab', (root, { tab }) => (RENDER[tab] || panel)(root), { roles: ['ADMIN'], title: 'Administración' });
