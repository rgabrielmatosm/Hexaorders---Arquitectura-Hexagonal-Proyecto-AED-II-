#!/usr/bin/env python3
"""Genera ENLACES.html: el mapa de enlaces de POLYMER por contenedor y por slice funcional.

Una sola fuente de datos (este archivo) para:
  * ENLACES.html en la raíz del proyecto (se abre con doble clic o con `polymer.cmd enlaces`);
  * la comprobación de `polymer.cmd enlaces` y del CI (tools/links.json, que lee PowerShell).

Cada slice indica QUIÉN lo verifica (rol y cuenta de demostración), qué páginas abrir, qué contenedores
intervienen (con su enlace local) y dónde ver la evidencia (Grafana, Prometheus, RabbitMQ, Mailpit…).
Las contraseñas NUNCA se escriben aquí: solo la ruta del archivo de secretos.

Uso:  python tools/generate_links.py          (escribe ENLACES.html y tools/links.json)
      python tools/generate_links.py --check  (falla si no están al día)
"""
from __future__ import annotations

import argparse
import html
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / 'ENLACES.html'
JSON_OUTPUT = ROOT / 'tools' / 'links.json'

SITE = 'http://localhost:8080'
GRAFANA = 'http://localhost:3000'
PROMETHEUS = 'http://localhost:9091'

# ---------------------------------------------------------------- contenedores (los 13 de docker-compose.yml)
# check: URL que responde 2xx/3xx cuando el contenedor está sano (la usa `polymer.cmd enlaces`).
CONTAINERS = [
    {'id': 'frontend', 'name': 'frontend (gateway Nginx)', 'layer': 'Adaptador de borde',
     'what': 'Tienda, portales por rol y API pública detrás de un único punto de entrada.',
     'url': f'{SITE}/', 'check': 'http://127.0.0.1:8080/', 'mode': 'basic'},
    {'id': 'order-api', 'name': 'order-api (núcleo hexagonal, Java)', 'layer': 'Dominio + casos de uso + adaptadores',
     'what': 'El mismo sistema SIN pasar por el gateway: demuestra que Nginx solo es un adaptador de borde.',
     'url': 'http://localhost:8081/', 'check': 'http://127.0.0.1:8081/api/catalog/categories', 'mode': 'basic'},
    {'id': 'risk-engine', 'name': 'risk-engine (microservicio Python)', 'layer': 'Servicio externo detrás de RiskAssessmentPort',
     'what': 'Documentación interactiva: prueba POST /score (≥ 1000 → REVIEW).',
     'url': 'http://localhost:8000/docs', 'check': 'http://127.0.0.1:8000/health/ready', 'mode': 'basic'},
    {'id': 'db', 'name': 'db (PostgreSQL 17)', 'layer': 'Adaptador de salida (persistencia)',
     'what': 'Privado a propósito: solo order-api lo ve. Evidencia: escenario «postgres» o Docker Desktop → db → Exec → '
             'psql -U orders -d orders.',
     'url': None, 'check': None, 'mode': 'basic'},
    {'id': 'prometheus', 'name': 'prometheus', 'layer': 'Observabilidad (métricas)',
     'what': 'Targets, reglas de alerta y consultas PromQL.',
     'url': f'{PROMETHEUS}/targets', 'check': 'http://127.0.0.1:9091/-/ready', 'mode': 'basic'},
    {'id': 'grafana', 'name': 'grafana', 'layer': 'Observabilidad (paneles)',
     'what': '8 dashboards; usuario admin, contraseña en secrets\\grafana_password.txt.',
     'url': f'{GRAFANA}/d/polymer', 'check': 'http://127.0.0.1:3000/api/health', 'mode': 'basic'},
    {'id': 'redis', 'name': 'redis', 'layer': 'Adaptador de salida (caché, TTL de Yape/Plin)',
     'what': 'Privado. Evidencia: escenario «redis» (miss → hit → TTL → expiración) y panel de caché en Grafana; '
             'Docker Desktop → redis → Exec → redis-cli.',
     'url': None, 'check': None, 'mode': 'full'},
    {'id': 'rabbitmq', 'name': 'rabbitmq', 'layer': 'Adaptador de salida (eventos)',
     'what': 'Consola: exchange, cola de notificaciones y DLQ. Usuario hexaorders (o polymer), contraseña en secrets\\rabbitmq_password.txt.',
     'url': 'http://localhost:15672/#/queues/%2F/hexaorders.notifications', 'check': 'http://127.0.0.1:15672/', 'mode': 'full'},
    {'id': 'mailpit', 'name': 'mailpit', 'layer': 'SMTP de desarrollo',
     'what': 'Bandeja local: códigos de 6 dígitos, boletas y avisos de pedidos (si no configuras Gmail).',
     'url': 'http://localhost:8025/', 'check': 'http://127.0.0.1:8025/', 'mode': 'full'},
    {'id': 'loki', 'name': 'loki', 'layer': 'Observabilidad (logs)',
     'what': 'Sin interfaz propia. Evidencia: Grafana → Explore → Loki ({service="order-api"}).',
     'url': f'{GRAFANA}/explore', 'check': None, 'mode': 'full'},
    {'id': 'tempo', 'name': 'tempo', 'layer': 'Observabilidad (trazas)',
     'what': 'Sin interfaz propia. Evidencia: Grafana → Explore → Tempo (traza order-api → risk-engine).',
     'url': f'{GRAFANA}/explore', 'check': None, 'mode': 'full'},
    {'id': 'otel-collector', 'name': 'otel-collector', 'layer': 'Observabilidad (OpenTelemetry)',
     'what': 'Estado del recolector que recibe las trazas OTLP y las envía a Tempo.',
     'url': 'http://localhost:13133/', 'check': 'http://127.0.0.1:13133/', 'mode': 'full'},
    {'id': 'alloy', 'name': 'alloy', 'layer': 'Observabilidad (agente de logs)',
     'what': 'Interfaz con el flujo de logs Docker → Loki (sin secretos).',
     'url': 'http://localhost:12345/', 'check': 'http://127.0.0.1:12345/-/ready', 'mode': 'full'},
]

