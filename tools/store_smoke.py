"""Prueba funcional de la tienda tecnológica v6 contra el stack REAL (sin simulaciones).

Comprueba catálogo (300+ productos, categorías y subcategorías), búsqueda y filtros reales, ficha de
producto, productos relacionados, promociones vigentes, carrito persistente, checkout con métodos de
entrega y pago, número de pedido, inventario, cancelación, gestión de promociones y productos por el
administrador y reportes comerciales. Solo usa la biblioteca estándar.

Uso:
    python tools/store_smoke.py --demo-password-file secrets/demo_password.txt
Variables: API_URL (por defecto http://127.0.0.1:8080).
"""
import argparse
import os
import sys
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from urllib.parse import quote, urlencode

sys.path.insert(0, str(Path(__file__).resolve().parent))
from platform_smoke import Session, checks, ok  # noqa: E402

D = lambda value: Decimal(str(value))  # noqa: E731


def search(session, **params):
    query = urlencode([(k, v) for k, values in params.items() for v in (values if isinstance(values, list) else [values])])
    return session.call('GET', '/api/catalog/search?' + query, expect=200)[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--demo-password-file', default='secrets/demo_password.txt')
    args = parser.parse_args()
    demo_password = os.environ.get('DEMO_PASSWORD') or open(args.demo_password_file, encoding='utf-8').read().strip()
    anon = Session()

    print('Catálogo tecnológico')
    _, categories, _ = anon.call('GET', '/api/catalog/categories', expect=200)
    mains = [c for c in categories if c['parentId'] is None]
    subs = [c for c in categories if c['parentId'] is not None]
    ok(len(mains) >= 20 and len(subs) >= 150 and all(c['active'] for c in categories),
       f'{len(mains)} categorías principales y {len(subs)} subcategorías visibles (las anteriores a la v6 quedan ocultas)')
    names = ' '.join(c['name'].lower() for c in categories)
    ok(not any(word in names for word in ['bienestar', 'moda', 'alimento', 'mascota', 'cosmét']), 'Ninguna categoría ajena a la tecnología')
    everything = search(anon, size=60)
    ok(everything['total'] >= 300, f'Catálogo persistido en PostgreSQL: {everything["total"]} productos tecnológicos activos')
    product = everything['items'][0]
    required = ['id', 'sku', 'name', 'brand', 'model', 'categoryName', 'mainCategoryName', 'description', 'longDescription', 'specs',
                'price', 'currency', 'stock', 'availability', 'status', 'createdAt', 'updatedAt']
    ok(all(product.get(key) not in (None, '') for key in required) and product['currency'] == 'PEN',
       'Cada producto trae marca, modelo, categoría, ficha técnica, precio en soles, stock, disponibilidad y fechas')

    print('Búsqueda, filtros, orden y paginación')
    by_brand = search(anon, q='lenovo')
    ok(by_brand['total'] > 3 and all('lenovo' in (p['brand'] + p['name']).lower() for p in by_brand['items']), f'Búsqueda por marca: {by_brand["total"]} resultados de Lenovo')
    by_model = search(anon, q='RTX 4060')
    ok(by_model['total'] >= 3, f'Búsqueda por modelo «RTX 4060»: {by_model["total"]} resultados')
    sku = product['sku']
    ok(search(anon, q=sku)['items'][0]['sku'] == sku, f'Búsqueda por SKU ({sku})')
    laptops = next(c for c in mains if c['slug'] == 'computadoras')
    in_laptops = search(anon, category=laptops['id'])
    ok(in_laptops['total'] > 20 and all(p['mainCategoryId'] == laptops['id'] for p in in_laptops['items']),
       'Filtro por categoría principal incluye sus subcategorías')
    ram_facet = next(f for f in in_laptops['facets']['attributes'] if f['key'] == 'ram')
    value = ram_facet['values'][-1]
    filtered = search(anon, category=laptops['id'], **{'attr.ram': value['value']})
    ok(filtered['total'] == value['count'] and all(p['attributes'].get('ram') == value['value'] for p in filtered['items']),
       f'Filtro técnico RAM = {value["value"]}: {filtered["total"]} resultados, igual que su faceta')
    priced = search(anon, min=500, max=1500, sort='price_asc', size=60)
    prices = [D(p['price']) for p in priced['items']]
    ok(prices == sorted(prices) and all(D(500) <= p <= D(1500) for p in prices), 'Filtro por precio y orden ascendente por precio')
    available = search(anon, available='true', size=60)
    ok(all(p['stock'] > 0 for p in available['items']), 'Filtro por disponibilidad (solo con stock)')
    offers = search(anon, offers='true', size=60)
    ok(offers['total'] > 0 and all(p['previousPrice'] and D(p['previousPrice']) > D(p['price']) and p['discountPercent'] > 0 for p in offers['items']),
       f'Filtro de ofertas: {offers["total"]} productos con precio anterior y descuento calculado')
    page2 = search(anon, size=24, page=2, sort='name')
    ok(page2['page'] == 2 and page2['pages'] >= 13 and len(page2['items']) == 24, 'Paginación del servidor (24 por página)')
    newest = search(anon, sort='newest', size=10)['items']
    ok([p['createdAt'] for p in newest] == sorted([p['createdAt'] for p in newest], reverse=True), 'Orden por novedades')
    status, _, _ = anon.call('GET', '/api/catalog/search?attr.color=rojo')
    ok(status == 400, 'Un filtro técnico inexistente se rechaza (400): no hay filtros decorativos')

    print('Detalle de producto y promociones')
    _, promotions, _ = anon.call('GET', '/api/catalog/promotions', expect=200)
    ok(len(promotions) >= 6 and all(p['state'] == 'RUNNING' and p['products'] > 0 for p in promotions),
       f'{len(promotions)} promociones vigentes con productos asociados')
    gaming = next(c for c in mains if c['slug'] == 'gaming')
    promo_item = next(p for p in search(anon, category=gaming['id'], available='true', size=60)['items'] if p['promotionName'])
    _, detail, _ = anon.call('GET', f'/api/catalog/products/{promo_item["id"]}', expect=200)
    expected = (D(detail['listPrice']) * (100 - next(p['discountPercent'] for p in promotions if p['name'] == detail['promotionName'])) / 100) \
        .quantize(Decimal('0.01'))
    ok(D(detail['price']) == expected and len(detail['specs']) >= 3,
       f'«{detail["name"]}»: {detail["promotionName"]} aplicada en el servidor (S/ {detail["listPrice"]} → S/ {detail["price"]})')
    _, related, _ = anon.call('GET', f'/api/catalog/products/{detail["id"]}/related', expect=200)
    ok(related and all(r['id'] != detail['id'] and r['mainCategoryId'] == detail['mainCategoryId'] for r in related), 'Productos relacionados de la misma categoría')

    print('Carrito persistente del cliente')
    suffix = uuid.uuid4().hex[:8]
    customer = Session()
    customer.refresh_csrf()
    customer.call('POST', '/api/auth/register', {'username': f'tienda{suffix}', 'displayName': 'Cliente Tienda',
                                                 'email': f'tienda{suffix}@example.test', 'password': 'Tienda-2026-segura'}, expect=201)
    customer.login(f'tienda{suffix}', 'Tienda-2026-segura')
    in_stock = next(p for p in search(anon, available='true', sort='price_asc', size=60)['items'] if 3 <= p['stock'] <= 60)
    _, cart, _ = customer.call('POST', '/api/shop/cart/items', {'productId': in_stock['id'], 'quantity': 2}, expect=200)
    ok(cart['units'] == 2 and D(cart['subtotal']) == D(in_stock['price']) * 2, 'Agregar al carrito: subtotal calculado por el servidor')
    status, problem, _ = customer.call('PUT', f'/api/shop/cart/items/{in_stock["id"]}', {'quantity': in_stock['stock'] + 1})
    ok(status == 409, f'No se puede pedir más que el stock disponible (409: {problem.get("detail")})')
    other = next(p for p in search(anon, available='true', sort='price_desc', size=60)['items'] if p['stock'] >= 2 and p['id'] != in_stock['id'])
    _, cart, _ = customer.call('POST', '/api/shop/cart/merge', {'items': [{'productId': other['id'], 'quantity': 1}]}, expect=200)
    ok(len(cart['lines']) == 2, 'El carrito del visitante se fusiona con el carrito guardado al iniciar sesión')
    _, cart, _ = customer.call('DELETE', f'/api/shop/cart/items/{other["id"]}', expect=200)
    relogin = Session().login(f'tienda{suffix}', 'Tienda-2026-segura')
    _, saved, _ = relogin.call('GET', '/api/shop/cart', expect=200)
    ok(len(saved['lines']) == 1 and saved['lines'][0]['quantity'] == 2, 'El carrito persiste en PostgreSQL entre sesiones')

    print('Checkout: entrega, pago, número de pedido e inventario')
    items = [{'productId': in_stock['id'], 'quantity': 2}]
    anon.refresh_csrf()  # el visitante también envía el token CSRF, igual que el navegador
    _, options, _ = anon.call('GET', '/api/shop/checkout-options', expect=200)
    ok({o['code'] for o in options['delivery']} == {'STANDARD', 'EXPRESS', 'PICKUP'} and len(options['payment']) == 3,
       'Métodos de entrega y de pago disponibles (sin pagos reales)')
    _, summary, _ = anon.call('POST', '/api/shop/checkout-summary', {'items': items, 'deliveryMethod': 'EXPRESS'}, expect=200)
    ok(D(summary['shippingCost']) == D('29.90') and D(summary['total']) == D(summary['subtotal']) + D('29.90'), 'Resumen: envío express sumado al total')
    status, _, _ = anon.call('POST', '/api/shop/checkout-summary', {'items': items, 'deliveryMethod': 'PICKUP', 'paymentMethod': 'CASH_ON_DELIVERY'})
    ok(status == 400, 'Pago contra entrega con recojo en tienda se rechaza (400)')
    shipping = {'recipient': 'Cliente Tienda', 'address': 'Av. Arequipa 1234, Miraflores', 'city': 'Lima', 'phone': '999888777'}
    key = f'store-{suffix}'
    _, order, _ = customer.call('POST', '/api/shop/orders', {'items': items, 'shipping': shipping, 'deliveryMethod': 'EXPRESS',
                                                             'paymentMethod': 'BANK_TRANSFER'}, headers={'Idempotency-Key': key}, expect=201)
    ok(order['currency'] == 'PEN' and order['orderNumber'] and D(order['amount']) == D(summary['total']),
       f'Pedido HX-{order["orderNumber"]} confirmado en soles por S/ {order["amount"]} (riesgo {order["riskScore"]}, {order["status"]})')
    status, again, _ = customer.call('POST', '/api/shop/orders', {'items': items, 'shipping': shipping, 'deliveryMethod': 'EXPRESS',
                                                                  'paymentMethod': 'BANK_TRANSFER'}, headers={'Idempotency-Key': key})
    ok(status == 200 and again['id'] == order['id'], 'Repetir la confirmación con la misma clave devuelve el mismo pedido')
    _, details, _ = customer.call('GET', f'/api/shop/orders/{order["id"]}', expect=200)
    ok(details['checkout']['delivery'] == 'EXPRESS' and details['checkout']['payment'] == 'BANK_TRANSFER' and details['shipping']['city'] == 'Lima',
       'El detalle del pedido guarda entrega, pago, dirección y totales')
    _, after, _ = anon.call('GET', f'/api/catalog/products/{in_stock["id"]}', expect=200)
    ok(after['stock'] == in_stock['stock'] - 2, f'Inventario actualizado en PostgreSQL ({in_stock["stock"]} → {after["stock"]})')
    _, emptied, _ = customer.call('GET', '/api/shop/cart', expect=200)
    ok(not emptied['lines'], 'El carrito guardado se vacía al confirmar el pedido')
    _, mine, _ = customer.call('GET', '/api/shop/orders', expect=200)
    ok(any(o['id'] == order['id'] for o in mine), 'El pedido aparece en el historial del cliente')
    _, cancelled, _ = customer.call('POST', f'/api/shop/orders/{order["id"]}/cancel', expect=200)
    _, restored, _ = anon.call('GET', f'/api/catalog/products/{in_stock["id"]}', expect=200)
    ok(cancelled['status'] == 'CANCELLED' and restored['stock'] == in_stock['stock'], 'Cancelar devuelve las unidades al inventario')

    print('Administración comercial')
    admin = Session().login('admin', demo_password)
    _, all_categories, _ = admin.call('GET', '/api/admin/categories', expect=200)
    ok(len(all_categories) == len(categories) + sum(1 for c in all_categories if not c['active']), 'El administrador ve también las categorías ocultas')
    target = next(p for p in search(anon, available='true', size=60)['items'] if not p['promotionName'] and not p['previousPrice'])
    now = datetime.now(timezone.utc)
    promo = {'slug': f'prueba-{suffix}', 'name': f'Prueba {suffix}', 'description': 'Campaña de prueba automática',
             'discountPercent': 25, 'startsAt': (now - timedelta(hours=1)).isoformat(), 'endsAt': (now + timedelta(days=2)).isoformat(),
             'active': True, 'categoryIds': [], 'productIds': [target['id']]}
    _, created, _ = admin.call('POST', '/api/admin/promotions', promo, expect=201)
    _, discounted, _ = anon.call('GET', f'/api/catalog/products/{target["id"]}', expect=200)
    ok(created['state'] == 'RUNNING' and discounted['discountPercent'] == 25 and D(discounted['previousPrice']) == D(target['price']),
       f'Promoción creada por el administrador: el precio baja al momento (S/ {target["price"]} → S/ {discounted["price"]})')
    admin.call('PUT', f'/api/admin/promotions/{created["id"]}/active', {'enabled': False}, expect=200)
    _, back, _ = anon.call('GET', f'/api/catalog/products/{target["id"]}', expect=200)
    ok(D(back['price']) == D(target['price']) and not back['previousPrice'], 'Al desactivarla, el precio vuelve al regular')
    status, _, _ = customer.call('POST', '/api/admin/promotions', promo)
    ok(status == 403, 'Un cliente no puede gestionar promociones (403)')
    _, full, _ = admin.call('GET', '/api/admin/products', expect=200)
    editable = next(p for p in full if p['id'] == target['id'])
    _, edited, _ = admin.call('PUT', f'/api/admin/products/{target["id"]}', {
        'sku': editable['sku'], 'name': editable['name'], 'description': editable['description'], 'categoryId': editable['categoryId'],
        'price': editable['price'], 'active': True, 'hue': editable['hue']}, expect=200)
    ok(edited['specs'] == editable['specs'] and edited['brand'] == editable['brand'], 'Editar los campos básicos conserva la ficha técnica')
    _, dashboard, _ = admin.call('GET', '/api/admin/dashboard', expect=200)
    ok('topProducts' in dashboard and all('currency' in row for row in dashboard['salesByCategory']),
       'Reportes comerciales: ventas por categoría principal y moneda, y productos más vendidos')

    print(f'\nOK tienda: {len(checks)} comprobaciones')


if __name__ == '__main__':
    main()
