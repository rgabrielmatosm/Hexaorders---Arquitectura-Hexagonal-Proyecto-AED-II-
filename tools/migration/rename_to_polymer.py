#!/usr/bin/env python3
"""Migración de la identidad técnica HexaOrders → POLYMER (se ejecutó una sola vez; se conserva
como registro reproducible de qué se cambió y qué se dejó a propósito).

Reglas, en orden:
  HexaOrders seguido de mayúscula (HexaOrdersServiceDown) → Polymer…
  HexaOrders / HEXAORDERS                                   → POLYMER
  hexaorders (paquetes, métricas, claves, imágenes, proyecto) → polymer
  HexaUserPrincipal → PolymerUserPrincipal · eventos DOM hexa:* → polymer:* · HEXA_PS → POLYMER_PS

EXCEPCIONES (identificadores heredados ligados a datos persistentes; ver docs/MIGRACION_POLYMER.md):
  * Migraciones Flyway ya publicadas (V1–V8) y su generador/datos (V7): Flyway valida su suma de
    comprobación; editarlas impediría arrancar sobre una base existente.
  * RabbitMQ: usuario «hexaorders» y exchange/colas hexaorders.events(.dlx),
    hexaorders.notifications(.dlq). Viven dentro del volumen de RabbitMQ: renombrarlos dejaría a
    order-api sin credenciales válidas y los mensajes pendientes en colas huérfanas.
  * El nombre del repositorio de GitHub («Hexaorders---Arquitectura-…») no se toca desde aquí.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]

EXCLUDED = [
    re.compile(r'^services/order-api/src/main/resources/db/migration/'),
    re.compile(r'^tools/catalog_v6_data\.py$'),
    re.compile(r'^tools/generate_catalog\.py$'),
    re.compile(r'^tools/migration/'),
    re.compile(r'^docker-compose\.yml$'),            # se regenera con tools/generate_delivery.py
    re.compile(r'^k8s/10-monitoring-config\.yaml$'),  # se regenera con tools/generate_delivery.py
    re.compile(r'^docs/historial/'),
]
PROTECTED = [
    re.compile(r'hexaorders\.(?:events\.dlx|notifications\.dlq|notifications|events)(?![\w.-])'),
    re.compile(r'RABBITMQ_USER(?::-?| *: *)hexaorders'),
    re.compile(r"rabbit = \('hexaorders'"),
]
RULES = [
    (re.compile(r'HexaUserPrincipal'), 'PolymerUserPrincipal'),
    (re.compile(r'HexaOrders(?=[A-Z])'), 'Polymer'),
    (re.compile(r'HexaOrders'), 'POLYMER'),
    (re.compile(r'HEXAORDERS'), 'POLYMER'),
    (re.compile(r'hexaorders'), 'polymer'),
    (re.compile(r'''(?<=['"])hexa:'''), 'polymer:'),
    (re.compile(r'HEXA_PS'), 'POLYMER_PS'),
]
MOVES = [
    ('services/order-api/src/main/java/com/hexaorders', 'services/order-api/src/main/java/com/polymer'),
    ('services/order-api/src/test/java/com/hexaorders', 'services/order-api/src/test/java/com/polymer'),
    ('services/order-api/src/main/java/com/polymer/adapter/in/web/security/HexaUserPrincipal.java',
     'services/order-api/src/main/java/com/polymer/adapter/in/web/security/PolymerUserPrincipal.java'),
    ('infra/rabbitmq/hexaorders-entrypoint.sh', 'infra/rabbitmq/polymer-entrypoint.sh'),
    ('docs/demo/HexaOrders.postman_collection.json', 'docs/demo/POLYMER.postman_collection.json'),
    ('tools/hexaorders.ps1', 'tools/polymer.ps1'),
    ('hexaorders.cmd', 'polymer.cmd'),
] + [(f'infra/grafana/dashboards/hexaorders{s}.json', f'infra/grafana/dashboards/polymer{s}.json')
     for s in ['', '-api', '-business', '-infrastructure', '-integrations', '-logistics', '-risk', '-security']]


def git(*args):
    return subprocess.run(['git', *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def rewrite(text):
    saved = []

    def hold(match):
        saved.append(match.group(0))
        return f'\x00{len(saved) - 1}\x00'
    for pattern in PROTECTED:
        text = pattern.sub(hold, text)
    for pattern, replacement in RULES:
        text = pattern.sub(replacement, text)
    return re.sub('\x00(\\d+)\x00', lambda m: saved[int(m.group(1))], text)


def main():
    for source, target in MOVES:
        if (ROOT / source).exists():
            git('mv', source, target)
    changed = 0
    for name in git('ls-files').splitlines():
        if any(p.search(name) for p in EXCLUDED):
            continue
        path = ROOT / name
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeDecodeError, IsADirectoryError, FileNotFoundError):
            continue
        new = rewrite(text)
        if new != text:
            path.write_text(new, encoding='utf-8', newline='')
            changed += 1
    print(f'{changed} archivos reescritos')


if __name__ == '__main__':
    sys.exit(main())
