# Auditoría de POLYMER

**Fecha:** 12 de noviembre de 2026 · **Rama:** `claude/clever-curie-1597uv` · **Alcance:** todo el repositorio
(`services/`, `infra/`, `k8s/`, `tools/`, `docs/`, `.github/workflows/verify.yml`, `docker-compose.yml`,
`polymer.cmd`).

**Método.** Lectura del código y de la configuración; pruebas ejecutadas **de verdad** en dos entornos:
**Local** (contenedor Linux con Java 21, Maven sin red, Python 3, Nginx 1.24; Docker sin acceso a red para
construir imágenes) y **CI** (GitHub Actions: Ubuntu con Docker real y los 13 contenedores; Windows con
PowerShell 5.1). Ninguna funcionalidad se marca como verificada solo porque exista su código.

**Clasificación**

- ✅ **Implementada y verificada** (con la prueba o ejecución que lo demuestra).
- 🟡 **Implementada, pendiente de verificación** (no se pudo ejecutar en este entorno).
- 🔴 **Implementada con errores** (se indica si se corrigió en este trabajo).
- ⚪ **No implementada**.
- ➖ **No aplicable**.

## 1. Funcionalidades

| Funcionalidad | Estado | Evidencia |
| --- | --- | --- |
| Tienda: catálogo (556 productos, 20 categorías, 170 subcategorías), búsqueda, filtros, ficha, ofertas | ✅ | `store_smoke.py` (38 comprobaciones), `pages-e2e.js`, `generate_catalog.py --check` |
| Carrito guardado y fusión del carrito del visitante | ✅ | `store_smoke.py`, `CommerceServiceTest`, `TechStoreServicesTest` |
| Checkout con entrega/pago, idempotencia, stock, número de pedido | ✅ | `store_smoke.py`, `ui-e2e.js`, `CommerceServiceTest` |
| Seguimiento y cancelación del cliente | ✅ | `pages-e2e.js`, `store_smoke.py` |
| Registro, login, CSRF, BCrypt, política de contraseñas | ✅ | `SecurityRulesTest`, `PasswordPolicyTest`, `i18n-e2e.js` (rechazos `WEAK_PASSWORD` del servidor) |
| Roles y permisos (cliente, admin, operador, logística ×2, observador) | ✅ | `SecurityRulesTest`, `pages-e2e.js` (45 páginas × 6 perfiles), `platform_smoke.py` |
| Operaciones: revisión de riesgo, transiciones, incidencias | ✅ | `OperationsAndLogisticsTest`, `platform_smoke.py` |
| Logística: calendario, asignaciones, reprogramación sin choques | ✅ | `OperationsAndLogisticsTest`, `platform_smoke.py`, `pages-e2e.js` |
| Administración: productos, categorías, inventario, promociones, usuarios, auditoría, reportes | ✅ | `store_smoke.py`, `platform_smoke.py`, `pages-e2e.js` |
| Monitoreo interno (lee Prometheus por `MonitoringPort`) | ✅ | `pages-e2e.js` en CI («order-api, risk-engine y prometheus en UP leídos de Prometheus») |
| Identidad POLYMER: intro, sonidos opcionales, transiciones, pie, Libro de reclamaciones | ✅ | `intro-e2e.js` (41 comprobaciones) |
| Internacionalización (6 idiomas, rutas, SEO, sitemap) | ✅ | `i18n-e2e.js` (77), `LocalizedPagesTest`, `check_i18n.py` (2471 textos) |
| Arquitectura hexagonal (Java y Python) | ✅ | 8 reglas ArchUnit (4 añadidas en este trabajo), `test_architecture.py`; mutación que viola la regla → detectada |
| Sustitución de adaptadores (`RiskAssessmentPort`, `CatalogCache`) | ✅ | `AdapterSubstitutionTest` (4); modo `basic` usa `CatalogCache.NONE` (CI `compose-basic`) |
| Motor de riesgo Python y comunicación Java → Python | ✅ | pytest (62), `integration_smoke.py` (traza con spans de ambos servicios en Tempo) |
| 503 si `risk-engine` cae; recuperación | ✅ | `polymer.ps1 verify` pasos 9 y 10 (CI) |
| Redis: caché del catálogo con TTL e invalidación por versión | ✅ | `integration_smoke.py` (aciertos en Prometheus); escenario `redis` (miss → hit → TTL → expiración → miss) tras corregir el error 6; degradación y recuperación (paso 7b) |
| RabbitMQ: outbox, publisher confirms, cola durable, DLQ, consumidor idempotente, Mailpit | ✅ | `integration_smoke.py` (correos, `ack`, DLQ vacía, outbox vacío; caída y recuperación sin pérdidas) |
| Persistencia PostgreSQL tras reinicios | ✅ | `polymer.ps1 verify` paso 8; `migrate-from-v6` (recuentos idénticos) |
| Migración segura desde HexaOrders v6 (secretos y volúmenes) | ✅ | Job `migrate-from-v6` |
| `change-password` (PostgreSQL, Grafana, RabbitMQ) | ✅ | Job `migrate-from-v6` |
| Secretos aleatorios y memorables, nunca impresos | ✅ | Job `windows`; `integration_smoke.py` comprueba que ningún log de Loki contiene un secreto |
| Observabilidad: Prometheus (9 jobs, 9 alertas), Grafana (8 dashboards), Loki, Tempo, Collector, Alloy | ✅ | `integration_smoke.py` (targets UP, fuentes de datos OK, dashboards aprovisionados, logs en Loki, trazas en Tempo), `check_observability.py` |
| Escenarios de la exposición (`tools/demo/polymer-demo.ps1`) | ESTADO_ESCENARIOS | Paso «Exposición» del job `compose` |
| Comandos `health`, `rebuild`, `restart`, `demo`, `demo-on`, `demo-off` | 🟡 | Analizados con PowerShell 5.1 en CI; `demo-on`/`demo-off` se ejecutan en el paso «Exposición»; `health`, `rebuild` y `restart` no se ejecutan en el CI |
| Manifiestos de Kubernetes (`k8s/`) | 🟡 | Validados estáticamente (`validate_delivery.py`: 14 objetos); nunca desplegados en un clúster |
| Guía de despliegue en Google Cloud (`docs/despliegue-cloud.md`) | 🟡 | No ejecutada |
| Despliegue en AWS con HTTPS (`infra/aws/`) | 🟡 | Plantilla de Nginx del host probada localmente (301, TLS, HSTS, 403 por IP); no desplegado (requiere aprobación, cuenta y dominio) |
| Docker Desktop en un Windows real | 🟡 | El CI de Windows no ejecuta contenedores Linux; el mismo script se ejecuta en Linux con Docker |
| Circuit breaker | ⚪ | No existe (ni Resilience4j ni equivalente). No se añade: ver `DEMOSTRACION_MICROSERVICIOS.md` |
| Exportador de métricas de Redis (`redis_exporter`) | ⚪ | No existe: las métricas de caché son las de la aplicación (`polymer_cache_requests_total`) |
| Alertmanager / envío de alertas | ⚪ | Las 9 reglas se evalúan en Prometheus; no hay notificaciones (documentado en `docs/arquitectura.md`) |
| Envío de correo real (SES, SMTP externo) | ⚪ | Mailpit captura los correos; es intencionado para la demostración |
| Contenedores Windows | ➖ | El proyecto usa contenedores Linux en Docker Desktop (requisito) |

