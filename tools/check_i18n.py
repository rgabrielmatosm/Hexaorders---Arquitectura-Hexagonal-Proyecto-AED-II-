#!/usr/bin/env python3
"""Comprueba la internacionalización de POLYMER.

Reúne todos los textos traducibles («msgid» = texto en español):
  * llamadas t('…') de los módulos de la interfaz (static/js);
  * título y descripción de cada página (static/i18n/routes.json, generado por generate_pages.py);
  * datos del catálogo de demostración: categorías, frases comerciales de subcategoría, etiquetas de
    especificaciones, filtros técnicos, promociones, estados comerciales y disponibilidad;
  * datos de producto (tools/generate_catalog.py): valores de ficha técnica y de filtros con palabras
    en español, como plantilla con las cifras como marcadores («Hasta {0} h»), el sustantivo genérico
    del nombre («Adaptador de red») y las descripciones de los productos anteriores a la v6;
  * mensajes del servidor que llegan a la interfaz (títulos de error y validaciones).
y verifica que cada catálogo static/i18n/<idioma>.json los traduzca todos, que los marcadores {0}…
coincidan y que no haya claves sobrantes. Uso:
    python3 tools/check_i18n.py            # falla si falta algo
    python3 tools/check_i18n.py --missing en  # lista lo que falta en un idioma (ayuda al traducir)
"""
import importlib.util
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
STATIC = ROOT / 'services/order-api/src/main/resources/static'
JAVA = ROOT / 'services/order-api/src/main/java/com/polymer'
sys.path.insert(0, str(ROOT / 'tools/i18n'))
from js_strings import tokens  # noqa: E402

LANGUAGES = ['en', 'fr', 'it', 'pt', 'de']
# Módulos internos que no se traducen (laboratorio y documentación técnica: personal técnico, en español).
UNTRANSLATED = {'app.js', 'architecture.js', 'dashboard.js', 'observability.js', 'orders.js', 'grid-effect.js', 'intro-gate.js',
                'i18n.js', 'architecture-pages.js'}


ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '0': '\0'}


def unescape(body):
    """Decodifica las secuencias de escape de un literal JS ('\\n', '\\u00e1', '\\'' …) como lo hace el navegador."""
    def repl(match):
        esc = match.group(1)
        if esc[0] == 'u':
            return chr(int(esc[1:].strip('{}'), 16))
        if esc[0] == 'x':
            return chr(int(esc[1:], 16))
        return ESCAPES.get(esc, esc)
    return re.sub(r'\\(u\{[0-9a-fA-F]+\}|u[0-9a-fA-F]{4}|x[0-9a-fA-F]{2}|.)', repl, body, flags=re.S)


def js_messages():
    found = {}
    for path in sorted((STATIC / 'js').rglob('*.js')):
        if path.name in UNTRANSLATED:
            continue
        src = path.read_text(encoding='utf-8')
        for start, end, quote in tokens(src):
            if quote == '`' or not re.search(r'\b(t|td)\(\s*$', src[:start]):
                continue
            value = unescape(src[start + 1:end - 1])
            found.setdefault(value, f'{path.relative_to(STATIC)}')
        # t('…') dentro de ${…} de una plantilla: el tokenizador ve la plantilla entera como un solo literal.
        for start, end, quote in tokens(src):
            if quote != '`':
                continue
            for match in re.finditer(r"\b(?:t|td)\(\s*('(?:[^'\\\n]|\\.)*'|\"(?:[^\"\\\n]|\\.)*\")", src[start:end]):
                found.setdefault(unescape(match.group(1)[1:-1]), f'{path.relative_to(STATIC)}')
    return found


def page_messages():
    routes = json.loads((STATIC / 'i18n/routes.json').read_text(encoding='utf-8'))
    found = {}
    for file, meta in routes['meta'].items():
        found.setdefault(meta['title'], f'routes.json {file}')
        found.setdefault(meta['description'], f'routes.json {file}')
    for text in routes.get('shell', []):
        found.setdefault(text, 'plantilla HTML (routes.json shell)')
    return found


