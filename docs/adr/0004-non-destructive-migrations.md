# ADR 0004 — Migraciones no destructivas y tabla `orders` compartida

**Contexto.** Existen bases de datos con pedidos de la v1/v2 que no se pueden perder, y `docker compose down -v` está prohibido como solución.

**Decisión.** `V2__commerce_platform.sql` solo crea tablas y añade columnas con valores por defecto (`channel = 'LAB'`, `fulfillment_status` derivado de `status`, `version = 0`) y escribe historial de migración para los pedidos existentes; `V3__seed_catalog.sql` carga el catálogo. Laboratorio y tienda comparten `orders`, distinguidos por `channel`; las consultas originales filtran `channel = 'LAB'` para no exponer pedidos de clientes en la API pública, y `/statistics` mantiene su significado.

**Verificación.** Se arrancó la versión anterior sobre PostgreSQL 16, se crearon pedidos y después se arrancó la v3: Flyway aplicó V2 y V3 y los pedidos siguieron intactos.

**Consecuencias.** Las migraciones publicadas son inmutables; cualquier cambio futuro va en `V4+`.
