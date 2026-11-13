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
| 4 | Unitarias Java | Dominio, casos de uso, adaptadores, seguridad (163) | JUnit 5 + Mockito | `mvn test` | Local, CI (build y Windows) |
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
| 35 | v7 · Dominio | Código de 6 dígitos (vigencia, intentos, reenvío), identidad de Google, tarjeta (Luhn, marca, vencimiento, CVV), Yape/Plin (60 s, límite), boleta (base + IGV = total), horario y feriados (Semana Santa) | `SignInPaymentStoreDomainTest` | `mvn test` | Local, CI |
| 36 | v7 · Casos de uso | Inicio de sesión en dos pasos, Google (crear/vincular), dispositivo de confianza, sesión recordada con rotación y detección de robo, sin servidor de correo | `SignInServiceTest` | `mvn test` | Local, CI |
| 37 | v7 · Casos de uso | Tarjeta aprobada/rechazada, 5 rechazos, Yape que vence a los 60 s (reloj controlado), anulación y reemplazo, propiedad del pedido, correo de la boleta en el idioma del cliente, horario de hoy en Lima | `PaymentsAndReceiptsTest` | `mvn test` | Local, CI |
| 38 | v7 · Web | 202 hasta el código; sesión y cookies de 30 días tras el código; sesión recordada y ficha robada; `/api/store` público; pago solo para clientes; CSP con `frame-src` de Google | `SignInWebTest` | `mvn test` | Local, CI |
| 39 | v7 · API | Tienda (mapa, horario, Google Calendar), tarjeta (402 rechazada, Luhn 400, aprobada, 409 doble cobro), boleta, Yape (QR, 60 s, 402) y Plin aprobado | `store_smoke.py` | `.\polymer.cmd smoke` | CI (`compose` y `compose-basic`) |
| 40 | v7 · Integración | Código de verificación por correo; boleta por correo (outbox → RabbitMQ → consumidor → SMTP) con IGV; métrica `polymer_payments_total` en Prometheus | `integration_smoke.py` | — | CI |
| 41 | v7 · Navegador | Video de portada (Range, pausa), mapa, horario de hoy, calendario, Google Calendar; Continuar con Google (proveedor OIDC de pruebas) + código + «Recordarme» (cookies 30 días, sesión recuperada, dispositivo de confianza); tarjeta rechazada/aprobada; boleta; Yape que vence a los 60 s y QR nuevo aprobado; registro con código; móvil | `v7-e2e.js` + `infra/ci/compose.google-mock.yml` | `node v7-e2e.js` | Local (34 comprobaciones, sin RabbitMQ); CI [43384929823](https://github.com/rgabrielmatosm/Polymer-Tech-Proyecto-AED-II/actions/runs/43384929823): 34 comprobaciones ✅ |
| 42 | v7 · Exposición | TTL de la ventana de Yape en Redis (60 → -2), 409 al vencer, QR nuevo pagado, tabla `payments`, tarjeta rechazada/aprobada, correo de la boleta | `polymer-demo.ps1 -Scenario pagos` | `.\polymer.cmd demo -Scenario pagos` | CI [43384929823](https://github.com/rgabrielmatosm/Polymer-Tech-Proyecto-AED-II/actions/runs/43384929823): 9 evidencias ✅ |
| 43 | v7 · Pendiente | Inicio de sesión con los servidores reales de Google y envío a un Gmail real | `GUIA_GOOGLE_Y_PAGOS.md` | — | **Pendiente** (requiere tus credenciales) |

