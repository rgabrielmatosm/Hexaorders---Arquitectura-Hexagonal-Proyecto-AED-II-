"""Genera las páginas HTML del sitio multipágina de HexaOrders desde un único registro.

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
    # ---- Portal público
    dict(path='index.html', route='/inicio', title='Inicio', description='HexaOrders: pedidos, riesgo y logística sobre una arquitectura hexagonal real.'),
    dict(path='catalogo.html', route='/tienda', title='Catálogo', description='Catálogo de productos con búsqueda, filtros, orden y paginación (PostgreSQL).'),
    dict(path='producto.html', route='/producto/:id', title='Producto', description='Detalle de producto, disponibilidad y productos relacionados.', parent='/catalogo.html|Catálogo'),
    dict(path='carrito.html', route='/carrito', title='Carrito', description='Carrito con totales calculados por el servidor y validación de stock.'),
    dict(path='checkout.html', route='/checkout', title='Checkout', description='Confirmación del pedido con datos de entrega y evaluación de riesgo.', roles=['CUSTOMER'], parent='/carrito.html|Carrito'),
    dict(path='login.html', route='/entrar', title='Iniciar sesión', description='Acceso para clientes y equipos (cinco roles).'),
    dict(path='registro.html', route='/registro', title='Crear cuenta', description='Registro de clientes con contraseña cifrada con BCrypt.'),
    dict(path='nosotros.html', route='/nosotros', title='Nosotros', description='Misión, principios y roles de ingeniería del proyecto HexaOrders.'),
    dict(path='contacto.html', route='/contacto', title='Contacto', description='Formulario de contacto: los mensajes se guardan en PostgreSQL.'),
    dict(path='403.html', route='/error/403', title='Acceso restringido', description='Tu rol no tiene permiso para esta página.'),
    dict(path='404.html', route='/error/404', title='Página no encontrada', description='La página solicitada no existe.'),
    # ---- Portal del cliente
    dict(path='cliente/dashboard.html', route='/cliente', title='Mi cuenta', description='Resumen de pedidos del cliente.', section='cliente', roles=['CUSTOMER']),
    dict(path='cliente/pedidos.html', route='/mis-pedidos', title='Mis pedidos', description='Historial de pedidos del cliente.', section='cliente', roles=['CUSTOMER']),
    dict(path='cliente/pedido-detalle.html', route='/mis-pedidos/:id', title='Detalle del pedido', description='Artículos, totales, entrega y cancelación.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    dict(path='cliente/seguimiento.html', route='/seguimiento/:id', title='Seguimiento', description='Estado real del pedido y su historial.', section='cliente', roles=['CUSTOMER'], parent='/cliente/pedidos.html|Mis pedidos', active='/cliente/pedidos.html'),
    dict(path='cliente/perfil.html', route='/perfil', title='Perfil y seguridad', description='Datos personales y cambio de contraseña.', section='cliente', roles=['auth']),
    # ---- Administración
    *[dict(path=f'admin/{name}.html', route='/admin/:tab', params={'tab': tab}, title=title, description=desc, section='admin', roles=['ADMIN'])
      for name, tab, title, desc in [
          ('dashboard', 'panel', 'Panel de administración', 'Indicadores reales, alertas operativas, actividad y mensajes de contacto.'),
          ('productos', 'productos', 'Productos', 'Alta y edición de productos con búsqueda y paginación.'),
          ('categorias', 'categorias', 'Categorías', 'Categorías del catálogo y su contenido.'),
          ('inventario', 'inventario', 'Inventario', 'Existencias, stock bajo y ajustes auditados.'),
          ('pedidos', 'pedidos', 'Pedidos', 'Todos los pedidos con su ciclo de vida.'),
          ('usuarios', 'usuarios', 'Usuarios y roles', 'Gestión de usuarios, roles y activación.'),
          ('incidencias', 'incidencias', 'Incidencias', 'Incidencias operativas.'),
          ('reportes', 'reportes', 'Reportes', 'Ventas por categoría exportables a CSV.'),
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
    # ---- Arquitectura
    dict(path='arquitectura/overview.html', route='/arquitectura/overview', title='Arquitectura', description='Arquitectura hexagonal y explorador interactivo.', section='arquitectura', fragment='arquitectura-explorer.html'),
    dict(path='arquitectura/flujo-pedidos.html', route='/arquitectura/flujo-pedidos', title='Flujo de pedidos', description='Secuencia del checkout y ciclo de vida del pedido.', section='arquitectura'),
]

TEMPLATE = """<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="{description}">
  <meta name="theme-color" content="#0B0F14">
  <title>{title} · HexaOrders</title>
  <link rel="icon" href="/img/logo.svg" type="image/svg+xml">
  <link rel="stylesheet" href="/css/base.css">
  <link rel="stylesheet" href="/css/components.css">
  <link rel="stylesheet" href="/css/effects.css">
  <link rel="stylesheet" href="/css/platform.css">
  <link rel="stylesheet" href="/css/site.css">
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
      <noscript><p class="card">HexaOrders necesita JavaScript para cargar los datos de esta página desde la API.</p></noscript>
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
