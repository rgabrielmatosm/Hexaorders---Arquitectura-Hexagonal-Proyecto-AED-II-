# ADR 0003 — Ciclo de vida como política pura en el dominio

**Contexto.** Los pedidos pasan por diez estados y cada transición tiene actores permitidos. Esta lógica no debe quedar repartida entre controladores, SQL e interfaz.

**Decisión.** `domain/fulfillment/TransitionPolicy` declara la matriz estado → estado → actores; `FulfillmentStatus` sabe qué estados son terminales y cuándo se devuelve el stock. `LifecycleTransitions` (aplicación) aplica la política, exige entrega programada para `IN_TRANSIT`, trata como idempotente repetir el estado actual y registra historial y auditoría. La interfaz pide al backend las transiciones permitidas en lugar de duplicar la matriz.

**Consecuencias.** La política se prueba sin infraestructura (`LifecycleDomainTest`). Los pedidos de laboratorio existentes se integran con un estado inicial derivado de su decisión de riesgo.
