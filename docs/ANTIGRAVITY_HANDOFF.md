# Handoff técnico — HexaOrders v6 (preparado para Google Antigravity)

Documento de traspaso para **cualquier herramienta o equipo que continúe el proyecto**: Google Antigravity para los retoques visuales finales, otro asistente de IA, Copilot o personas. Describe el estado real del repositorio, **qué se puede retocar libremente**, qué requiere pruebas de regresión y **qué no se debe romper**.

> ## ⚠️ Instrucciones obligatorias para quien modifique este repositorio
>
> 1. **Conserva todos los dashboards de Grafana (8), paneles, consultas PromQL, métricas, alertas, jobs de Prometheus y servicios de Docker Compose.** Solo se añade; nunca se borra ni se renombra. `tools/check_observability.py` falla si algo desaparece. Detalle: [`OBSERVABILITY_PRESERVATION.md`](OBSERVABILITY_PRESERVATION.md).
> 2. **Conserva los 13 servicios.**
>    - Modo básico: `frontend`, `order-api`, `risk-engine`, `db`, `prometheus`, `grafana`.
>    - Perfil `full`: `redis`, `rabbitmq`, `mailpit`, `loki`, `tempo`, `otel-collector`, `alloy`.
>    - Conserva también sus healthchecks, perfiles, límites, `read_only`, `cap_drop: ALL`, `no-new-privileges`, secretos en archivo, redes y volúmenes.
> 3. **No ejecutes `docker compose down -v`** como solución: borra PostgreSQL, Prometheus, Grafana, RabbitMQ, Loki y Tempo. Usa `.\hexaorders.cmd stop` (conserva datos).
> 4. **No edites a mano** `docker-compose.yml`, `k8s/10-monitoring-config.yaml`, los HTML generados ni `tools/ui-e2e/pages.json`: se generan con `tools/generate_delivery.py` y `tools/generate_pages.py`. El CI falla si no están al día.
> 5. **No modifiques migraciones Flyway publicadas** (`V1`–`V7`). Crea `V8__…sql` no destructiva. El catálogo de demostración se edita en `tools/catalog_v6_data.py`, y solo antes de publicar una migración nueva.
> 6. **No pongas secretos en Git.** Viven en `secrets/*.txt` (ignorado) y los crea `tools/init-env.ps1` sin sobrescribir nunca.
> 7. **No elimines pruebas, jobs del CI, scripts de Windows ni documentación útil.** Si algo falla, corrige la causa.
> 8. **Mantén la arquitectura hexagonal**: `domain/` y `application/` no importan Spring, JDBC, HTTP, Jackson, Micrometer, Redis, AMQP ni JavaMail (ArchUnit y `test_architecture.py`). Las tecnologías nuevas entran solo por adaptadores.
> 9. **Mantén la API original** `/api/orders` y el laboratorio (`/laboratorio.html` con `#inicio`, `#panel`, `#pedidos`, `#arquitectura`, `#observabilidad`). Desde la v6, el laboratorio y `/arquitectura/*` son **internos** (ADMIN y OBSERVABILITY_VIEWER, ADR 0014).
> 13. **La tienda es comercial**: no añadas a las páginas públicas ni a las del cliente explicaciones técnicas o académicas (arquitectura, Docker, bases de datos, mensajería, observabilidad). `check_frontend.py` y `pages-e2e.js` fallan si aparecen.
> 10. **No quites la protección de páginas** de `SecurityConfiguration` ni la sustituyas por ocultar enlaces.
> 11. **No publiques en el host** PostgreSQL, Redis ni el puerto AMQP de RabbitMQ. No cambies Mailpit por un SMTP real.
> 12. Trabaja en una rama, abre un Pull Request y espera la aprobación del propietario antes de fusionar con `main`. Sin force push.

## 1. Mapa de lo que se puede tocar

