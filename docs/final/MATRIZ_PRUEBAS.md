# Matriz de pruebas

«Dónde se ejecuta» indica dónde se ha ejecutado **de verdad** en este trabajo: **Local** (contenedor Linux
sin Docker funcional, con Java 21, Maven sin red, Python y Nginx), **CI** (GitHub Actions, Ubuntu con Docker
real y Windows con PowerShell 5.1) o **Pendiente** (requiere tu equipo, AWS o credenciales). Resultados
concretos: [`RESULTADOS_VALIDACION.md`](RESULTADOS_VALIDACION.md).

| # | Área | Prueba | Herramienta / archivo | Comando | Dónde se ejecuta |
| --- | --- | --- | --- | --- | --- |
| 1 | Compose | Entregable al día y válido (13 servicios, perfiles, `read_only`, secretos como archivos) | `tools/generate_delivery.py --check`, `tools/validate_delivery.py` | `.\polymer.cmd check` | Local, CI (Linux y Windows) |
| 2 | Compose | `docker compose config` del archivo real | `polymer.ps1 verify` paso 3 | `.\polymer.cmd verify` | CI |
| 3 | Compilación | Imágenes de los 13 servicios (los Dockerfiles ejecutan las pruebas) | Dockerfiles | `.\polymer.cmd rebuild` | CI |
| 4 | Unitarias Java | Dominio, casos de uso, adaptadores, seguridad (131) | JUnit 5 + Mockito | `mvn test` | Local, CI (build y Windows) |
| 5 | Unitarias Python | Motor de riesgo (62) | pytest | `python -m pytest` | Local, CI |
| 6 | Arquitectura | 8 reglas ArchUnit + arquitectura Python | `HexagonalArchitectureTest`, `test_architecture.py` | `.\polymer.cmd demo -Scenario arquitectura` | Local, CI |
| 7 | Arquitectura | Sustitución de adaptador (4) | `AdapterSubstitutionTest` | idem | Local, CI |
| 8 | Arquitectura | Detección de una violación (mutación temporal en el dominio) | ArchUnit | manual (ver `DEMOSTRACION_HEXAGONAL.md`) | Local |
| 9 | Integración | Adaptadores JDBC, HTTP, Prometheus | `InfrastructureAdaptersTest`, `HttpRiskAssessmentAdapterTest`, `PrometheusMonitoringAdapterTest` | `mvn test` | Local, CI |
| 10 | Healthchecks | 13 contenedores *healthy* | `docker compose up --wait` | `.\polymer.cmd health` | CI (`compose` y `migrate-from-v6`) |
| 11 | Frontend | 45 páginas × 6 perfiles, enlaces, recarga, móvil | `tools/ui-e2e/pages-e2e.js` (Playwright) | `node pages-e2e.js` | Local (sin Prometheus), CI |
| 12 | Frontend | UI, intro, idiomas, contraseñas | `ui-e2e.js`, `intro-e2e.js`, `i18n-e2e.js` | `node …` | Local, CI |
| 13 | Autenticación | Login, CSRF, roles, 401/403 | `SecurityRulesTest`, `platform_smoke.py`, `pages-e2e.js` | `.\polymer.cmd smoke` | Local, CI |
| 14 | Pedidos | Checkout, idempotencia, stock, cancelación | `store_smoke.py`, `CommerceServiceTest` | `.\polymer.cmd smoke` | Local, CI |
| 15 | Persistencia | Reinicio de `db` y `order-api` conserva el pedido | `polymer.ps1 verify` paso 8; escenario `postgres` | `.\polymer.cmd demo -Scenario postgres` | CI |
| 16 | Persistencia | Migración real v6 → POLYMER con datos (recuentos idénticos) | job `migrate-from-v6` | — | CI |
| 17 | Redis | Aciertos, miss/hit/TTL/expiración, claves reales | `integration_smoke.py`; escenario `redis` | `.\polymer.cmd demo -Scenario redis` | CI |
| 18 | Redis | Degradación y recuperación | `integration_smoke.py degraded/recovered`; escenario `redis-caida` | `.\polymer.cmd demo -Scenario redis-caida` | CI |
| 19 | RabbitMQ | Outbox → cola → consumidor → Mailpit; topología durable; DLQ vacía | `integration_smoke.py`; escenario `rabbitmq` | `.\polymer.cmd demo -Scenario rabbitmq` | CI |
| 20 | RabbitMQ | Caída: pedido guardado, evento pendiente, publicación al volver | `integration_smoke.py`; escenario `rabbitmq-caida` | `.\polymer.cmd demo -Scenario rabbitmq-caida` | CI |
| 21 | Microservicios | Java → Python (10/APPROVED, 75/REVIEW), logs de `/score` | escenario `riesgo` | `.\polymer.cmd demo -Scenario riesgo` | CI |
| 22 | Resiliencia | `risk-engine` caído → 503 sin guardar pedido | `polymer.ps1 verify` paso 9; escenario `riesgo-caida` | `.\polymer.cmd demo -Scenario riesgo-caida` | CI |
| 23 | Inversión de dependencias | Adaptador fijo con `risk-engine` detenido | escenario `adaptador` | `.\polymer.cmd demo-on -FixedRisk` + `demo -Scenario adaptador` | CI |
| 24 | Trazas | Una traza con spans de `order-api` y `risk-engine` en Tempo | `integration_smoke.py` | — | CI |
| 25 | Prometheus | *Targets* UP, métricas de negocio, Nginx y collector | `integration_smoke.py`; escenario `observabilidad` | `.\polymer.cmd demo -Scenario observabilidad` | CI |
| 26 | Grafana | Fuentes de datos OK y 8 dashboards aprovisionados | `integration_smoke.py`; `check_observability.py` | — | CI, Local (estático) |
| 27 | Loki | Logs de los servicios y **ningún** valor de secreto en los logs | `integration_smoke.py` | — | CI |
| 28 | Contraseñas | `change-password` de PostgreSQL, Grafana y RabbitMQ | job `migrate-from-v6` | `.\polymer.cmd change-password -Service …` | CI |
| 29 | Secretos | Generación aleatoria y memorable (nunca impresa), sin salto de línea | job `windows` | `.\polymer.cmd secrets` | CI (Windows) |
| 30 | Gateway HTTPS | `X-Forwarded-Proto` conservado tras un proxy TLS | `infra/frontend/nginx.conf` con Nginx 1.24 y servidor de eco | manual | Local |
| 31 | AWS | Plantilla del Nginx del host: 301, TLS, HSTS, 403 por IP | `infra/aws/nginx-host-polymer.conf` con certificados de prueba | `nginx -t` | Local |
| 32 | AWS | Despliegue real en EC2 con dominio y Let's Encrypt | `DESPLIEGUE_AWS_HTTPS.md` | — | **Pendiente** (requiere aprobación, cuenta y dominio) |
| 33 | Windows | Docker Desktop en un equipo Windows real | `GUIA_DOCKER_WINDOWS.md` | `.\polymer.cmd start` | **Pendiente** (el CI de Windows no ejecuta contenedores Linux) |
| 34 | Secretos | Ausencia de secretos en archivos rastreados y en el historial | `git ls-files`, `git log --all -p` | ver `GUIA_GITHUB.md` | Local |
