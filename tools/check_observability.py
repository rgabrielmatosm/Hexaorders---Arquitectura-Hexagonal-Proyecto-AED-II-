"""Prueba de preservación de la observabilidad.

Compara la configuración actual con infra/observability-baseline.json (capturada antes de la
ampliación) y falla si algún dashboard, panel, consulta, datasource, job de scraping, regla
de alerta o métrica instrumentada previa desaparece o cambia. Las ampliaciones están permitidas;
las pérdidas no. Requiere PyYAML (requirements-tools.txt).
"""
import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / 'infra/observability-baseline.json').read_text(encoding='utf-8'))
errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


# 1. Dashboards y paneles previos: mismos uid, títulos y consultas.
for old in BASELINE['grafana_dashboards']:
    path = ROOT / old['file']
    if not path.is_file():
        fail(f'Dashboard eliminado: {old["file"]}')
        continue
    current = json.loads(path.read_text(encoding='utf-8'))
    if current.get('uid') != old['uid']:
        fail(f'{old["file"]}: uid cambiado {old["uid"]} -> {current.get("uid")}')
    if current.get('title') != old['title']:
        fail(f'{old["file"]}: título cambiado')
    panels = {panel['id']: panel for panel in current.get('panels', [])}
    for panel in old['panels']:
        now = panels.get(panel['id'])
        if now is None:
            fail(f'{old["uid"]}: panel {panel["id"]} «{panel["title"]}» eliminado')
            continue
        exprs = [target['expr'] for target in now.get('targets', [])]
        if now['title'] != panel['title'] or now['type'] != panel['type'] or exprs != panel['exprs']:
            fail(f'{old["uid"]}: panel {panel["id"]} «{panel["title"]}» modificado')

# 2. Todos los dashboards del repositorio se cargan en Grafana y usan el datasource conservado.
grafana_dockerfile = (ROOT / 'infra/grafana/Dockerfile').read_text(encoding='utf-8')
dashboards = sorted((ROOT / 'infra/grafana/dashboards').glob('*.json'))
uids = {}
for path in dashboards:
    data = json.loads(path.read_text(encoding='utf-8'))
    if f'dashboards/{path.name} /etc/grafana/dashboards/{path.name}' not in grafana_dockerfile:
        fail(f'{path.name} no se copia en la imagen de Grafana (infra/grafana/Dockerfile)')
    if data.get('uid') in uids:
        fail(f'uid duplicado {data.get("uid")} en {path.name} y {uids[data.get("uid")]}')
    uids[data.get('uid')] = path.name
    for panel in data.get('panels', []):
        if panel.get('type') in ('row', 'text'):
            continue
        if panel.get('datasource', {}).get('uid') != 'prometheus':
            fail(f'{path.name}: panel {panel.get("id")} no usa el datasource uid=prometheus')

# 3. Datasources.
datasources = yaml.safe_load((ROOT / 'infra/grafana/provisioning/datasource.yml').read_text(encoding='utf-8'))['datasources']
for old in BASELINE['grafana_datasources']:
    match = [d for d in datasources if d.get('uid') == old['uid']]
    if not match or any(match[0].get(key) != old[key] for key in ('name', 'type', 'url')):
        fail(f'Datasource {old["uid"]} eliminado o modificado')

# 4. Prometheus: global, ficheros de reglas y jobs previos intactos.
prometheus = yaml.safe_load((ROOT / 'infra/prometheus/prometheus.yml').read_text(encoding='utf-8'))
if prometheus.get('global') != BASELINE['prometheus']['global']:
    fail('prometheus.yml: bloque global modificado (intervalos o etiquetas)')
for rule_file in BASELINE['prometheus']['rule_files']:
    if rule_file not in prometheus.get('rule_files', []):
        fail(f'prometheus.yml: rule_file eliminado {rule_file}')
jobs = {job['job_name']: job for job in prometheus.get('scrape_configs', [])}
for old in BASELINE['prometheus']['scrape_jobs']:
    job = jobs.get(old['job_name'])
    if job is None:
        fail(f'Job de scraping eliminado: {old["job_name"]}')
        continue
    if job.get('metrics_path', '/metrics') != old['metrics_path'] or job['static_configs'][0]['targets'] != old['targets']:
        fail(f'Job {old["job_name"]} modificado')

# 5. Reglas de alerta previas intactas.
alerts = yaml.safe_load((ROOT / 'infra/prometheus/alerts.yml').read_text(encoding='utf-8'))
rules = {rule['alert']: rule for group in alerts['groups'] for rule in group['rules'] if 'alert' in rule}
for old in BASELINE['alert_rules']:
    rule = rules.get(old['alert'])
    if rule is None:
        fail(f'Alerta eliminada: {old["alert"]}')
    elif rule['expr'] != old['expr'] or rule.get('for') != old['for']:
        fail(f'Alerta {old["alert"]} modificada')

# 5b. Regla de compatibilidad: los paneles originales consultan orders_created_total, que el cliente
#     Prometheus 1.x exporta como orders_total; la recording rule la republica con su nombre original.
recorded = {rule.get('record'): rule for group in alerts['groups'] for rule in group['rules'] if 'record' in rule}
if recorded.get('orders_created_total', {}).get('expr') != 'orders_total':
    fail('Falta la recording rule orders_created_total = orders_total (los paneles originales quedarían sin datos)')

# 6. Métricas instrumentadas previas: siguen declaradas en el código.
sources = '\n'.join(p.read_text(encoding='utf-8') for p in list((ROOT / 'services/order-api/src/main/java').rglob('*.java'))
                    + list((ROOT / 'services/risk-engine/risk_engine').rglob('*.py')))
code_names = {
    'orders_created_total': '"orders_created"',
    'orders_risk_failures_total': '"orders_risk_failures"',
    'risk_evaluations_total': '"risk_evaluations_total"',
    'risk_evaluation_duration_seconds_bucket': '"risk_evaluation_duration_seconds"',
}
for metric, literal in code_names.items():
    if metric in BASELINE['instrumented_metrics'] and literal not in sources:
        fail(f'Métrica instrumentada eliminada del código: {metric}')
if not re.search(r'percentiles-histogram:\s*\n\s*http\.server\.requests:\s*true',
                 (ROOT / 'services/order-api/src/main/resources/application.yml').read_text(encoding='utf-8')):
    fail('application.yml: histograma http.server.requests desactivado (rompe los paneles de latencia)')

if errors:
    print('FALLO de preservación de observabilidad:\n  - ' + '\n  - '.join(errors))
    sys.exit(1)
print(f'OK: observabilidad previa conservada ({len(BASELINE["grafana_dashboards"][0]["panels"])} paneles, '
      f'{len(BASELINE["alert_rules"])} alertas, {len(BASELINE["prometheus"]["scrape_jobs"])} jobs) · '
      f'{len(dashboards)} dashboards en total, {len(rules)} alertas en total')
