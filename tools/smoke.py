"""Prueba HTTP integral tras docker compose up --wait. Solo usa stdlib."""
import base64
import json
import os
import time
import urllib.error
import urllib.request


def request(url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json', **(headers or {})})
    with urllib.request.urlopen(req, timeout=10) as response:
        return response.status, json.load(response)


api = os.environ.get('API_URL', 'http://127.0.0.1:8080')
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
print('OK: tres riesgos, persistencia, validación, targets Prometheus y Grafana')
