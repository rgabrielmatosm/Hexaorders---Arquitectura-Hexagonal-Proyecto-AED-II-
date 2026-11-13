# Resultados de validación

Solo se anota lo **ejecutado y observado**. Lo que no se pudo ejecutar figura en la sección 4 con su
comando exacto. Matriz completa: [`MATRIZ_PRUEBAS.md`](MATRIZ_PRUEBAS.md).

## 1. Local (12/11/2026)

Entorno: contenedor Linux con Java 21, Maven (sin red: dependencias en caché), Python 3 con las
dependencias del motor de riesgo y Nginx 1.24. Docker no puede construir imágenes aquí (sin red), por eso
el stack completo se valida en el CI.

| Prueba | Resultado observado |
| --- | --- |
| `mvn package` (order-api) | **131 pruebas, 0 fallos, 0 errores** (122 anteriores + 4 reglas ArchUnit nuevas + 4 de `AdapterSubstitutionTest` + 1 de regresión de la caché) |
| `HexagonalArchitectureTest` | 8 reglas en verde |
| Mutación: campo `JdbcTemplate` en `domain/OrderStatus.java` | **Detectada**: `CORE_HAS_NO_FRAMEWORK_OR_INFRASTRUCTURE` falla con `Field <com.polymer.domain.OrderStatus.DEMO> has type <org.springframework.jdbc.core.JdbcTemplate>`; archivo restaurado |
| Cambio del umbral Java 70 → 80 (análisis) | Fallan 3 pruebas (`OrderDomainTest.riskThresholdIsOwnedByDomain`, `OrderServiceTest.assessesRiskBeforePersistenceAndCountsOnlyPersistedOrders`, `CommerceServiceTest.pricesOnTheServerAsksPythonForRiskBeforePersistingAndRecordsHistory`); restaurado. Además chocaría con la restricción `orders_status_matches_risk` de PostgreSQL |
| Regresión de la caché (`redisCacheReadsBackWhatItWroteIncludingDerivedAccessors`) | **Falla** con el `RedisCatalogCache` anterior y pasa con la corrección |
| `pytest` (risk-engine) | **62 passed** |
| Regla de importe 1000 → 500 solo en `risk.py` | Fallan `test_risk_thresholds[500.00-40-APPROVED]` y `[999.99-40-APPROVED]` (las pruebas protegen la regla) |
| Parche `tools/demo/regla-revision-desde-500.patch` | Aplicado: 62 passed; revertido (`git apply -R`): 62 passed; `git apply --check` correcto |
| `generate_delivery.py --check`, `validate_delivery.py` | OK (Compose autosuficiente, 14 objetos Kubernetes) |
| `check_observability.py` | OK: 8 dashboards, 9 alertas, observabilidad previa conservada |
| `generate_pages.py --check`, `check_frontend.py`, `generate_brand.py --check`, `generate_catalog.py --check`, `check_i18n.py` | OK (45 páginas, 20 categorías y 170 subcategorías, 2471 textos en 5 idiomas) |
| Gateway `infra/frontend/nginx.conf` (`nginx -t` + servidor de eco) | Sintaxis correcta. Sin `X-Forwarded-Proto` → `http` (comportamiento local intacto); con `X-Forwarded-Proto: https` y `Host` del proxy → `https` y el dominio |
| Plantilla `infra/aws/nginx-host-polymer.conf` (Nginx 1.24 = Ubuntu 24.04, certificados autofirmados de prueba) | `nginx -t` correcto; HTTP → **301** a `https://`; tienda por TLS con `X-Forwarded-Proto: https`; `/api/orders` → **403** desde una IP no permitida; Grafana → **403** desde una IP no permitida; cabecera **HSTS** |
| `bash -n` de `infra/aws/*.sh` | Sintaxis correcta (no ejecutados: requieren la instancia) |
| Auditoría de secretos en Git | Ningún archivo de secretos rastreado ni en el historial (50 commits); solo valores efímeros del CI y plantillas |

## 1 bis. v7 · Local (13/11/2026): stack real sin Docker

Entorno: PostgreSQL 16 y Redis 7 instalados en el contenedor, motor de riesgo Python (uvicorn), `order-api`
(jar), el gateway con `infra/frontend/nginx.conf` real, un receptor SMTP con la API de búsqueda de Mailpit y el
mismo proveedor OpenID Connect de pruebas que usa el CI (`mock-oauth2-server` 2.1.10). Sin RabbitMQ, Prometheus
ni Grafana (por eso el correo de la boleta y los pasos de monitoreo se validan en el CI).

