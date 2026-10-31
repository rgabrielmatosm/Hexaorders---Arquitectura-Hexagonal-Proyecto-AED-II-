"""Empaqueta fuentes y configuraciones en UN Compose reproducible, sin dependencias Python."""
from __future__ import annotations

import argparse
import hashlib
import json
import shlex
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def inline_dockerfile(service: str) -> str:
    context = ROOT / 'services' / service
    result: list[str] = []
    index = 0
    for line in (context / 'Dockerfile').read_text(encoding='utf-8').splitlines():
        if not line.startswith('COPY ') or line.startswith('COPY --from='):
            result.append(line)
            continue
        parts = shlex.split(line)
        if len(parts) != 3 or parts[1].startswith('--'):
            raise ValueError(f'COPY no soportado: {line}')
        source, destination = parts[1:]
        origin = context / source
        files = sorted(origin.rglob('*')) if origin.is_dir() else [origin]
        for file in files:
            if not file.is_file() or any(part in {'__pycache__', '.pytest_cache', '.venv'} for part in file.parts):
                continue
            if origin.is_dir():
                target = str(Path(destination) / file.relative_to(origin)).replace('\\', '/')
            else:
                target = f'{destination.rstrip("/")}/{file.name}' if destination.endswith('/') or destination == '.' else destination
            marker = f'HEXFILE_{index}'
            content = file.read_text(encoding='utf-8').replace('\r\n', '\n').rstrip('\n')
            if marker in content:
                raise ValueError(f'Colisión de heredoc en {file}')
            result.extend([f"COPY <<'{marker}' {target}", content, marker])
            index += 1
    return '\n'.join(result) + '\n'


def block(content: str, indent: int) -> str:
    # Compose interpola incluso los escalares YAML literales: proteger Java/JS/PromQL.
    return '\n'.join(' ' * indent + line.replace('$', '$$') for line in content.rstrip('\n').splitlines())


def compose() -> str:
    base = (ROOT / 'infra/compose.base.yml').read_text(encoding='utf-8')
    for marker, service in [('__INLINE_JAVA__', 'order-api'), ('__INLINE_RISK__', 'risk-engine')]:
        base = base.replace(marker, '|\n' + block(inline_dockerfile(service), 8))
    config_files = {
        'prometheus_config': 'infra/prometheus/prometheus.yml',
        'prometheus_alerts': 'infra/prometheus/alerts.yml',
        'grafana_datasource': 'infra/grafana/provisioning/datasource.yml',
        'grafana_provider': 'infra/grafana/provisioning/dashboards.yml',
        'grafana_dashboard': 'infra/grafana/dashboards/hexaorders.json',
    }
    configs = ['configs:']
    for name, path in config_files.items():
        configs.extend([f'  {name}:', '    content: |', block((ROOT / path).read_text(encoding='utf-8'), 6)])
    base = base.replace('# __INLINE_CONFIGS__', '\n'.join(configs))
    digest = hashlib.sha256(base.encode()).hexdigest()
    return f'# AUTOGENERADO: python tools/generate_delivery.py — SHA256 cuerpo {digest}\n' + base


def monitoring_configmap() -> str:
    # JSON es YAML 1.2 válido y evita alterar $labels de las reglas Prometheus.
    paths = {
        'prometheus.yml': 'infra/prometheus/prometheus.yml',
        'alerts.yml': 'infra/prometheus/alerts.yml',
        'datasource.yml': 'infra/grafana/provisioning/datasource.yml',
        'dashboards.yml': 'infra/grafana/provisioning/dashboards.yml',
        'hexaorders.json': 'infra/grafana/dashboards/hexaorders.json',
    }
    doc = {'apiVersion': 'v1', 'kind': 'ConfigMap', 'metadata': {'name': 'monitoring-config', 'namespace': 'hexaorders'},
           'data': {key: (ROOT / value).read_text(encoding='utf-8') for key, value in paths.items()}}
    return json.dumps(doc, ensure_ascii=False, indent=2) + '\n'


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='No escribir; fallar si entregables están desactualizados')
    args = parser.parse_args()
    outputs = {'docker-compose.yml': compose(), 'k8s/10-monitoring-config.yaml': monitoring_configmap()}
    for name, expected in outputs.items():
        target = ROOT / name
        if args.check:
            if not target.exists() or target.read_text(encoding='utf-8') != expected:
                raise SystemExit(f'Entregable desactualizado: {name}; ejecutar python tools/generate_delivery.py')
        else:
            target.write_text(expected, encoding='utf-8', newline='\n')
        print(f'OK {name}')


if __name__ == '__main__':
    main()

