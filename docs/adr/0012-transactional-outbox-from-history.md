# ADR 0012 — Outbox transaccional sobre las tablas de historial

**Contexto.** Los cambios de pedidos y entregas deben producir eventos de RabbitMQ para notificar al cliente. Publicar directamente desde el caso de uso acoplaría el pedido al broker: si RabbitMQ cae, se pierden eventos o fallan los pedidos. Además, un fallo entre el `COMMIT` y la publicación perdería el evento.

**Decisión.** Patrón **transactional outbox** con *polling publisher*, usando como outbox las tablas que ya se escriben en la misma transacción que cada cambio de negocio:

- `order_status_history`, una fila por cada transición del ciclo de vida, incluida la creación;
- `delivery_changes`, una fila por cada programación o reprogramación.

Funcionamiento:

- `EventRelayService` publica los eventos pendientes cada 2 s con confirmaciones del broker. Solo tras el *ack* inserta su `eventId` en `outbox_published`.
- La marca de agua `event_relay_watermark` (migración V5) excluye los cambios anteriores a la versión 5.
- El consumidor es idempotente gracias a `processed_events`.

**Alternativas descartadas.**

- Una tabla `outbox` nueva escrita por cada adaptador: obligaba a cambiar las firmas de los repositorios y de sus transacciones, con riesgo de regresión.
- Publicar después del commit sin outbox: pierde eventos.
- Usar Redis como cola: no es duradero ni transaccional con PostgreSQL.

**Consecuencias.**

- Entrega **al menos una vez**, sin pérdidas. No se afirma «exactamente una vez».
- Los eventos no llevan datos personales; el consumidor busca el destinatario por `orderId`.
- Si RabbitMQ no está, los pedidos funcionan y los eventos esperan (`hexaorders_outbox_pending`).
- Los eventos derivan del historial, así que cualquier cambio futuro de estado genera su evento sin código adicional en los casos de uso.

Detalle: [`ASYNCHRONOUS_EVENTS.md`](../ASYNCHRONOUS_EVENTS.md).