| Prueba | Resultado observado |
| --- | --- |
| `mvn test` (order-api) | **163 pruebas, 0 fallos** (131 anteriores + 32 nuevas: dominio, casos de uso y web de la v7); 8 reglas ArchUnit en verde con el código nuevo |
| Prueba de regresión del dominio | `SignInServiceTest` encontró y se corrigió un desempate: con dos solicitudes creadas en el mismo instante la vista podía mostrar la anulada; ahora prioriza aprobado → pendiente → más reciente |
| Flyway V11 y V12 sobre una base nueva | Aplicadas al arrancar (`order-api` arranca en 6 s) |
| `v7-e2e.js` | **34 comprobaciones** (con `SKIP_RECEIPT_MAIL=1` por no haber RabbitMQ): video servido con Range (206), pausa, mapa, horario de hoy, calendario, Google Calendar; Google + código + «Recordarme» (cookies de 30,0 días, sesión recuperada sin la cookie de sesión, segundo acceso sin código); tarjeta rechazada y aprobada; boleta con IGV; Yape vencido **a los 60 s reales** y QR nuevo aprobado; registro con código; móvil |
| `i18n-e2e.js` | **77 comprobaciones** (incluye el registro en inglés con el paso del código) |
| `intro-e2e.js` | **41 comprobaciones** |
| `pages-e2e.js` | **284 comprobaciones** de páginas × roles (incluidas `/cliente/pago.html` y `/cliente/boleta.html`: 200 para el cliente, 403 para el personal); se detiene en los pasos que consultan Prometheus (no instalado aquí) |
| Errores encontrados y corregidos por estas pruebas | (1) URL de retorno de Google sin `:8080` detrás del gateway → el gateway envía `X-Forwarded-Port`; (2) el botón de pausa del video quedaba bajo las tarjetas de promoción; (3) faltaban las rutas lógicas `/pago/:id` y `/boleta/:id` en el router |
| Validaciones estáticas | `generate_pages --check` (47 páginas), `check_frontend`, `check_i18n` (2663 textos × 5 idiomas), `check_observability` (8 dashboards, 2 paneles nuevos), `generate_delivery --check`, `validate_delivery` |
| Entregable autosuficiente | Restauración simulada de los heredocs: `services/order-api/src` y el video idénticos byte a byte |

## 2. CI con Docker real