## 2. Errores encontrados en esta auditoría

| # | Problema | Gravedad | Estado |
| --- | --- | --- | --- |
| 1 | El gateway `frontend` sobrescribía `X-Forwarded-Proto` con `$scheme`: detrás de un proxy TLS (AWS), canonical, hreflang y sitemap saldrían con `http://` | Media (solo despliegue público) | 🔴 → **corregido** (`infra/frontend/nginx.conf`, `map`); probado con Nginx y un servidor de eco: sin cabecera → `http`, con proxy TLS → `https` |
| 2 | `.gitignore` ignoraba `.env.example` (regla `.env.*`) | Baja | **Corregido** (`!.env.example`) |
| 3 | La API original `POST /api/orders` es pública sin sesión (compatibilidad v1) | Media en un despliegue público | **Mitigado en AWS**: el Nginx del host solo la permite desde la IP de administración (probado: 403). En local se mantiene por compatibilidad |
| 4 | El umbral de revisión 70 está duplicado en el dominio Java y en la restricción `orders_status_matches_risk` de PostgreSQL | Baja (defensa en profundidad), pero cambiar solo uno rompe las inserciones | Documentado; la demo de cambio de regla usa el umbral de importe del dominio Python |
| 5 | Ventana de duplicado en el consumidor: correo enviado y caída antes de registrar `processed_events` | Baja (entrega al menos una vez) | Documentado (ya lo estaba en `ASYNCHRONOUS_EVENTS.md`) |
| 6 | **La caché Redis nunca servía el árbol de categorías**: `Category.isMain()` se escribe como `"main"` y el mismo `ObjectMapper` lo rechazaba al leer; cada *hit* contaba un `error` y activaba 30 s de *back-off* (`bypass`). Los productos no estaban afectados, por eso `integration_smoke.py` veía aciertos | Media (rendimiento: el catálogo iba siempre a PostgreSQL) | 🔴 → **corregido** en `RedisCatalogCache` (ignora propiedades desconocidas al leer; `hit` solo tras leer bien; entrada ilegible = `miss` que se sobrescribe, sin *back-off*) y prueba de regresión `redisCacheReadsBackWhatItWroteIncludingDerivedAccessors` (falla sin la corrección). Lo detectó el escenario `redis` en el CI |
| 7 | Errores en el script de escenarios nuevo detectados por el CI (referencia `$ComposeFile:` y contraseña del cliente ficticio con su usuario) | — | **Corregidos** antes de la verificación final |

