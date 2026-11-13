# ADR 0015 — Precios, promociones y moneda en el dominio

- **Estado:** aceptada (v6)
- **Contexto:** la tienda necesita precios en soles, un precio anterior opcional, descuentos calculados y campañas con vigencia que el administrador gestiona. El checkout debe cobrar exactamente lo que se mostró. La API original `/api/orders` (v1) está documentada en USD y su contrato no puede cambiar.

## Decisión

1. **Las reglas de precio son de dominio.** `PriceTag.of(producto, promociones, ahora)` es una función pura:
   - aplica la promoción vigente con mayor porcentaje que alcance al producto, por producto, subcategoría o categoría principal, y **nunca acumula** varias;
   - redondea a céntimos (`HALF_UP`);
   - muestra como precio tachado el mayor entre el precio anterior manual y el precio regular;
   - calcula el porcentaje de descuento a partir de los dos precios, sin escribirlo a mano.
2. **Un único servicio aplica las promociones.** `PricingService` (aplicación) lee una vez las promociones vigentes por operación y lo usan el catálogo, el carrito y el checkout, así que el precio mostrado y el cobrado coinciden. Las líneas del pedido guardan el precio cobrado, el precio tachado y la promoción.
3. **El checkout suma el envío** según `DeliveryMethod`: el estándar es gratis desde S/ 299. `PaymentMethod` restringe las combinaciones: no hay pago contra entrega con recojo ni pago en tienda con envío. No hay pagos en línea ni se guardan datos bancarios.
4. **Dos monedas:**
   - `OrderDraft` acepta `USD` o `PEN`;
   - `OrderService` (API original) sigue exigiendo `USD`;
   - `CommerceService` (tienda) usa `PEN`;
   - los pedidos anteriores conservan su moneda;
   - los reportes agrupan por moneda en lugar de sumar importes de monedas distintas.
5. **El motor de riesgo** recibe el total que se cobra. Sus umbrales (500 y 1000) se aplican tal cual al importe en soles, lo que envía a revisión del operador los pedidos de mayor valor.

## Consecuencias

- Las campañas se activan y vencen solas por fecha; el administrador puede desactivarlas al momento.
- Un precio anterior nunca puede ser menor o igual al actual: lo validan el dominio y una restricción `CHECK`.
- Como el motor sigue calibrado con los mismos números, en soles más pedidos pasan por revisión manual. Esto se documenta como comportamiento esperado, no como fallo.
