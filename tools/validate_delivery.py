"""Valida empaquetado y manifiestos; esquemas oficiales opcionales descargados por el operador."""
import argparse
import json
from pathlib import Path

import jsonschema
import yaml

from generate_delivery import ROOT, compose, inline_dockerfile, monitoring_configmap


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
assert raw == compose(), 'Compose desactualizado'
document = yaml.safe_load(raw)
assert (ROOT / 'k8s/10-monitoring-config.yaml').read_text(encoding='utf-8') == monitoring_configmap()
for service in ['order-api', 'risk-engine']:
    actual = document['services'][service]['build']['dockerfile_inline'].replace('$$', '$')
    assert actual == inline_dockerfile(service), f'Fuentes inline alterados: {service}'
    for line in actual.splitlines():
        assert not (line.startswith('COPY ') and not line.startswith(('COPY <<', 'COPY --from='))), line
for definition in document['configs'].values():
    assert set(definition) == {'content'}, 'Config depende de un archivo externo'
for service in document['services'].values():
    for mount in service.get('volumes', []):
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
