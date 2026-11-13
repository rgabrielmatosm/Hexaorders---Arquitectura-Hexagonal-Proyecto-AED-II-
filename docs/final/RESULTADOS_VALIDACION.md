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

## 1 quáter. v8 · Enlaces por slice y Google con `secrets/google-oauth.json` (13/11/2026)

**CI con Docker real:** ejecución [43459503346](https://github.com/rgabrielmatosm/Polymer-Tech-Proyecto-AED-II/actions/runs/43459503346) (commit `1d6dc31`, enlaces y asistente) y ejecución **[43464846107](https://github.com/rgabrielmatosm/Polymer-Tech-Proyecto-AED-II/actions/runs/43464846107)**
(commit `a1a23e2`, JSON de Google): **los 4 jobs en verde** en ambas. Además de todo lo de la sección 1 ter, el job
`compose` ejecutó:

| Paso | Qué comprobó |
| --- | --- |
| `generate_links.py --check` + `polymer.ps1 enlaces` | Los 9 enlaces publicados responden (tienda 8080, `order-api` 8081, `risk-engine` 8000, Grafana, Prometheus, RabbitMQ, Mailpit, OpenTelemetry, Alloy); `order-api` directo devuelve el catálogo y `risk-engine` abre `/docs` |
| `polymer.ps1 google -GoogleJsonFile` (JSON **ficticio** con el formato de Google Cloud) | Lo copia a `secrets/google-oauth.json`, reinicia `order-api`, el botón se activa y redirige a `accounts.google.com` con ese cliente; el secreto **no** aparece en la salida del asistente ni en los logs; el log indica `credenciales: secrets/google-oauth.json` |
| Job `windows` | `polymer.ps1` (con `enlaces` y `google`) analizado por Windows PowerShell 5.1 |

**Pruebas locales con las credenciales REALES del usuario** (sin imprimir valores; el archivo no está en Git):

| Prueba | Resultado |
| --- | --- |
| `order-api` con `GOOGLE_OAUTH_JSON_FILE` | `/api/auth/providers` → `google: true`; log «activado (credenciales: secrets/google-oauth.json; 2 URI de retorno registradas)»; el secreto no aparece en el log |
| `/oauth2/authorization/google` con `localhost:8080` y con `127.0.0.1:8080` | 302 a `accounts.google.com` con el `client_id` del JSON, `scope=openid profile email` y `redirect_uri` registrada en el JSON |
| Seguir esa redirección en Google | **HTTP 200, pantalla de inicio de sesión** (`/v3/signin/identifier`) para ambas URI: sin `redirect_uri_mismatch` ni `invalid_client` |
| Endpoint de tokens de Google con un código ficticio | Con el secreto real: `invalid_grant` (Google aceptó ID y secreto); control con un secreto falso: `invalid_client` |
| `v7-e2e.js` (proveedor OIDC de pruebas, con el JSON montado) | **34 comprobaciones**: cuenta creada con Google, código de 6 dígitos, sesión, vinculación, dispositivo de confianza; las variables explícitas tienen prioridad sobre el JSON |
| `mvn test` | **168 pruebas, 0 fallos** (5 nuevas en `GoogleOAuthCredentialsTest`); pytest 63 |
| Búsqueda en archivos versionables | Ni el ID ni el secreto aparecen en ningún archivo de Git; `git check-ignore secrets/google-oauth.json` = ignorado |

**No verificado:** completar el inicio de sesión con una cuenta de Google real (requiere la contraseña del
usuario en su navegador). Se hace en `http://localhost:8080/login.html` con un Gmail añadido como *usuario de prueba*.

## 1 ter. v7 · CI con Docker real (13/11/2026)

Ejecución **[43384929823](https://github.com/rgabrielmatosm/Polymer-Tech-Proyecto-AED-II/actions/runs/43384929823)** del workflow `Verify POLYMER` sobre el commit `c6da401` (rama
`claude/clever-curie-1597uv`, PR 4): **los 4 jobs en verde** (`compose` 07:19–07:35 UTC, `compose-basic`,
`windows`, `migrate-from-v6`). Líneas literales del registro:

| Paso | Resultado (texto del registro) |
| --- | --- |
| `polymer.ps1 verify` (modo full) | `Tests run: 163, Failures: 0, Errors: 0, Skipped: 0` · pytest `62 passed` · `OK plataforma: 39 comprobaciones` · `OK tienda: 51 comprobaciones` · `OK integración (check): 49 comprobaciones` · `degraded` 4 · `recovered` 3 · persistencia tras reinicio · `HTTP 503 recibido correctamente.` · `VERIFICACIÓN COMPLETA (modo full): todos los servicios funcionan de extremo a extremo.` |
| Validaciones estáticas | `OK: 47 páginas HTML al día` · `frontend multipágina coherente (47 páginas …, 39 rutas, 13 transiciones, CSP sin estilos ni scripts en línea …)` · `OK i18n: 2663 textos traducidos en 5 idiomas (+ español)` |
| `ui-e2e.js` | `UI E2E correcta: 28 comprobaciones` |
| `pages-e2e.js` | `Páginas E2E correctas: 295 comprobaciones` (incluye Prometheus/Grafana y la vista móvil de 390 px) |
| `intro-e2e.js` | `OK: intro y movimiento de POLYMER (41 comprobaciones)` |
| `i18n-e2e.js` | `OK i18n e2e: 78 comprobaciones` |
| `v7-e2e.js` (con el proveedor OIDC de pruebas y RabbitMQ: incluye el correo de la boleta) | `OK v7 e2e: 34 comprobaciones` |
| Escenario `pagos` (exposición) | `OK demostración «pagos»: 9 evidencias comprobadas en 67 s` |
| Resto de escenarios | `preparar` 1 · `arquitectura` 11 · `rabbitmq` 5 · `postgres` 3 · `riesgo` 3 · `observabilidad` 2 · `redis-caida` 3 · `rabbitmq-caida` 6 · `riesgo-caida` 3 · `activar-demo` 2 · `redis` 8 · `adaptador` 2 · `desactivar-demo` 1 · `preparar` 1 — todos «OK» |
| `migrate-from-v6` | Flyway hasta **V12**; `Volúmenes: 8 originales conservados y 8 polymer_* migrados` · `Antes: 1/6/557/1 · después: 1/6/557/1` · `Pedido de la v6 intacto` · cuentas existentes siguen verificadas (V11) y tabla `payments` creada (V12) · `change-password` de PostgreSQL, Grafana y RabbitMQ · segunda migración: `No hay volúmenes pendientes de migrar.` |
| `windows` (PowerShell 5.1) | `OK init-env.ps1` · `OK polymer.ps1` · `OK polymer-demo.ps1` · `Comprobaciones correctas.` · `62 passed` · `Tests unitarios correctos.` |

Errores que el CI encontró en la v7 y se corrigieron antes de esta ejecución:

1. El contexto de construcción de `order-api` superaba el límite de 4 MiB de BuildKit (gRPC) por el video
   y los archivos grandes del entregable → los archivos de más de 4 KB se empaquetan aparte (`LARGE_FILE`).
2. El `RUN` que desempaqueta el frontend se ejecutaba como usuario sin privilegios → `USER root` durante la
   construcción y `USER 101` al final (archivos idénticos byte a byte).
3. `integration_smoke.py recovered` contaba también el correo del código de 6 dígitos → se filtra.
4. `migrate-from-v6` esperaba Flyway V10 → V12, con comprobaciones nuevas de `email_verified` y `payments`.
5. **Vista móvil (390 px) de la portada con desplazamiento horizontal**: las tarjetas del mapa y del horario
   crecían con su contenido (`1fr` con mínimo automático) → `minmax(0, 1fr)`, `min-width: 0` y ajuste de
   línea en dirección, correo y botones. La prueba ahora indica qué elemento desborda y guarda una captura.
6. `intro-e2e.js` aún exigía «pagos próximamente» en el pie → ahora comprueba que el pie lista tarjeta, Yape,
   Plin y contra entrega sin anunciar marcas de tarjeta.

## 2. CI con Docker real (v6, referencia)

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
