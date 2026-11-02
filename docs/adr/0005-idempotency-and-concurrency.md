# ADR 0005 — Idempotencia y concurrencia

**Contexto.** Un doble clic o un reintento de red no deben crear dos pedidos, y dos operadores no deben pisarse al cambiar el estado de un mismo pedido.

**Decisión.**
- `POST /api/shop/orders` exige `Idempotency-Key`; la columna es única. Repetir la clave con el mismo usuario devuelve el pedido original (`200` + `Idempotent-Replay: true`); reutilizarla de otra forma da `409 IDEMPOTENCY_KEY_REUSED`.
- El riesgo se evalúa **antes** de abrir la transacción; dentro de ella se insertan pedido, líneas, historial y auditoría, y el stock se descuenta con `UPDATE … WHERE stock >= ?` (nunca negativo, `409 OUT_OF_STOCK`).
- Las transiciones usan bloqueo optimista (`version`): si otro cambio ganó, `409 CONCURRENT_UPDATE`.
- La agenda logística rechaza solapes del mismo repartidor (`409 SCHEDULE_CONFLICT`).

**Consecuencias.** Sin bloqueos largos ni reintentos automáticos; la interfaz muestra el conflicto y recarga.
