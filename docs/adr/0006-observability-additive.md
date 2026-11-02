# ADR 0006 — Observabilidad solo aditiva con línea base verificada

**Contexto.** El dashboard, las métricas y las alertas existentes forman parte de la evaluación y no se pueden perder al ampliar el sistema.

**Decisión.** Capturar el estado previo en `infra/observability-baseline.json` y comprobarlo con `tools/check_observability.py` en `check` (Windows y Linux) y en el CI. Los dashboards, alertas y métricas nuevos se añaden en archivos y grupos nuevos. El defecto previo de `orders_created_total` (renombrado a `orders_total` por el cliente Prometheus 1.x) se corrige con una regla de registro, sin editar paneles.

**Consecuencias.** Retirar algo de la línea base exige un cambio explícito y justificado del archivo de línea base. Ver [`OBSERVABILITY_PRESERVATION.md`](../OBSERVABILITY_PRESERVATION.md).
