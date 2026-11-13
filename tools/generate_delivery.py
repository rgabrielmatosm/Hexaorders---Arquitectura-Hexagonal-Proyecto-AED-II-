"""Empaqueta fuentes y configuraciones en UN Compose reproducible, sin dependencias Python."""
from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import json
import shlex
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read_text(path: Path) -> str:
    # Normaliza CRLF -> LF: el entregable debe ser idéntico en Windows, Linux y macOS.
    return path.read_text(encoding='utf-8').replace('\r\n', '\n')


def sorted_files(origin: Path) -> list[Path]:
    # Path.__lt__ ignora mayúsculas en Windows pero no en POSIX; ordenar por la ruta
    # relativa POSIX (sensible a mayúsculas) garantiza el mismo orden en cualquier SO.
    return sorted(origin.rglob('*'), key=lambda file: file.relative_to(origin).as_posix())


# Servicio -> contexto de build con Dockerfile. Prometheus y Grafana llevan su
# configuración dentro de la imagen para poder ejecutarse con read_only: true
# (Compose no puede inyectar configs.content en contenedores de solo lectura).
BUILD_CONTEXTS = {
    'order-api': 'services/order-api',
    'risk-engine': 'services/risk-engine',
    'prometheus': 'infra/prometheus',
    'grafana': 'infra/grafana',
    # v5
    'frontend': 'infra/frontend',
    'rabbitmq': 'infra/rabbitmq',
    'loki': 'infra/loki',
    'tempo': 'infra/tempo',
    'otel-collector': 'infra/otel-collector',
    'alloy': 'infra/alloy',
}
INLINE_MARKERS = {
    '__INLINE_JAVA__': 'order-api',
    '__INLINE_RISK__': 'risk-engine',
    '__INLINE_PROMETHEUS__': 'prometheus',
    '__INLINE_GRAFANA__': 'grafana',
    '__INLINE_FRONTEND__': 'frontend',
    '__INLINE_RABBITMQ__': 'rabbitmq',
    '__INLINE_LOKI__': 'loki',
    '__INLINE_TEMPO__': 'tempo',
    '__INLINE_OTEL_COLLECTOR__': 'otel-collector',
    '__INLINE_ALLOY__': 'alloy',
}


# BuildKit recibe el Dockerfile en un solo mensaje gRPC (máximo 4 MiB, y el contenido viaja más de una
# vez): los archivos de texto grandes (catálogos de traducción, catálogo SQL) se incrustan comprimidos
# con gzip + base64 y una instrucción RUN los restaura byte a byte antes de compilar.
LARGE_FILE = 64 * 1024


# v7: archivos binarios (video y póster de la portada) viajan igual, en gzip + base64, byte a byte.
BINARY_SUFFIXES = {'.mp4', '.webm', '.jpg', '.jpeg', '.png', '.gif', '.ico', '.woff2'}


def packed(content) -> str:
    raw = content if isinstance(content, bytes) else content.encode('utf-8')
    data = gzip.compress(raw, compresslevel=9, mtime=0)
    encoded = base64.b64encode(data).decode('ascii')
    return '\n'.join(encoded[i:i + 76] for i in range(0, len(encoded), 76))


def canonical(text: str) -> str:
    """Sustituye cada bloque gzip + base64 por el SHA-256 de su contenido descomprimido.

    gzip no produce los mismos bytes con todas las versiones de zlib (Python de Windows, de Linux, del CI):
    la comprobación --check y el SHA de la cabecera se calculan sobre este texto canónico, así que dos
    entregables con el mismo contenido son iguales aunque se hayan generado en equipos distintos.
    """
    lines, out, i = text.split('\n'), [], 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        i += 1
        stripped = line.lstrip(' ')
        if not stripped.startswith('COPY <<'):
            continue
        indent = line[:len(line) - len(stripped)]
        for name in [part.split("'")[1] for part in stripped.split() if part.startswith("<<'")]:
            body = []
            while i < len(lines) and lines[i] != indent + name:
                body.append(lines[i][len(indent):])
                i += 1
            if name.endswith('.gz.b64'):
                data = gzip.decompress(base64.b64decode(''.join(body)))
                body = [f'sha256:{hashlib.sha256(data).hexdigest()}']
            out.extend(indent + b for b in body)
            if i < len(lines):
                out.append(lines[i])
                i += 1
    return '\n'.join(out)


