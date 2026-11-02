# Handoff técnico — HexaOrders v4 (preparado para Google Antigravity)

Documento de traspaso para **cualquier herramienta o equipo que continúe el proyecto**: Google Antigravity para los retoques visuales finales, otro asistente de IA, Copilot o personas. Describe el estado real del repositorio, **qué se puede retocar libremente**, qué requiere pruebas de regresión y **qué no se debe romper**.

> ## ⚠️ Instrucciones obligatorias para quien modifique este repositorio
>
> 1. **Conserva todos los dashboards de Grafana (7), paneles, consultas PromQL, métricas, alertas, jobs de Prometheus y servicios de Docker Compose.** Solo se añade; nunca se borra ni se renombra. `tools/check_observability.py` falla si algo desaparece. Detalle: [`OBSERVABILITY_PRESERVATION.md`](OBSERVABILITY_PRESERVATION.md).
> 2. **Conserva los cinco servicios** (`db`, `risk-engine`, `order-api`, `prometheus`, `grafana`), sus healthchecks, `read_only`, `cap_drop: ALL`, `no-new-privileges`, secretos en archivo, redes y volúmenes.
> 3. **No ejecutes `docker compose down -v`** como solución: borra PostgreSQL, Prometheus y Grafana. Usa `.\hexaorders.cmd stop` (conserva datos).
> 4. **No edites a mano** `docker-compose.yml`, `k8s/10-monitoring-config.yaml`, los HTML generados ni `tools/ui-e2e/pages.json`: se generan con `tools/generate_delivery.py` y `tools/generate_pages.py`. El CI falla si no están al día.
> 5. **No modifiques migraciones Flyway publicadas** (`V1`–`V4`). Crea `V5__…sql` no destructiva.
> 6. **No pongas secretos en Git.** Viven en `secrets/*.txt` (ignorado) y los crea `tools/init-env.ps1` sin sobrescribir nunca.
> 7. **No elimines pruebas, jobs del CI, scripts de Windows ni documentación útil.** Si algo falla, corrige la causa.
> 8. **Mantén la arquitectura hexagonal**: `domain/` y `application/` no importan Spring, JDBC, HTTP, Jackson ni Micrometer (ArchUnit y `test_architecture.py`).
> 9. **Mantén la API original** `/api/orders` y el laboratorio (`/laboratorio.html` con `#inicio`, `#panel`, `#pedidos`, `#arquitectura`, `#observabilidad`).
> 10. **No quites la protección de páginas** de `SecurityConfiguration` ni la sustituyas por ocultar enlaces.
> 11. Trabaja en una rama, abre un Pull Request y espera la aprobación del propietario antes de fusionar con `main`. Sin force push.

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
| Backend, seguridad, migraciones, Compose, Prometheus, Grafana | — | — | Ver instrucciones obligatorias |

## 2. Arquitectura

Resumen: navegador → `order-api` (Spring Boot, hexagonal) → PostgreSQL, motor de riesgo FastAPI y Prometheus; Grafana lee de Prometheus. Diagramas completos (hexágono, componentes, secuencia, integración Java ↔ FastAPI, despliegue Docker y modelo de datos): [`HEXAGONAL_ARCHITECTURE.md`](HEXAGONAL_ARCHITECTURE.md). Decisiones: [`adr/`](adr/).

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
| Backend (roles, portal, ciclo de vida, logística, incidencias, auditoría, contacto, monitoreo) | Implementado; 81 pruebas Java en verde en local |
| Sitio multipágina (39 páginas + laboratorio) | Implementado; `pages-e2e.js` en verde en local (244 comprobaciones, sin Prometheus) y en CI con el stack Docker |
| Observabilidad (7 dashboards, 9 alertas, regla de compatibilidad) | `check_observability.py` en verde; Grafana real en `smoke.py` (CI) |
| Docker Compose | Generado, validado con el esquema oficial y ejecutado en el job `compose` del CI |
| Windows | `hexaorders.cmd`/`hexaorders.ps1` probados en el job `windows` (PowerShell 5.1); **sin prueba manual en Docker Desktop** ([guía](WINDOWS_DOCKER_GUIDE.md)) |
| Kubernetes | Manifiestos actualizados y validados; **no desplegados** |

## 9. Dependencias

- Java 21, Spring Boot 3.5.7 (`web`, `jdbc`, `validation`, `actuator`, `security`), Flyway, PostgreSQL JDBC, Micrometer Prometheus; JUnit 5, Mockito, ArchUnit, `spring-security-test`.
- Python 3.12, FastAPI, Uvicorn, `prometheus_client`; pytest.
- Frontend: **ninguna** dependencia.
- Herramientas: Python (`requirements-tools.txt`), Node 22 + Playwright solo para `tools/ui-e2e`.

## 10. Docker e integraciones

- `infra/compose.base.yml` + `services/` + `infra/` → `tools/generate_delivery.py` → `docker-compose.yml` autosuficiente (un `COPY` con heredocs por directorio).
- Puertos solo en `127.0.0.1`: 8080 (sitio + API), 9091 (Prometheus), 3000 (Grafana). Entre contenedores, nombres de servicio: `db`, `risk-engine`, `prometheus`.
- Java → Python: `HttpRiskAssessmentAdapter` (`POST risk-engine:8000/score`, timeouts, 503 si falla).
- Java → PostgreSQL: `JdbcTemplate` + `TransactionTemplate`, bloqueo optimista, stock condicional, `idempotency_key` única.
- Java → Prometheus (v4): `PrometheusMonitoringAdapter` (`PROMETHEUS_URL=http://prometheus:9090`).

## 11. Grafana y Prometheus

- Prometheus: `prometheus.yml` (3 jobs, 10 s) y `alerts.yml` (grupo original + `hexaorders-compat` + `hexaorders-platform`).
- Grafana: datasource `prometheus` y 7 dashboards (`hexaorders` original + `business`, `risk`, `api`, `infrastructure`, `security`, `logistics`).
- Línea base protegida: `infra/observability-baseline.json`.

## 12. No modificar sin acuerdo explícito

- `infra/grafana/dashboards/hexaorders.json`, los grupos existentes de `alerts.yml`, `prometheus.yml`, `provisioning/`, `infra/observability-baseline.json`.
- Migraciones `V1`–`V4`; contrato de `/api/orders` y de `risk-engine /score`.
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
| `.\hexaorders.cmd verify` | Stack real con Docker: build con tests, promtool, `smoke.py`, `platform_smoke.py`, caída y recuperación del motor de riesgo |
| `node tools/ui-e2e/ui-e2e.js` | Laboratorio y flujos por rol (necesita `DEMO_PASSWORD`) |
| `node tools/ui-e2e/pages-e2e.js` | Todas las páginas con todos los roles, enlaces, recarga, formularios, monitoreo y móvil (necesita `DEMO_PASSWORD`) |

## 15. Problemas conocidos

- El cliente Prometheus 1.x exporta `orders_created` como `orders_total`; la regla `orders_created_total` lo compensa (no la elimines).
- `/api/orders` sigue sin autenticación por compatibilidad.
- Paginación en el navegador sobre listas ya limitadas por el servidor (200 elementos).
- Sin cAdvisor (no hay métricas de contenedor) ni límite de intentos de inicio de sesión.
- Grafana no envía CORS: el laboratorio solo comprueba que responde; el portal de monitoreo consulta Prometheus desde el backend.
- Kubernetes no desplegado; Docker Desktop en Windows sin prueba manual.
