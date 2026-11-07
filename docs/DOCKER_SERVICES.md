# Servicios Docker de HexaOrders (v5)

Para cada contenedor se indica qué hace, cómo se integra y **qué prueba demuestra que funciona**. Ningún servicio se considera funcional solo por estar encendido. Las pruebas son:

- `smoke.py` y `platform_smoke.py`: modos básico y completo;
- `integration_smoke.py`: modo completo;
- los healthchecks de Compose.

Todas se ejecutan en `.\hexaorders.cmd verify` y en el CI.

| # | Servicio | Imagen | Modo | Puerto en el host | Redes | Volumen |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `frontend` | `nginxinc/nginx-unprivileged:1.31.7-alpine` + config | básico | 127.0.0.1:8080 | edge, gateway, telemetry | — |
| 2 | `order-api` | build `services/order-api` (Java 21) | básico | — | gateway, data, compute, telemetry, cache, messaging | — |
| 3 | `risk-engine` | build `services/risk-engine` (Python 3.12) | básico | — | compute, telemetry | — |
| 4 | `db` | `postgres:17.11-bookworm` | básico | — | data | postgres_data |
| 5 | `prometheus` | `prom/prometheus:v3.5.1` + config | básico | 127.0.0.1:9091 | edge, telemetry | prometheus_data |
| 6 | `grafana` | `grafana/grafana:12.4.1` + provisioning | básico | 127.0.0.1:3000 | edge, telemetry | grafana_data |
| 7 | `redis` | `redis:7.4.12-alpine` | full | — | cache | redis_data |
| 8 | `rabbitmq` | `rabbitmq:4.1.8-management-alpine` + config | full | 127.0.0.1:15672 (consola) | edge, messaging, telemetry | rabbitmq_data |
| 9 | `mailpit` | `axllent/mailpit:v1.33.0` | full | 127.0.0.1:8025 (web) | edge, messaging | — (en memoria) |
| 10 | `loki` | `grafana/loki:3.7.9` + config | full | — | telemetry | loki_data |
| 11 | `tempo` | `grafana/tempo:2.8.4` + config | full | — | telemetry | tempo_data |
| 12 | `otel-collector` | `otel/opentelemetry-collector-contrib:0.164.0` + config | full | — | telemetry | — |
| 13 | `alloy` | `grafana/alloy:v1.21.3` + config | full | — | telemetry | alloy_data + socket de Docker (solo lectura) |

Las imágenes con «+ config» se construyen *inline* desde `docker-compose.yml`, generado por `tools/generate_delivery.py`, con la configuración dentro de la imagen para poder ejecutarse con `read_only`. Varias copian un `busybox` estático solo para el healthcheck, porque sus imágenes base no traen shell ni `wget`.

## 1. frontend — gateway Nginx

- **Responsabilidad.**
  - Puerta de entrada web en `127.0.0.1:8080`.
  - Sirve directamente los recursos públicos `/css`, `/js` e `/img`, con compresión y caché de 10 minutos. Son los mismos archivos de `services/order-api/src/main/resources/static`, copiados en la imagen.
  - Reenvía **todo lo demás** (las 40 páginas HTML, `/api/**` y las URLs limpias) a `order-api`, con `Host`, `X-Forwarded-*` y `X-Request-Id`.
- **Por qué así (ADR 0011).** Spring Security sigue decidiendo sobre cada página protegida (302 al login, 403 por rol), las cookies de sesión y el CSRF. Hay un único origen: sin CORS y con la misma URL que antes. Ver [ADR 0011](adr/0011-nginx-gateway.md).
- **Otros detalles.**
  - Re-resuelve `order-api` cada 10 s, así que sobrevive a reinicios.
  - Logs de acceso en JSON, sin query string ni cabeceras.
  - `stub_status` solo en la red interna (puerto 8081), para el collector.
- **Verificación.**
  - `smoke.py` descarga **todos** los archivos estáticos por el puerto 8080 y los compara byte a byte con el repositorio.
  - `integration_smoke.py` comprueba la cabecera `Cache-Control` propia de Nginx, el reenvío de `/api` y el 302 de una página protegida conservando el puerto.
  - `pages-e2e.js` recorre las 39 páginas con cada rol.

## 2. order-api — núcleo hexagonal

Sin cambios funcionales en sus APIs. En la v5 añade adaptadores:

- caché Redis;
- publicador y consumidor RabbitMQ;
- SMTP;
- trazas OTLP;
- observación de puertos.

Es el **único** servicio que conoce PostgreSQL. Ver [`HEXAGONAL_ARCHITECTURE.md`](HEXAGONAL_ARCHITECTURE.md).

**Verificación:** 97 pruebas Java en el build de la imagen; `smoke.py`, `platform_smoke.py` e `integration_smoke.py`.

## 3. risk-engine — motor de riesgo FastAPI

Reglas deterministas de tres tramos: no es IA. En la v5 exporta trazas OTLP solo si existe el collector; continúa el contexto W3C de Java y añade el span `ScoreRisk.execute` sin datos del cliente.

**Verificación:** 62 pruebas Python en el build. La traza de un checkout en Tempo debe contener spans de `risk-engine`.

## 4. db — PostgreSQL 17

Fuente de verdad de pedidos, usuarios, catálogo, historial y outbox. Migraciones Flyway `V1`–`V5`, no destructivas.