def inline_dockerfile(service: str) -> str:
    context = ROOT / BUILD_CONTEXTS[service]
    result: list[str] = []
    index = 0
    for line in read_text(context / 'Dockerfile').splitlines():
        if not line.startswith('COPY ') or line.startswith('COPY --from='):
            result.append(line)
            continue
        parts = shlex.split(line)
        if len(parts) != 3 or parts[1].startswith('--'):
            raise ValueError(f'COPY no soportado: {line}')
        source, destination = parts[1:]
        # '@repo/…' = ruta desde la raíz del repositorio (p. ej. el gateway reutiliza los estáticos de order-api).
        origin = ROOT / source[len('@repo/'):] if source.startswith('@repo/') else context / source
        files = sorted_files(origin) if origin.is_dir() else [origin]
        files = [file for file in files if file.is_file()
                 and not any(part in {'__pycache__', '.pytest_cache', '.venv'} for part in file.parts)]
        if not origin.is_dir():
            file = files[0]
            target = f'{destination.rstrip("/")}/{file.name}' if destination.endswith('/') or destination == '.' else destination
            marker = f'HEXFILE_{index}'
            content = read_text(file).rstrip('\n')
            if marker in content:
                raise ValueError(f'Colisión de heredoc en {file}')
            result.extend([f"COPY <<'{marker}' {target}", content, marker])
            index += 1
            continue
        # Un COPY por directorio con heredocs con nombre (el delimitador es el nombre del
        # archivo): cada instrucción COPY es una capa y overlayfs admite como máximo ~125
        # capas por imagen, así que un COPY por archivo deja de construir al crecer el proyecto.
        groups: dict[str, list[Path]] = {}
        for file in files:
            relative = file.relative_to(origin)
            directory = str(Path(destination) / relative.parent).replace('\\', '/')
            groups.setdefault(directory, []).append(file)
        for directory, members in groups.items():
            heredocs, bodies, unpack = [], [], []
            for file in members:
                if not all(ch.isalnum() or ch in '._-' for ch in file.name):
                    raise ValueError(f'Nombre de archivo no soportado en heredoc: {file}')
                name = file.name
                if file.suffix.lower() in BINARY_SUFFIXES:
                    content, name = packed(file.read_bytes()), f'{file.name}.gz.b64'
                    unpack.append(f'base64 -d {name} | gunzip > {file.name} && rm {name}')
                    heredocs.append(f"<<'{name}'")
                    bodies.extend([content, name])
                    continue
                content = read_text(file).rstrip('\n')
                if len(content.encode('utf-8')) > LARGE_FILE:
                    # El heredoc termina en salto de línea: se comprime el contenido con él para restaurarlo igual.
                    content, name = packed(content + '\n'), f'{file.name}.gz.b64'
                    unpack.append(f'base64 -d {name} | gunzip > {file.name} && rm {name}')
                if name in content.split('\n'):
                    raise ValueError(f'Colisión de heredoc en {file}')
                heredocs.append(f"<<'{name}'")
                bodies.extend([content, name])
            target_dir = directory.rstrip('/')
            result.append(f"COPY {' '.join(heredocs)} {target_dir}/")
            result.extend(bodies)
            if unpack:
                result.append(f"RUN cd {target_dir} && " + ' && '.join(unpack))
            index += 1
    return '\n'.join(result) + '\n'


def block(content: str, indent: int) -> str:
    # Compose interpola incluso los escalares YAML literales: proteger Java/JS/PromQL.
    return '\n'.join(' ' * indent + line.replace('$', '$$') for line in content.rstrip('\n').splitlines())


def compose() -> str:
    base = read_text(ROOT / 'infra/compose.base.yml')
    for marker, service in INLINE_MARKERS.items():
        if base.count(marker) != 1:
            raise ValueError(f'Marcador {marker} ausente o duplicado en compose.base.yml')
        base = base.replace(marker, '|\n' + block(inline_dockerfile(service), 8))
    digest = hashlib.sha256(canonical(base).encode()).hexdigest()
    return f'# AUTOGENERADO: python tools/generate_delivery.py — SHA256 cuerpo {digest}\n' + base


def monitoring_configmap() -> str:
    # JSON es YAML 1.2 válido y evita alterar $labels de las reglas Prometheus.
    paths = {
        'prometheus.yml': 'infra/prometheus/prometheus.yml',
        'alerts.yml': 'infra/prometheus/alerts.yml',
        'datasource.yml': 'infra/grafana/provisioning/datasource.yml',
        'dashboards.yml': 'infra/grafana/provisioning/dashboards.yml',
        'polymer.json': 'infra/grafana/dashboards/polymer.json',
        'polymer-business.json': 'infra/grafana/dashboards/polymer-business.json',
        'polymer-risk.json': 'infra/grafana/dashboards/polymer-risk.json',
        'polymer-api.json': 'infra/grafana/dashboards/polymer-api.json',
        'polymer-infrastructure.json': 'infra/grafana/dashboards/polymer-infrastructure.json',
        'polymer-security.json': 'infra/grafana/dashboards/polymer-security.json',
        'polymer-logistics.json': 'infra/grafana/dashboards/polymer-logistics.json',
        'polymer-integrations.json': 'infra/grafana/dashboards/polymer-integrations.json',
    }
    doc = {'apiVersion': 'v1', 'kind': 'ConfigMap', 'metadata': {'name': 'monitoring-config', 'namespace': 'polymer'},
           'data': {key: read_text(ROOT / value) for key, value in paths.items()}}
    return json.dumps(doc, ensure_ascii=False, indent=2) + '\n'


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='No escribir; fallar si entregables están desactualizados')
    args = parser.parse_args()
    outputs = {'docker-compose.yml': compose(), 'k8s/10-monitoring-config.yaml': monitoring_configmap()}
    for name, expected in outputs.items():
        target = ROOT / name
        if args.check:
            if not target.exists() or canonical(read_text(target)) != canonical(expected):
                raise SystemExit(f'Entregable desactualizado: {name}; ejecutar python tools/generate_delivery.py')
        else:
            target.write_text(expected, encoding='utf-8', newline='\n')
        print(f'OK {name}')


if __name__ == '__main__':
    main()

