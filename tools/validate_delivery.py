"""Valida empaquetado y manifiestos; esquemas oficiales opcionales descargados por el operador."""
import argparse
import json
from pathlib import Path

import jsonschema
import yaml

from generate_delivery import BUILD_CONTEXTS, ROOT, canonical, compose, inline_dockerfile, monitoring_configmap


def validate(schema, document, name, draft=None):
    validator = (draft or jsonschema.validators.validator_for(schema))(schema)
    errors = sorted(validator.iter_errors(document), key=lambda error: str(error.path))
    if errors:
        raise SystemExit(name + ': ' + '\n'.join(f'{list(error.path)} {error.message}' for error in errors))


parser = argparse.ArgumentParser()
parser.add_argument('--compose-schema', type=Path)
parser.add_argument('--kubernetes-schema', type=Path)
args = parser.parse_args()
raw = (ROOT / 'docker-compose.yml').read_text(encoding='utf-8')
# Comparación canónica: los bloques gzip + base64 cambian de bytes según la versión de zlib.
assert canonical(raw) == canonical(compose()), 'Compose desactualizado'
document = yaml.safe_load(raw)
assert (ROOT / 'k8s/10-monitoring-config.yaml').read_text(encoding='utf-8') == monitoring_configmap()
for service in BUILD_CONTEXTS:
    actual = document['services'][service]['build']['dockerfile_inline'].replace('$$', '$')
    assert canonical(actual) == canonical(inline_dockerfile(service)), f'Fuentes inline alterados: {service}'
    for line in actual.splitlines():
        assert not (line.startswith('COPY ') and not line.startswith(('COPY <<', 'COPY --from='))), line
for definition in document.get('configs', {}).values():
    assert set(definition) == {'content'}, 'Config depende de un archivo externo'
for name in ['order-api', 'risk-engine', 'prometheus', 'grafana',
             # v5: gateway y servicios del perfil "full"
             'frontend', 'redis', 'rabbitmq', 'mailpit', 'loki', 'tempo', 'otel-collector', 'alloy']:
    assert document['services'][name].get('read_only') is True, f'{name} debe ser read_only'
# v5: modos básico y completo.
BASIC = {'db', 'risk-engine', 'order-api', 'prometheus', 'grafana', 'frontend'}
FULL = {'redis', 'rabbitmq', 'mailpit', 'loki', 'tempo', 'otel-collector', 'alloy'}
assert set(document['services']) == BASIC | FULL, f'Servicios inesperados: {sorted(set(document["services"]) ^ (BASIC | FULL))}'
for name, service in document['services'].items():
    assert ('profiles' in service) == (name in FULL), f'{name}: perfil incorrecto'
    if name in FULL:
        assert service['profiles'] == ['full'], f'{name}: debe pertenecer solo al perfil full'
    assert 'healthcheck' in service, f'{name}: falta healthcheck'
    image = service.get('image', '')
    assert ':' in image and not image.endswith(':latest'), f'{name}: imagen sin versión fijada ({image})'
    for dependency, condition in (service.get('depends_on') or {}).items():
        if dependency in FULL and name not in FULL:
            assert condition.get('required') is False, f'{name}: la dependencia {dependency} debe ser opcional (required: false)'
# La tienda entra por el gateway; cada interfaz web tiene un enlace local de verificación y AMQP nunca se publica.
published = {name: service.get('ports', []) for name, service in document['services'].items() if service.get('ports')}
assert '127.0.0.1:8080:8080' in published.get('frontend', []), 'El gateway debe publicar 127.0.0.1:8080'
# Los datos (PostgreSQL, Redis) y los almacenes de telemetría (Loki, Tempo) nunca se publican.
for name in ['db', 'redis', 'loki', 'tempo']:
    assert name not in published, f'{name} no debe publicar puertos en el host'
# v8 · Enlaces de verificación por contenedor (Docker Desktop): lista cerrada, solo en 127.0.0.1.
LINKS = {
    'frontend': ['127.0.0.1:8080:8080'], 'order-api': ['127.0.0.1:8081:8080'], 'risk-engine': ['127.0.0.1:8000:8000'],
    'prometheus': ['127.0.0.1:9091:9090'], 'grafana': ['127.0.0.1:3000:3000'], 'rabbitmq': ['127.0.0.1:15672:15672'],
    'mailpit': ['127.0.0.1:8025:8025'], 'otel-collector': ['127.0.0.1:13133:13133'], 'alloy': ['127.0.0.1:12345:12345'],
}
for name, ports in published.items():
    assert [str(port) for port in ports] == LINKS.get(name), f'{name}: puertos publicados {ports}; permitidos {LINKS.get(name)}'
for name, ports in published.items():
    assert all(str(port).startswith('127.0.0.1:') for port in ports), f'{name}: publicar solo en 127.0.0.1'
assert all(str(port) != '127.0.0.1:5672:5672' for port in published.get('rabbitmq', [])), 'AMQP no se publica'
for name, definition in document['secrets'].items():
    # Compose solo admite secretos de archivo en servicios read_only.
    assert set(definition) == {'file'} and definition['file'] == f'./secrets/{name}.txt', f'Secreto {name}: usar ./secrets/{name}.txt'
DOCKER_SOCKET = '/var/run/docker.sock:/var/run/docker.sock:ro'
for name, service in document['services'].items():
    for mount in service.get('volumes', []):
        if mount == DOCKER_SOCKET and name == 'alloy':
            continue  # única excepción documentada: el agente de logs lee la API de Docker en solo lectura
        assert not mount.startswith(('./', '../', '/')), f'Bind mount no autosuficiente: {mount}'
    if service.get('ports'):
        assert any(not document['networks'][network].get('internal', False) for network in service['networks'])
if args.compose_schema:
    validate(json.loads(args.compose_schema.read_text(encoding='utf-8')), document, 'Compose')

objects = []
for file in sorted((ROOT / 'k8s').glob('*.yaml')):
    objects.extend((file.name, item) for item in yaml.safe_load_all(file.read_text(encoding='utf-8')) if item)
assert len(objects) == 14, f'Objetos Kubernetes inesperados: {len(objects)}'
if args.kubernetes_schema:
    definitions = json.loads(args.kubernetes_schema.read_text(encoding='utf-8'))['definitions']
    for filename, resource in objects:
        version = resource['apiVersion'].split('/')[-1]
        group = resource['apiVersion'].split('/')[0] if '/' in resource['apiVersion'] else ''
        gvk = {'group': group, 'version': version, 'kind': resource['kind']}
        key = next(name for name, definition in definitions.items() if gvk in definition.get('x-kubernetes-group-version-kind', []))
        schema = {'$ref': '#/definitions/' + key, 'definitions': definitions}
        validate(schema, resource, filename + '/' + resource['metadata']['name'], jsonschema.Draft4Validator)
print(f'OK: Compose autosuficiente y {len(objects)} objetos Kubernetes; esquemas aplicados cuando se suministran')
