// POLYMER — internacionalización del frontend (español por defecto; en, fr, it, pt, de).
//
// Patrón gettext: la CLAVE es el texto en español escrito en el código, t('Agregar al carrito').
// Cada idioma tiene un catálogo central /i18n/<idioma>.json { "texto en español": "traducción" }.
// El español no necesita catálogo y cualquier texto sin traducir se muestra en español (alternativa
// válida, nunca un hueco). tools/check_i18n.py verifica que los cinco catálogos cubran todos los t().
//
// Rutas: /<idioma>/<ruta-localizada> (p. ej. /en/products, /fr/produits). Las resuelve el servidor
// (LocalizedPagesController) a partir de /i18n/routes.json, que genera tools/generate_pages.py.
// Agregar un idioma: añadirlo a LANGUAGES (aquí, en generate_pages.py y en el servidor), crear su
// catálogo y sus rutas, y ejecutar tools/check_i18n.py.

export const LANGUAGES = Object.freeze([
  { code: 'es', name: 'Español', locale: 'es-PE' },
  { code: 'en', name: 'English', locale: 'en-US' },
  { code: 'fr', name: 'Français', locale: 'fr-FR' },
  { code: 'it', name: 'Italiano', locale: 'it-IT' },
  { code: 'pt', name: 'Português', locale: 'pt-BR' },
  { code: 'de', name: 'Deutsch', locale: 'de-DE' },
]);
export const DEFAULT_LANG = 'es';
const CODES = LANGUAGES.map((l) => l.code);
const PREFIX = new RegExp(`^/(${CODES.join('|')})(?=/|$)`);

const prefixMatch = window.location.pathname.match(PREFIX);
/** Idioma de la página actual: el del prefijo de la URL o, sin prefijo, español. */
export const lang = prefixMatch ? prefixMatch[1] : DEFAULT_LANG;
/** true si la URL lleva prefijo de idioma (/es/…, /en/…): los enlaces internos se localizan. */
export const localized = Boolean(prefixMatch);
export const locale = (LANGUAGES.find((l) => l.code === lang) || LANGUAGES[0]).locale;

async function loadJson(url) {
  try {
    const response = await fetch(url, { credentials: 'same-origin' });
    return response.ok ? await response.json() : null;
  } catch {
    return null; // sin catálogo: la interfaz sigue en español
  }
}

// Carga inicial (top-level await): todos los módulos que importan i18n.js esperan a los catálogos,
// así que también se traducen los textos definidos a nivel de módulo.
const [catalogData, routesData] = await Promise.all([
  lang === DEFAULT_LANG ? Promise.resolve({}) : loadJson(`/i18n/${lang}.json`),
  loadJson('/i18n/routes.json'),
]);
const catalog = catalogData || {};
const routes = routesData || { languages: CODES, pages: {} };

/** Traduce un texto de la interfaz. Los marcadores {0}, {1}… se sustituyen por los argumentos. */
export function t(text, ...args) {
  const source = String(text);
  const value = Object.prototype.hasOwnProperty.call(catalog, source) ? catalog[source] : source;
  return args.length ? value.replace(/\{(\d+)\}/g, (match, index) => (index < args.length ? String(args[index]) : match)) : value;
}

/** ¿Existe traducción para este texto? (para marcar con lang="es" un contenido que no la tiene). */
export const hasTranslation = (text) => lang === DEFAULT_LANG || Object.prototype.hasOwnProperty.call(catalog, String(text));

/** Datos del catálogo (categorías, especificaciones…): igual que t(), pero sin marcadores. */
export const td = (text) => (text == null ? text : t(text));

// ---- Rutas localizadas ---------------------------------------------------------------------------
const fileBySlug = Object.fromEntries(Object.entries(routes.pages).map(([file, slugs]) => [`${lang}:${slugs[lang] ?? ''}`, file]));

/** Archivo HTML de la página actual (/catalogo.html), con o sin prefijo de idioma en la URL. */
export function currentFile() {
  if (!localized) return window.location.pathname === '/' ? '/index.html' : window.location.pathname;
  const slug = window.location.pathname.replace(PREFIX, '').replace(/^\/+|\/+$/g, '');
  return fileBySlug[`${lang}:${slug}`] || '/index.html';
}

/** URL localizada de un archivo de página en un idioma (o null si la página no tiene ruta localizada). */
export function pageUrl(file, code = lang) {
  const slugs = routes.pages[file === '/' ? '/index.html' : file];
  if (!slugs || slugs[code] === undefined) return null;
  return `/${code}/${slugs[code]}`;
}

/** Convierte un enlace interno (/catalogo.html?x=1) en su ruta localizada (/en/products?x=1). */
export function localizeHref(href) {
  if (!localized || !href) return href;
  let url;
  try { url = new URL(href, window.location.origin); } catch { return href; }
  if (url.origin !== window.location.origin || PREFIX.test(url.pathname)) return href;
  const target = pageUrl(url.pathname === '/' ? '/index.html' : url.pathname);
  return target ? target + url.search + url.hash : href;
}

/** Mantiene localizados los enlaces y formularios que crean las vistas (solo con prefijo de idioma). */
export function startLinkLocalization() {
  if (!localized) return;
  const fix = (node) => {
    if (!(node instanceof Element)) return;
    const targets = node.matches('a[href], form[action]') ? [node] : [];
    targets.push(...node.querySelectorAll('a[href], form[action]'));
    for (const element of targets) {
      const attribute = element.tagName === 'FORM' ? 'action' : 'href';
      const value = element.getAttribute(attribute);
      if (!value || value.startsWith('#') || value.startsWith('mailto:')) continue;
      const next = localizeHref(value);
      if (next !== value) element.setAttribute(attribute, next);
    }
  };
  fix(document.body);
  new MutationObserver((records) => records.forEach((record) => {
    if (record.type === 'attributes') fix(record.target);
    record.addedNodes.forEach(fix);
  })).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'action'] });
}

const STORAGE_KEY = 'polymer.lang';

/** URL equivalente de la página actual en otro idioma, conservando filtros, búsqueda y ancla. */
export function equivalentUrl(code) {
  const target = pageUrl(currentFile(), code) || `/${code}/`;
  return target + window.location.search + window.location.hash;
}

/**
 * Cambia de idioma: guarda la preferencia (y, con sesión, la del servidor para los correos) y abre
 * la misma página en el idioma elegido. El carrito, la sesión y la intro ya vista se conservan
 * porque viven en el navegador y en la cookie de sesión, que no dependen de la URL.
 */
export async function switchLanguage(code, { persist } = {}) {
  if (!CODES.includes(code)) return;
  try { localStorage.setItem(STORAGE_KEY, code); } catch { /* sin almacenamiento */ }
  if (persist) { try { await persist(code); } catch { /* la preferencia del servidor es opcional */ } }
  window.location.assign(equivalentUrl(code));
}

export const preferredLanguage = () => {
  try { return localStorage.getItem(STORAGE_KEY); } catch { return null; }
};

document.documentElement.lang = lang;