| Zona | Retoque visual libre | Requiere pruebas de regresión | No tocar sin acuerdo |
| --- | --- | --- | --- |
| `static/css/site.css`, `platform.css`, `components.css`, `effects.css` | ✅ colores, espaciados, sombras, tipografía, animaciones (respetando `prefers-reduced-motion`) | Cambios de layout (`.page-shell`, `.side-nav`, `.topbar`): ejecutar `pages-e2e.js` (móvil sin desbordamiento) | — |
| `static/css/base.css` (tokens `:root`) | ✅ valores de los tokens | Contraste WCAG AA de texto sobre fondo | Nombres de los tokens (los usan todas las hojas) |
| Textos de las vistas (`js/views/*.js`) | ✅ redacción, títulos, ayudas | Si cambias textos que buscan las pruebas (p. ej. «Confirmar pedido», «Acceso restringido», «Mensajes de la página de contacto»), actualiza `ui-e2e.js`/`pages-e2e.js` | — |
| Estructura DOM de las vistas | Añadir envoltorios o clases | `pages-e2e.js` y `ui-e2e.js` usan selectores como `#view-root form`, `.product-card`, `.cal-month .cal-day`, `.tracker li`, `.pager__info`, `.notice-box--ok` | IDs del laboratorio (`#splash`, `#stat-total`, `#orders-body`, `#arch-diagram`, `#run-request`…) |
| Layout común (`js/layout.js`) | ✅ orden y estilo de menús, pie, iconos | Menú móvil (`.nav-toggle`, `#primary-nav`) y breadcrumbs | Que los enlaces dependan de la sesión real |
| Plantilla HTML (`TEMPLATE` en `tools/generate_pages.py`) | ✅ metadatos, fuentes locales, estructura del `<head>` | Regenerar y pasar `check_frontend.py` | Sin `<script>` ni `style=""` en línea (CSP) |
| Imágenes | ✅ `static/img/` (SVG/PNG propios) | Añadir la extensión a `EXPECTED_TYPES` en `tools/smoke.py` si es nueva | Recursos de terceros sin licencia |
| Lógica de vistas (llamadas a la API, reglas mostradas) | — | Toda la batería | Reglas de negocio en el navegador: van en el dominio |
| Configuración de Nginx (`infra/frontend/nginx.conf`) | — | `smoke.py` (estáticos byte a byte) y `integration_smoke.py` | Reenvío de páginas a Spring (ADR 0011), CSP y cabeceras |
| Backend, seguridad, migraciones, Compose, Prometheus, Grafana, RabbitMQ, Redis, Loki, Tempo, collector, Alloy | — | — | Ver instrucciones obligatorias |

## 2. Arquitectura

Resumen v5: navegador → `frontend` (Nginx: sirve `/css`, `/js`, `/img` y reenvía el resto) → `order-api` (Spring Boot, hexagonal) → PostgreSQL y motor de riesgo FastAPI. En modo completo, además:

- Redis como caché del catálogo;
- RabbitMQ con outbox, consumidor idempotente y DLQ;
- Mailpit para los correos;
- trazas OTLP → collector → Tempo;
- logs → Alloy → Loki;
- Prometheus y Grafana sobre todo lo anterior.

Ver [`DOCKER_ARCHITECTURE.md`](DOCKER_ARCHITECTURE.md) y [`DOCKER_SERVICES.md`](DOCKER_SERVICES.md). Diagramas completos (hexágono, componentes, secuencia, integración Java ↔ FastAPI, despliegue Docker y modelo de datos): [`HEXAGONAL_ARCHITECTURE.md`](HEXAGONAL_ARCHITECTURE.md). Decisiones: [`adr/`](adr/).

## 3. Estructura relevante

```text
services/order-api/src/main/java/com/hexaorders/
  domain/                 reglas puras (pedido, catálogo, ciclo de vida, logística, contacto, monitoreo…)
  application/port/in     casos de uso que ofrece el núcleo
  application/port/out    lo que el núcleo necesita (repositorios, riesgo, telemetría, monitoreo…)
  application/service     implementación de los casos de uso
  adapter/in/web          controladores REST y seguridad
  adapter/out/...         persistence, http, monitoring, metrics, security, health
  config/                 SecurityConfiguration, PlatformConfiguration, WebPagesConfiguration, PageErrorConfiguration
services/order-api/src/main/resources/
  db/migration/           V1 … V4
  static/
    index.html, catalogo.html, …            páginas públicas (generadas)
    cliente/ admin/ operaciones/ logistica/ monitoreo/ arquitectura/   portales (generados)
    laboratorio.html                        laboratorio original (no generado)
    css/  base · components · effects · platform · site
    js/   page.js (entrada) · layout.js · router.js · api.js · components.js · session.js · cart.js · ui.js
    js/views/  home · shop · customer · admin · operations · logistics · ops · architecture-pages
    js/   app.js, architecture.js, dashboard.js, orders.js, observability.js, grid-effect.js (laboratorio)
tools/
  generate_pages.py       registro de páginas → HTML + tools/ui-e2e/pages.json
  page_fragments/         HTML estático incrustado (explorador de arquitectura)
  check_frontend.py       comprobaciones estáticas del sitio
  ui-e2e/                 ui-e2e.js (laboratorio y flujos v3) · pages-e2e.js (todas las páginas y roles)
```

