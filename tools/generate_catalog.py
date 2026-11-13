"""Genera la migración V7 (catálogo tecnológico de HexaOrders v6) desde tools/catalog_v6_data.py.

La migración es idempotente: categorías, productos y promociones se insertan con
ON CONFLICT DO NOTHING (por slug o SKU), así que nunca duplica ni pisa datos editados después por
el administrador. Los 18 productos anteriores a la v6 se reasignan a las subcategorías nuevas y
pasan a soles; ninguno se borra.

Uso:
    py tools\\generate_catalog.py            (Windows)   python3 tools/generate_catalog.py
    py tools\\generate_catalog.py --check    falla si la migración no coincide con los datos

Los filtros técnicos (RAM, almacenamiento, tipo de procesador, marca de GPU, resolución,
frecuencia, conectividad, batería...) se derivan de las etiquetas de la ficha técnica, así que
siempre corresponden a atributos reales del producto.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import catalog_v6_data as data  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'services/order-api/src/main/resources/db/migration/V7__tech_catalog.sql'
MIN_PRODUCTS = 300
# Productos de demostración: fechas de alta fijas (no dependen del día en que se migra).
BASE_DATE = datetime(2026, 4, 1, 9, 0, tzinfo=timezone.utc)
NEW_FROM = datetime(2026, 10, 1, 9, 0, tzinfo=timezone.utc)

# No se permiten categorías ajenas a la tecnología (moda, alimentos, cosmética, mascotas, deportes...).
FORBIDDEN_WORDS = ['moda', 'ropa', 'alimento', 'comida', 'cosmét', 'maquillaje', 'mascota', 'juguete', 'botella', 'mueble']

MAIN_CODES = {
    'computadoras': 'CMP', 'componentes': 'CPN', 'monitores': 'MON', 'smartphones-tablets': 'MOV', 'gaming': 'GAM',
    'perifericos': 'PER', 'audio-entretenimiento': 'AUD', 'redes': 'RED', 'almacenamiento': 'ALM', 'smart-home': 'SMH',
    'seguridad': 'SEG', 'televisores': 'TV', 'fotografia': 'FOT', 'empresarial': 'EMP', 'robotica': 'ROB',
    'impresion-3d': 'I3D', 'wearables': 'WEA', 'realidad-virtual': 'VR', 'energia': 'ENE', 'productividad': 'PRO',
}
WARRANTY = {'computadoras': 12, 'componentes': 36, 'monitores': 36, 'smartphones-tablets': 12, 'empresarial': 36, 'televisores': 12}
# El modelo ya dice qué es el producto: el nombre no repite el sustantivo de la subcategoría.
SELF_DESCRIBING = ('MacBook', 'iMac', 'Mac mini', 'iPhone', 'iPad', 'AirPods', 'PlayStation', 'Xbox', 'Switch', 'Steam Deck',
                   'Watch', 'TV ', 'Echo', 'Nest', 'Chromecast', 'Fire TV', 'Quest', 'Galaxy Ring', 'Galaxy Watch', 'Galaxy Buds',
                   'Galaxy Tab', 'Pixel Watch', 'Surface', 'Shield', 'Stream Deck', 'Raspberry', 'Uno ', 'Nano ', 'Mega ',
                   'Pico', 'DualSense', 'Pro Controller', 'Kit ', 'Cable', 'Adaptador', 'Carcasa', 'Lector', 'Correa', 'Protector',
                   'Soporte', 'Regleta', 'Reposa', 'Organizador', 'Estación', 'Bobina', 'Caja', 'Crimpadora', 'Módulo', 'Sensor',
                   'Motor', 'Cama', 'Hotend', 'Resina', 'Filamento', 'Fuente', 'PDU', 'Rack', 'Funda', 'Selfie', 'Mochila')


def h(text: str) -> int:
    return int(hashlib.sha256(text.encode('utf-8')).hexdigest()[:12], 16)


def sql(value) -> str:
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def parse_specs(text: str) -> list[dict]:
    specs = []
    for part in text.split(';'):
        part = part.strip()
        if not part:
            continue
        label, _, value = part.partition('=')
        if not value.strip():
            raise ValueError(f'Especificación sin valor: {part!r}')
        specs.append({'label': label.strip(), 'value': value.strip()})
    return specs


def spec(specs: list[dict], *labels: str) -> str | None:
    for item in specs:
        if item['label'] in labels:
            return item['value']
    return None


RESOLUTIONS = [((7680, 4320), '8K'), ((5120, 1440), 'DQHD 32:9'), ((4480, 2520), '4.5K'), ((3840, 2160), '4K UHD'),
               ((3440, 1440), 'UWQHD 21:9'), ((2560, 1440), 'QHD'), ((2560, 1600), 'WQXGA'), ((1920, 1200), 'WUXGA'),
               ((1920, 1080), 'Full HD'), ((1366, 768), 'HD')]
CONNECTIVITY = ['Wi-Fi 7', 'Wi-Fi 6E', 'Wi-Fi 6', '5G', 'Thunderbolt', 'Wi-Fi 5', 'Thread', 'Zigbee', 'PoE', 'Fibra óptica',
                'Bluetooth', 'Wi-Fi', 'USB-C', 'Ethernet', 'USB']
CPU_BRANDS = ['Intel', 'AMD', 'Apple', 'Qualcomm', 'MediaTek', 'Samsung', 'Google', 'Broadcom']
GPU_BRANDS = ['NVIDIA', 'AMD', 'Intel', 'Apple', 'Qualcomm']


def attributes(specs: list[dict]) -> dict:
    """Filtros normalizados derivados de la ficha técnica."""
    result = {}
    cpu = spec(specs, 'Procesador')
    if cpu:
        brand = next((b for b in CPU_BRANDS if b.lower() in cpu.lower()), None)
        if brand:
            result['procesador'] = brand
    ram = spec(specs, 'RAM')
    if ram:
        match = re.match(r'(\d+)\s*GB', ram)
        if match:
            result['ram'] = f'{match.group(1)} GB'
    storage = spec(specs, 'Almacenamiento')
    if storage:
        match = re.match(r'(\d+(?:\.\d+)?)\s*(GB|TB)', storage)
        if match:
            result['almacenamiento'] = f'{match.group(1)} {match.group(2)}'
    capacity = spec(specs, 'Capacidad')
    if capacity:
        match = re.match(r'(\d+(?:\.\d+)?)\s*(GB|TB|mAh|Wh|VA|L|kg)', capacity)
        if match:
            result['capacidad'] = f'{match.group(1)} {match.group(2)}'
    gpu = spec(specs, 'GPU', 'Gráficos')
    if gpu:
        brand = next((b for b in GPU_BRANDS if b.lower() in gpu.lower()), None)
        if brand:
            result['gpu'] = brand
    screen = spec(specs, 'Pantalla')
    if screen:
        match = re.match(r'(\d+(?:\.\d+)?)"', screen)
        if match:
            result['pantalla'] = f'{match.group(1)}"'
    resolution = spec(specs, 'Resolución')
    if resolution:
        match = re.search(r'(\d{3,4})\s*x\s*(\d{3,4})', resolution)
        if match:
            size = (int(match.group(1)), int(match.group(2)))
            result['resolucion'] = next((name for dims, name in RESOLUTIONS if dims == size), f'{size[0]}x{size[1]}')
        elif '4K' in resolution:
            result['resolucion'] = '4K UHD'
        elif '1080p' in resolution:
            result['resolucion'] = 'Full HD'
    refresh = spec(specs, 'Frecuencia')
    if refresh:
        match = re.match(r'(\d+)\s*Hz', refresh)
        if match:
            result['frecuencia'] = f'{match.group(1)} Hz'
    connectivity = spec(specs, 'Conectividad', 'Conexión')
    if connectivity:
        found = next((c for c in CONNECTIVITY if c.lower() in connectivity.lower()), None)
        if found:
            result['conectividad'] = found
    battery = spec(specs, 'Batería')
    if battery:
        mah = re.search(r'(\d{4,5})\s*mAh', battery)
        hours = re.search(r'(\d+)\s*h\b', battery)
        days = re.search(r'(\d+)\s*días', battery)
        if mah:
            value = int(mah.group(1))
            result['bateria'] = 'Más de 6000 mAh' if value > 6000 else '4000 a 6000 mAh' if value >= 4000 else 'Menos de 4000 mAh'
        elif days:
            result['bateria'] = 'Varios días'
        elif hours:
            value = int(hours.group(1))
            result['bateria'] = 'Más de 20 h' if value > 20 else '10 a 20 h' if value >= 10 else 'Menos de 10 h'
    power = spec(specs, 'Potencia')
    if power:
        match = re.match(r'(\d+(?:\.\d+)?)\s*W\b', power)
        if match:
            result['potencia'] = f'{match.group(1)} W'
    form = spec(specs, 'Formato')
    if form and len(form) <= 40:
        result['formato'] = form
    return result


def product_name(noun: str, brand: str, model: str) -> str:
    if model.startswith(SELF_DESCRIBING) or noun.split()[0].lower() in model.lower():
        return f'{brand} {model}'
    return f'{noun} {brand} {model}'


def build():
    categories = []
    products = []
    skus = set()
    names = set()
    sub_info = {}
    for position, (slug, name, hue, subs) in enumerate(data.CATEGORIES, start=1):
        if any(word in name.lower() for word in FORBIDDEN_WORDS):
            raise ValueError(f'Categoría ajena a la tecnología: {name}')
        categories.append((slug, name, None, position))
        for sub_position, (sub_slug, sub_name, noun, phrase) in enumerate(subs, start=1):
            if any(word in sub_name.lower() for word in FORBIDDEN_WORDS):
                raise ValueError(f'Subcategoría ajena a la tecnología: {sub_name}')
            categories.append((sub_slug, sub_name, slug, sub_position))
            sub_info[sub_slug] = (slug, noun, phrase, hue, sub_position)
    slugs = [c[0] for c in categories]
    if len(slugs) != len(set(slugs)):
        raise ValueError('Slugs de categoría repetidos')
    if set(slugs) & set(data.LEGACY_HIDDEN_CATEGORIES):
        raise ValueError('Un slug nuevo coincide con una categoría anterior a la v6')

    for sub_slug, entries in data.PRODUCTS.items():
        main, noun, phrase, hue, sub_position = sub_info[sub_slug]
        for index, entry in enumerate(entries, start=1):
            parts = [p.strip() for p in entry.split('|')]
            if len(parts) != 5:
                raise ValueError(f'Producto mal formado: {entry!r}')
            brand, model, price, compare, spec_text = parts
            specs = parse_specs(spec_text)
            price_value = float(price)
            compare_value = float(compare) if compare else None
            if price_value <= 0 or price_value > 100000:
                raise ValueError(f'Precio fuera de rango: {entry!r}')
            if compare_value is not None and compare_value <= price_value:
                raise ValueError(f'El precio anterior debe ser mayor: {entry!r}')
            name = product_name(noun, brand, model)
            if len(name) > 120:
                raise ValueError(f'Nombre demasiado largo: {name}')
            key = name.lower()
            if key in names:
                raise ValueError(f'Producto duplicado: {name}')
            names.add(key)
            # SKU estable: categoría principal, posición de la subcategoría y del producto (p. ej. CMP-02-05).
            sku = f'{MAIN_CODES[main]}-{sub_position:02d}-{index:02d}'
            if sku in skus:
                raise ValueError(f'SKU repetido: {sku}')
            skus.add(sku)
            seed = h(sku)
            roll = seed % 100
            stock = 0 if roll < 4 else (roll % 5) + 1 if roll < 14 else 6 + roll % 55
            if compare_value is not None and seed % 5 == 0:
                status = 'CLEARANCE'
            elif seed % 9 == 0:
                status = 'NEW'
            elif seed % 7 == 0:
                status = 'FEATURED'
            elif seed % 6 == 0:
                status = 'BESTSELLER'
            else:
                status = 'STANDARD'
            created = (NEW_FROM + timedelta(days=seed % 28, hours=seed % 9)) if status == 'NEW' else \
                (BASE_DATE + timedelta(days=seed % 180, hours=seed % 11))
            short = ' · '.join(item['value'] for item in specs[:4])
            if len(short) > 300:
                short = short[:297] + '...'
            highlights = '; '.join(f"{item['label'].lower()}: {item['value']}" for item in specs[:5])
            warranty = WARRANTY.get(main, 12)
            long = (f'{name} es {phrase}. Sus características principales son {highlights}. '
                    f'Producto nuevo y sellado, con {warranty} meses de garantía de marca, comprobante electrónico y '
                    f'soporte postventa de HexaOrders. Revisa la ficha técnica completa antes de comprar.')
            products.append(dict(sku=sku, name=name, brand=brand, model=model, sub=sub_slug, price=f'{price_value:.2f}',
                                 compare=None if compare_value is None else f'{compare_value:.2f}', stock=stock, status=status,
                                 short=short, long=long, specs=specs, attributes=attributes(specs),
                                 hue=(hue + seed % 31 - 15) % 360, created=created))
    if len(products) < MIN_PRODUCTS:
        raise ValueError(f'Se necesitan al menos {MIN_PRODUCTS} productos y hay {len(products)}')
    return categories, products


def render(categories, products) -> str:
    lines = [
        '-- HexaOrders v6: catálogo tecnológico de demostración (GENERADO por tools/generate_catalog.py).',
        '-- No editar a mano: modifica tools/catalog_v6_data.py y vuelve a generar.',
        '-- Idempotente y no destructiva: ON CONFLICT DO NOTHING; los productos anteriores se reasignan y pasan',
        '-- a soles, pero ninguno se borra. Precios y stock de demostración (no son ofertas reales del mercado).',
        f'-- {len([c for c in categories if c[2] is None])} categorías principales, {len([c for c in categories if c[2]])} subcategorías, {len(products)} productos nuevos.',
        '',
        '-- 1) Categorías principales y subcategorías.',
    ]
    for slug, name, parent, position in categories:
        if parent is None:
            lines.append(f"INSERT INTO categories (slug, name, position) VALUES ({sql(slug)}, {sql(name)}, {position}) ON CONFLICT (slug) DO NOTHING;")
    for slug, name, parent, position in categories:
        if parent is not None:
            lines.append(f"INSERT INTO categories (slug, name, parent_id, position) SELECT {sql(slug)}, {sql(name)}, id, {position} "
                         f"FROM categories WHERE slug = {sql(parent)} ON CONFLICT (slug) DO NOTHING;")
    lines += ['', '-- 2) Productos anteriores a la v6: subcategoría nueva, ficha técnica y precio en soles (×3,70; terminación .90).']
    for sku, (sub, brand, model, spec_text) in data.LEGACY.items():
        specs = parse_specs(spec_text)
        lines.append(
            f"UPDATE products SET category_id = (SELECT id FROM categories WHERE slug = {sql(sub)}), brand = {sql(brand)}, "
            f"model = {sql(model)}, specs = {sql(json.dumps(specs, ensure_ascii=False))}::jsonb, "
            f"attributes = {sql(json.dumps(attributes(specs), ensure_ascii=False))}::jsonb, "
            f"long_description = description || ' Producto con 12 meses de garantía de marca y soporte postventa de HexaOrders.', "
            f"price = ceil(price * 3.70) - 0.10, updated_at = now() WHERE sku = {sql(sku)} AND brand = 'HexaOrders' AND model = '';")
    lines.append(f"UPDATE products SET active = FALSE, updated_at = now() WHERE sku IN ({', '.join(sql(s) for s in data.LEGACY_RETIRED)});")
    lines.append(f"UPDATE categories SET active = FALSE WHERE parent_id IS NULL AND slug IN ({', '.join(sql(s) for s in data.LEGACY_HIDDEN_CATEGORIES)});")
    lines += ['', '-- 3) Catálogo nuevo.']
    for p in products:
        lines.append(
            'INSERT INTO products (sku, name, description, category_id, price, stock, active, hue, brand, model, long_description, '
            'specs, attributes, compare_at_price, commercial_status, created_at, updated_at) '
            f"SELECT {sql(p['sku'])}, {sql(p['name'])}, {sql(p['short'])}, id, {p['price']}, {p['stock']}, TRUE, {p['hue']}, "
            f"{sql(p['brand'])}, {sql(p['model'])}, {sql(p['long'])}, {sql(json.dumps(p['specs'], ensure_ascii=False))}::jsonb, "
            f"{sql(json.dumps(p['attributes'], ensure_ascii=False))}::jsonb, {p['compare'] or 'NULL'}, {sql(p['status'])}, "
            f"{sql(p['created'].isoformat())}, {sql(p['created'].isoformat())} FROM categories WHERE slug = {sql(p['sub'])} "
            'ON CONFLICT (sku) DO NOTHING;')
    lines += ['', '-- 4) Campañas comerciales (vigencia relativa a la fecha de carga; el administrador puede cambiarla).']
    for slug, name, description, percent, start, days, targets in data.PROMOTIONS:
        lines.append(
            f"INSERT INTO promotions (slug, name, description, discount_percent, starts_at, ends_at) VALUES ({sql(slug)}, {sql(name)}, "
            f"{sql(description)}, {percent}, date_trunc('day', now()) + interval '{start} days', "
            f"date_trunc('day', now()) + interval '{start + days} days') ON CONFLICT (slug) DO NOTHING;")
        for target in targets:
            lines.append(
                f"INSERT INTO promotion_targets (promotion_id, category_id) SELECT p.id, c.id FROM promotions p, categories c "
                f"WHERE p.slug = {sql(slug)} AND c.slug = {sql(target)} ON CONFLICT DO NOTHING;")
    return '\n'.join(lines) + '\n'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--check', action='store_true', help='No escribe: falla si la migración difiere de los datos.')
    args = parser.parse_args()
    categories, products = build()
    content = render(categories, products)
    current = TARGET.read_text(encoding='utf-8').replace('\r\n', '\n') if TARGET.exists() else None
    summary = (f'{len([c for c in categories if c[2] is None])} categorías, {len([c for c in categories if c[2]])} subcategorías, '
               f'{len(products)} productos nuevos + {len(data.LEGACY)} reasignados, {len(data.PROMOTIONS)} promociones')
    if current == content:
        print(f'OK: catálogo al día ({summary})')
        return 0
    if args.check:
        print('La migración V7 no coincide con tools/catalog_v6_data.py: ejecuta tools/generate_catalog.py', file=sys.stderr)
        return 1
    TARGET.write_bytes(content.encode('utf-8'))
    print(f'escrito {TARGET.relative_to(ROOT)} ({summary})')
    return 0


if __name__ == '__main__':
    sys.exit(main())
