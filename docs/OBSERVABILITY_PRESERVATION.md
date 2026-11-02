# Preservación de la observabilidad (HexaOrders v3)

Este documento recoge **qué observabilidad existía antes de la versión 3**, qué se ha conservado, qué se ha añadido y **cómo se comprueba automáticamente** que nada de lo anterior desaparece. Va dirigido al equipo, a quien evalúe el proyecto y a cualquier herramienta (IA o humana) que modifique el repositorio después.

> **Regla:** en Grafana y Prometheus solo se **añade**. Ningún dashboard, panel, consulta PromQL, datasource, job, alerta ni métrica previa se borra, se renombra ni cambia de significado. `tools/check_observability.py` hace cumplir esta regla en `hexaorders.cmd check` y en el CI.

## 1. Alcance y método

- **Línea base**: commit `604394a` (versión 2, la última antes de la plataforma v3).
- **Captura**: `infra/observability-baseline.json`, un inventario en JSON con los dashboards (uid, título, paneles y sus expresiones), datasources, configuración global de Prometheus, jobs de scraping, reglas de alerta y métricas instrumentadas de esa versión.
- **Verificación**: `tools/check_observability.py` compara el estado actual con la línea base y falla si algo falta o ha cambiado. No usa red ni Docker, así que también se ejecuta en Windows (`hexaorders.cmd check`).

## 2. Inventario previo (versión 2)

| Elemento | Cantidad | Dónde |
| --- | --- | --- |
| Dashboards de Grafana | 1 (`hexaorders`) | `infra/grafana/dashboards/hexaorders.json` |
| Paneles | 9 | Dashboard `hexaorders` |
| Datasources | 1 (`Prometheus`, uid `prometheus`) | `infra/grafana/provisioning/datasource.yml` |
| Jobs de Prometheus | 3 (`order-api`, `risk-engine`, `prometheus`) | `infra/prometheus/prometheus.yml` |
| Reglas de alerta | 2 (`HexaOrdersServiceDown`, `RiskRequestsFailing`) | `infra/prometheus/alerts.yml` |
| Métricas usadas en paneles y alertas | 7 | Java (Micrometer/Actuator), Python (`prometheus_client`), Prometheus |

## 3. Paneles conservados (dashboard «HexaOrders · Puertos, adaptadores y métricas»)

| Id | Panel | Tipo | PromQL (sin cambios) |
| --- | --- | --- | --- |
| 1 | Servicios disponibles (1 = UP) | stat | `up{job=~"order-api\|risk-engine"}` |
| 2 | Pedidos creados desde el arranque de Java | stat | `sum(orders_created_total) or vector(0)` |
| 3 | Fallos de riesgo desde el arranque de Java | stat | `sum(orders_risk_failures_total) or vector(0)` |
| 4 | Pedidos por estado / segundo | timeseries | `sum by(status)(rate(orders_created_total[2m]))` |
| 5 | Evaluaciones de riesgo / segundo | timeseries | `sum by(decision)(rate(risk_evaluations_total[2m]))` |
| 6 | Latencia p95 API de pedidos | timeseries | `histogram_quantile(0.95, sum by(le)(rate(http_server_requests_seconds_bucket{uri=~"/api/orders.*"}[2m])))` |
| 7 | Latencia p95 evaluación de riesgo Python | timeseries | `histogram_quantile(0.95, sum by(le)(rate(risk_evaluation_duration_seconds_bucket[2m])))` |
| 8 | Respuestas HTTP /api/orders por código / segundo | timeseries | `sum by(status)(rate(http_server_requests_seconds_count{uri=~"/api/orders.*"}[2m]))` |
| 9 | Errores HTTP 5xx / segundo | timeseries | `sum by(uri, status)(rate(http_server_requests_seconds_count{status=~"5.."}[2m])) or vector(0)` |

El archivo `hexaorders.json` **no se ha modificado** en la versión 3 (`git diff 604394a -- infra/grafana/dashboards/hexaorders.json` está vacío).

## 4. Métricas conservadas

| Métrica | Origen | Estado en v3 |
| --- | --- | --- |
| `orders_created_total{status}` | Java · `MicrometerOrderTelemetry` | Conservada; ahora con datos reales gracias a la regla de registro (sección 10). |
| `orders_risk_failures_total` | Java · `MicrometerOrderTelemetry` | Sin cambios. |
| `http_server_requests_seconds_count` / `_bucket` | Spring Boot Actuator (histograma activado en `application.yml`) | Sin cambios; el histograma sigue activado y se comprueba. |
| `risk_evaluations_total{decision}` | Python · `adapters/metrics.py` | Sin cambios. |
| `risk_evaluation_duration_seconds_bucket` | Python · `adapters/metrics.py` | Sin cambios. |
| `up{job}` | Prometheus | Sin cambios. |