## 4. Sistema de diseño

Tema oscuro con tokens en `static/css/base.css` (no introducir colores sueltos):

| Token | Valor | Uso |
| --- | --- | --- |
| `--bg`, `--bg-2`, `--bg-3` | `#0B0F14`, `#151B23`, `#1C2430` | Fondo, tarjetas, elementos elevados |
| `--violet`, `--violet-2`, `--violet-soft` | `#8B5CF6`, `#A78BFA`, 14 % | Acento principal, enlaces, foco |
| `--blue` (site.css), `--blue-2`, `--blue-soft` | `#3B82F6` … | Acento secundario, estados de despacho |
| `--green` / `--amber` / `--red` (+ `-soft`) | `#10B981` / `#F59E0B` / `#EF4444` | Éxito, revisión/aviso, error |
| `--text`, `--text-2` | `#F8FAFC`, `#94A3B8` | Texto principal y secundario |
| `--radius`, `--radius-sm` | 14 px, 9 px | Bordes |
| `--font`, `--mono` | Inter / system-ui; Cascadia/JetBrains Mono | Tipografía |
| `--sidebar-w`, `--content-max` (site.css) | 248 px, 1320 px | Layout de los portales |

**CSP estricta**: `script-src 'self'; style-src 'self'`. Nada de `style=""`, `<style>` ni `<script>` en línea; `check_frontend.py` lo comprueba. Para estilos dinámicos usa `element.style.x = …` desde JS (permitido) o clases.

## 5. Componentes reutilizables

- `js/components.js`: `lifecycleBadge`, `channelBadge`, `progressBar`, `icon` (iconos SVG propios), `productArt`, `stockNote`, `emptyState`, `skeletons`, `errorMessage`, `modal`, `field`, `formError`, `submitting`, `barChart`, `downloadCsv`, **`paginated`**, **`searchBox`**, **`kpi`**.
- `js/layout.js`: `MAIN_NAV`, `AREAS` (portales por rol), `SECTIONS` (barras laterales), `renderLayout()`.
- `js/router.js`: `route()`, `link()` (ruta lógica → página), `navigate()`, `rerender()`, `startPage()`.
- `js/ui.js`: `el`, `$`, `$$`, `formatMoney`, `formatDateTime`, `formatRelative`, `toast`.

## 6. Páginas, rutas y APIs

