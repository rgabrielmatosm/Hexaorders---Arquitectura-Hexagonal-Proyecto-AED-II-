# Validación de POLYMER

Este registro separa lo **ejecutado y observado** de lo **pendiente de verificación**. Un resultado esperado nunca se presenta como observado. Los registros de versiones anteriores están en [`historial/validacion-hexaorders-v1-v6.md`](historial/validacion-hexaorders-v1-v6.md).

## Identidad técnica POLYMER y migración segura (8 de noviembre de 2026)

### Comparación con la versión anterior (sin inventar resultados)

Se compararon, archivo por archivo, los dos ZIP entregados: `HexaOrders-v6.zip` (425 archivos) y `POLYMER-HexaOrders.zip` (449 archivos).

| Comprobación | Resultado observado |
| --- | --- |
| Archivos de la v6 ausentes en POLYMER | **Ninguno** (los 24 archivos nuevos son de la marca, la intro y las páginas informativas) |
| Rutas del frontend (`route(…)`), funciones y constantes exportadas, endpoints Java (`@…Mapping`), métodos Java, `@Bean`, servicios de Compose, funciones de Python y de PowerShell, títulos y uid de dashboards, alertas | **Nada se perdió**: todo lo de la v6 sigue presente |
| Causa real del fallo de `start` | El ZIP no incluye `secrets\` (las contraseñas no se distribuyen). Al descomprimirlo en otra carpeta, el script encontraba los volúmenes `hexaorders_postgres_data` y `hexaorders_grafana_data` sin sus contraseñas y se detenía **a propósito**, para no dejar inaccesibles los datos |

### Pruebas locales

Contenedor Linux **sin Docker**, con PostgreSQL 16 y una base que ya tenía datos de la v6 y de POLYMER.

| Comprobación | Resultado observado |
| --- | --- |
| Java (`mvn test`), con el paquete `com.polymer` | **110 pruebas**, cero fallos, incluidas las de arquitectura hexagonal (ArchUnit) |
| Motor de riesgo (`pytest`) | **62 pruebas** correctas, incluida la de arquitectura |
| Migración V9 sobre la base con datos | `Successfully applied 1 migration … version 9`. Se conservan los **23 pedidos y 15 usuarios**; las cuentas de demostración pasan a `@polymer.local` y «Administración Polymer» |
| `store_smoke.py` / `platform_smoke.py` | `OK tienda: 38 comprobaciones` / `OK plataforma: 35 comprobaciones` |
| `intro-e2e.js` | `OK: intro y movimiento de POLYMER (41 comprobaciones)` |
| `ui-e2e.js` | `UI E2E correcta: 25 comprobaciones` (compra completa con número HX) |
| `pages-e2e.js` | `Páginas E2E correctas: 284 comprobaciones`: las 45 páginas como visitante y con `cliente`, `admin`, `operador`, `logistica` y `observador`; `logistica2` se comprueba en la plataforma |
| `generate_delivery.py --check`, `validate_delivery.py`, `check_observability.py`, `generate_pages.py --check`, `check_frontend.py`, `generate_brand.py --check`, `generate_catalog.py --check` | Correctos |

En local se usó `SKIP_PROMETHEUS=1` porque no hay Prometheus: Prometheus, Grafana, Redis, RabbitMQ, Mailpit, Loki, Tempo y OpenTelemetry se prueban en el CI con Docker.

### CI con Docker real

*(Se completa con la ejecución de GitHub Actions de este cambio.)*

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| Docker Desktop en Windows | Este entorno no tiene Docker ni Windows. El CI ejecuta el mismo `polymer.ps1` con Docker en Ubuntu, y en Windows analiza los scripts con PowerShell 5.1 y ejecuta `check` y las pruebas unitarias |
| Tu migración real | El CI reproduce el caso (v6 con datos → POLYMER en otra carpeta sin `secrets\`), pero no con tus datos. Al ejecutar `.\polymer.cmd start`, revisa el registro `polymer-migration.log` |
| Que el sonido se oiga | Solo se comprobó que funciona sin errores |