def catalog_messages():
    spec = importlib.util.spec_from_file_location('catalog', ROOT / 'tools/catalog_v6_data.py')
    data = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(data)
    found = {}
    for slug, name, _hue, subs in data.CATEGORIES:
        found.setdefault(name, 'categoría')
        for _sub_slug, sub_name, _noun, phrase in subs:
            found.setdefault(sub_name, 'subcategoría')
            found.setdefault(phrase, 'frase de subcategoría')
    for entries in data.PRODUCTS.values():
        for line in entries:
            for pair in line.split('|')[-1].split(';'):
                if '=' in pair:
                    found.setdefault(pair.split('=', 1)[0].strip(), 'etiqueta de especificación')
    for legacy in data.LEGACY.values():
        for pair in legacy[3].split(';'):
            if '=' in pair:
                found.setdefault(pair.split('=', 1)[0].strip(), 'etiqueta de especificación')
    for promo in data.PROMOTIONS:
        found.setdefault(promo[1], 'promoción')
        found.setdefault(promo[2], 'promoción')
    java = (JAVA / 'domain/catalog/CatalogSearch.java').read_text(encoding='utf-8')
    for label in re.findall(r'labels\.put\("[^"]+", "([^"]+)"\)', java):
        found.setdefault(label, 'filtro técnico')
    for enum in ('CommercialStatus', 'Availability'):
        for label in re.findall(r'[A-Z_]+\("([^"]+)"\)', (JAVA / f'domain/catalog/{enum}.java').read_text(encoding='utf-8')):
            found.setdefault(label, enum)
    # Entrega y pago del checkout: etiqueta y detalle de cada constante (STANDARD("Envío estándar", "2 a 4 días hábiles", …)).
    for enum in ('DeliveryMethod', 'PaymentMethod'):
        source = (JAVA / f'domain/fulfillment/{enum}.java').read_text(encoding='utf-8')
        for label, detail in re.findall(r'^\s+[A-Z_]+\("([^"]+)", "([^"]+)"', source, re.M):
            found.setdefault(label, enum)
            found.setdefault(detail, enum)
    return found


NUMBER = re.compile(r'\d+(?:[.,]\d+)?')  # igual que NUMBER en static/js/i18n.js
WORD = re.compile(r"(?<![\w-])[A-ZÁÉÍÓÚÑ]?[a-záéíóúñü]{2,}(?![\w-])")
# Palabras que no se traducen en una ficha técnica: tecnologías, unidades y nombres comerciales. Las
# marcas de los productos se añaden solas (los modelos no: pueden llevar palabras en español). Un valor solo con estas palabras no se traduce.
NEUTRAL_WORDS = set('''
Bluetooth Ethernet Gigabit Core Windows Home Matter Thread Zigbee Gen Android Retina Arm Hub Radeon Thunderbolt Alexa
Teams Zoom Meet Power fps mm cm kg km min Gbps Mbps rpm bits jack Full Dual Digital Webcam Blue Metal Nylon Fast App app
flash audio coaxial Gimbal Switch Super Ultra Plus Pro Mini Max Smart Wireless Clicky clicky Red Brown Green Black White
Linear Tactile Silent Low Profile Speed Turbo Gaming Studio Air Lite Neo Sense Touch Pen Pencil Dock Station Box Cloud
Link Mesh Wi Fi Ready Boost Sync Noise Active Passive Hybrid Quick Charge Delivery Display Port Type Wide Color Night
Vision Motion Cancelling Over Ear Rumble Lightning MagSafe Qi Edge Glass Gorilla Dolby Atmos True Tone Ray Tracing
Optical Mechanical Qualcomm Snapdragon NVIDIA Ada macOS Linux iOS iPadOS SteamVR Darkfield DualSense BioActive Intel
Apple Google Samsung MediaTek Broadcom Exynos Tensor Bionic Iris Xe Arc Adreno Mali Lightsync Omada SmartConnect
'''.split())


def template(value):
    """«Hasta 22 h» → «Hasta {0} h»: lo mismo que td() en static/js/i18n.js."""
    counter = iter(range(1000))
    return NUMBER.sub(lambda _m: '{%d}' % next(counter), value)