Los nombres de las clases que emiten estas métricas (`MicrometerOrderTelemetry`, `OrderTelemetry`, `adapters/metrics.py`) tampoco han cambiado. El checkout de la tienda **reutiliza** `OrderTelemetry`, por lo que los pedidos de la tienda también cuentan en `orders_created_total` y en los paneles originales.

## 5. Targets de Prometheus conservados

| Job | Ruta | Target | Intervalo |
| --- | --- | --- | --- |
| `order-api` | `/actuator/prometheus` | `order-api:9090` | 10 s (global) |
| `risk-engine` | `/metrics` | `risk-engine:8000` | 10 s |
| `prometheus` | `/metrics` | `localhost:9090` | 10 s |

`global` (`scrape_interval: 10s`, `evaluation_interval: 10s`, `external_labels.project: hexaorders`) y `rule_files: [/etc/prometheus/alerts.yml]` no cambian. `prometheus.yml` **no se ha modificado**.

## 6. Datasources conservados

| Nombre | uid | Tipo | URL |
| --- | --- | --- | --- |
| Prometheus | `prometheus` | prometheus | `http://prometheus:9090` |

Todos los paneles nuevos usan este mismo datasource por uid; `check_observability.py` falla si un panel apunta a otro.

## 7. Configuraciones conservadas

- `infra/prometheus/prometheus.yml`: sin cambios.
- `infra/grafana/provisioning/datasource.yml` y `dashboards.yml`: sin cambios.
- `infra/prometheus/alerts.yml`: el grupo original y sus dos alertas se mantienen byte a byte; los grupos nuevos se **añaden al final**.
- `infra/grafana/Dockerfile`: se mantienen todas las líneas; se añaden los `COPY` de los dashboards nuevos.
- Docker Compose: los servicios `prometheus` y `grafana`, sus puertos (`127.0.0.1:9091`, `127.0.0.1:3000`), volúmenes (`prometheus_data`, `grafana_data`), healthchecks y endurecimiento no cambian. El YAML sigue generándose con `tools/generate_delivery.py`.
- Kubernetes: `k8s/40-observability.yaml` conserva sus montajes y añade uno `subPath` por dashboard nuevo; el ConfigMap generado (`k8s/10-monitoring-config.yaml`) incluye los nuevos JSON.
- Puerto de gestión de Java (`9090`, interno), endpoints `/actuator/health/{liveness,readiness}` y `/actuator/prometheus`: sin cambios. Spring Security deja `/actuator/**` accesible para que Prometheus siga recogiendo métricas sin credenciales (el puerto 9090 no se publica fuera de la red de Compose).

## 8. Elementos añadidos en la versión 3

### Dashboards (5)

| uid | Título | Paneles |
| --- | --- | --- |
| `hexaorders-business` | HexaOrders — Business Operations | 19: pedidos por estado del ciclo de vida (PostgreSQL), pedidos del portal por decisión, transiciones, checkouts rechazados, tiempo de checkout, incidencias, stock bajo, conflictos de agenda, entregas |
| `hexaorders-risk` | HexaOrders — Risk Engine | 10: disponibilidad, evaluaciones, decisiones, latencia p50/p95/p99 del caso de uso Python, latencia Java → Python, rechazos por validación, fallos y timeouts |
| `hexaorders-api` | HexaOrders — API Performance | 10: peticiones/s, tasa 5xx, p50/p95/p99, códigos, top 10 endpoints por tráfico y latencia, 4xx y 5xx por endpoint |
| `hexaorders-infrastructure` | HexaOrders — Infrastructure | 13: disponibilidad de los 3 targets, uptime y reinicios, conexiones PostgreSQL, memoria JVM, CPU, HikariCP, hilos y GC, duración del scraping, muestras; un panel de texto explica que no hay cAdvisor |
| `hexaorders-security` | HexaOrders — Security & Audit | 9: inicios de sesión correctos y fallidos, accesos sin sesión, accesos denegados por rol, 401/403 por endpoint, actividad auditada |

### Métricas (Java salvo indicación)

