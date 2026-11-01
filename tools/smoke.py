"""Prueba HTTP integral tras docker compose up --wait. Solo usa stdlib."""
import base64
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

STATIC_DIR = Path(__file__).resolve().parents[1] / 'services/order-api/src/main/resources/static'
EXPECTED_TYPES = {'.html': 'text/html', '.css': 'text/css', '.js': 'javascript', '.svg': 'image/svg+xml'}


def request(url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json', **(headers or {})})
    with urllib.request.urlopen(req, timeout=10) as response:
        return response.status, json.load(response)


def fetch_raw(url):
    with urllib.request.urlopen(url, timeout=10) as response:
        return response.status, response.headers.get('Content-Type', ''), response.read()


api = os.environ.get('API_URL', 'http://127.0.0.1:8080')
_, before = request(api + '/api/orders/statistics')
prometheus = os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:9091')
grafana = os.environ.get('GRAFANA_URL', 'http://127.0.0.1:3000')
for amount, score, status in [(125.50, 10, 'APPROVED'), (750, 40, 'APPROVED'), (1500, 75, 'REVIEW')]:
    code, order = request(api + '/api/orders', {'customerId': 'demo-001', 'amount': amount})
    assert code == 201, (code, order)
    assert order['status'] == status and order['riskScore'] == score, order
    _, persisted = request(api + '/api/orders/' + order['id'])
    assert persisted['id'] == order['id'] and persisted['status'] == status
try:
    request(api + '/api/orders', {'customerId': 'demo-001', 'amount': 0})
    raise AssertionError('Aceptó importe inválido')
except urllib.error.HTTPError as error:
    assert error.code == 400, error.code
# Estadísticas persistentes (PostgreSQL): coherentes y con los 3 pedidos recién creados.
_, stats = request(api + '/api/orders/statistics')
assert stats['total'] == stats['approved'] + stats['review'], stats
assert stats['approved'] - before['approved'] >= 2 and stats['review'] - before['review'] >= 1, (before, stats)
# Frontend: cada archivo estático del repositorio se sirve desde el contenedor con su tipo MIME.
for file in sorted(STATIC_DIR.rglob('*')):
    if not file.is_file():
        continue
    path = '/' + file.relative_to(STATIC_DIR).as_posix()
    code, content_type, body = fetch_raw(api + path)
    assert code == 200 and EXPECTED_TYPES[file.suffix] in content_type, (path, code, content_type)
    assert body == file.read_bytes(), f'{path}: el contenedor sirve una versión distinta del repositorio'
code, content_type, index = fetch_raw(api + '/')
assert code == 200 and b'js/app.js' in index and b'css/base.css' in index, 'index.html no enlaza sus recursos'
for attempt in range(30):
    _, targets = request(prometheus + '/api/v1/targets')
    ready = {target['labels']['job']: target['health'] for target in targets['data']['activeTargets']}
    if all(ready.get(job) == 'up' for job in ['order-api', 'risk-engine', 'prometheus']):
        break
    time.sleep(2)
else:
    raise AssertionError(f'Targets no listos: {ready}')
_, health = request(grafana + '/api/health')
assert health.get('database') == 'ok', health
if os.environ.get('GRAFANA_PASSWORD'):
    credentials = base64.b64encode(('admin:' + os.environ['GRAFANA_PASSWORD']).encode()).decode()
    _, dashboard = request(grafana + '/api/dashboards/uid/hexaorders', headers={'Authorization': 'Basic ' + credentials})
    assert dashboard['dashboard']['uid'] == 'hexaorders'
print('OK: tres riesgos, persistencia, estadísticas, validación, recursos del frontend, targets Prometheus y Grafana')