def product_messages():
    sys.path.insert(0, str(ROOT / 'tools'))
    import generate_catalog  # noqa: E402  (fuente única del catálogo; también genera V7__tech_catalog.sql)
    _categories, products = generate_catalog.build()
    data = generate_catalog.data
    neutral = set(NEUTRAL_WORDS)
    for product in products:
        neutral |= set(WORD.findall(product['brand']))
    values = set()
    for product in products:
        values |= {item['value'] for item in product['specs']}
        values |= set(product['attributes'].values())
    for legacy in data.LEGACY.values():
        values |= {item['value'] for item in generate_catalog.parse_specs(legacy[3])}
    found = {}
    for value in sorted(values):
        if any(word not in neutral for word in WORD.findall(value)):
            found.setdefault(template(value), 'valor de ficha técnica o filtro')
    for _slug, _name, _hue, subs in data.CATEGORIES:
        for _sub_slug, _sub_name, noun, _phrase in subs:
            if any(p['name'].startswith(noun + ' ') for p in products):
                found.setdefault(noun, 'sustantivo del nombre de producto')
    # Productos anteriores a la v6 que siguen a la venta: su descripción corta viene de V3__seed_catalog.sql.
    seed = (STATIC.parent / 'db/migration/V3__seed_catalog.sql').read_text(encoding='utf-8')
    retired = set(data.LEGACY_RETIRED)
    for sku, description in re.findall(r"\('([A-Z]+-[A-Z0-9-]+)', '(?:[^']|'')*', '((?:[^']|'')*)'", seed):
        if sku in data.LEGACY and sku not in retired:
            found.setdefault(description.replace("''", "'"), 'descripción de producto anterior a la v6')
    return found


def server_messages():
    """Títulos y mensajes que el servidor devuelve a la interfaz (ProblemDetail y validaciones)."""
    found = {}
    handler = (JAVA / 'adapter/in/web/ApiExceptionHandler.java').read_text(encoding='utf-8')
    for text in re.findall(r'problem\(HttpStatus\.\w+, "([^"]+)"(?:, "([^"]+)")?', handler):
        for value in text:
            if value:
                found.setdefault(value, 'ApiExceptionHandler')
    for path in list((JAVA / 'application').rglob('*.java')) + list((JAVA / 'domain').rglob('*.java')):
        for text in re.findall(r'Exception\(\s*(?:"[A-Z_]+",\s*)?"([^"]+)"\s*\)', path.read_text(encoding='utf-8')):
            if re.search(r'[a-záéíóúñ]{3,}', text) and '+' not in text:
                found.setdefault(text, path.name)
    return found


def all_messages():
    messages = {}
    for source in (js_messages(), page_messages(), catalog_messages(), product_messages(), server_messages()):
        for key, origin in source.items():
            messages.setdefault(key, origin)
    return messages


def placeholders(text):
    return sorted(re.findall(r'\{\d+\}', text))


def main(argv):
    messages = all_messages()
    if '--missing' in argv:
        lang = argv[argv.index('--missing') + 1]
        path = STATIC / f'i18n/{lang}.json'
        catalog = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {}
        for key in messages:
            if key not in catalog:
                print(key)
        return 0
    errors = []
    for lang in LANGUAGES:
        path = STATIC / f'i18n/{lang}.json'
        if not path.exists():
            errors.append(f'{lang}: falta {path.relative_to(ROOT)}')
            continue
        catalog = json.loads(path.read_text(encoding='utf-8'))
        missing = [k for k in messages if k not in catalog or not str(catalog[k]).strip()]
        extra = [k for k in catalog if k not in messages]
        wrong = [k for k in messages if k in catalog and placeholders(k) != placeholders(catalog[k])]
        if missing:
            errors.append(f'{lang}: {len(missing)} textos sin traducir, p. ej. {missing[:3]}')
        if extra:
            errors.append(f'{lang}: {len(extra)} claves que ya no se usan, p. ej. {extra[:3]}')
        if wrong:
            errors.append(f'{lang}: marcadores distintos en {wrong[:3]}')
    if errors:
        print('i18n con problemas:\n  ' + '\n  '.join(errors), file=sys.stderr)
        return 1
    print(f'OK i18n: {len(messages)} textos traducidos en {len(LANGUAGES)} idiomas (+ español), marcadores coherentes')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