`hexaorders_shop_orders_placed_total{risk_decision}`, `hexaorders_checkout_failures_total{reason}`, `hexaorders_order_transitions_total{from,to}`, `hexaorders_orders_by_status{status}`, `hexaorders_products_low_stock`, `hexaorders_incidents_open`, `hexaorders_incidents_reported_total{category}`, `hexaorders_deliveries_total{type}`, `hexaorders_delivery_conflicts_total`, `hexaorders_auth_logins_total{outcome}`, `hexaorders_access_denied_total{reason}` y, en Python, `risk_requests_rejected_total{reason}`. Los contadores con etiquetas se registran al arrancar con todos sus valores, así que existen (a 0) antes del primer evento y los paneles no muestran «No data».

### Alertas (7) y regla de registro (1)

| Nombre | Expresión resumida | for |
| --- | --- | --- |
| `orders_created_total` (record) | `orders_total` | — |
| `RiskEngineDown` | `up{job="risk-engine"} == 0` | 30s |
| `HexaOrdersHighErrorRate` | 5xx de order-api > 0,05/s | 2m |
| `HexaOrdersHighLatency` | p95 de `/api/.*` > 1 s | 5m |
| `HexaOrdersCheckoutFailures` | > 3 checkouts con `risk_unavailable` en 5 min | 1m |
| `HexaOrdersPendingReviewGrowing` | > 10 pedidos en `RISK_REVIEW` | 10m |
| `HexaOrdersAuthFailuresSpike` | > 10 inicios fallidos en 5 min | 1m |
| `HexaOrdersLowStock` | `hexaorders_products_low_stock > 0` | 15m |

Fuente: `infra/prometheus/alerts.yml`.

### Versión 4: dashboard «Logistics Operations» y portal web de monitoreo

- **`hexaorders-logistics`** (12 paneles): pedidos confirmados o en preparación, listos para despacho, en tránsito y entregados (`hexaorders_orders_by_status`); entregas programadas y reprogramadas, conflictos de agenda e incidencias de retraso, dirección o daño en 24 h; despachos y entregas por hora (`hexaorders_order_transitions_total{to}`); tramo logístico apilado. Usa solo métricas que ya existían: la v4 no añade ni renombra métricas.
- **Páginas `/monitoreo/*`** para OBSERVABILITY_VIEWER y ADMIN: servicios (targets), métricas (14 indicadores con PromQL fijo) y alertas (reglas y estado). order-api las obtiene de Prometheus por la red interna `telemetry` (`PROMETHEUS_URL=http://prometheus:9090`) mediante `MonitoringPort` → `PrometheusMonitoringAdapter` ([ADR 0010](adr/0010-monitoring-portal-through-a-port.md)). **No sustituyen a Grafana**: enlazan a sus 7 dashboards. Si Prometheus no responde, lo dicen sin romper la página.
- La única configuración tocada es la necesaria para servir el dashboard nuevo: un `COPY` en `infra/grafana/Dockerfile`, una entrada en el ConfigMap generado y un montaje `subPath` en `k8s/40-observability.yaml`, más la variable `PROMETHEUS_URL` de order-api. `prometheus.yml`, `alerts.yml`, el datasource y los 6 dashboards anteriores no cambian.

## 9. Pruebas de compatibilidad

| Prueba | Dónde se ejecuta | Qué garantiza |
| --- | --- | --- |
| `tools/check_observability.py` | `hexaorders.cmd check` (Windows y Linux), job `windows` del CI | Dashboards, paneles, expresiones, datasource, jobs, alertas y métricas de la línea base siguen presentes e idénticos; cada dashboard JSON se copia en la imagen de Grafana; no hay uids duplicados; los paneles usan el datasource `prometheus`; existe la regla `orders_created_total`; el histograma HTTP sigue activado. |
| `promtool check config` | `hexaorders.cmd verify` | `prometheus.yml` y `alerts.yml` (incluidas las reglas nuevas) son válidos. |
| `tools/smoke.py` | `verify` | Targets `up`, métricas originales en Prometheus, dashboard `hexaorders` **y los 6 nuevos** (incluido `hexaorders-logistics`) aprovisionados en Grafana. |
| `tools/ui-e2e/pages-e2e.js` (v4) | job `compose` | El rol observador ve los 3 targets en UP y las 9 reglas de alerta leídas de Prometheus a través del backend. |
| `PrometheusMonitoringAdapterTest` (v4) | `mvn verify` | El adaptador interpreta targets, reglas e indicadores de la API de Prometheus y convierte un fallo en «no disponible». |
| `tools/platform_smoke.py` con `PROMETHEUS_URL` | `verify` | Las métricas nuevas y `orders_created_total` existen en Prometheus, junto con métricas de JVM y HikariCP. |
| `tools/ui-e2e/ui-e2e.js` | job `compose` | La sección Observabilidad del laboratorio sigue mostrando los 3 targets y las métricas reales. |
| `generate_delivery.py --check`, `validate_delivery.py` | `check` | El Compose y el ConfigMap de Kubernetes incluyen la configuración de monitoreo al día. |