ACCOUNTS = [
    ('Visitante', '—', 'Sin cuenta: tienda, ofertas, mapa y horario.'),
    ('Cliente', 'cliente', 'Compra, paga (tarjeta, Yape, Plin), boleta y seguimiento.'),
    ('Operador de pedidos', 'operador', 'Revisión de riesgo, transiciones e incidencias.'),
    ('Logística', 'logistica · logistica2', 'Calendario, entregas y asignaciones.'),
    ('Administrador', 'admin', 'Catálogo, inventario, promociones, usuarios, auditoría y reportes.'),
    ('Observabilidad', 'observador', 'Monitoreo, arquitectura y laboratorio técnico.'),
]


def page(path: str, label: str | None = None) -> dict:
    return {'label': label or path, 'url': f'{SITE}{path}'}


def grafana(uid: str, label: str) -> dict:
    return {'label': f'Grafana · {label}', 'url': f'{GRAFANA}/d/{uid}'}


def promql(query: str, label: str) -> dict:
    from urllib.parse import quote
    return {'label': f'Prometheus · {label}', 'url': f'{PROMETHEUS}/query?g0.expr={quote(query)}&g0.tab=table'}


# ---------------------------------------------------------------- slices (docs/final/MAPA_SLICES_FUNCIONALES.md)
SLICES = [
    {'id': 'hexagonal', 'name': 'Arquitectura hexagonal: núcleo, puertos y adaptadores', 'verifier': 'Observabilidad',
     'account': 'observador', 'login': '/arquitectura/overview.html',
     'pages': [page('/arquitectura/overview.html', 'Vista general'), page('/arquitectura/flujo-pedidos.html', 'Flujo de un pedido'),
               page('/laboratorio.html', 'Laboratorio (API original)')],
     'containers': ['order-api', 'frontend', 'risk-engine', 'db'],
     'evidence': [grafana('polymer', 'Puertos, adaptadores y métricas'),
                  {'label': 'order-api directo (sin gateway) :8081', 'url': 'http://localhost:8081/'}],
     'demo': ['arquitectura', 'adaptador'],
     'test': 'ArchUnit (8 reglas) · AdapterSubstitutionTest'},
    {'id': 'catalogo', 'name': 'Catálogo, búsqueda y ofertas', 'verifier': 'Visitante', 'account': '—', 'login': None,
     'pages': [page('/', 'Portada'), page('/catalogo.html', 'Catálogo'), page('/ofertas.html', 'Ofertas')],
     'containers': ['frontend', 'order-api', 'db', 'redis'],
     'evidence': [grafana('polymer-integrations', 'Caché Redis'),
                  promql('sum by (result) (polymer_cache_requests_total)', 'aciertos/fallos de caché'),
                  page('/api/catalog/promotions', 'API · promociones (JSON)')],
     'demo': ['redis', 'redis-caida'], 'test': 'store_smoke.py · pages-e2e.js'},
    {'id': 'checkout', 'name': 'Carrito, checkout y evaluación de riesgo', 'verifier': 'Cliente', 'account': 'cliente',
     'login': '/checkout.html',
     'pages': [page('/carrito.html', 'Carrito'), page('/checkout.html', 'Checkout')],
     'containers': ['frontend', 'order-api', 'risk-engine', 'db'],
     'evidence': [grafana('polymer-business', 'Operaciones del negocio'), grafana('polymer-risk', 'Motor de riesgo'),
                  {'label': 'risk-engine · POST /score', 'url': 'http://localhost:8000/docs'}],
     'demo': ['riesgo', 'riesgo-caida'], 'test': 'CommerceServiceTest · ui-e2e.js'},
    {'id': 'pagos', 'name': 'v7 · Pago en línea (tarjeta, Yape, Plin) y boleta', 'verifier': 'Cliente', 'account': 'cliente',
     'login': '/cliente/pedidos.html',
     'pages': [page('/cliente/pedidos.html', 'Mis pedidos → Pagar'), page('/cliente/pago.html', 'Pago'),
               page('/cliente/boleta.html', 'Boleta')],
     'containers': ['order-api', 'redis', 'db', 'rabbitmq', 'mailpit'],
     'evidence': [promql('sum by (method, outcome) (polymer_payments_total)', 'pagos por método'),
                  grafana('polymer-business', 'Panel «Pagos»'), {'label': 'Mailpit · boleta por correo', 'url': 'http://localhost:8025/'}],
     'demo': ['pagos'], 'test': 'PaymentsAndReceiptsTest · v7-e2e.js'},
    {'id': 'acceso', 'name': 'v7 · Acceso con Google, código por correo y «Recordarme 30 días»', 'verifier': 'Cliente',
     'account': 'cliente (o tu cuenta de Google)', 'login': '/login.html',
     'pages': [page('/login.html', 'Iniciar sesión'), page('/registro.html', 'Registro'), page('/cliente/perfil.html', 'Perfil → Seguridad')],
     'containers': ['order-api', 'mailpit', 'db'],
     'evidence': [{'label': 'Mailpit · código de 6 dígitos', 'url': 'http://localhost:8025/'},
                  page('/api/auth/providers', 'API · ¿Google configurado? (JSON)'),
                  grafana('polymer-security', 'Seguridad y auditoría')],
     'demo': [], 'test': 'SignInServiceTest · SignInWebTest · v7-e2e.js'},
    {'id': 'tienda', 'name': 'v7 · Tienda física: Google Maps, horario de hoy y Google Calendar', 'verifier': 'Visitante',
     'account': '—', 'login': None,
     'pages': [page('/contacto.html#visitanos', 'Visítanos (mapa y horario)'), page('/', 'Portada con video')],
     'containers': ['frontend', 'order-api'],
     'evidence': [page('/api/store/today', 'API · horario de hoy (JSON)'), page('/api/store', 'API · tienda (JSON)')],
     'demo': [], 'test': 'v7-e2e.js · PeruHolidays'},
    {'id': 'seguimiento', 'name': 'Mis pedidos y seguimiento', 'verifier': 'Cliente', 'account': 'cliente',
     'login': '/cliente/dashboard.html',
     'pages': [page('/cliente/dashboard.html', 'Mi cuenta'), page('/cliente/pedidos.html', 'Mis pedidos'),
               page('/cliente/seguimiento.html', 'Seguimiento')],
     'containers': ['order-api', 'db'],
     'evidence': [grafana('polymer-business', 'Pedidos por estado')], 'demo': ['postgres'], 'test': 'pages-e2e.js'},
    {'id': 'operaciones', 'name': 'Operaciones: pedidos y revisión de riesgo', 'verifier': 'Operador de pedidos',
     'account': 'operador', 'login': '/operaciones/dashboard.html',
     'pages': [page('/operaciones/dashboard.html', 'Panel'), page('/operaciones/revisiones.html', 'Revisión de riesgo'),
               page('/operaciones/pedidos.html', 'Pedidos'), page('/operaciones/incidencias.html', 'Incidencias')],
     'containers': ['order-api', 'risk-engine', 'db', 'rabbitmq'],
     'evidence': [grafana('polymer-risk', 'Motor de riesgo'), promql('sum by (status) (polymer_orders_by_status)', 'pedidos por estado')],
     'demo': ['riesgo'], 'test': 'OperationsAndLogisticsTest · platform_smoke.py'},
    {'id': 'logistica', 'name': 'Logística: calendario, entregas y asignaciones', 'verifier': 'Logística',
     'account': 'logistica · logistica2', 'login': '/logistica/dashboard.html',
     'pages': [page('/logistica/calendario.html', 'Calendario'), page('/logistica/entregas.html', 'Entregas'),
               page('/logistica/asignaciones.html', 'Asignaciones')],
     'containers': ['order-api', 'db', 'rabbitmq'],
     'evidence': [grafana('polymer-logistics', 'Operaciones logísticas')], 'demo': [], 'test': 'OperationsAndLogisticsTest · pages-e2e.js'},
    {'id': 'admin', 'name': 'Administración: catálogo, inventario, promociones, usuarios y auditoría', 'verifier': 'Administrador',
     'account': 'admin', 'login': '/admin/dashboard.html',
     'pages': [page('/admin/dashboard.html', 'Panel'), page('/admin/productos.html', 'Productos'),
               page('/admin/inventario.html', 'Inventario'), page('/admin/promociones.html', 'Promociones'),
               page('/admin/usuarios.html', 'Usuarios'), page('/admin/auditoria.html', 'Auditoría'), page('/admin/reportes.html', 'Reportes')],
     'containers': ['order-api', 'db', 'redis'],
     'evidence': [grafana('polymer-security', 'Seguridad y auditoría'), promql('polymer_cache_invalidations_total', 'invalidaciones de caché')],
     'demo': [], 'test': 'store_smoke.py · platform_smoke.py'},
    {'id': 'notificaciones', 'name': 'Notificaciones asíncronas (outbox → RabbitMQ → correo)', 'verifier': 'Observabilidad',
     'account': 'observador (o admin)', 'login': None,
     'pages': [],
     'containers': ['order-api', 'rabbitmq', 'mailpit', 'db'],
     'evidence': [{'label': 'RabbitMQ · cola de notificaciones', 'url': 'http://localhost:15672/#/queues/%2F/hexaorders.notifications'},
                  {'label': 'Mailpit · correos enviados', 'url': 'http://localhost:8025/'},
                  grafana('polymer-integrations', 'Integraciones')],
     'demo': ['rabbitmq', 'rabbitmq-caida'], 'test': 'integration_smoke.py'},
    {'id': 'monitoreo', 'name': 'Monitoreo y observabilidad', 'verifier': 'Observabilidad', 'account': 'observador',
     'login': '/monitoreo/dashboard.html',
     'pages': [page('/monitoreo/dashboard.html', 'Panel'), page('/monitoreo/servicios.html', 'Servicios'),
               page('/monitoreo/metricas.html', 'Métricas'), page('/monitoreo/alertas.html', 'Alertas')],
     'containers': ['prometheus', 'grafana', 'loki', 'tempo', 'otel-collector', 'alloy'],
     'evidence': [grafana('polymer-api', 'Rendimiento de la API'), grafana('polymer-infrastructure', 'Infraestructura'),
                  {'label': 'Prometheus · targets', 'url': f'{PROMETHEUS}/targets'}, {'label': 'Grafana · Explore (Loki/Tempo)', 'url': f'{GRAFANA}/explore'}],
     'demo': ['observabilidad'], 'test': 'integration_smoke.py · check_observability.py'},
    {'id': 'testimonios', 'name': 'v9 · Testimonios: equipo (LinkedIn) y clientes', 'verifier': 'Cliente', 'account': 'cliente (y admin para moderar)',
     'login': '/nosotros.html#deja-tu-testimonio',
     'pages': [page('/nosotros.html#testimonios', 'Nosotros → carrusel y formulario'), page('/#testimonios', 'Portada → carrusel'),
               page('/admin/dashboard.html', 'Admin → moderación')],
     'containers': ['frontend', 'order-api', 'db'],
     'evidence': [page('/api/testimonials', 'API · testimonios publicados (JSON)')],
     'demo': [], 'test': 'TestimonialServiceTest · v7-e2e.js'},
    {'id': 'contacto', 'name': 'Contacto y libro de reclamaciones', 'verifier': 'Visitante', 'account': '— (lo revisa admin)',
     'login': None,
     'pages': [page('/contacto.html', 'Contacto'), page('/libro-reclamaciones.html', 'Libro de reclamaciones')],
     'containers': ['order-api', 'db'], 'evidence': [page('/admin/dashboard.html', 'Admin · mensajes recibidos')],
     'demo': [], 'test': 'pages-e2e.js'},
]

