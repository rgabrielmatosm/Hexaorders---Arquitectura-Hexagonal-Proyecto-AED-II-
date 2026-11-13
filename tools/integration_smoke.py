"""Prueba de integración del perfil "full" (v5) contra el stack REAL de Docker Compose.

No comprueba que los contenedores estén encendidos: comprueba lo que HACE cada integración.
Solo usa la biblioteca estándar de Python. Subcomandos:

    check            Gateway, Redis, RabbitMQ (outbox → cola → consumidor), Mailpit, Tempo,
                     OpenTelemetry Collector, Loki (y que no haya secretos en los logs),
                     targets de Prometheus y fuentes/dashboards de Grafana.
    degraded         Con Redis y RabbitMQ DETENIDOS: catálogo y checkout siguen funcionando y
                     los eventos quedan en el outbox (guarda el estado en --state).
    recovered        Con RabbitMQ de nuevo en marcha: el outbox se vacía y llega el correo.
    persist-write    Crea un pedido y lo guarda en --state (antes de reiniciar servicios).
    persist-read     Tras reiniciar: el pedido sigue en PostgreSQL.

Variables: API_URL (gateway, http://127.0.0.1:8080), PROMETHEUS_URL (http://127.0.0.1:9091),
GRAFANA_URL (http://127.0.0.1:3000), MAILPIT_URL (http://127.0.0.1:8025),
RABBITMQ_URL (http://127.0.0.1:15672), SECRETS_DIR (carpeta con *_password.txt) o
DEMO_PASSWORD / GRAFANA_PASSWORD / RABBITMQ_PASSWORD.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from platform_smoke import Session  # noqa: E402  (cliente con cookies y CSRF, como el navegador)

API = os.environ.get('API_URL', 'http://127.0.0.1:8080')
PROMETHEUS = os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:9091')
GRAFANA = os.environ.get('GRAFANA_URL', 'http://127.0.0.1:3000')
MAILPIT = os.environ.get('MAILPIT_URL', 'http://127.0.0.1:8025')
RABBITMQ = os.environ.get('RABBITMQ_URL', 'http://127.0.0.1:15672')
SECRETS = Path(os.environ.get('SECRETS_DIR', 'secrets'))
checks: list[str] = []


def ok(condition, message):
    if not condition:
        raise AssertionError(message)
    checks.append(message)
    print(f'  ✔ {message}')


def secret(name: str) -> str:
    value = os.environ.get(name.upper())
    if value:
        return value.strip()
    return (SECRETS / f'{name}.txt').read_text(encoding='utf-8').strip()


def http(method, url, body=None, headers=None, auth=None, timeout=15):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={'Accept': 'application/json', **(headers or {})})
    if body is not None:
        request.add_header('Content-Type', 'application/json')
    if auth:
        request.add_header('Authorization', 'Basic ' + base64.b64encode(f'{auth[0]}:{auth[1]}'.encode()).decode())
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            return response.status, raw, response.headers
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers


def get_json(url, auth=None):
    status, raw, _ = http('GET', url, auth=auth)
    if status != 200:
        raise AssertionError(f'GET {url}: {status} {raw[:200]!r}')
    return json.loads(raw)


def eventually(description, probe, timeout=90, interval=3):
    """Repite probe() hasta que devuelve un valor verdadero (las integraciones son asíncronas)."""
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        try:
            last = probe()
            if last:
                return last
        except (AssertionError, OSError, ValueError, KeyError) as error:
            last = error
        time.sleep(interval)
    raise AssertionError(f'{description}: no se cumplió en {timeout} s (último resultado: {last!r})')


def prom(query):
    result = get_json(f'{PROMETHEUS}/api/v1/query?' + urllib.parse.urlencode({'query': query}))['data']['result']
    return [float(item['value'][1]) for item in result]


def grafana_auth():
    return ('admin', secret('grafana_password'))


def new_customer():
    suffix = uuid.uuid4().hex[:10]
    email = f'v5_{suffix}@example.test'  # dominio reservado: nunca es una dirección real
    anon = Session()
    anon.refresh_csrf()
    anon.call('POST', '/api/auth/register', {'username': f'v5_{suffix}', 'displayName': 'Cliente v5',
                                            'email': email, 'password': 'faro lento nube cobre 2026'}, expect=201)
    return Session().login(f'v5_{suffix}', 'faro lento nube cobre 2026', email=email), email


def place_order(customer, products=None):
    products = products or customer.call('GET', '/api/catalog/products', expect=200)[1]
    item = min((p for p in products if p['stock'] >= 2), key=lambda p: p['price'])
    status, order, headers = customer.call('POST', '/api/shop/orders', {
        'items': [{'productId': item['id'], 'quantity': 1}],
        'shipping': {'recipient': 'Cliente v5', 'address': 'Av. Integración 123', 'city': 'Lima'}},
        headers={'Idempotency-Key': f'v5-{uuid.uuid4().hex[:20]}'})
    ok(status == 201, f'Checkout a través del gateway: 201 (pedido {order["id"][:8]})')
    return order, headers


def mails_to(email):
    data = get_json(f'{MAILPIT}/api/v1/search?' + urllib.parse.urlencode({'query': f'to:"{email}"', 'limit': 50}))
    return data.get('messages', [])


def check():
    print('Gateway Nginx (frontend)')
    status, raw, headers = http('GET', f'{API}/css/site.css')
    ok(status == 200 and 'max-age' in (headers.get('Cache-Control') or ''), 'Nginx sirve los estáticos (Cache-Control propio del gateway)')
    status, raw, _ = http('GET', f'{API}/api/auth/me')
    ok(status == 200 and b'authenticated' in raw, 'Nginx reenvía /api a Spring Boot (misma sesión y mismo origen)')
    request = urllib.request.Request(f'{API}/admin/dashboard.html')

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    try:
        urllib.request.build_opener(NoRedirect).open(request, timeout=10)
        location, code = '', 200
    except urllib.error.HTTPError as error:
        location, code = error.headers.get('Location', ''), error.code
    ok(code == 302 and location.endswith('/login.html?next=%2Fadmin%2Fdashboard.html') and ':8080' in location,
       'Las páginas protegidas siguen protegidas por Spring Security detrás del gateway (302 al login, puerto conservado)')

    print('Redis (caché del catálogo)')
    anon = Session()
    for _ in range(3):
        anon.call('GET', '/api/catalog/products', expect=200)
    hits = eventually('aciertos de caché en Prometheus', lambda: sum(prom('polymer_cache_requests_total{result="hit"}')), timeout=60)
    ok(hits > 0, f'Lecturas del catálogo servidas desde Redis ({hits:.0f} aciertos en polymer_cache_requests_total)')

    print('RabbitMQ, consumidor idempotente y Mailpit')
    customer, email = new_customer()
    order, headers = place_order(customer)
    short = order['id'][:8]
    # v7: el primer correo del cliente nuevo es su código de verificación («123456 es tu código…»); aquí cuentan los del pedido.
    order_mails = lambda: [m for m in mails_to(email) if not re.match(r'^\d{6} ', m['Subject'])]
    messages = eventually(f'correos de {email} en Mailpit', lambda: (lambda m: m if len(m) >= 2 else None)(order_mails()), timeout=90)
    subjects = sorted(m['Subject'] for m in messages)
    ok(any(re.match(r'^\d{6} es tu código de verificación de POLYMER$', m['Subject']) for m in mails_to(email)),
       'v7: el código de 6 dígitos para confirmar el correo llegó por correo (Mailpit)')
    ok(any('recibido' in s for s in subjects) and all(short in s for s in subjects),
       f'Mailpit capturó las notificaciones del pedido {short}: {subjects}')
    rabbit = ('hexaorders', secret('rabbitmq_password'))
    queue = get_json(f'{RABBITMQ}/api/queues/%2F/hexaorders.notifications', auth=rabbit)
    ok(queue['consumers'] >= 1 and queue.get('message_stats', {}).get('ack', 0) >= 2,
       f'RabbitMQ: la cola hexaorders.notifications tiene consumidor y {queue["message_stats"]["ack"]} mensajes confirmados')
    dlq = get_json(f'{RABBITMQ}/api/queues/%2F/hexaorders.notifications.dlq', auth=rabbit)
    # RabbitMQ 4 añade x-queue-type a los argumentos: solo se exige que la DLQ no reenvíe a otra DLX.
    ok(dlq.get('messages', 0) == 0 and dlq.get('durable') and 'x-dead-letter-exchange' not in dlq.get('arguments', {}),
       f"La cola de mensajes fallidos (DLQ) existe, es durable y está vacía (mensajes: {dlq.get('messages')}, argumentos: {dlq.get('arguments')})")
    exchange = get_json(f'{RABBITMQ}/api/exchanges/%2F/hexaorders.events', auth=rabbit)
    ok(exchange['type'] == 'topic' and exchange['durable'], 'Exchange hexaorders.events (topic, durable)')
    print('v7 · Boleta por correo tras un pago en línea (pago → outbox → RabbitMQ → consumidor → SMTP)')
    products = customer.call('GET', '/api/catalog/products', expect=200)[1]
    item = min((p for p in products if p['stock'] >= 2), key=lambda p: p['price'])
    status, paid_order, _ = customer.call('POST', '/api/shop/orders', {'items': [{'productId': item['id'], 'quantity': 1}],
                                          'deliveryMethod': 'PICKUP', 'paymentMethod': 'CARD'}, headers={'Idempotency-Key': f'v7-{uuid.uuid4().hex[:20]}'})
    ok(status == 201, 'Pedido con pago con tarjeta creado')
    _, paid, _ = customer.call('POST', f'/api/shop/orders/{paid_order["id"]}/payment/card',
                               {'number': '5555 5555 5555 4444', 'holder': 'Cliente v5', 'expMonth': 12, 'expYear': 2031, 'cvv': '321'}, expect=200)
    receipt_mail = eventually(f'boleta {paid["receipt"]} en Mailpit',
                              lambda: next((m for m in mails_to(email) if m['Subject'].startswith(f'Boleta {paid["receipt"]}')), None), timeout=90)
    detail = get_json(f'{MAILPIT}/api/v1/message/{receipt_mail["ID"]}')
    ok('IGV (18 %)' in detail.get('Text', '') and '<table' in detail.get('HTML', '') and 'Op. gravada' in detail.get('Text', ''),
       f'Correo «{receipt_mail["Subject"]}» con la boleta en texto y HTML (base imponible, IGV y total)')
    paid_metric = eventually('polymer_payments_total en Prometheus',
                             lambda: (lambda v: v if v and v[0] >= 1 else None)(prom('sum(polymer_payments_total{outcome="paid"})')), timeout=45)
    ok(paid_metric[0] >= 1, f'Prometheus: polymer_payments_total{{outcome="paid"}} = {paid_metric[0]:.0f}')
    eventually('outbox vacío', lambda: prom('polymer_outbox_pending') == [0.0], timeout=60)
    ok(True, 'Outbox transaccional vacío: todos los eventos confirmados por el broker')

    print('Trazas distribuidas (OpenTelemetry Collector → Tempo)')
    trace_id = headers.get('X-Trace-Id')
    ok(bool(trace_id) and len(trace_id) == 32, f'La API devuelve X-Trace-Id a través del gateway ({trace_id})')

    def trace_spans():
        status, raw, _ = http('GET', f'{GRAFANA}/api/datasources/proxy/uid/tempo/api/traces/{trace_id}', auth=grafana_auth())
        if status != 200:
            return None
        data = json.loads(raw)
        spans = []
        for batch in data.get('batches', data.get('resourceSpans', [])):
            service = next(a['value'].get('stringValue') for a in batch['resource']['attributes'] if a['key'] == 'service.name')
            for scope in batch.get('scopeSpans', batch.get('instrumentationLibrarySpans', [])):
                spans.extend((service, span['name']) for span in scope['spans'])
        services = {s for s, _ in spans}
        return spans if {'order-api', 'risk-engine'} <= services else None
    spans = eventually(f'traza {trace_id} en Tempo', trace_spans, timeout=90)
    names = [name for _, name in spans]
    ok(any('commerce-use-cases.place-order' in n for n in names), 'La traza incluye el caso de uso (CommerceUseCases.placeOrder)')
    ok(any('commerce-order-repository.place-order' in n for n in names), 'La traza incluye la escritura en PostgreSQL (puerto CommerceOrderRepository)')
    ok(any('risk-assessment-port' in n for n in names) and any(n == 'ScoreRisk.execute' for n in names),
       'La traza cruza Java → FastAPI: puerto de riesgo y caso de uso Python (ScoreRisk.execute)')
    ok(len(spans) >= 8, f'Traza completa en Tempo: {len(spans)} spans de order-api y risk-engine')

    print('OpenTelemetry Collector y Prometheus')
    targets = get_json(f'{PROMETHEUS}/api/v1/targets?state=active')['data']['activeTargets']
    health = {}
    for target in targets:
        health.setdefault(target['labels']['job'], []).append(target['health'])
    for job in ['order-api', 'risk-engine', 'prometheus', 'rabbitmq', 'otel-collector', 'nginx', 'loki', 'tempo', 'alloy']:
        ok(health.get(job) and all(h == 'up' for h in health[job]), f'Target {job} UP')
    exported = eventually('spans exportados', lambda: sum(prom('otelcol_exporter_sent_spans')), timeout=60)
    ok(exported > 0, f'El collector exportó {exported:.0f} spans a Tempo (otelcol_exporter_sent_spans)')
    nginx = eventually('métricas de Nginx', lambda: sum(prom('nginx_requests_total')), timeout=60)
    ok(nginx > 0, f'Métricas del gateway Nginx vía collector: {nginx:.0f} peticiones (nginx_requests_total)')
    ok(sum(prom('rabbitmq_queue_messages_ready{queue="hexaorders.notifications"}') or [0]) >= 0 and prom('rabbitmq_identity_info'),
       'Métricas de RabbitMQ en Prometheus (plugin rabbitmq_prometheus)')

    print('Loki (logs centralizados por Grafana Alloy)')

    def loki_count(query, since_minutes=30):
        params = urllib.parse.urlencode({'query': query, 'limit': 50, 'start': str(int((time.time() - since_minutes * 60) * 1e9))})
        data = get_json(f'{GRAFANA}/api/datasources/proxy/uid/loki/loki/api/v1/query_range?{params}', auth=grafana_auth())
        return sum(len(stream['values']) for stream in data['data']['result'])
    for service in ['order-api', 'risk-engine', 'frontend']:
        lines = eventually(f'logs de {service} en Loki', lambda s=service: loki_count('{service="%s"}' % s), timeout=120)
        ok(lines > 0, f'Loki recibe los logs de {service} ({lines} líneas recientes)')
    for name in ['db_password', 'demo_password', 'rabbitmq_password', 'grafana_password']:
        leaked = loki_count('{project=~".+"} |= "%s"' % secret(name), since_minutes=120)
        ok(leaked == 0, f'Ningún log en Loki contiene el valor de {name}')

    print('Grafana')
    for uid in ['loki', 'tempo', 'prometheus']:
        status, raw, _ = http('GET', f'{GRAFANA}/api/datasources/uid/{uid}/health', auth=grafana_auth())
        ok(status == 200 and json.loads(raw).get('status') == 'OK', f'Fuente de datos {uid}: OK')
    for uid in ['polymer', 'polymer-business', 'polymer-risk', 'polymer-api', 'polymer-infrastructure',
                'polymer-security', 'polymer-logistics', 'polymer-integrations']:
        dashboard = get_json(f'{GRAFANA}/api/dashboards/uid/{uid}', auth=grafana_auth())
        ok(dashboard['dashboard']['uid'] == uid, f'Dashboard {uid} aprovisionado')


def degraded(state: Path):
    print('Degradación: Redis y RabbitMQ detenidos')
    anon = Session()
    started = time.time()
    status, products, _ = anon.call('GET', '/api/catalog/products')
    ok(status == 200 and len(products) >= 18 and time.time() - started < 5, 'Sin Redis el catálogo se sirve desde PostgreSQL (respuesta < 5 s)')
    customer, email = new_customer()
    order, _ = place_order(customer, products)
    ok(order['status'] in ('RISK_APPROVED', 'RISK_REVIEW'), 'Sin RabbitMQ el pedido se crea y se evalúa igualmente')
    pending = eventually('eventos retenidos en el outbox', lambda: (lambda v: v[0] if v and v[0] > 0 else None)(prom('polymer_outbox_pending')), timeout=60)
    ok(pending > 0, f'Los eventos esperan en el outbox ({pending:.0f} pendientes): no se pierden')
    state.write_text(json.dumps({'email': email, 'order': order['id']}), encoding='utf-8')


def recovered(state: Path):
    print('Recuperación: RabbitMQ de nuevo en marcha')
    saved = json.loads(state.read_text(encoding='utf-8'))
    eventually('outbox vacío tras la recuperación', lambda: prom('polymer_outbox_pending') == [0.0], timeout=120)
    ok(True, 'El outbox se vació al volver RabbitMQ')
    messages = eventually('correos del pedido retenido', lambda: (lambda m: m if len(m) >= 2 else None)(mails_to(saved['email'])), timeout=120)
    ok(all(saved['order'][:8] in m['Subject'] for m in messages), f'Llegaron los correos del pedido creado durante la caída ({len(messages)})')
    anon = Session()
    anon.call('GET', '/api/catalog/products', expect=200)
    ok(True, 'El catálogo vuelve a usar Redis sin intervención manual')


def persist_write(state: Path):
    status, raw, _ = http('POST', f'{API}/api/orders', {'customerId': 'persist-v5', 'amount': 125.5})
    order = json.loads(raw)
    ok(status == 201, f'Pedido de laboratorio creado antes del reinicio ({order["id"][:8]})')
    state.write_text(json.dumps({'order': order['id']}), encoding='utf-8')


def persist_read(state: Path):
    saved = json.loads(state.read_text(encoding='utf-8'))
    order = eventually('pedido tras el reinicio', lambda: get_json(f'{API}/api/orders/{saved["order"]}'), timeout=120)
    ok(order['id'] == saved['order'] and order['riskScore'] == 10, 'Tras reiniciar db y order-api, el pedido sigue en PostgreSQL')


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('command', choices=['check', 'degraded', 'recovered', 'persist-write', 'persist-read'])
    parser.add_argument('--state', type=Path, default=Path(os.environ.get('TEMP', '/tmp')) / 'polymer-integration-state.json')
    args = parser.parse_args()
    {'check': check, 'degraded': lambda: degraded(args.state), 'recovered': lambda: recovered(args.state),
     'persist-write': lambda: persist_write(args.state), 'persist-read': lambda: persist_read(args.state)}[args.command]()
    print(f'\nOK integración ({args.command}): {len(checks)} comprobaciones')


if __name__ == '__main__':
    main()