## 3. Servicios y su integración

| Servicio | Integrado con | Verificación |
| --- | --- | --- |
| `frontend` | `order-api` (proxy), estáticos propios; métricas vía `otel-collector` | ✅ CI |
| `order-api` | `db`, `risk-engine`, `redis`, `rabbitmq`, `mailpit`, `otel-collector`, `prometheus` (lectura) | ✅ CI |
| `risk-engine` | `order-api` (HTTP), `otel-collector` (trazas) | ✅ CI |
| `db` | `order-api` | ✅ CI |
| `redis` | `order-api` (caché) | ✅ CI |
| `rabbitmq` | `order-api` (publicador y consumidor); `prometheus` (plugin) | ✅ CI |
| `mailpit` | `order-api` (SMTP) | ✅ CI |
| `prometheus` | 9 jobs: order-api, risk-engine, prometheus, rabbitmq, otel-collector, nginx, loki, tempo, alloy | ✅ CI (targets UP) |
| `grafana` | Prometheus, Loki, Tempo | ✅ CI (fuentes de datos OK) |
| `loki` / `alloy` | Logs de los contenedores | ✅ CI (logs recibidos) |
| `tempo` / `otel-collector` | Trazas OTLP de Java y Python; métricas de Nginx | ✅ CI |

## 4. Dependencias críticas

- **Crear pedidos:** `db` y `risk-engine` (sin riesgo → 503, decisión de negocio).
- **Navegar la tienda:** `frontend`, `order-api`, `db`. Redis es opcional (fallback a PostgreSQL).
- **Notificaciones:** `rabbitmq` y `mailpit`; si faltan, los eventos esperan en el outbox.
- **Observabilidad:** independiente del negocio (si cae, la tienda sigue).

## 5. Riesgos para AWS y al modificar la estructura

| Riesgo | Mitigación |
| --- | --- |
| Memoria: 4,5 GB de límites + compilación | `t3.large` (8 GiB); `t3.medium` no basta |
| Exposición de interfaces internas | Contenedores solo en `127.0.0.1`; Nginx del host como única entrada; Grafana con login + IP permitida; resto por SSM |
| HTTPS sin dominio | Comprar dominio o CloudFront (ver `DESPLIEGUE_AWS_HTTPS.md`) |
| Rollback tras una migración Flyway nueva | Copia (`backup.sh`) o snapshot antes de actualizar |
| `docker-compose.yml` autosuficiente: el Dockerfile en línea de `order-api` se acerca al límite gRPC de BuildKit | Archivos > 64 KB comprimidos (1,6 MB actuales); `--check` canónico |
| Renombrar identificadores heredados (`hexaorders.*` en RabbitMQ, usuario `hexaorders`) | **No renombrar**: están en los volúmenes existentes |
| Cambiar el umbral de riesgo solo en Java | Romper la restricción de PostgreSQL; ver error 4 |

## 6. Credenciales

| Credencial | Uso | Gestión |
| --- | --- | --- |
| `secrets/db_password.txt` | PostgreSQL (usuario `orders`) | Docker Secret; guardada también dentro del volumen |
| `secrets/grafana_password.txt` | Grafana `admin` | Docker Secret; guardada en el volumen de Grafana |
| `secrets/rabbitmq_password.txt` | RabbitMQ (usuario heredado `hexaorders`) | Docker Secret; guardada en el volumen de RabbitMQ |
| `secrets/demo_password.txt` | Cuentas de demostración (solo al crearlas) | Después, cada usuario cambia la suya en la tienda |

- **Ningún secreto** está en archivos rastreados ni en el historial de Git (50 commits revisados); solo
  valores efímeros del CI (`ci-only-…`) y marcadores de plantilla (`GUIA_GITHUB.md`, sección 1).
- No se cambió ninguna contraseña ni se regeneró ningún secreto existente en este trabajo.
- Rotación segura: `.\polymer.cmd change-password -Service db|grafana|rabbitmq` (copia, cambio, verificación,
  restauración si falla). Producción (AWS) usa secretos propios, con copia en SSM Parameter Store.

