"""Genera las páginas HTML del sitio multipágina de POLYMER desde un único registro.

Cada página es un archivo HTML real (URL propia, se puede abrir directamente y recargar) con la
misma cabecera, hojas de estilo y punto de entrada (js/page.js). El contenido lo renderiza la
vista registrada en <body data-route>, que llama a la API real. El registro PAGES es también la
fuente de tools/check_frontend.py y de las pruebas end-to-end.

Uso:
    py tools\\generate_pages.py          (Windows)   python3 tools/generate_pages.py
    py tools\\generate_pages.py --check  falla si algún HTML no coincide con el registro

Para cambiar la estructura común (head, cabecera, pie) edita TEMPLATE y vuelve a generar. Para
cambiar el contenido de una página edita su vista en static/js/views/.
"""
from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / 'services/order-api/src/main/resources/static'
FRAGMENTS = Path(__file__).resolve().parent / 'page_fragments'

# path, route, title, description, section, roles (servidor), params, extra
#   roles: [] = pública; ['auth'] = cualquier sesión; si no, roles de Spring Security.
#   parent: breadcrumb intermedio "url|texto"; active: enlace resaltado de la barra lateral.
PAGES: list[dict] = [
    # ---- Tienda (v6: contenido comercial; nada técnico ni académico)
    dict(path='index.html', route='/inicio', title='Tienda de tecnología', description='POLYMER, tecnología que conecta contigo: laptops, componentes, monitores, smartphones, gaming y smart home con envío a todo el Perú.'),
    dict(path='catalogo.html', route='/tienda', title='Catálogo', description='Catálogo de tecnología con búsqueda, filtros por marca, precio y características técnicas.'),
    dict(path='producto.html', route='/producto/:id', title='Producto', description='Ficha del producto con especificaciones técnicas, precio, disponibilidad y productos relacionados.', parent='/catalogo.html|Catálogo'),
    dict(path='ofertas.html', route='/ofertas', title='Ofertas', description='Ofertas y promociones vigentes en tecnología.'),
    dict(path='carrito.html', route='/carrito', title='Carrito', description='Tu carrito de compras.'),
    dict(path='checkout.html', route='/checkout', title='Checkout', description='Finaliza tu compra: entrega, pago y confirmación.', roles=['CUSTOMER'], parent='/carrito.html|Carrito'),
    dict(path='login.html', route='/entrar', title='Iniciar sesión', description='Inicia sesión en tu cuenta POLYMER.'),
    dict(path='registro.html', route='/registro', title='Crear cuenta', description='Crea tu cuenta POLYMER para comprar y seguir tus pedidos.'),
    dict(path='ayuda.html', route='/ayuda', title='Centro de ayuda', description='Envíos, métodos de pago, garantías, devoluciones y tu cuenta.'),
    dict(path='nosotros.html', route='/nosotros', title='Nosotros', description='Conoce POLYMER, tu tienda de tecnología: quiénes somos y nuestra historia.'),
    dict(path='contacto.html', route='/contacto', title='Contacto', description='Escríbenos: pedidos, soporte técnico, garantías y ventas corporativas.'),
    dict(path='empresa.html', route='/empresa', title='Empresa', description='Canales de atención, compra segura, métodos de pago, tiendas, cobertura de delivery y disponibilidad.'),
    dict(path='legal.html', route='/legal', title='Información legal', description='Términos y condiciones, privacidad, cookies, cambios y devoluciones, garantías y facturación.'),
    dict(path='libro-reclamaciones.html', route='/libro-reclamaciones', title='Libro de reclamaciones', description='Registra un reclamo o una queja sobre tu compra.'),
    dict(path='403.html', route='/error/403', title='Acceso restringido', description='Tu cuenta no tiene permiso para esta página.'),
    dict(path='404.html', route='/error/404', title='Página no encontrada', description='La página solicitada no existe.'),
    # ---- Portal del cliente
    dict(path='cliente/dashboard.html', route='/cliente', title='Mi cuenta', description='Resumen de tus compras y pedidos.', section='cliente', roles=['CUSTOMER']),
    dict(path='cliente/pedidos.html', route='/mis-pedidos', title='Mis pedidos', description='Historial de tus pedidos.', section='cliente', roles=['CUSTOMER']),
    dict(path='cliente/pedido-detalle.html', route='/mis-pedidos/:id', title='Detalle del pedido', description='Productos, totales, entrega, pago y cancelación.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    dict(path='cliente/seguimiento.html', route='/seguimiento/:id', title='Seguimiento', description='Estado de tu pedido paso a paso.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    dict(path='cliente/perfil.html', route='/perfil', title='Perfil y seguridad', description='Datos personales y cambio de contraseña.', section='cliente', roles=['auth']),
    dict(path='cliente/pago.html', route='/pago/:id', title='Pago del pedido', description='Paga tu pedido con tarjeta, Yape o Plin.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    dict(path='cliente/boleta.html', route='/boleta/:id', title='Boleta de venta', description='Comprobante de tu compra pagada en línea, listo para imprimir.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    # ---- Administración
    *[dict(path=f'admin/{name}.html', route='/admin/:tab', params={'tab': tab}, title=title, description=desc, section='admin', roles=['ADMIN'])
      for name, tab, title, desc in [
          ('dashboard', 'panel', 'Panel de administración', 'Indicadores reales, alertas operativas, actividad y mensajes de contacto.'),
          ('productos', 'productos', 'Productos', 'Alta y edición de productos con búsqueda y paginación.'),
          ('categorias', 'categorias', 'Categorías', 'Categorías del catálogo y su contenido.'),
          ('inventario', 'inventario', 'Inventario', 'Existencias, stock bajo y ajustes auditados.'),
          ('promociones', 'promociones', 'Promociones', 'Campañas comerciales con vigencia y descuento.'),
          ('pedidos', 'pedidos', 'Pedidos', 'Todos los pedidos con su ciclo de vida.'),
          ('usuarios', 'usuarios', 'Usuarios y roles', 'Gestión de usuarios, roles y activación.'),
          ('incidencias', 'incidencias', 'Incidencias', 'Incidencias operativas.'),
          ('reportes', 'reportes', 'Reportes', 'Ventas por categoría y productos más vendidos.'),
          ('auditoria', 'auditoria', 'Auditoría', 'Registro de auditoría con filtros.'),
      ]],
    # ---- Operaciones
    dict(path='operaciones/dashboard.html', route='/operaciones', title='Operaciones', description='Resumen de la cola de pedidos.', section='operaciones', roles=['ORDER_OPERATOR', 'ADMIN']),
    dict(path='operaciones/pedidos.html', route='/operaciones/pedidos', title='Bandeja de pedidos', description='Procesamiento y cambios de estado.', section='operaciones', roles=['ORDER_OPERATOR', 'ADMIN']),
    dict(path='operaciones/revisiones.html', route='/operaciones/revisiones', title='Revisiones de riesgo', description='Pedidos en revisión de riesgo.', section='operaciones', roles=['ORDER_OPERATOR', 'ADMIN']),
    dict(path='operaciones/incidencias.html', route='/operaciones/incidencias', title='Incidencias', description='Registro y resolución de incidencias.', section='operaciones', roles=['ORDER_OPERATOR', 'ADMIN']),
    # ---- Logística
    dict(path='logistica/dashboard.html', route='/logistica', title='Logística', description='Resumen logístico.', section='logistica', roles=['LOGISTICS_OPERATOR', 'ADMIN']),
    dict(path='logistica/calendario.html', route='/logistica/calendario', title='Calendario logístico', description='Calendario mensual, semanal y diario de entregas.', section='logistica', roles=['LOGISTICS_OPERATOR', 'ADMIN']),
    dict(path='logistica/entregas.html', route='/logistica/entregas', title='Entregas', description='Entregas programadas y estados de despacho.', section='logistica', roles=['LOGISTICS_OPERATOR', 'ADMIN']),
    dict(path='logistica/asignaciones.html', route='/logistica/asignaciones', title='Asignaciones', description='Carga semanal por responsable.', section='logistica', roles=['LOGISTICS_OPERATOR', 'ADMIN']),
    # ---- Monitoreo
    dict(path='monitoreo/dashboard.html', route='/observabilidad-tecnica', title='Monitoreo', description='Salud técnica y estado de Prometheus.', section='monitoreo', roles=['OBSERVABILITY_VIEWER', 'ADMIN']),
    dict(path='monitoreo/servicios.html', route='/monitoreo/servicios', title='Servicios', description='Targets de Prometheus y dependencias.', section='monitoreo', roles=['OBSERVABILITY_VIEWER', 'ADMIN']),
    dict(path='monitoreo/metricas.html', route='/monitoreo/metricas', title='Métricas', description='Indicadores operativos calculados por Prometheus.', section='monitoreo', roles=['OBSERVABILITY_VIEWER', 'ADMIN']),
    dict(path='monitoreo/alertas.html', route='/monitoreo/alertas', title='Alertas', description='Reglas de alerta y su estado.', section='monitoreo', roles=['OBSERVABILITY_VIEWER', 'ADMIN']),
    # ---- Documentación técnica interna (v6: fuera de la tienda, solo personal autorizado)
    dict(path='arquitectura/overview.html', route='/arquitectura/overview', title='Documentación técnica', description='Documentación técnica interna para el personal autorizado.', section='arquitectura', roles=['ADMIN', 'OBSERVABILITY_VIEWER'], fragment='arquitectura-explorer.html'),
    dict(path='arquitectura/flujo-pedidos.html', route='/arquitectura/flujo-pedidos', title='Flujo de pedidos', description='Documentación técnica interna para el personal autorizado.', section='arquitectura', roles=['ADMIN', 'OBSERVABILITY_VIEWER']),
]

# ---- Idiomas y rutas localizadas ---------------------------------------------------------------
# Rutas reales /<idioma>/<ruta> (ver LocalizedPagesController y static/js/i18n.js). Una sola página
# HTML sirve a los seis idiomas: el servidor ajusta lang, título, descripción, canonical y hreflang.
# Agregar un idioma: añadir su código aquí (y en i18n.js y LocalizedPagesController), una columna en
# SLUGS y su catálogo static/i18n/<idioma>.json; tools/check_i18n.py comprueba que esté completo.
LANGUAGES = ['es', 'en', 'fr', 'it', 'pt', 'de']
_ADMIN = ('admin',) * 6
_OPS = ('operaciones', 'operations', 'operations', 'operazioni', 'operacoes', 'betrieb')
_LOG = ('logistica', 'logistics', 'logistique', 'logistica', 'logistica', 'logistik')
_MON = ('monitoreo', 'monitoring', 'supervision', 'monitoraggio', 'monitoramento', 'monitoring')
_ARQ = ('arquitectura', 'architecture', 'architecture', 'architettura', 'arquitetura', 'architektur')


def _join(base, tail):
    return tuple(f'{b}/{t}' for b, t in zip(base, tail))


SLUGS: dict[str, tuple] = {
    'index.html': ('',) * 6,
    'catalogo.html': ('productos', 'products', 'produits', 'prodotti', 'produtos', 'produkte'),
    'producto.html': ('producto', 'product', 'produit', 'prodotto', 'produto', 'produkt'),
    'ofertas.html': ('ofertas', 'offers', 'offres', 'offerte', 'ofertas', 'angebote'),
    'carrito.html': ('carrito', 'cart', 'panier', 'carrello', 'carrinho', 'warenkorb'),
    'checkout.html': ('checkout', 'checkout', 'paiement', 'cassa', 'finalizar-compra', 'kasse'),
    'login.html': ('iniciar-sesion', 'login', 'connexion', 'accedi', 'entrar', 'anmelden'),
    'registro.html': ('registro', 'register', 'inscription', 'registrazione', 'cadastro', 'registrieren'),
    'ayuda.html': ('ayuda', 'help', 'aide', 'aiuto', 'ajuda', 'hilfe'),
    'nosotros.html': ('nosotros', 'about', 'a-propos', 'chi-siamo', 'sobre-nos', 'ueber-uns'),
    'contacto.html': ('contacto', 'contact', 'contact', 'contatti', 'contato', 'kontakt'),
    'empresa.html': ('empresa', 'company', 'entreprise', 'azienda', 'empresa', 'unternehmen'),
    'legal.html': ('legal', 'legal', 'mentions-legales', 'note-legali', 'legal', 'rechtliches'),
    'libro-reclamaciones.html': ('libro-de-reclamaciones', 'complaints-book', 'registre-des-reclamations', 'libro-reclami',
                                 'livro-de-reclamacoes', 'beschwerdebuch'),
    '403.html': ('error/403',) * 6,
    '404.html': ('error/404',) * 6,
    'cliente/dashboard.html': ('mi-cuenta', 'my-account', 'mon-compte', 'il-mio-account', 'minha-conta', 'mein-konto'),
    'cliente/pedidos.html': ('mis-pedidos', 'my-orders', 'mes-commandes', 'i-miei-ordini', 'meus-pedidos', 'meine-bestellungen'),
    'cliente/pedido-detalle.html': ('mis-pedidos/detalle', 'my-orders/detail', 'mes-commandes/detail', 'i-miei-ordini/dettaglio',
                                    'meus-pedidos/detalhe', 'meine-bestellungen/detail'),
    'cliente/seguimiento.html': ('seguimiento', 'tracking', 'suivi', 'tracciamento', 'rastreamento', 'sendungsverfolgung'),
    'cliente/perfil.html': ('perfil', 'profile', 'profil', 'profilo', 'perfil', 'profil'),
    'cliente/pago.html': ('pago', 'payment', 'reglement', 'pagamento', 'pagamento-pedido', 'zahlung'),
    'cliente/boleta.html': ('boleta', 'receipt', 'recu', 'ricevuta', 'recibo', 'beleg'),
    'admin/dashboard.html': _ADMIN,
    'admin/productos.html': _join(_ADMIN, ('productos', 'products', 'produits', 'prodotti', 'produtos', 'produkte')),
    'admin/categorias.html': _join(_ADMIN, ('categorias', 'categories', 'categories', 'categorie', 'categorias', 'kategorien')),
    'admin/inventario.html': _join(_ADMIN, ('inventario', 'inventory', 'inventaire', 'inventario', 'estoque', 'bestand')),
    'admin/promociones.html': _join(_ADMIN, ('promociones', 'promotions', 'promotions', 'promozioni', 'promocoes', 'aktionen')),
    'admin/pedidos.html': _join(_ADMIN, ('pedidos', 'orders', 'commandes', 'ordini', 'pedidos', 'bestellungen')),
    'admin/usuarios.html': _join(_ADMIN, ('usuarios', 'users', 'utilisateurs', 'utenti', 'usuarios', 'benutzer')),
    'admin/incidencias.html': _join(_ADMIN, ('incidencias', 'incidents', 'incidents', 'incidenti', 'incidentes', 'vorfaelle')),
    'admin/reportes.html': _join(_ADMIN, ('reportes', 'reports', 'rapports', 'report', 'relatorios', 'berichte')),
    'admin/auditoria.html': _join(_ADMIN, ('auditoria', 'audit', 'audit', 'audit', 'auditoria', 'audit')),
    'operaciones/dashboard.html': _OPS,
    'operaciones/pedidos.html': _join(_OPS, ('pedidos', 'orders', 'commandes', 'ordini', 'pedidos', 'bestellungen')),
    'operaciones/revisiones.html': _join(_OPS, ('revisiones', 'reviews', 'revisions', 'revisioni', 'revisoes', 'pruefungen')),
    'operaciones/incidencias.html': _join(_OPS, ('incidencias', 'incidents', 'incidents', 'incidenti', 'incidentes', 'vorfaelle')),
    'logistica/dashboard.html': _LOG,
    'logistica/calendario.html': _join(_LOG, ('calendario', 'calendar', 'calendrier', 'calendario', 'calendario', 'kalender')),
    'logistica/entregas.html': _join(_LOG, ('entregas', 'deliveries', 'livraisons', 'consegne', 'entregas', 'lieferungen')),
    'logistica/asignaciones.html': _join(_LOG, ('asignaciones', 'assignments', 'affectations', 'assegnazioni', 'atribuicoes', 'zuweisungen')),
    'monitoreo/dashboard.html': _MON,
    'monitoreo/servicios.html': _join(_MON, ('servicios', 'services', 'services', 'servizi', 'servicos', 'dienste')),
    'monitoreo/metricas.html': _join(_MON, ('metricas', 'metrics', 'metriques', 'metriche', 'metricas', 'metriken')),
    'monitoreo/alertas.html': _join(_MON, ('alertas', 'alerts', 'alertes', 'avvisi', 'alertas', 'warnungen')),
    'arquitectura/overview.html': _ARQ,
    'arquitectura/flujo-pedidos.html': _join(_ARQ, ('flujo-pedidos', 'order-flow', 'flux-commandes', 'flusso-ordini', 'fluxo-pedidos', 'bestellablauf')),
}


# Textos fijos de la plantilla HTML: el servidor (LocalizedPagesController) los traduce en /<idioma>/….
SHELL_TEXTS = ['Saltar al contenido', 'Ruta de navegación', 'Secciones del portal',
               'Activa JavaScript en tu navegador para comprar en POLYMER.']


def routes_manifest() -> str:
    """static/i18n/routes.json: rutas localizadas y metadatos (título y descripción en español = clave de traducción)."""
    pages, meta = {}, {}
    for page in PAGES:
        slugs = SLUGS[page['path']]
        pages['/' + page['path']] = dict(zip(LANGUAGES, slugs))
        meta['/' + page['path']] = {'title': page['title'], 'description': page['description'], 'roles': page.get('roles') or [],
                                    'indexable': not page.get('roles') and page['path'] not in ('403.html', '404.html', 'producto.html')}
    return json.dumps({'languages': LANGUAGES, 'default': 'es', 'pages': pages, 'meta': meta, 'shell': SHELL_TEXTS},
                      ensure_ascii=False, indent=1) + '\n'


TEMPLATE = """<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="{description}">
  <meta name="theme-color" content="#101827">
  <meta name="application-name" content="POLYMER">
  <title>{title} · POLYMER</title>
  <link rel="icon" href="/img/logo.svg" type="image/svg+xml">
  <script src="/js/intro-gate.js"></script>
  <link rel="stylesheet" href="/css/base.css">
  <link rel="stylesheet" href="/css/components.css">
  <link rel="stylesheet" href="/css/effects.css">
  <link rel="stylesheet" href="/css/platform.css">
  <link rel="stylesheet" href="/css/site.css">
  <link rel="stylesheet" href="/css/store.css">
  <link rel="stylesheet" href="/css/polymer.css">
  <script type="module" src="/js/page.js"></script>
</head>
<!-- Página generada por tools/generate_pages.py: edita el registro o la vista, no este archivo. -->
<body class="site{body_class}" {attributes}>
<canvas id="grid-canvas" class="grid-canvas" aria-hidden="true"></canvas>
<a class="skip-link" href="#main">Saltar al contenido</a>
<header id="site-header" class="topbar"></header>
<div class="page-shell{shell_class}">
{sidebar}  <main id="main" class="page-main" tabindex="-1">
    <nav id="breadcrumbs" class="breadcrumbs" aria-label="Ruta de navegación"></nav>
    <div id="view-root" class="view-root" aria-live="polite">
      <noscript><p class="card">Activa JavaScript en tu navegador para comprar en POLYMER.</p></noscript>
      <div class="page-loading" aria-hidden="true"><div class="skeleton skeleton--title"></div><div class="skeleton skeleton--row"></div><div class="skeleton skeleton--row"></div></div>
    </div>
{fragment}  </main>
</div>
<footer id="site-footer" class="site-footer"></footer>
<div class="toasts" id="toasts" aria-live="polite" aria-atomic="false"></div>
</body>
</html>
"""


def render(page: dict) -> str:
    attributes = {
        'data-route': page['route'],
        'data-title': page['title'],
    }
    if page.get('params'):
        attributes['data-params'] = json.dumps(page['params'], separators=(',', ':'))
    if page.get('section'):
        attributes['data-section'] = page['section']
    if page.get('parent'):
        attributes['data-parent'] = page['parent']
    if page.get('active'):
        attributes['data-active'] = page['active']
    roles = page.get('roles') or []
    if roles:
        attributes['data-roles'] = ','.join(roles)
    attr_text = ' '.join(f'{key}="{html.escape(value, quote=True)}"' for key, value in attributes.items())
    has_sidebar = bool(page.get('section'))
    fragment = ''
    if page.get('fragment'):
        body = (FRAGMENTS / page['fragment']).read_text(encoding='utf-8').replace('\r\n', '\n').rstrip('\n')
        fragment = '\n'.join(('    ' + line) if line else '' for line in body.split('\n')) + '\n'
    return TEMPLATE.format(
        title=html.escape(page['title']), description=html.escape(page['description'], quote=True),
        body_class=f" site--{page['section']}" if has_sidebar else '', attributes=attr_text,
        shell_class=' page-shell--sidebar' if has_sidebar else '',
        sidebar='  <aside id="side-nav" class="side-nav" aria-label="Secciones del portal"></aside>\n' if has_sidebar else '',
        fragment=fragment)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--check', action='store_true', help='No escribe: falla si algún HTML difiere del registro.')
    args = parser.parse_args()
    stale = []
    for page in PAGES:
        target = STATIC / page['path']
        content = render(page)
        current = target.read_text(encoding='utf-8').replace('\r\n', '\n') if target.exists() else None
        if current == content:
            continue
        if args.check:
            stale.append(page['path'])
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content.encode('utf-8'))
            print(f'escrito {page["path"]}')
    # Rutas por idioma: cada página tiene su ruta en los seis idiomas y no se repite dentro de un idioma.
    missing = [page['path'] for page in PAGES if page['path'] not in SLUGS]
    if missing:
        print('Páginas sin ruta localizada en SLUGS: ' + ', '.join(missing), file=sys.stderr)
        return 1
    for index, code in enumerate(LANGUAGES):
        seen = [SLUGS[page['path']][index] for page in PAGES]
        duplicates = sorted({slug for slug in seen if seen.count(slug) > 1})
        if duplicates:
            print(f'Rutas repetidas en {code}: {duplicates}', file=sys.stderr)
            return 1
    routes_file = STATIC / 'i18n/routes.json'
    current_routes = routes_file.read_text(encoding='utf-8').replace('\r\n', '\n') if routes_file.exists() else None
    if current_routes != routes_manifest():
        if args.check:
            stale.append('static/i18n/routes.json')
        else:
            routes_file.parent.mkdir(parents=True, exist_ok=True)
            routes_file.write_bytes(routes_manifest().encode('utf-8'))
            print('escrito static/i18n/routes.json')
    manifest = json.dumps([{'path': '/' + page['path'], 'route': page['route'], 'title': page['title'], 'roles': page.get('roles') or [],
                            'needsId': ':id' in page['route']} for page in PAGES], ensure_ascii=False, indent=2) + '\n'
    manifest_file = ROOT / 'tools/ui-e2e/pages.json'
    current = manifest_file.read_text(encoding='utf-8').replace('\r\n', '\n') if manifest_file.exists() else None
    if current != manifest:
        if args.check:
            stale.append('tools/ui-e2e/pages.json')
        else:
            manifest_file.write_bytes(manifest.encode('utf-8'))
            print('escrito tools/ui-e2e/pages.json')
    if stale:
        print('Páginas desactualizadas (ejecuta tools/generate_pages.py):\n  ' + '\n  '.join(stale), file=sys.stderr)
        return 1
    print(f'OK: {len(PAGES)} páginas HTML al día')
    return 0


if __name__ == '__main__':
    sys.exit(main())