CONTAINER_IDS = {c['id'] for c in CONTAINERS}
for _slice in SLICES:
    assert set(_slice['containers']) <= CONTAINER_IDS, _slice['id']


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def link(item: dict) -> str:
    return f'<a href="{esc(item["url"])}" target="_blank" rel="noopener">{esc(item["label"])}</a>'


def render() -> str:
    by_id = {c['id']: c for c in CONTAINERS}
    rows = []
    for c in CONTAINERS:
        status = (f'<span class="dot" data-check="{esc(c["check"])}" title="Comprobando…"></span>' if c['check']
                  else '<span class="dot dot--private" title="Sin enlace propio (privado o sin interfaz)"></span>')
        target = (f'<a class="btn" href="{esc(c["url"])}" target="_blank" rel="noopener">Abrir</a>' if c['url']
                  else '<span class="muted">privado</span>')
        mode = '' if c['mode'] == 'basic' else ' <span class="tag">modo completo</span>'
        rows.append(f'<tr id="c-{c["id"]}"><td>{status}</td><td><strong>{esc(c["name"])}</strong>{mode}<br>'
                    f'<span class="muted">{esc(c["layer"])}</span></td><td>{esc(c["what"])}</td><td>{target}</td></tr>')
    cards = []
    for index, s in enumerate(SLICES, 1):
        entry = (f'<a class="btn btn--primary" href="{esc(SITE + "/login.html?next=" + s["login"])}" target="_blank" rel="noopener">'
                 f'Entrar como {esc(s["account"].split(" ")[0])}</a>') if s['login'] else ''
        pages = ''.join(f'<li>{link(p)}</li>' for p in s['pages']) or '<li class="muted">Sin página propia (proceso del sistema)</li>'
        chips = ''.join(
            (f'<a class="chip" href="{esc(by_id[cid]["url"])}" target="_blank" rel="noopener">' if by_id[cid]['url'] else '<span class="chip chip--private">')
            + (f'<span class="dot" data-check="{esc(by_id[cid]["check"])}"></span>' if by_id[cid]['check'] else '<span class="dot dot--private"></span>')
            + esc(cid) + ('</a>' if by_id[cid]['url'] else '</span>') for cid in s['containers'])
        evidence = ''.join(f'<li>{link(e)}</li>' for e in s['evidence'])
        demo = ' · '.join(f'<code>.\\polymer.cmd demo -Scenario {esc(d)}</code>' for d in s['demo']) or '<span class="muted">—</span>'
        cards.append(f'''<article class="slice" id="s-{s["id"]}">
  <header><span class="num">{index:02d}</span><h3>{esc(s["name"])}</h3></header>
  <p class="who"><span class="tag tag--role">Verifica: {esc(s["verifier"])}</span> <span class="tag">Cuenta: {esc(s["account"])}</span>
     <label class="owner">Expone: <input data-owner="{esc(s["id"])}" placeholder="nombre del integrante" maxlength="60"></label></p>
  <div class="cols">
    <div><h4>Páginas</h4><ul>{pages}</ul>{entry}</div>
    <div><h4>Contenedores</h4><p class="chips">{chips}</p><h4>Evidencia</h4><ul>{evidence}</ul></div>
  </div>
  <p class="foot"><span>Demostración: {demo}</span><span class="muted">Pruebas: {esc(s["test"])}</span></p>
</article>''')
    accounts = ''.join(f'<tr><td>{esc(r)}</td><td><code>{esc(u)}</code></td><td>{esc(d)}</td></tr>' for r, u, d in ACCOUNTS)
    index = ''.join(f'<li><a href="#s-{s["id"]}">{esc(s["name"])}</a></li>' for s in SLICES)
    return TEMPLATE.replace('{{CONTAINERS}}', '\n'.join(rows)).replace('{{SLICES}}', '\n'.join(cards)) \
        .replace('{{ACCOUNTS}}', accounts).replace('{{INDEX}}', index) \
        .replace('{{N_SLICES}}', str(len(SLICES))).replace('{{N_LINKS}}', str(sum(1 for c in CONTAINERS if c['check'])))