**Verificación:** `integration_smoke.py persist-write` / `persist-read` reinicia `db` y `order-api` y comprueba que el pedido sigue ahí.

## 5. prometheus

Los 3 jobs originales sin cambios. La v5 añade `rabbitmq`, `otel-collector`, `nginx`, `loki`, `tempo` y `alloy`, que se descubren por DNS: en el modo básico no tienen targets, en lugar de aparecer caídos.

**Verificación:** `promtool check config`; `integration_smoke.py` exige los 9 jobs en UP en el modo completo.

## 6. grafana

- Datasource Prometheus original sin cambios.
- Loki y Tempo en un archivo de aprovisionamiento nuevo.
- 8 dashboards: los 7 anteriores más **Platform Integrations**.

**Verificación:** `smoke.py` carga los 8 dashboards; `integration_smoke.py` comprueba la salud de las fuentes `prometheus`, `loki` y `tempo`.

## 7. redis — caché del catálogo

- **Uso real.** Lecturas públicas y no sensibles del catálogo: categorías, listados por categoría y ficha de producto. TTL de 60 s.
- **Invalidación.** Se invalida por versión tras cualquier cambio de productos, categorías o stock, incluidos los pedidos y las cancelaciones.
- **No es fuente de verdad.** La idempotencia de pedidos sigue en PostgreSQL. Ver [`REDIS_INTEGRATION.md`](REDIS_INTEGRATION.md).
- **Configuración.** `maxmemory 48mb`, `allkeys-lru`. Solo en la red interna `cache`.
- **Verificación.** Aciertos en `hexaorders_cache_requests_total{result="hit"}`. Con Redis detenido, el catálogo responde desde PostgreSQL (`degraded`).

## 8. rabbitmq — eventos de negocio

- **Topología.** Exchange `hexaorders.events` (topic) → cola `hexaorders.notifications` → DLQ `hexaorders.notifications.dlq`.
- **Garantías.** Publicación con confirmaciones desde un outbox transaccional, consumidor idempotente y reintentos. Ver [`ASYNCHRONOUS_EVENTS.md`](ASYNCHRONOUS_EVENTS.md).
- **Configuración.**
  - Credenciales desde `secrets\rabbitmq_password.txt`, escritas en un tmpfs al arrancar.
  - Métricas por cola mediante el plugin `rabbitmq_prometheus`.
  - `hostname: rabbitmq`, para que el nodo y sus mensajes persistan al recrear el contenedor.
- **Verificación.** API de gestión: consumidor activo, mensajes confirmados, DLQ vacía y exchange durable. Con RabbitMQ detenido, los eventos esperan en el outbox y se envían al volver (`degraded` / `recovered`).

## 9. mailpit — correo de desarrollo

Servidor SMTP que **captura** los correos de notificación: no los envía a Internet. Es una herramienta de desarrollo, **no un proveedor de correo de producción**. Los destinatarios son las cuentas de demostración (`*@hexaorders.local`) y los clientes de prueba (`*@example.test`).

**Verificación:** `integration_smoke.py` registra un cliente, compra y busca sus correos en la API de Mailpit.

## 10. loki — logs centralizados

Almacenamiento local con esquema `tsdb v13` y retención de 7 días. Consulta en *Grafana → Explore → Loki* o en el panel de logs del dashboard Platform Integrations.

**Verificación:**

- hay líneas recientes de `order-api`, `risk-engine` y `frontend`;
- **ningún log contiene el valor de los cuatro secretos**: se consulta Loki buscando cada contraseña.

## 11. tempo — trazas distribuidas

Recibe OTLP solo del collector y guarda 72 h. Grafana enlaza trazas con logs (Tempo → Loki) y logs con trazas (Loki → Tempo, por el trace id).

**Verificación:** la cabecera `X-Trace-Id` de un checkout se busca en Tempo. Debe contener el caso de uso, la escritura en PostgreSQL, el puerto de riesgo y `ScoreRisk.execute` de Python.

## 12. otel-collector — telemetría

- Recibe OTLP por gRPC y HTTP de `order-api` y `risk-engine`.
- Aplica `memory_limiter`, borra atributos sensibles (cookies, `Authorization`, correo) y agrupa en lotes.
- Exporta las trazas a Tempo con reintentos y cola.
- Convierte el `stub_status` de Nginx en métricas Prometheus (`nginx_requests_total`, `nginx_connections_current`).
- No duplica las métricas que Prometheus ya recoge de las aplicaciones.

**Verificación:** `otelcol-contrib validate` en `verify`; `otelcol_exporter_sent_spans` mayor que 0; `nginx_requests_total` mayor que 0.

## 13. alloy — agente de logs (servicio adicional)

Lee los logs de los contenedores **de este proyecto** (etiqueta `com.hexaorders.logs=true`) mediante la API de Docker, con el socket en solo lectura. Elimina cualquier valor que siga a `password`, `token`, `cookie`, `authorization`… y los envía a Loki con las etiquetas `service`, `project`, `container` y `stream`. Docker Desktop para Windows expone `/var/run/docker.sock` a los contenedores Linux. Ver [ADR 0013](adr/0013-log-collection-with-alloy.md).

**Verificación:** los logs de los servicios aparecen en Loki (punto 10).
