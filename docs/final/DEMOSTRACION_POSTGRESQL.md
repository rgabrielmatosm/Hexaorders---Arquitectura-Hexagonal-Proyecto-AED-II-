# Demostración de PostgreSQL: persistencia real

## 1. Esquema (verificado en las migraciones)

Migraciones Flyway en `services/order-api/src/main/resources/db/migration/` (V1 a V10,
`validate-on-migrate: true`, `clean-disabled: true`: Flyway nunca puede borrar la base).

| Tabla | Clave primaria | Relaciones | Migración |
| --- | --- | --- | --- |
| `orders` | `id UUID` | `user_id → app_users(id)` | V1 (+ V2: `channel`, `fulfillment_status`, `version`, `idempotency_key UNIQUE`; V6: `order_number UNIQUE`, moneda PEN) |
| `order_lines` | `(order_id, line_no)` | `order_id → orders`, `product_id → products` | V2 |
| `order_status_history` | `id BIGSERIAL` | `order_id → orders` | V2 (también es el outbox de eventos) |
| `order_shipping` | `order_id` | `→ orders` | V4 |
| `order_checkout` | `order_id` | `→ orders` | V6 |
| `app_users`, `app_user_roles` | `id` / `(user_id, role)` | | V2 |
| `user_preferences` | `user_id` | `→ app_users ON DELETE CASCADE` | V10 |
| `products`, `categories`, `promotions`, `promotion_targets`, `cart_items` | | | V2, V6, V7 |
| `deliveries`, `delivery_changes`, `incidents`, `audit_events`, `contact_messages` | | | V2, V4 |
| `outbox_published`, `processed_events`, `event_relay_watermark` | | | V5 |

Restricciones de negocio en la propia base (defensa en profundidad), por ejemplo:

- `orders_status_matches_risk`: `(risk_score >= 70 AND status = 'REVIEW') OR (risk_score < 70 AND status = 'APPROVED')`.
- `order_lines_total_matches`: `line_total = unit_price * quantity`.
- `order_checkout_total_matches`: `total = subtotal + shipping_cost`.

Adaptador que escribe el pedido: `services/order-api/src/main/java/com/polymer/adapter/out/persistence/JdbcCommerceOrderRepository.java`
(una transacción: pedido, líneas, historial, envío, checkout y descuento condicional de stock).

## 2. Demostración automatizada

```powershell
.\polymer.cmd demo -Scenario postgres
```

1. Crea un pedido con un cliente **ficticio** nuevo.
2. Muestra con consultas de **solo lectura** el pedido, sus líneas, su historial y su dirección.
3. Calcula una huella (`número | importe | estado | n.º de líneas | n.º de cambios de estado`).
4. `docker compose restart db order-api` y espera a que estén sanos (**los volúmenes no se tocan**).
5. Vuelve a calcular la huella: debe ser **idéntica**.
6. Vuelve a iniciar sesión con el cliente y la tienda muestra el pedido.

## 3. Consultas para la exposición (solo lectura)

```powershell
docker compose exec db psql -U orders -d orders
```

Dentro de `psql`:

```sql
-- Últimos pedidos de la tienda
SELECT order_number, amount, currency, risk_score, status AS decision, fulfillment_status, created_at
FROM orders WHERE channel = 'SHOP' ORDER BY created_at DESC LIMIT 5;

-- Líneas de un pedido
SELECT line_no, product_name, unit_price, quantity, line_total
FROM order_lines WHERE order_id = (SELECT id FROM orders WHERE order_number = 100123);

-- Historial (ciclo de vida) de un pedido
SELECT from_status, to_status, actor, note, changed_at
FROM order_status_history WHERE order_id = (SELECT id FROM orders WHERE order_number = 100123) ORDER BY id;

-- Totales de checkout y dirección
SELECT c.delivery_method, c.payment_method, c.subtotal, c.shipping_cost, c.total, s.city
FROM order_checkout c LEFT JOIN order_shipping s USING (order_id)
WHERE c.order_id = (SELECT id FROM orders WHERE order_number = 100123);

-- Migraciones aplicadas
SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;
```

(Sustituye `100123` por el número que muestra la tienda en **Mi cuenta → Pedidos**.)

`\q` para salir. **No ejecutes** `UPDATE`, `DELETE`, `DROP` ni `TRUNCATE` durante la exposición.

## 4. Persistencia tras reiniciar (manual)

```powershell
docker compose restart db order-api
```

```powershell
.\polymer.cmd health
```

Repite la consulta: el pedido sigue igual. Los datos viven en el volumen `polymer_postgres_data`:

```powershell
docker volume inspect polymer_postgres_data
```

Incluso `.\polymer.cmd stop` y `.\polymer.cmd start` conservan los datos. Solo `stop -RemoveData` los
borraría (no lo uses).

## 5. Evidencia automática adicional

En cada ejecución del CI, `polymer.ps1 verify` (paso 8) reinicia PostgreSQL y `order-api` y comprueba que
el pedido creado antes sigue en la base, y el job `migrate-from-v6` comprueba que los recuentos
`pedidos/usuarios/productos/mensajes` son idénticos antes y después de migrar una instalación real de la v6.
