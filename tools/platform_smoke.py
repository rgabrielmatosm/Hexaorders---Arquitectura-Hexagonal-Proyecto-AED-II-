"""Prueba funcional de la plataforma (portal, roles, ciclo de vida, logística) contra el stack REAL.

Solo usa la biblioteca estándar. Cada rol inicia sesión con cookie de sesión + token CSRF, igual que
el navegador. Uso:
    python tools/platform_smoke.py --demo-password-file secrets/demo_password.txt
Variables: API_URL (por defecto http://127.0.0.1:8080), PROMETHEUS_URL (opcional, para métricas).
"""
import argparse
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

API = os.environ.get('API_URL', 'http://127.0.0.1:8080')
PROMETHEUS = os.environ.get('PROMETHEUS_URL', '')
checks = []


def ok(condition, message):
    if not condition:
        raise AssertionError(message)
    checks.append(message)
    print(f'  ✔ {message}')


class Session:
    """Cliente HTTP con cookies y token CSRF (equivalente a una pestaña del navegador)."""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None

    def call(self, method, path, body=None, headers=None, expect=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(API + path, data=data, method=method,
                                         headers={'Content-Type': 'application/json', 'Accept': 'application/json', **(headers or {})})
        if method != 'GET' and self.csrf:
            request.add_header('X-CSRF-TOKEN', self.csrf)
        try:
            with self.opener.open(request, timeout=15) as response:
                status, raw, response_headers = response.status, response.read(), response.headers
        except urllib.error.HTTPError as error:
            status, raw, response_headers = error.code, error.read(), error.headers
        payload = json.loads(raw) if raw else None
        if expect is not None and status != expect:
            raise AssertionError(f'{method} {path}: esperado {expect}, recibido {status}: {payload}')
        return status, payload, response_headers

    def refresh_csrf(self):
        _, token, _ = self.call('GET', '/api/auth/csrf', expect=200)
        self.csrf = token['token']

    def login(self, username, password):
        self.refresh_csrf()
        self.call('POST', '/api/auth/login', {'username': username, 'password': password}, expect=200)
        self.refresh_csrf()
        return self


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--demo-password-file', default='secrets/demo_password.txt')
    args = parser.parse_args()
    demo_password = os.environ.get('DEMO_PASSWORD') or open(args.demo_password_file, encoding='utf-8').read().strip()
    suffix = uuid.uuid4().hex[:8]

    print('Catálogo público')
    anon = Session()
    _, categories, _ = anon.call('GET', '/api/catalog/categories', expect=200)
    _, products, _ = anon.call('GET', '/api/catalog/products', expect=200)
    ok(len(categories) >= 6 and len(products) >= 18, f'Catálogo persistido: {len(categories)} categorías y {len(products)} productos')
    _, found, _ = anon.call('GET', '/api/catalog/products?q=monitor', expect=200)
    ok(any('Monitor' in p['name'] for p in found), 'La búsqueda de productos filtra en el backend')
    item = max((p for p in products if p['stock'] >= 3), key=lambda p: p['price'])
    cheap = min((p for p in products if p['stock'] >= 3), key=lambda p: p['price'])

    print('Precios calculados en el backend')
    anon.refresh_csrf()
    _, quote, _ = anon.call('POST', '/api/shop/quote', {'items': [{'productId': item['id'], 'quantity': 2}]}, expect=200)
    ok(abs(float(quote['total']) - 2 * float(item['price'])) < 0.001, f'Total del carrito calculado por el servidor: {quote["total"]} USD')
    status, _, _ = anon.call('POST', '/api/shop/quote', {'items': [{'productId': item['id'], 'quantity': 1, 'price': 0.01}]})
    ok(status == 400, 'Un precio enviado por el cliente se rechaza (400)')
    status, _, _ = anon.call('GET', '/api/shop/orders')
    ok(status == 401, 'Sin sesión no hay acceso a pedidos (401)')

    print('Registro, inicio de sesión y checkout idempotente')
    customer = Session()
    customer.refresh_csrf()
    # Política de contraseñas (PasswordPolicy): el servidor rechaza las comunes y las que contienen el usuario.
    for weak, reason in [('Password2026!', 'COMMON'), (f'Clave{suffix}9', 'CONTAINS_PERSONAL_DATA'), ('corta2026', 'TOO_SHORT')]:
        status, body, _ = customer.call('POST', '/api/auth/register', {'username': f'c_{suffix}', 'displayName': 'Cliente E2E',
                                                                       'email': f'c_{suffix}@example.com', 'password': weak})
        ok(status == 400 and body.get('code') == 'WEAK_PASSWORD' and reason in body.get('problems', []),
           f'El servidor rechaza una contraseña débil ({reason})')
    password = 'nube cobre faro tigre 2026'
    customer.call('POST', '/api/auth/register', {'username': f'c_{suffix}', 'displayName': 'Cliente E2E',
                                                 'email': f'c_{suffix}@example.com', 'password': password}, expect=201)
    customer.login(f'c_{suffix}', password)
    _, me, _ = customer.call('GET', '/api/auth/me', expect=200)
    ok(me['authenticated'] and me['user']['roles'] == ['CUSTOMER'], 'El registro crea una cuenta CUSTOMER y la sesión funciona')
    stock_before = item['stock']
    key = f'e2e-{suffix}-1'
    status, order, headers = customer.call('POST', '/api/shop/orders', {'items': [{'productId': item['id'], 'quantity': 1}]},
                                           headers={'Idempotency-Key': key})
    ok(status == 201 and order['channel'] == 'SHOP', f'Pedido creado desde el portal ({order["status"]}, riesgo {order["riskScore"]})')
    ok(order['status'] in ('RISK_APPROVED', 'RISK_REVIEW'), 'El motor de riesgo Python decide el estado inicial')
    status, replay, headers = customer.call('POST', '/api/shop/orders', {'items': [{'productId': item['id'], 'quantity': 1}]},
                                            headers={'Idempotency-Key': key})
    ok(status == 200 and replay['id'] == order['id'] and headers.get('Idempotent-Replay') == 'true',
       'Repetir la misma Idempotency-Key devuelve el mismo pedido sin duplicarlo')
    _, after, _ = anon.call('GET', f'/api/catalog/products/{item["id"]}', expect=200)
    ok(after['stock'] == stock_before - 1, 'El stock se descontó una sola vez')
    _, details, _ = customer.call('GET', f'/api/shop/orders/{order["id"]}', expect=200)
    ok([h['to'] for h in details['history']][:2] == ['CREATED', order['status']], 'El historial registra CREATED y la decisión de riesgo')
    status, _, _ = anon.call('GET', f'/api/orders/{order["id"]}')
    ok(status == 404, 'La API pública original no expone pedidos del portal (404)')

    print('Aislamiento entre clientes y roles')
    other = Session()
    other.refresh_csrf()
    other.call('POST', '/api/auth/register', {'username': f'o_{suffix}', 'displayName': 'Otro cliente',
                                              'email': f'o_{suffix}@example.com', 'password': password}, expect=201)
    other.login(f'o_{suffix}', password)
    status, _, _ = other.call('GET', f'/api/shop/orders/{order["id"]}')
    ok(status == 404, 'Un cliente no puede consultar pedidos de otro cliente (404)')
    status, _, _ = customer.call('GET', '/api/admin/dashboard')
    ok(status == 403, 'Un cliente no accede a la administración (403)')

    print('Cancelación por el cliente con devolución de stock')
    _, cancel_order, _ = customer.call('POST', '/api/shop/orders', {'items': [{'productId': cheap['id'], 'quantity': 2}]},
                                       headers={'Idempotency-Key': f'e2e-{suffix}-2'}, expect=201)
    _, mid, _ = anon.call('GET', f'/api/catalog/products/{cheap["id"]}', expect=200)
    _, cancelled, _ = customer.call('POST', f'/api/shop/orders/{cancel_order["id"]}/cancel', expect=200)
    _, restored, _ = anon.call('GET', f'/api/catalog/products/{cheap["id"]}', expect=200)
    ok(cancelled['status'] == 'CANCELLED' and restored['stock'] == mid['stock'] + 2, 'Cancelar devuelve las unidades al stock')

    print('Operador de pedidos')
    operator = Session().login('operador', demo_password)
    _, queue, _ = operator.call('GET', '/api/operations/orders?status=RISK_APPROVED,RISK_REVIEW', expect=200)
    ok(any(o['id'] == order['id'] for o in queue), 'El pedido aparece en la cola del operador')
    for target in ('CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH'):
        _, moved, _ = operator.call('POST', f'/api/operations/orders/{order["id"]}/transitions', {'status': target, 'note': 'e2e'}, expect=200)
    ok(moved['status'] == 'READY_FOR_DISPATCH', 'El operador confirma, prepara y deja listo para despacho')
    _, again, _ = operator.call('POST', f'/api/operations/orders/{order["id"]}/transitions', {'status': 'READY_FOR_DISPATCH'}, expect=200)
    ok(again['version'] == moved['version'], 'Repetir una transición es idempotente (misma versión)')
    status, _, _ = operator.call('POST', f'/api/operations/orders/{order["id"]}/transitions', {'status': 'DELIVERED'})
    ok(status == 409, 'Una transición inexistente se rechaza (409)')
    status, _, _ = operator.call('GET', '/api/logistics/assignees')
    ok(status == 403, 'El operador de pedidos no accede a logística (403)')

    print('Logística y calendario')
    courier = Session().login('logistica', demo_password)
    _, assignees, _ = courier.call('GET', '/api/logistics/assignees', expect=200)
    ana = next(a for a in assignees if a['username'] == 'logistica')
    status, _, _ = courier.call('POST', f'/api/logistics/orders/{order["id"]}/transitions', {'status': 'IN_TRANSIT'})
    ok(status == 409, 'No se puede despachar sin una entrega programada (409)')
    day = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0) + timedelta(days=2 + int(suffix, 16) % 300)
    start, end = day + timedelta(hours=10), day + timedelta(hours=12)
    iso = lambda value: value.strftime('%Y-%m-%dT%H:%M:%SZ')
    _, delivery, _ = courier.call('POST', '/api/logistics/deliveries', {'orderId': order['id'], 'assigneeId': ana['id'],
                                                                       'start': iso(start), 'end': iso(end), 'notes': 'Portería'}, expect=201)
    ok(delivery['assigneeId'] == ana['id'], 'Entrega programada y asignada a un responsable')
    _, second, _ = customer.call('POST', '/api/shop/orders', {'items': [{'productId': cheap['id'], 'quantity': 1}]},
                                 headers={'Idempotency-Key': f'e2e-{suffix}-3'}, expect=201)
    operator.call('POST', f'/api/operations/orders/{second["id"]}/transitions', {'status': 'CONFIRMED'}, expect=200)
    status, conflict, _ = courier.call('POST', '/api/logistics/deliveries', {'orderId': second['id'], 'assigneeId': ana['id'],
                                                                             'start': iso(start + timedelta(hours=1)), 'end': iso(end + timedelta(hours=1))})
    ok(status == 409 and conflict.get('code') == 'SCHEDULE_CONFLICT', 'Un solapamiento del mismo responsable se rechaza (409 SCHEDULE_CONFLICT)')
    _, moved_delivery, _ = courier.call('PUT', f'/api/logistics/deliveries/{delivery["id"]}', {
        'assigneeId': ana['id'], 'start': iso(start + timedelta(hours=4)), 'end': iso(end + timedelta(hours=4)), 'reason': 'Cliente ausente'}, expect=200)
    _, history, _ = courier.call('GET', f'/api/logistics/deliveries/{delivery["id"]}/history', expect=200)
    ok(moved_delivery['version'] == 1 and [h['action'] for h in history] == ['CREATED', 'RESCHEDULED'],
       'Reprogramación con motivo e historial de cambios')
    _, calendar, _ = courier.call('GET', f'/api/logistics/deliveries?from={iso(day)}&to={iso(day + timedelta(days=1))}', expect=200)
    ok(any(v['delivery']['id'] == delivery['id'] for v in calendar), 'El calendario devuelve la entrega persistida en PostgreSQL')
    courier.call('POST', f'/api/logistics/orders/{order["id"]}/transitions', {'status': 'IN_TRANSIT'}, expect=200)
    _, delivered, _ = courier.call('POST', f'/api/logistics/orders/{order["id"]}/transitions', {'status': 'DELIVERED'}, expect=200)
    ok(delivered['status'] == 'DELIVERED', 'Logística despacha y entrega el pedido')
    _, incident, _ = courier.call('POST', '/api/incidents', {'orderId': order['id'], 'category': 'DELAY', 'description': 'Tráfico intenso'}, expect=201)
    ok(incident['status'] == 'OPEN', 'Incidencia registrada')

    print('Administración')
    admin = Session().login('admin', demo_password)
    _, dashboard, _ = admin.call('GET', '/api/admin/dashboard', expect=200)
    ok(dashboard['ordersByStatus']['DELIVERED'] >= 1 and float(dashboard['shopSales']) > 0, 'El panel administrativo usa datos reales')
    _, resolved, _ = admin.call('POST', f'/api/incidents/{incident["id"]}/resolve', {'resolution': 'Entregado con retraso'}, expect=200)
    ok(resolved['status'] == 'RESOLVED', 'El administrador resuelve la incidencia')
    _, audit, _ = admin.call('GET', '/api/admin/audit?limit=200', expect=200)
    actions = {event['action'] for event in audit}
    ok({'ORDER_PLACED', 'ORDER_STATUS_CHANGED', 'DELIVERY_SCHEDULED', 'DELIVERY_RESCHEDULED', 'LOGIN_SUCCEEDED'} <= actions,
       'La auditoría registra pedidos, transiciones, entregas e inicios de sesión')
    _, adjusted, _ = admin.call('POST', f'/api/admin/products/{cheap["id"]}/stock', {'delta': 5, 'reason': 'Reposición e2e'}, expect=200)
    ok(adjusted['stock'] >= 5, 'Ajuste de inventario')
    status, _, _ = admin.call('POST', f'/api/admin/products/{cheap["id"]}/stock', {'delta': -100000, 'reason': 'x'})
    ok(status == 409, 'El stock nunca queda negativo (409)')

    print('Observabilidad (rol de solo lectura)')
    viewer = Session().login('observador', demo_password)
    _, health, _ = viewer.call('GET', '/api/ops/health', expect=200)
    ok(health['database'] and health['riskEngine'], 'Salud: PostgreSQL y motor de riesgo comprobados desde el backend')
    status, _, _ = viewer.call('POST', f'/api/operations/orders/{second["id"]}/transitions', {'status': 'PREPARING'})
    ok(status == 403, 'El rol de observabilidad no modifica datos de negocio (403)')

    bad = Session()
    bad.refresh_csrf()
    status, _, _ = bad.call('POST', '/api/auth/login', {'username': 'admin', 'password': 'incorrecta'})
    ok(status == 401, 'Credenciales incorrectas → 401')

    if PROMETHEUS:
        print('Métricas nuevas en Prometheus')
        wanted = ['polymer_shop_orders_placed_total', 'polymer_order_transitions_total', 'polymer_orders_by_status',
                  'polymer_auth_logins_total', 'polymer_deliveries_total', 'polymer_delivery_conflicts_total',
                  'polymer_incidents_reported_total', 'polymer_access_denied_total', 'jvm_memory_used_bytes',
                  'hikaricp_connections_active', 'orders_created_total', 'risk_evaluations_total']
        for attempt in range(20):
            with urllib.request.urlopen(PROMETHEUS + '/api/v1/label/__name__/values', timeout=10) as response:
                names = set(json.load(response)['data'])
            missing = [name for name in wanted if name not in names]
            if not missing:
                break
            time.sleep(3)
        ok(not missing, f'Prometheus recoge las métricas de la plataforma y las originales ({len(wanted)}){" — faltan " + str(missing) if missing else ""}')

    print(f'\nOK plataforma: {len(checks)} comprobaciones')


if __name__ == '__main__':
    main()