Resultado local de `check_observability.py` (v4): `OK: observabilidad previa conservada (9 paneles, 2 alertas, 3 jobs) · 7 dashboards en total, 9 alertas en total`.

## 10. Defectos encontrados y corregidos

**`orders_created_total` no tenía datos (defecto previo a la v3).** El cliente Java de Prometheus 1.x que usa Spring Boot 3.5 reserva el sufijo `_created` para las marcas de tiempo de los contadores. Por eso el contador Micrometer `orders_created` se exporta como **`orders_total`**, y los paneles 2 y 4 del dashboard original, que consultan `orders_created_total`, aparecían vacíos o a 0.

- **Corrección elegida:** una regla de registro en `alerts.yml` (grupo `hexaorders-compat`) que republica `orders_total` con su nombre original: `record: orders_created_total`, `expr: orders_total`.
- **Por qué así:** no se modifica ningún panel ni consulta, no se renombra la métrica en Java (otros consumidores podrían usar `orders_total`) y los dos nombres quedan disponibles.
- **Efecto:** los paneles originales muestran datos; la serie registrada tiene un retraso de un intervalo de evaluación (10 s).
- **Comprobación:** `check_observability.py` exige la regla, y `platform_smoke.py` comprueba que la serie existe en Prometheus.

Durante el trabajo no se encontró ningún otro panel ni alerta roto.

## 11. Antes y después

| Aspecto | Antes (v2) | Después (v3) | Después (v4) |
| --- | --- | --- | --- |
| Dashboards | 1 | 6 (el original intacto) | 7 (+ Logistics Operations) |
| Paneles | 9 | 70 (9 originales + 61 nuevos) | 82 (+ 12 de logística) |
| Alertas | 2 | 9 (2 originales intactas + 7) | 9 |
| Reglas de registro | 0 | 1 (compatibilidad) | 1 |
| Jobs de Prometheus | 3 | 3 (sin cambios) | 3 (sin cambios) |
| Datasources | 1 | 1 (sin cambios) | 1 (sin cambios) |
| Métricas de negocio | pedidos y fallos de riesgo | + tienda, ciclo de vida, logística, incidencias, stock, seguridad | sin cambios |
| Paneles originales con datos | 7 de 9 (2 sin datos reales por el defecto de la sección 10) | 9 de 9 | 9 de 9 |
| Verificación automática de preservación | no | sí (`check_observability.py`) | sí |
| Portal web de monitoreo | no | salud de dependencias | + targets, alertas e indicadores de Prometheus |

## 12. Cómo añadir observabilidad sin romper nada

1. Crea un dashboard **nuevo** en `infra/grafana/dashboards/` con un `uid` único y el datasource `{"type": "prometheus", "uid": "prometheus"}`. No edites `hexaorders.json`.
2. Añade su `COPY` en `infra/grafana/Dockerfile`, su montaje en `k8s/40-observability.yaml` y su entrada en `monitoring_configmap` de `tools/generate_delivery.py`.
3. Añade alertas en un grupo **nuevo** al final de `alerts.yml`; no edites los grupos existentes.
4. Para una métrica nueva, regístrala desde un adaptador de salida (`adapter/out/metrics`) detrás de un puerto del núcleo; nunca desde el dominio.
5. Ejecuta `py tools\generate_delivery.py`, `.\hexaorders.cmd check` y, con Docker, `.\hexaorders.cmd verify`.
6. Si un cambio **intencionado** debe alterar la línea base (por ejemplo, retirar una métrica con acuerdo del equipo), actualiza `infra/observability-baseline.json` en un commit separado que lo explique. Nunca para hacer pasar el check.

No uses `docker compose down -v` para «arreglar» Grafana o Prometheus: borra los volúmenes con el historial de métricas y los datos de PostgreSQL. Para recargar dashboards basta con `docker compose up -d --build grafana`.