Ejecución **[43324523676](https://github.com/rgabrielmatosm/Hexaorders---Arquitectura-Hexagonal-Proyecto-AED-II-/actions/runs/43324523676)** del workflow `Verify POLYMER` sobre el commit `016f521` (rama
`claude/clever-curie-1597uv`): **los 4 jobs en verde** (`compose`, `compose-basic`, `windows`,
`migrate-from-v6`). Las ejecuciones [43321136405](https://github.com/rgabrielmatosm/Hexaorders---Arquitectura-Hexagonal-Proyecto-AED-II-/actions/runs/43321136405) y [43324404093](https://github.com/rgabrielmatosm/Hexaorders---Arquitectura-Hexagonal-Proyecto-AED-II-/actions/runs/43324404093) (commits anteriores) también terminaron en verde.

| Job | Qué ejecutó (Docker real salvo `windows`) | Resultado |
| --- | --- | --- |
| `compose` | `polymer.ps1 verify` (13 contenedores sanos, smoke de tienda, plataforma e integraciones, persistencia tras reinicio, `risk-engine` caído → 503), Playwright (UI, 45 páginas × roles, intro, idiomas y contraseñas) y **todos los escenarios de la exposición** | ✅ |
| `compose-basic` | Modo `basic` de extremo a extremo | ✅ |
| `windows` | Análisis de todos los `.ps1` con PowerShell 5.1, secretos, `check` y pruebas unitarias (Java y Python) | ✅ |
| `migrate-from-v6` | HexaOrders v6 con datos → POLYMER (secretos importados, volúmenes migrados, recuentos idénticos), `change-password` de PostgreSQL, Grafana y RabbitMQ | ✅ |

### Escenarios de la exposición (`tools/demo/polymer-demo.ps1`, en el orden del CI)

Cada escenario falla si una evidencia no se cumple; todos terminaron con «OK».

| Escenario | Evidencias comprobadas | Duración |
| --- | --- | --- |
| `preparar` | 1 | 1 s |
| `arquitectura` | 11 | 23 s |
| `rabbitmq` | 5 | 5 s |
| `postgres` | 3 | 20 s |
| `riesgo` | 3 | 2 s |
| `observabilidad` | 2 | 4 s |
| `redis-caida` | 3 | 34 s |
| `rabbitmq-caida` | 6 | 21 s |
| `riesgo-caida` | 3 | 11 s |
| `activar-demo` | 2 | 17 s |
| `redis` | 8 | 18 s |
| `adaptador` | 2 | 9 s |
| `desactivar-demo` | 1 | 18 s |
| `preparar` | 1 | 1 s |

Evidencias observadas (texto literal del registro del CI):

- **Arquitectura:** ArchUnit (8 reglas) y AdapterSubstitutionTest (4 pruebas) en verde.
- **RabbitMQ:** outbox_published contiene order-status-27; processed_events contiene order-status-27; Correo de notificación en Mailpit (http://127.0.0.1:8025); Exchange topic durable, cola durable con consumidor y DLQ configurada.
- **PostgreSQL:** Huella del pedido antes del reinicio: 100017|36.25|RISK_APPROVED|1|2 → Huella después del reinicio: 100017|36.25|RISK_APPROVED|1|2; El pedido, sus líneas y su historial son idénticos tras el reinicio.
- **Microservicios:** risk-engine devolvió 10 y el dominio Java aprobó; risk-engine devolvió 75 y el pedido quedó EN REVISIÓN (visible en Operaciones → Revisión de riesgo); risk-engine registró las llamadas POST /score de order-api.
- **Observabilidad:** Prometheus muestra 3 pedidos de la tienda, igual que order-api (3); Prometheus recopila 9 targets; order-api, risk-engine y prometheus en UP.
  Valores leídos de Prometheus en ese momento: pedidos de la tienda `APPROVED` 2 y `REVIEW` 1;
  HTTP `200` 15 y `201` 5; latencia p95 0,037 s; eventos publicados `ORDER_CREATED` 2,
  `ORDER_APPROVED` 1, `ORDER_STATUS_CHANGED` 1; outbox pendiente 0; correos enviados 4; 9 *targets* con
  `up = 1` (alloy, loki, nginx, order-api, otel-collector, prometheus, rabbitmq, risk-engine, tempo).
  Son contadores desde el último reinicio de `order-api` (escenario E).
- **Redis (TTL de demostración 15 s):** polymer_cache_requests_total{result="miss"} aumentó; polymer_cache_requests_total{result="hit"} aumentó; TTL = -2: la clave ya no existe; Nuevo miss tras la expiración; Entrada nueva en Redis con TTL completo (15 s).
- **Redis caído:** Catálogo servido sin Redis: 556 productos en 388 ms; Aumentan error/bypass: RedisCatalogCache degrada sin romper la tienda; El catálogo vuelve a servirse desde Redis sin intervención manual.
- **RabbitMQ caído:** El evento order-status-37 sigue PENDIENTE (no está en outbox_published); El evento order-status-37 se publicó al volver RabbitMQ; El pedido no se perdió.
- **risk-engine caído:** POST /api/shop/orders → 503 Service Unavailable (RiskUnavailableException); Ningún pedido sin evaluación de riesgo en PostgreSQL; Tras la recuperación el pedido se crea (201).
- **Sustitución de adaptador:** Pedido creado con el puntaje fijo 10 sin consultar risk-engine; order-api vuelve a la configuración de docker-compose.yml (TTL 60 s, adaptador HTTP).

## 3. Errores encontrados por la validación y corregidos

- El script de escenarios tenía dos errores que el CI detectó: una referencia `$ComposeFile:` que
  PowerShell interpreta como ámbito (detectado por el análisis con PowerShell 5.1) y una contraseña de
  cliente ficticio que contenía parte de su usuario, que la política de contraseñas del servidor rechazó
  con razón. Ambos corregidos.
- **Error real de la aplicación** detectado por el escenario `redis` en el CI: la caché contaba un *hit*
  y a la vez un `error`, y tras la expiración solo había `bypass`. Causa: `Category.isMain()` se
  serializa como `"main"` y no se podía volver a leer, así que el árbol de categorías nunca se servía
  desde Redis. Corregido en `RedisCatalogCache` con prueba de regresión (ver `AUDITORIA_POLYMER.md`, error 6).
- Dos funciones del script con el mismo nombre (`Invoke-Redis`) provocaban recursión infinita; el
  ayudante se renombró a `Invoke-RedisCli`.
- El escenario de observabilidad mostraba contadores a 0 justo después del reinicio de `order-api`
  (escenario E): Prometheus aún no había leído los valores nuevos. Ahora genera un pedido y espera al
  siguiente *scrape* hasta que Prometheus coincide con `/actuator/prometheus`; no se muestra un cero
  que no corresponde a la realidad.
- `infra/frontend/nginx.conf` perdía `https` detrás de un proxy TLS (ver `AUDITORIA_POLYMER.md`, error 1).

## 4. Pendiente de verificación (no se afirma que funcione)

| Qué | Por qué no se ejecutó | Cómo ejecutarlo |
| --- | --- | --- |
| Docker Desktop en un Windows real | Este entorno no es Windows con Docker Desktop; el CI de Windows no ejecuta contenedores Linux | `.\polymer.cmd start`, `.\polymer.cmd health`, `.\polymer.cmd demo -Scenario todo -NoPause` |
| Escenario `arquitectura` con Maven en contenedor en Windows | Igual que arriba | `.\polymer.cmd demo -Scenario arquitectura` |
| `health`, `rebuild`, `restart` | Solo analizados sintácticamente (PowerShell 5.1) | `.\polymer.cmd health`, `.\polymer.cmd rebuild -Services risk-engine`, `.\polymer.cmd restart -Services order-api` |
| Despliegue en AWS con HTTPS | Requiere tu aprobación (genera costos), una cuenta y un dominio | `DESPLIEGUE_AWS_HTTPS.md`, sección 6 |
| Copias `backup.sh` / `backup-verify.sh` | Requieren el stack en marcha | `bash infra/aws/backup.sh` y `bash infra/aws/backup-verify.sh <archivo>` (también funcionan en Linux local con Docker) |
| Manifiestos de Kubernetes | Sin clúster | `kubectl apply -f k8s/` en un clúster de pruebas |
| Publicación en un repositorio GitHub nuevo | Requiere tu autorización | `GUIA_GITHUB.md` |
