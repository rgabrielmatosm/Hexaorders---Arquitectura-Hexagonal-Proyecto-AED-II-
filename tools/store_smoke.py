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
    parser.add_argument('--quick', action='store_true', help='Solo comprueba que catálogo, categorías y promociones persisten (tras reiniciar).')
    args = parser.parse_args()
    anon = Session()
    if args.quick:
        print('Persistencia de la tienda')
        _, categories, _ = anon.call('GET', '/api/catalog/categories', expect=200)
        total = search(anon, size=1)['total']
        _, promotions, _ = anon.call('GET', '/api/catalog/promotions', expect=200)
        ok(total >= 300 and len(categories) >= 170 and promotions,
           f'Tras reiniciar siguen {total} productos, {len(categories)} categorías y {len(promotions)} promociones en PostgreSQL')
        print(f'\nOK tienda (persistencia): {len(checks)} comprobaciones')
        return
    demo_password = os.environ.get('DEMO_PASSWORD') or open(args.demo_password_file, encoding='utf-8').read().strip()

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
                                                 'email': f'tienda{suffix}@example.test', 'password': 'cobre lluvia faro norte 2026'}, expect=201)
    customer.login(f'tienda{suffix}', 'cobre lluvia faro norte 2026', email=f'tienda{suffix}@example.test')
    in_stock = next(p for p in search(anon, available='true', sort='price_asc', size=60)['items'] if 3 <= p['stock'] <= 60)
    _, cart, _ = customer.call('POST', '/api/shop/cart/items', {'productId': in_stock['id'], 'quantity': 2}, expect=200)
    ok(cart['units'] == 2 and D(cart['subtotal']) == D(in_stock['price']) * 2, 'Agregar al carrito: subtotal calculado por el servidor')
    status, problem, _ = customer.call('PUT', f'/api/shop/cart/items/{in_stock["id"]}', {'quantity': in_stock['stock'] + 1})
    ok(status == 409, f'No se puede pedir más que el stock disponible (409: {problem.get("detail")})')
    other = next(p for p in search(anon, available='true', sort='price_desc', size=60)['items'] if p['stock'] >= 2 and p['id'] != in_stock['id'])
    _, cart, _ = customer.call('POST', '/api/shop/cart/merge', {'items': [{'productId': other['id'], 'quantity': 1}]}, expect=200)
    ok(len(cart['lines']) == 2, 'El carrito del visitante se fusiona con el carrito guardado al iniciar sesión')
    _, cart, _ = customer.call('DELETE', f'/api/shop/cart/items/{other["id"]}', expect=200)
    relogin = Session().login(f'tienda{suffix}', 'cobre lluvia faro norte 2026')
    _, saved, _ = relogin.call('GET', '/api/shop/cart', expect=200)
    ok(len(saved['lines']) == 1 and saved['lines'][0]['quantity'] == 2, 'El carrito persiste en PostgreSQL entre sesiones')

    print('Checkout: entrega, pago, número de pedido e inventario')
    items = [{'productId': in_stock['id'], 'quantity': 2}]
    anon.refresh_csrf()  # el visitante también envía el token CSRF, igual que el navegador
    _, options, _ = anon.call('GET', '/api/shop/checkout-options', expect=200)
    ok({o['code'] for o in options['delivery']} == {'STANDARD', 'EXPRESS', 'PICKUP'} and len(options['payment']) == 6
       and {o['code'] for o in options['payment'] if o['online']} == {'CARD', 'YAPE', 'PLIN'},
       'Métodos de entrega y de pago: tarjeta, Yape y Plin en línea (pasarelas de prueba) y tres pagos diferidos')
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

    print('v7 · Tienda física, pago en línea (tarjeta, Yape, Plin) y boleta')
    _, store, _ = anon.call('GET', '/api/store', expect=200)
    ok(store['mapsEmbedUrl'].startswith('https://www.google.com/maps?q=') and 'output=embed' in store['mapsEmbedUrl']
       and store['mapsDirectionsUrl'].startswith('https://www.google.com/maps/dir/?api=1') and len(store['week']) == 7,
       f'Tienda: mapa de Google Maps, «cómo llegar» y horario semanal ({store["fullAddress"]})')
    _, today, _ = anon.call('GET', '/api/store/today', expect=200)
    ok(today['date'] and today['googleCalendarUrl'].startswith('https://calendar.google.com/calendar/render?action=TEMPLATE'),
       f'Hoy en Lima: {today["date"]} · {"abierto" if today["openNow"] else "cerrado"} · {today["opens"] or "sin atención"}–{today["closes"] or ""}')
    cheap = next(p for p in search(anon, available='true', sort='price_asc', size=30)['items'] if p['stock'] >= 3)
    pay_items = [{'productId': cheap['id'], 'quantity': 1}]

    def online_order(method, tag):
        _, placed, _ = customer.call('POST', '/api/shop/orders', {'items': pay_items, 'deliveryMethod': 'PICKUP', 'paymentMethod': method},
                                     headers={'Idempotency-Key': f'pay-{tag}-{suffix}'}, expect=201)
        return placed

    card_order = online_order('CARD', 'card')
    path = f'/api/shop/orders/{card_order["id"]}'
    _, pending, _ = customer.call('GET', f'{path}/payment', expect=200)
    ok(pending['online'] and pending['status'] is None and pending['paymentMethod'] == 'CARD', 'Pedido con tarjeta: pendiente de pago')
    card = {'holder': 'Cliente Tienda', 'expMonth': 12, 'expYear': 2030, 'cvv': '123'}
    status, declined, _ = customer.call('POST', f'{path}/payment/card', {**card, 'number': '4000 0000 0000 0002'})
    ok(status == 402 and declined['status'] == 'DECLINED' and declined['failureCode'] == 'INSUFFICIENT_FUNDS', 'Tarjeta de prueba rechazada: 402 con el motivo')
    status, _, _ = customer.call('POST', f'{path}/payment/card', {**card, 'number': '4111 1111 1111 1112'})
    ok(status == 400, 'Número de tarjeta con dígito verificador incorrecto (Luhn): 400')
    _, paid, _ = customer.call('POST', f'{path}/payment/card', {**card, 'number': '4111 1111 1111 1111'}, expect=200)
    ok(paid['status'] == 'PAID' and paid['receipt'].startswith('B001-') and paid['cardLast4'] == '1111', f'Tarjeta aprobada al instante: boleta {paid["receipt"]}')
    _, receipt, _ = customer.call('GET', f'{path}/receipt', expect=200)
    ok(D(receipt['taxableBase']) + D(receipt['igv']) == D(receipt['total']) == D(card_order['amount']), 'Boleta: base imponible + IGV 18 % = total')
    status, _, _ = customer.call('POST', f'{path}/payment/card', {**card, 'number': '4111 1111 1111 1111'})
    ok(status == 409, 'No se cobra dos veces el mismo pedido (409)')
    yape_order = online_order('YAPE', 'yape')
    ypath = f'/api/shop/orders/{yape_order["id"]}'
    _, qr, _ = customer.call('POST', f'{ypath}/payment/wallet', {'method': 'YAPE'}, expect=201)
    ok(qr['status'] == 'PENDING' and 55 <= qr['remainingSeconds'] <= 60 and qr['qrPayload'].startswith('POLYMER-SANDBOX|YAPE|'),
       f'Yape: solicitud con QR que vence en 60 s (quedan {qr["remainingSeconds"]} s)')
    status, rejected, _ = customer.call('POST', f'{ypath}/payment/wallet/{qr["paymentId"]}/confirm', {'phone': '987654321', 'approvalCode': '000000'})
    ok(status == 402 and rejected['failureCode'] == 'WRONG_APPROVAL_CODE', 'Yape: código de aprobación incorrecto → 402')
    _, qr2, _ = customer.call('POST', f'{ypath}/payment/wallet', {'method': 'PLIN'}, expect=201)
    _, wallet_paid, _ = customer.call('POST', f'{ypath}/payment/wallet/{qr2["paymentId"]}/confirm', {'phone': '+51 987 654 321', 'approvalCode': '482913'}, expect=200)
    ok(wallet_paid['status'] == 'PAID' and wallet_paid['method'] == 'PLIN' and wallet_paid['reference'].startswith('PLIN-'),
       f'Plin aprobado dentro de los 60 s: boleta {wallet_paid["receipt"]}')
    _, used, _ = customer.call('GET', ypath, expect=200)
    ok(used['checkout']['payment'] == 'PLIN', 'El pedido registra el método realmente usado (de Yape a Plin)')
    status, _, _ = anon.call('GET', f'{path}/payment')
    ok(status == 401, 'El pago de un pedido requiere la sesión del cliente (401)')

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