## 7. Métricas → servicio → panel

Extraído de los dashboards reales (`infra/grafana/dashboards/*.json`). «no data» en un panel significa que
no hay muestras recientes, nunca un cero inventado.

| Métrica | Servicio que la produce | Dashboard › panel |
| --- | --- | --- |
| `hikaricp_connections_active` | order-api | POLYMER — Infrastructure › Conexiones PostgreSQL activas; POLYMER — Infrastructure › Pool HikariCP (orders-pool) |
| `hikaricp_connections_idle` | order-api | POLYMER — Infrastructure › Pool HikariCP (orders-pool) |
| `hikaricp_connections_max` | order-api | POLYMER — Infrastructure › Pool HikariCP (orders-pool) |
| `hikaricp_connections_pending` | order-api | POLYMER — Infrastructure › Pool HikariCP (orders-pool) |
| `http_client_requests_seconds_count` | order-api (cliente HTTP hacia risk-engine) | POLYMER — Risk Engine › Latencia media Java → Python (POST /score, cliente HTTP); POLYMER — Risk Engine › Llamadas Java → Python por resultado / s |
| `http_client_requests_seconds_sum` | order-api (cliente HTTP hacia risk-engine) | POLYMER — Risk Engine › Latencia media Java → Python (POST /score, cliente HTTP) |
| `http_server_requests_seconds_bucket` | order-api | POLYMER · Puertos, adaptadores y métricas › Latencia p95 API de pedidos; POLYMER — API Performance › Latencia HTTP p50 / p95 / p99; POLYMER — API Performance › Latencia p95 (+1) |
| `http_server_requests_seconds_count` | order-api | POLYMER · Puertos, adaptadores y métricas › Errores HTTP 5xx / segundo (p. ej. 503 si cae el motor de riesgo); POLYMER · Puertos, adaptadores y métricas › Respuestas HTTP /api/orders por código / segundo; POLYMER — API Performance › Distribución de códigos HTTP / s (+7) |
| `http_server_requests_seconds_sum` | order-api | POLYMER — Business Operations › Tiempo medio de checkout (POST /api/shop/orders) |
| `jvm_gc_pause_seconds_count` | order-api | POLYMER — Infrastructure › Hilos JVM y pausas de GC |
| `jvm_memory_used_bytes` | order-api | POLYMER — Infrastructure › Memoria JVM usada por área |
| `jvm_threads_live_threads` | order-api | POLYMER — Infrastructure › Hilos JVM y pausas de GC |
| `loki_distributor_lines_received_total` | loki | POLYMER — Platform Integrations › Ingesta de logs (Loki) y trazas (Tempo) / s |
| `nginx_requests_total` | frontend (vía otel-collector) | POLYMER — Platform Integrations › Gateway Nginx: peticiones / s y conexiones |
| `orders_created_total` | order-api | POLYMER · Puertos, adaptadores y métricas › Pedidos por estado / segundo; POLYMER — Business Operations › Pedidos creados por decisión de riesgo / min (todos los canales) |
| `orders_risk_failures_total` | order-api | POLYMER — Risk Engine › Errores y timeouts de integración (5 min) |
| `otelcol_exporter_send_failed_spans` | otel-collector | POLYMER — Platform Integrations › Telemetría: spans recibidos y exportados por el collector / s |
| `otelcol_exporter_sent_spans` | otel-collector | POLYMER — Platform Integrations › Telemetría: spans recibidos y exportados por el collector / s |
| `otelcol_receiver_accepted_spans` | otel-collector | POLYMER — Platform Integrations › Telemetría: spans recibidos y exportados por el collector / s |
| `polymer_access_denied_total` | order-api | POLYMER — Security & Audit › Accesos denegados por rol (1 h); POLYMER — Security & Audit › Accesos sin sesión rechazados (1 h); POLYMER — Security & Audit › Rechazos de autorización por motivo / min |
| `polymer_auth_logins_total` | order-api | POLYMER — Security & Audit › Inicios de sesión correctos (24 h); POLYMER — Security & Audit › Inicios de sesión fallidos (15 min); POLYMER — Security & Audit › Inicios de sesión por resultado / min |
| `polymer_cache_requests_total` | order-api | POLYMER — Platform Integrations › Aciertos de caché del catálogo (5 min); POLYMER — Platform Integrations › Caché Redis del catálogo por resultado / s |
| `polymer_checkout_failures_total` | order-api | POLYMER — Business Operations › Checkouts rechazados por motivo (15 min); POLYMER — Risk Engine › Errores y timeouts de integración (5 min) |
| `polymer_deliveries_total` | order-api | POLYMER — Business Operations › Entregas programadas y reprogramadas (1 h); POLYMER — Logistics Operations › Entregas programadas (24 h); POLYMER — Logistics Operations › Entregas programadas y reprogramadas / h (+2) |
| `polymer_delivery_conflicts_total` | order-api | POLYMER — Business Operations › Conflictos de agenda (24 h); POLYMER — Business Operations › Entregas programadas y reprogramadas (1 h); POLYMER — Logistics Operations › Conflictos de agenda (24 h) (+1) |
| `polymer_events_consumed_total` | order-api | POLYMER — Platform Integrations › Eventos consumidos por resultado / min |
| `polymer_events_publish_failures_total` | order-api | POLYMER — Platform Integrations › Eventos publicados y fallos de publicación / min |
| `polymer_events_published_total` | order-api | POLYMER — Platform Integrations › Eventos publicados y fallos de publicación / min |
| `polymer_incidents_reported_total` | order-api | POLYMER — Business Operations › Incidencias registradas por categoría (1 h); POLYMER — Logistics Operations › Conflictos de agenda e incidencias logísticas / h; POLYMER — Logistics Operations › Incidencias de retraso o dirección (24 h) (+1) |
| `polymer_notifications_sent_total` | order-api | POLYMER — Platform Integrations › Correos enviados a Mailpit (24 h) |
| `polymer_order_transitions_total` | order-api | POLYMER — Business Operations › Transiciones del ciclo de vida / min (por estado destino); POLYMER — Logistics Operations › Despacho y entrega / h; POLYMER — Security & Audit › Actividad operativa auditada (transiciones e incidencias / 15 min) |
| `polymer_orders_by_status` | order-api | POLYMER — Business Operations › Aprobados por riesgo (sin confirmar); POLYMER — Business Operations › Cancelados; POLYMER — Business Operations › En preparación / listos (+9) |
| `polymer_port_calls_seconds_count` | order-api | POLYMER — Platform Integrations › Latencia media por puerto hexagonal (top 10) |
| `polymer_port_calls_seconds_sum` | order-api | POLYMER — Platform Integrations › Latencia media por puerto hexagonal (top 10) |
| `polymer_shop_orders_placed_total` | order-api | POLYMER — Business Operations › Pedidos del portal por decisión de riesgo / min |
| `process_cpu_usage` | order-api | POLYMER — Infrastructure › CPU (proceso JVM y sistema) |
| `process_start_time_seconds` | order-api | POLYMER — Infrastructure › Reinicios de order-api (24 h) |
| `process_uptime_seconds` | order-api | POLYMER — Infrastructure › Uptime order-api |
| `rabbitmq_queue_messages` | rabbitmq (plugin prometheus) | POLYMER — Platform Integrations › Mensajes en la DLQ |
| `risk_evaluation_duration_seconds_bucket` | risk-engine | POLYMER · Puertos, adaptadores y métricas › Latencia p95 evaluación de riesgo Python; POLYMER — Risk Engine › Latencia del caso de uso Python (p50 / p95 / p99) |
| `risk_evaluations_total` | risk-engine | POLYMER · Puertos, adaptadores y métricas › Evaluaciones de riesgo / segundo; POLYMER — Risk Engine › Evaluaciones por decisión / s |
| `risk_requests_rejected_total` | risk-engine | POLYMER — Risk Engine › Errores y timeouts de integración (5 min) |
| `system_cpu_usage` | order-api | POLYMER — Infrastructure › CPU (proceso JVM y sistema) |
| `tempo_distributor_spans_received_total` | tempo | POLYMER — Platform Integrations › Ingesta de logs (Loki) y trazas (Tempo) / s |
| `up` | prometheus | POLYMER · Puertos, adaptadores y métricas › Servicios disponibles (1 = UP); POLYMER — API Performance › Disponibilidad order-api (1 = UP); POLYMER — Infrastructure › order-api (+3) |


## 8. Datos que deben preservarse

Volúmenes `polymer_postgres_data` (pedidos, usuarios, catálogo, historial), `polymer_rabbitmq_data`
(usuario y colas), `polymer_grafana_data`, `polymer_prometheus_data`, `polymer_loki_data`,
`polymer_tempo_data`, `polymer_redis_data` (caché, reconstruible), `polymer_alloy_data`; los volúmenes
heredados `hexaorders_*` (respaldo de la migración) y la carpeta `secrets/`. Ningún comando de este trabajo
borra volúmenes (`down -v` y `stop -RemoveData` no se usan).