Tabla completa (39 páginas + laboratorio, rol, contenido y API de cada una), URLs limpias, compatibilidad con los hashes de la v3 y diagrama de navegación: [`FRONTEND_PAGES.md`](FRONTEND_PAGES.md). Endpoints REST: [README](../README.md#-endpoints).

**Añadir una página**: registra la vista con `route('/mi/ruta', render, { roles, title })` en `js/views/…`, añade la entrada en `PAGES` de `tools/generate_pages.py`, la regla en `SecurityConfiguration` si es protegida y el enlace en `SECTIONS` de `layout.js`; ejecuta `py tools\generate_pages.py` y `py tools\check_frontend.py`.

## 7. Roles

`CUSTOMER`, `ADMIN`, `ORDER_OPERATOR`, `LOGISTICS_OPERATOR`, `OBSERVABILITY_VIEWER`. Triple control: reglas de URL para API **y páginas** en `config/SecurityConfiguration.java`, `Guards` en los casos de uso, y navegación según la sesión. La matriz de transiciones está en `domain/fulfillment/TransitionPolicy.java` (la copia de `architecture-pages.js` se verifica en `check_frontend.py`). Cuentas demo: `admin`, `operador`, `logistica`, `logistica2`, `observador`, `cliente` con la contraseña de `secrets/demo_password.txt`.

## 8. Estado actual

| Área | Estado |
| --- | --- |
| Backend (roles, portal, ciclo de vida, logística, incidencias, auditoría, contacto, monitoreo) | Implementado; 97 pruebas Java en verde en local |
| Integraciones v5 (caché, eventos, correos, trazas, logs) | Implementado. Probado en local sin Docker con binarios reales (ver [`validacion.md`](validacion.md)) y en el CI con Docker (`compose` y `compose-basic`) |
| Sitio multipágina (39 páginas + laboratorio) | Implementado; `pages-e2e.js` en verde en local (244 comprobaciones, sin Prometheus) y en CI con el stack Docker |
| Observabilidad (8 dashboards, 9 alertas, regla de compatibilidad, Loki y Tempo) | `check_observability.py` en verde; Grafana real en `smoke.py` (CI) |
| Docker Compose | Generado, validado con el esquema oficial y ejecutado en el job `compose` del CI |
| Windows | `hexaorders.cmd`/`hexaorders.ps1` probados en el job `windows` (PowerShell 5.1); **sin prueba manual en Docker Desktop** ([guía](WINDOWS_DOCKER_GUIDE.md)) |
| Kubernetes | Manifiestos actualizados y validados; **no desplegados** |

## 9. Dependencias

- Java 21, Spring Boot 3.5.7 (`web`, `jdbc`, `validation`, `actuator`, `security`, `data-redis`, `amqp`, `mail`), Micrometer Tracing con OpenTelemetry y exportador OTLP, Flyway, PostgreSQL JDBC, Micrometer Prometheus; JUnit 5, Mockito, ArchUnit, `spring-security-test`.
- Python 3.12, FastAPI, Uvicorn, `prometheus_client`, OpenTelemetry SDK e instrumentación FastAPI; pytest.
- Frontend: **ninguna** dependencia.
- Herramientas: Python (`requirements-tools.txt`), Node 22 + Playwright solo para `tools/ui-e2e`.

## 10. Docker e integraciones

- `infra/compose.base.yml` + `services/` + `infra/` → `tools/generate_delivery.py` → `docker-compose.yml` autosuficiente (un `COPY` con heredocs por directorio).
- Puertos solo en `127.0.0.1`: 8080 (Nginx: sitio + API), 9091 (Prometheus), 3000 (Grafana), 15672 (consola de RabbitMQ) y 8025 (Mailpit). Entre contenedores se usan los nombres de servicio.
- Modos: `.\hexaorders.cmd start` (completo, por defecto) o `start -Mode basic`. Los servicios del perfil `full` son opcionales: order-api los detecta con `HEXAORDERS_INTEGRATIONS=auto`.
- Java → Redis: `RedisCatalogCache` ([`REDIS_INTEGRATION.md`](REDIS_INTEGRATION.md)).
- Java → RabbitMQ → SMTP: `RabbitMqEventPublisher`, `BusinessEventListener` y `SmtpMailSender` ([`ASYNCHRONOUS_EVENTS.md`](ASYNCHRONOUS_EVENTS.md)).
- Trazas y logs: [`OBSERVABILITY_STACK.md`](OBSERVABILITY_STACK.md).
- Java → Python: `HttpRiskAssessmentAdapter` (`POST risk-engine:8000/score`, timeouts, 503 si falla).
- Java → PostgreSQL: `JdbcTemplate` + `TransactionTemplate`, bloqueo optimista, stock condicional, `idempotency_key` única.
- Java → Prometheus (v4): `PrometheusMonitoringAdapter` (`PROMETHEUS_URL=http://prometheus:9090`).

## 11. Grafana y Prometheus

- Prometheus: `prometheus.yml` (3 jobs originales + 6 por DNS en la v5, 10 s) y `alerts.yml` (grupo original + `hexaorders-compat` + `hexaorders-platform`).
- Grafana: datasources `prometheus`, `loki` y `tempo`, y 8 dashboards (`hexaorders` original + `business`, `risk`, `api`, `infrastructure`, `security`, `logistics`, `integrations`).
- Línea base protegida: `infra/observability-baseline.json`.

## 12. No modificar sin acuerdo explícito

- `infra/grafana/dashboards/hexaorders.json`, los grupos existentes de `alerts.yml`, `prometheus.yml`, `provisioning/`, `infra/observability-baseline.json`.
- Migraciones `V1`–`V5`; contrato de `/api/orders`, de `risk-engine /score` y de los eventos (`schemaVersion` 1).
- `domain/` y sus invariantes (umbral 70, transiciones, validaciones).
- `SecurityConfiguration` (reglas de API y de páginas, CSP, CSRF) y `WebPagesConfiguration`.
- Endurecimiento de Compose y los jobs `compose` y `windows` del CI (se pueden **añadir** pasos).
- IDs y estructura del laboratorio (`laboratorio.html`) que usan `app.js` y `ui-e2e.js`.

## 13. Pendientes visuales sugeridos

- Ilustraciones o fotografías propias de producto (hoy: arte generado por tono, sin recursos de terceros).
- Arrastrar y soltar en el calendario logístico (hoy: formulario de reprogramación).
- Modo claro (el diseño es oscuro) y revisión fina de contraste en estados `muted`.
- Gráficos más ricos en reportes (hoy: barras propias en SVG/CSS).
- Transiciones entre páginas más elaboradas (hoy: aparición suave de 0,32 s, desactivada con movimiento reducido).

## 14. Pruebas

| Comando | Qué ejecuta |
| --- | --- |
| `.\hexaorders.cmd test` | pytest (motor de riesgo) y Maven (dominio, servicios, adaptadores, seguridad, ArchUnit) |
| `.\hexaorders.cmd check` | Entregable al día, validación Compose/Kubernetes, `check_observability.py`, `generate_pages.py --check`, `check_frontend.py` |
| `.\hexaorders.cmd verify` | Stack real con Docker (modo completo): build con tests, promtool, `otelcol validate`, `smoke.py`, `platform_smoke.py`, `integration_smoke.py` (check, degradación, recuperación y persistencia), y caída y recuperación del motor de riesgo |
| `.\hexaorders.cmd verify -Mode basic` | Lo mismo en el modo básico, sin integraciones |
| `.\hexaorders.cmd smoke` | Solo las pruebas de humo contra un stack ya levantado |
| `node tools/ui-e2e/ui-e2e.js` | Laboratorio y flujos por rol (necesita `DEMO_PASSWORD`) |
| `node tools/ui-e2e/pages-e2e.js` | Todas las páginas con todos los roles, enlaces, recarga, formularios, monitoreo y móvil (necesita `DEMO_PASSWORD`) |

## 15. Problemas conocidos

- El cliente Prometheus 1.x exporta `orders_created` como `orders_total`; la regla `orders_created_total` lo compensa (no la elimines).
- `/api/orders` sigue sin autenticación por compatibilidad.
- Paginación en el navegador sobre listas ya limitadas por el servidor (200 elementos).
- Sin cAdvisor (no hay métricas de contenedor) ni límite de intentos de inicio de sesión.
- Grafana no envía CORS: el laboratorio solo comprueba que responde; el portal de monitoreo consulta Prometheus desde el backend.
- Kubernetes no desplegado y limitado al núcleo: no incluye los servicios v5. Docker Desktop en Windows sin prueba manual.
- Entrega de eventos **al menos una vez**: un correo puede repetirse si el proceso cae justo tras enviarlo.
- Alloy necesita el socket de Docker en solo lectura (ADR 0013).
- El navegador no genera trazas: empiezan en el servidor.

## 16. Versión 6: tienda tecnológica (qué retocar y qué no)

- **Retoque visual libre:** `static/css/store.css` (cabecera, megamenú, tarjetas, filtros, ficha, carrito, checkout, ayuda, pie), los textos comerciales de `views/home.js` y `views/shop.js`, y los iconos de categoría (`components.js`, `MAIN_CATEGORY_ICONS`).
- **Sin imágenes ni videos en la v6:** las tarjetas usan un degradado y el icono de la categoría. Si una versión futura añade imágenes, que sea con un campo nuevo del producto, nunca dentro de V7.
- **Selectores que usan las pruebas** (si los cambias, actualiza `pages-e2e.js` y `ui-e2e.js`):
  - catálogo y tarjetas: `.product-card`, `.product-card__brand`, `.pager__info`;
  - filtros: `.filters .filter-option`, `.filter-group summary`;
  - ficha: `.product-detail .btn--primary` («Comprar ahora»), `.spec-table`;
  - checkout: `.choice input[value=…]`, `.summary-row--total`, «Confirmar pedido»;
  - confirmación: `.order-thanks`;
  - cabecera: `#cart-count`.
- **No mover al navegador** reglas de precio, descuentos, envío o stock: viven en el dominio (ADR 0015).
- **Documentación técnica:** [`TECH_STORE.md`](TECH_STORE.md).
