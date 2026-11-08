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

### Segunda comparación: POLYMER anterior → POLYMER actual (después del renombrado técnico)

Se compararon `POLYMER-HexaOrders.zip` (449 archivos) y `POLYMER.zip` (461 archivos), normalizando con las mismas reglas de `tools/migration/rename_to_polymer.py`.

| Comprobación | Resultado observado |
| --- | --- |
| Archivos de la v6 o del POLYMER anterior ausentes | **Ninguno** |
| Líneas sin equivalente | 57, todas intencionadas: textos de documentación actualizados, comprobaciones de la marca anterior y funciones de `polymer.ps1` ampliadas |
| Única pérdida real | Las instrucciones de actualización desde versiones anteriores a la v6, que estaban en la guía de Windows. **Restauradas** en `MIGRACION_POLYMER.md`; la guía original se conserva en `historial/` |
| Mejoras visuales presentes en el código actual | Intro con telón y letras, «Entrar con sonido» y «Omitir intro»; sonidos de bienvenida, confirmación, notificación y error; View Transitions, indicador de navegación, aparición al desplazarse, vuelo al carrito, check animado y movimiento reducido; pie con redes, pagos y Libro de reclamaciones; botón de sonido; 7 SVG del logotipo |

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

Ejecución **42506314312** de GitHub Actions (commit `3d55156`): los **4 jobs en verde**.

| Job | Resultado observado |
| --- | --- |
| `compose`: `polymer.ps1 verify -Mode full` con **13 contenedores** | Los 10 pasos: build con pruebas de Java y Python, promtool y validación del collector, 13 servicios sanos, `smoke`, plataforma (36), tienda (38), integraciones reales (45: Redis, RabbitMQ, Mailpit, Tempo, OpenTelemetry, Loki y Grafana), degradación sin Redis o RabbitMQ y recuperación, persistencia tras reiniciar PostgreSQL y order-api, y HTTP 503 sin el motor de riesgo. Después, `ui-e2e.js` (28), `pages-e2e.js` (285) e `intro-e2e.js` (41) contra el stack completo |
| `compose-basic` (6 contenedores) | Correcto |
| `windows` (PowerShell 5.1: análisis de los scripts, `polymer.cmd help`, secretos, `check` y pruebas unitarias) | Correcto |
| **`migrate-from-v6`** (tu caso real) | Ver la tabla siguiente |

Detalle del job `migrate-from-v6`. Partió de HexaOrders v6 (commit `6cc87c0`) en marcha con **13 contenedores**, contraseñas aleatorias y un pedido y un mensaje creados. Después ejecutó POLYMER desde otra carpeta **sin `secrets\`**.

| Comprobación | Resultado observado |
| --- | --- |
| Recuperación de las contraseñas | Encontró `Descargas/HexaOrders-v6/secrets`, pidió confirmación y copió los 4 archivos sin mostrarlos; son idénticos byte a byte a los originales |
| Stack anterior | «Detener (sin borrar) los 13 contenedores del proyecto anterior» |
| Migración de los 8 volúmenes, con SHA-256 archivo por archivo | `postgres_data` 1368 archivos (48M) · `grafana_data` 547 (50M) · `rabbitmq_data` 38 · `loki_data` 5 · `tempo_data` 4 · `prometheus_data` 2 · `alloy_data` 2 · `redis_data` 1. Todos verificados |
| Volúmenes originales | Los 8 `hexaorders_*` siguen existiendo |
| Contenedores POLYMER | **13 en marcha**, ninguno *unhealthy* |
| Datos (pedidos/usuarios/productos/mensajes) | Antes `1/6/557/1` · después `1/6/557/1`. El pedido de la v6 se lee por la API con el mismo cliente e importe, y el mensaje de contacto sigue ahí |
| Flyway | Versión 9 aplicada; `admin` pasa a «Administración Polymer»; hay productos de la marca `Polymer` |
| Pruebas sobre los datos migrados | `platform_smoke` (35) y `store_smoke` (38) con la contraseña original de las cuentas |
| Segunda ejecución de `migrate` | «No hay volúmenes pendientes de migrar» (idempotente) |

### Prueba local de la mecánica de migración con Docker real

Docker funciona en este entorno, pero las construcciones no tienen salida a Internet (Maven y pip), así que el stack completo se probó en el CI. En local se probó el mismo script de copia que usa `polymer.ps1`, con PostgreSQL 17 real y `--auth-host=scram-sha-256` como en el proyecto:

- copia de 1275 archivos (46M) verificada;
- 300 y 500 pedidos idénticos tras la copia;
- la **contraseña original** abre la base migrada y **una contraseña nueva se rechaza** («password authentication failed»), el error que se veía al no recuperar `secrets\`;
- si el destino no coincide con el origen, la verificación **falla con código 1**;
- el volumen original queda intacto.

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| Docker Desktop en Windows | Este entorno no tiene Docker ni Windows. El CI ejecuta el mismo `polymer.ps1` con Docker en Ubuntu, y en Windows analiza los scripts con PowerShell 5.1 y ejecuta `check` y las pruebas unitarias |
| Tu migración real | El CI reproduce el caso (v6 con datos → POLYMER en otra carpeta sin `secrets\`), pero no con tus datos. Al ejecutar `.\polymer.cmd start`, revisa el registro `polymer-migration.log` |
| Que el sonido se oiga | Solo se comprobó que funciona sin errores |