TEMPLATE = '''<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>POLYMER · Enlaces por slice</title>
<!-- Generado por tools/generate_links.py: no editar a mano. Se abre como archivo local (doble clic). -->
<style>
:root { --bg: #f6f7fb; --card: #ffffff; --text: #111827; --muted: #5b6474; --line: #e3e6ee; --accent: #6d28d9; --ok: #059669; --bad: #dc2626; }
@media (prefers-color-scheme: dark) { :root { --bg: #0b1220; --card: #111a2e; --text: #e5e7eb; --muted: #94a3b8; --line: #23304a; --accent: #a78bfa; --ok: #34d399; --bad: #f87171; } }
* { box-sizing: border-box; }
body { margin: 0; font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); }
main { max-width: 1180px; margin: 0 auto; padding: 28px 16px 60px; }
h1 { margin: 0 0 4px; font-size: 1.9rem; letter-spacing: -0.01em; } h1 span { color: var(--accent); }
h2 { margin: 36px 0 12px; font-size: 1.25rem; } h3 { margin: 0; font-size: 1.05rem; } h4 { margin: 10px 0 4px; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
a { color: var(--accent); } code { font-size: 0.85em; background: color-mix(in srgb, var(--accent) 10%, transparent); padding: 1px 5px; border-radius: 5px; }
.lead { color: var(--muted); max-width: 820px; margin: 6px 0 0; }
.bar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin: 18px 0 0; }
.summary { padding: 8px 12px; border-radius: 10px; background: var(--card); border: 1px solid var(--line); }
.btn { display: inline-block; padding: 6px 12px; border-radius: 8px; border: 1px solid var(--line); background: var(--card); color: var(--text); text-decoration: none; font-weight: 600; font-size: 0.9rem; cursor: pointer; }
.btn--primary { background: var(--accent); border-color: var(--accent); color: #fff; margin-top: 8px; }
.table-wrap { overflow-x: auto; border-radius: 12px; } table { width: 100%; min-width: 0; border-collapse: collapse; background: var(--card); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
th, td { padding: 9px 12px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; } th { font-size: 0.8rem; color: var(--muted); text-transform: uppercase; }
.muted { color: var(--muted); } .tag { display: inline-block; font-size: 0.75rem; padding: 1px 8px; border-radius: 999px; border: 1px solid var(--line); color: var(--muted); }
.tag--role { border-color: var(--accent); color: var(--accent); font-weight: 600; }
.dot { display: inline-block; width: 11px; height: 11px; border-radius: 50%; background: #9ca3af; margin-right: 6px; vertical-align: -1px; }
.dot.is-up { background: var(--ok); } .dot.is-down { background: var(--bad); } .dot--private { background: transparent; border: 2px dashed #9ca3af; }
.slices { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 540px), 1fr)); gap: 14px; }
.slice { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 14px 16px; min-width: 0; }
.slice header { display: flex; gap: 10px; align-items: baseline; } .num { font-weight: 800; color: var(--accent); }
.who { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin: 8px 0 0; }
.owner { margin-left: auto; font-size: 0.85rem; color: var(--muted); } .owner input { font: inherit; border: 1px solid var(--line); border-radius: 6px; padding: 2px 6px; background: transparent; color: var(--text); width: 170px; }
.cols { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; } @media (max-width: 640px) { .cols { grid-template-columns: minmax(0, 1fr); } .owner { margin-left: 0; } }
ul { margin: 0; padding-left: 18px; } li { margin: 2px 0; overflow-wrap: anywhere; } td { overflow-wrap: anywhere; } code { overflow-wrap: anywhere; }
.bar .muted { flex-basis: 100%; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; } .chip { display: inline-flex; align-items: center; padding: 2px 10px; border-radius: 999px; border: 1px solid var(--line); text-decoration: none; color: var(--text); font-size: 0.85rem; }
.chip--private { color: var(--muted); }
.foot { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 6px; margin: 12px 0 0; font-size: 0.85rem; border-top: 1px solid var(--line); padding-top: 8px; }
.index { columns: 2; } @media (max-width: 640px) { .index { columns: 1; } }
@media print { .bar, .owner input { border: 0; } .btn { display: none; } .slice { break-inside: avoid; } }
</style>
</head>
<body>
<main>
<h1><span>POLYMER</span> · Enlaces por slice</h1>
<p class="lead">Cada slice funcional indica <strong>quién lo verifica</strong> (rol y cuenta de demostración), las páginas a abrir,
los <strong>contenedores</strong> que intervienen (con su enlace local, el mismo que muestra Docker Desktop) y dónde ver la evidencia.
Arranca primero con <code>.\\polymer.cmd start</code>. Contraseña de las cuentas: <code>secrets\\demo_password.txt</code> (nunca se muestra aquí).</p>
<div class="bar"><span class="summary" id="summary">Comprobando {{N_LINKS}} enlaces…</span>
<button class="btn" id="recheck" type="button">Volver a comprobar</button>
<span class="muted">● verde = responde · ● rojo = apagado · ◌ = privado por diseño (sin puerto publicado)</span></div>

<h2>Slices ({{N_SLICES}})</h2>
<ul class="index">{{INDEX}}</ul>

<h2>Contenedores (13) y su enlace</h2>
<div class="table-wrap"><table><thead><tr><th></th><th>Contenedor</th><th>Qué demuestra</th><th>Enlace</th></tr></thead><tbody>
{{CONTAINERS}}
</tbody></table></div>

<h2>Cuentas de demostración (quién verifica)</h2>
<div class="table-wrap"><table><thead><tr><th>Rol</th><th>Usuario</th><th>Verifica</th></tr></thead><tbody>{{ACCOUNTS}}</tbody></table></div>

<h2>Slices en detalle</h2>
<section class="slices">
{{SLICES}}
</section>
</main>
<script>
(function () {
  function probe(url) {
    // Desde un archivo local no se puede leer la respuesta (CORS), pero sí saber si el servidor contesta.
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, 4000);
    return fetch(url, { mode: 'no-cors', cache: 'no-store', signal: controller.signal })
      .then(function () { return true; }, function () { return false; })
      .finally(function () { clearTimeout(timer); });
  }
  function check() {
    var dots = Array.prototype.slice.call(document.querySelectorAll('[data-check]'));
    var urls = Array.from(new Set(dots.map(function (d) { return d.getAttribute('data-check'); })));
    Promise.all(urls.map(function (u) { return probe(u).then(function (ok) { return [u, ok]; }); })).then(function (results) {
      var state = new Map(results), up = 0;
      results.forEach(function (r) { if (r[1]) up += 1; });
      dots.forEach(function (d) {
        var ok = state.get(d.getAttribute('data-check'));
        d.classList.toggle('is-up', ok); d.classList.toggle('is-down', !ok); d.title = ok ? 'Responde' : 'No responde';
      });
      document.getElementById('summary').textContent = up + ' de ' + results.length + ' enlaces responden'
        + (up === results.length ? ' · todo en marcha' : up === 0 ? ' · ¿arrancaste con .\\\\polymer.cmd start?' : '');
    });
  }
  document.getElementById('recheck').addEventListener('click', check);
  document.querySelectorAll('[data-owner]').forEach(function (input) {
    var key = 'polymer.owner.' + input.getAttribute('data-owner');
    try { input.value = localStorage.getItem(key) || ''; } catch (e) { /* sin almacenamiento */ }
    input.addEventListener('input', function () { try { localStorage.setItem(key, input.value); } catch (e) { /* sin almacenamiento */ } });
  });
  check(); setInterval(check, 20000);
})();
</script>
</body>
</html>
'''


def links_json() -> str:
    data = {'containers': [{k: c[k] for k in ('id', 'name', 'url', 'check', 'mode')} for c in CONTAINERS],
            'slices': [{'id': s['id'], 'name': s['name'], 'verifier': s['verifier'], 'account': s['account'],
                        'login': s['login'], 'containers': s['containers']} for s in SLICES]}
    return json.dumps(data, ensure_ascii=False, indent=2) + '\n'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    outputs = {OUTPUT: render(), JSON_OUTPUT: links_json()}
    if args.check:
        stale = [p.name for p, text in outputs.items() if not p.exists() or p.read_text(encoding='utf-8') != text]
        if stale:
            print(f'Desactualizado: {", ".join(stale)}. Ejecuta: python tools/generate_links.py', file=sys.stderr)
            return 1
        print(f'OK: ENLACES.html al día ({len(SLICES)} slices, {len(CONTAINERS)} contenedores)')
        return 0
    for path, text in outputs.items():
        path.write_text(text, encoding='utf-8', newline='\n')
    print(f'Escrito ENLACES.html ({len(SLICES)} slices, {len(CONTAINERS)} contenedores) y tools/links.json')
    return 0


if __name__ == '__main__':
    sys.exit(main())
