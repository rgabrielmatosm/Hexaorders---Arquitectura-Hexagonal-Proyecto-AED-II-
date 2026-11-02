# Validación efectuada

Este registro separa cuatro tipos de evidencia: **ejecución real**, **pruebas automáticas**, **revisión estática** y **pendiente de verificación**. Un resultado esperado nunca se presenta como observado.

## Versión 5 — plataforma de 13 contenedores (2 de noviembre de 2026)

Entorno local: contenedor Linux de desarrollo **sin motor Docker ni Windows**. Para probar las integraciones sin Docker se usaron los **binarios reales** de cada servicio:

- PostgreSQL 16, `redis-server` y `rabbitmq-server` (paquetes del sistema);
- Mailpit, Tempo, `otelcol-contrib` y Loki (binarios oficiales);
- Nginx con `infra/frontend/nginx.conf`, el motor de riesgo con trazas y el JAR con `HEXAORDERS_INTEGRATIONS=on`.

Prometheus, Grafana y Alloy no se ejecutaron en local: los cubre el job `compose` del CI, que levanta el stack Docker real.

### Ejecución real local (binarios, sin Docker)

| Comprobación | Resultado observado |
| --- | --- |
| `platform_smoke.py` a través del gateway Nginx | 35 comprobaciones correctas, sin regresiones. |
| Estáticos a través de Nginx | 28 archivos idénticos byte a byte a los del repositorio. |
| Eventos y correos | 15 eventos publicados con confirmación, 15 consumidos y 15 correos en Mailpit. DLQ vacía. |
| Redis | Aciertos registrados en `hexaorders_cache_requests_total{result="hit"}`. |
| Tempo | Traza de un checkout con 15 spans de `order-api` y `risk-engine`: HTTP, caso de uso, repositorio, puerto de riesgo, `POST /score` y `ScoreRisk.execute`. |
| Degradación | Con Redis y RabbitMQ detenidos: el catálogo respondió en 0,3 s, `platform_smoke.py` pasó (35 OK) y quedaron 15 eventos en el outbox. Al rearrancarlos: outbox en 0 y 15 correos más. |

### Pruebas automáticas

| Comprobación | Resultado observado |
| --- | --- |
| Java: `mvn verify` | **97 pruebas**, cero fallos (antes 81). Nuevas: `BusinessEventTest`, `IntegrationServicesTest` e `InfrastructureAdaptersTest` (contrato, publicador con *ack* y *nack*, Redis real, degradación contra un Redis inalcanzable y modos de `IntegrationEnvironment`). |
| Python: `pytest` | **62 pruebas** aprobadas (antes 60), incluida `test_tracing.py`. |
| `check_observability.py` | `OK: observabilidad previa conservada (9 paneles, 2 alertas, 3 jobs) · 8 dashboards en total, 9 alertas en total`. |
| `check_frontend.py` | `OK: frontend multipágina coherente (39 páginas generadas + laboratorio, 32 rutas, 13 transiciones, CSP sin estilos ni scripts en línea)`. |
| `generate_delivery.py --check`, `validate_delivery.py` | `OK: Compose autosuficiente y 14 objetos Kubernetes`. Se valida `read_only`, perfiles, healthchecks, imágenes fijadas, puertos solo en `127.0.0.1` y servicios internos sin puertos. |

### CI con Docker real (GitHub Actions)

| Job | Resultado |
| --- | --- |
| `windows` (PowerShell 5.1, cuatro secretos, `check`, `test`) | Correcto. |
| `compose-basic` (`verify -Mode basic`: 6 contenedores) | Correcto. |
| `compose` (`verify -Mode full`: 13 contenedores) | Ver el estado final en el Pull Request. Se corrigieron los errores de la tabla siguiente. |

### Errores del CI encontrados y corregidos

| Error | Causa | Corrección |
| --- | --- | --- |
| Alloy: `mkdir /var/lib/alloy/data: permission denied` | `COPY --chown` no cambia un directorio que ya existe en la imagen, y `cap_drop: ALL` impide escribir como otro usuario | El almacenamiento de Alloy pasa a `/var/lib/hexaorders-alloy`, creado con el usuario correcto |
| `integration_smoke.py`: comprobación de la DLQ | RabbitMQ 4 añade `x-queue-type: classic` a los argumentos de toda cola | Solo se exige que la DLQ sea durable, esté vacía y no tenga a su vez otra DLX |

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| Docker Desktop en Windows 10/11 | No hay Windows con Docker en este entorno. Lista manual: [`WINDOWS_DOCKER_GUIDE.md`](WINDOWS_DOCKER_GUIDE.md). |
| Alloy leyendo `/var/run/docker.sock` en Docker Desktop | Solo probado en Linux (CI). Docker Desktop expone el socket a los contenedores Linux, pero no se ha comprobado aquí. |
| Kubernetes | Solo el núcleo y solo validación de esquema; no incluye los servicios v5. |

## Versión 4 — sitio web multipágina (2 de noviembre de 2026)

Entorno local: contenedor Linux de desarrollo **sin motor Docker ni Windows** (Java 21, Maven, Python 3.13, PostgreSQL 16, Node 22, Chromium con Playwright). Sin Prometheus ni Grafana en local: su parte la ejecuta el job `compose` de GitHub Actions.

### Ejecución real (Java + Python + PostgreSQL 16, sin Docker)

| Comprobación | Resultado observado |
| --- | --- |
| Arranque sobre base vacía | Flyway aplicó `V1`–`V4`; 6 cuentas demo creadas. |
| `tools/platform_smoke.py` | `OK plataforma: 35 comprobaciones` (sin regresiones de la v3). |
| `tools/smoke.py` | Correcto hasta la consulta a Prometheus (no disponible en local), incluidas las páginas protegidas (302 al login), los archivos estáticos servidos idénticos, el laboratorio en `/laboratorio.html` y la página 404. |
| `tools/ui-e2e/pages-e2e.js` (`SKIP_PROMETHEUS=1`) | `Páginas E2E correctas: 244 comprobaciones`: las 39 páginas como visitante y con los 5 roles (200 / 302 / 403 / 404), enlaces internos sin 404, recarga, filtros del catálogo en la URL, contacto persistido y visible para el admin, checkout con dirección y seguimiento, calendario, monitoreo degradado sin Prometheus, móvil de 390 px sin desbordamiento, sin errores de JavaScript ni respuestas 5xx. |
| `tools/ui-e2e/ui-e2e.js` (`SKIP_PROMETHEUS=1`) | `UI E2E correcta: 21 comprobaciones` (laboratorio original y flujos por rol con las URLs nuevas). |
| Revisión visual con capturas | Corregidos: breadcrumb visible en la portada (la regla CSS anulaba `hidden`), contenido centrado en los portales (`main` heredaba `margin: 0 auto`), desbordamiento horizontal en móvil por la barra lateral y nodos del diagrama de la portada solapados. |

### Pruebas automáticas

| Comprobación | Resultado observado |
| --- | --- |
| Java: `mvn verify` | **81 pruebas**, cero fallos (antes 66): nuevas `WebPlatformDomainTest`, `WebPlatformServicesTest`, `PrometheusMonitoringAdapterTest` y casos de páginas en `SecurityRulesTest`. |
| Python: `pytest` | **60 pruebas** aprobadas (sin cambios en el motor de riesgo). |
| `check_frontend.py` | `OK: frontend multipágina coherente (39 páginas generadas + laboratorio, 32 rutas, 13 transiciones, CSP sin estilos ni scripts en línea)`. |
| `check_observability.py` | `OK: observabilidad previa conservada (9 paneles, 2 alertas, 3 jobs) · 7 dashboards en total, 9 alertas en total`. |
| `generate_delivery.py --check`, `validate_delivery.py` con esquemas oficiales | Correcto. |

### Errores del CI encontrados y corregidos

| Error | Causa | Corrección |
| --- | --- | --- |
| `ui-e2e.js` esperaba la línea de tiempo del modal del operador | La portada de operaciones muestra primero los pedidos más antiguos; en CI son pedidos de la API original sin historial, y la lista vacía no era visible | El modal muestra el estado actual real cuando no hay transiciones registradas |

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| Instalación y arranque en un PC Windows 10/11 con Docker Desktop | No hay Windows con Docker en este entorno. Los scripts se ejecutan en el runner `windows-2022` (PowerShell 5.1) y el stack completo con el mismo `hexaorders.ps1 verify` en Ubuntu. Lista de comprobaciones manuales: [`WINDOWS_DOCKER_GUIDE.md`](WINDOWS_DOCKER_GUIDE.md#9-comprobaciones-manuales-recomendadas-en-docker-desktop). |
| Monitoreo web contra Prometheus real y los 7 dashboards en Grafana | Lo ejecutan `smoke.py` y `pages-e2e.js` en el job `compose`; el resultado se indica en el Pull Request. |
| Kubernetes | Solo validación de esquema. |

## Versión 3 — plataforma comercial y operaciones (2 de noviembre de 2026)

Entorno: el mismo contenedor de desarrollo **sin motor Docker** (Java 21, Maven, Python 3.13, PostgreSQL 16 local, Node 22, Chromium con Playwright). Prometheus y Grafana no estaban disponibles en local; su parte la ejecuta el job `compose` de GitHub Actions.

### Ejecución real (Java + Python + PostgreSQL 16, sin Docker)

| Comprobación | Resultado observado |
| --- | --- |
| **Actualización desde la v2 con datos** | Se arrancó el JAR de la v2 (`604394a`), se crearon 3 pedidos y luego se arrancó la v3 sobre la misma base: Flyway aplicó `V2` y `V3`; los 3 pedidos siguieron intactos (`channel = LAB`, estado derivado de su decisión), con 3 filas de historial de migración; 18 productos, 6 categorías y 6 cuentas demo creadas; `/api/orders/statistics` sin cambios. |
| `tools/platform_smoke.py` | `OK plataforma: 35 comprobaciones` (catálogo, precio del servidor, `400` con precio del cliente, `401`, registro e inicio de sesión, checkout idempotente, stock, historial, aislamiento entre clientes, cancelación con devolución de stock, transiciones por rol, `409`, calendario con conflicto y reprogramación, entrega, incidencias, panel y auditoría de admin, salud y `403` del observador, `401` con contraseña incorrecta). |
| `tools/smoke.py` (original) | Todas las aserciones correctas hasta la consulta a Prometheus, que falla por no haber Prometheus en local (esperado). |
| `tools/ui-e2e/ui-e2e.js` con `SKIP_PROMETHEUS=1` y `DEMO_PASSWORD` | `UI E2E correcta: 21 comprobaciones` (laboratorio original y flujos de cliente, admin, operador, logística y observador). |
| Revisión visual con capturas | 23 capturas revisadas. Se corrigieron 3 defectos: contorno de foco visible en el contenedor de vistas, desbordamiento de la barra de navegación con el rol admin y nombres vacíos en «stock bajo» (el JSON anida `details.name`). |
| Métricas expuestas en `/actuator/prometheus` | Presentes las métricas nuevas `hexaorders_*`, `orders_total` y las de JVM/HikariCP. |
| **Defecto previo encontrado** | `orders_created` se exporta como `orders_total` (sufijo `_created` reservado por el cliente Prometheus 1.x), así que dos paneles originales no tenían datos. Corregido con la regla de registro `orders_created_total` (ver [`OBSERVABILITY_PRESERVATION.md`](OBSERVABILITY_PRESERVATION.md)). La fila de «Revisión estática» de la v2 que daba ese nombre por bueno estaba equivocada. |

### Pruebas automáticas

| Comprobación | Resultado observado |
| --- | --- |
| Java: `mvn verify` | **66 pruebas**, cero fallos (antes 36), incluidas `LifecycleDomainTest`, `CommerceServiceTest`, `OperationsAndLogisticsTest`, `SecurityRulesTest` y la nueva regla ArchUnit. |
| Python: `pytest` | **60 pruebas** aprobadas (antes 59; nueva métrica `risk_requests_rejected_total`). |
| Sintaxis JavaScript | `node --check` sobre los 17 módulos: correcto. |
| `check_observability.py` | `OK: observabilidad previa conservada (9 paneles, 2 alertas, 3 jobs) · 6 dashboards en total, 9 alertas en total`. |
| `generate_delivery.py --check` y `validate_delivery.py` con esquemas oficiales | Correcto (Compose Specification y Kubernetes 1.34.1, 14 objetos). |

### Error del CI encontrado y corregido

El primer CI de la v3 falló en `docker compose build` con `max depth exceeded`: el generador emitía **un `COPY` por archivo** (141 en `order-api`) y cada uno es una capa; overlayfs no admite tantas. El generador agrupa ahora los archivos de cada directorio en un único `COPY` con heredocs con nombre (33 capas en `order-api`). Se comprobó que todos los archivos se reconstruyen byte a byte desde el YAML generado.

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| `verify` completo con Docker, Prometheus y Grafana (incluidos `platform_smoke.py` con métricas en Prometheus y los 6 dashboards en Grafana) | Sin Docker en local; lo ejecuta el job `compose` del CI. El resultado se indica en el Pull Request. |
| Scripts PowerShell en Windows | No hay PowerShell en local; los ejecuta el job `windows` del CI. |
| Kubernetes | Solo validación de esquema; no se desplegó. |

## Versión 2 — interfaz rediseñada (1 de noviembre de 2026)

Entorno: contenedor Linux de desarrollo **sin motor Docker**, con Java 21.0.12, Maven, Python 3.13 (pruebas de Python), PostgreSQL 16.15 local, Node 22 y Chromium (Playwright 1.63.0).

### Ejecución real (stack sin Docker: Java + Python + PostgreSQL 16)

Se arrancó el JAR de `order-api` contra un PostgreSQL 16 local (base desechable, eliminada al terminar) y el motor de riesgo con `uvicorn`. Prometheus y Grafana no estaban disponibles en ese entorno.

| Comprobación | Resultado observado |
| --- | --- |
| Arranque y migración Flyway `V1` | Correcto; al reiniciar: «Schema is up to date. No migration necessary». |
| `tools/smoke.py` | Pasaron todas las aserciones hasta la consulta a Prometheus (línea 56), que falló por conexión rechazada al no haber Prometheus: pedidos de 125.50/750/1500 con riesgos 10/40/75, relectura por ID, `400` para importe 0, estadísticas coherentes desde PostgreSQL y los 13 recursos del frontend servidos byte a byte idénticos con el tipo MIME correcto. |
| Motor de riesgo detenido | `503` «Motor de riesgo no disponible»; estadísticas **sin cambios** (12 → 12); `orders_risk_failures_total` = 1. |
| Recuperación | Tras reiniciar el motor: `201`. |
| Persistencia tras reiniciar la API | 13 pedidos antes y después del reinicio del proceso Java. |
| `tools/ui-e2e/ui-e2e.js` (Chromium) | 11 de 11 comprobaciones previas a observabilidad correctas: bienvenida con consulta real, total del panel igual a PostgreSQL, validación del formulario, alta en revisión, listado, estadísticas, consulta por ID, archivo real en el explorador, solicitud real `201` con relectura idéntica y `400` sin guardar. La parte de observabilidad falló como se esperaba sin Prometheus. |
| **Error encontrado y corregido** | La respuesta del `POST` incluía `createdAt` con nanosegundos, pero PostgreSQL guarda microsegundos, así que un `GET` posterior devolvía otro valor. `OrderService` trunca ahora el instante a microsegundos (prueba `createdAtHasMicrosecondPrecisionSoTheResponseMatchesWhatIsPersisted`). Tras corregirlo, `POST` y `GET` son idénticos. |

### Pruebas automáticas

| Comprobación | Resultado observado |
| --- | --- |
| Java: `mvn verify` | **36 pruebas**, cero fallos (antes 32): `OrderDomainTest` 17, `OrderServiceTest` 8, `OrderControllerTest` 5, `HttpRiskAssessmentAdapterTest` 3 y `HexagonalArchitectureTest` 3 (ArchUnit). El JAR contiene los 13 recursos de `static/`. |
| Python: `pytest` | **59 pruebas** aprobadas (sin cambios en el código Python). |
| Interfaz contra servidor de prueba local | Sin errores de JavaScript; funcionan alta, filtros, explorador (dependencias, ejecución, 201 y 400), aviso de observabilidad degradada, móvil de 390 px sin scroll horizontal y `prefers-reduced-motion`. Es una API simulada solo para revisar la interfaz; **no** sustituye la prueba con el backend real. |

### Revisión estática

| Comprobación | Resultado observado |
| --- | --- |
| `generate_delivery.py --check` | `docker-compose.yml` y el ConfigMap, al día. |
| `validate_delivery.py` con esquemas oficiales | Correcto contra **Compose Specification** y **OpenAPI de Kubernetes 1.34.1** (14 objetos). |
| Métricas del dashboard y de la interfaz | Nombres comprobados en el código: `orders_created_total`, `orders_risk_failures_total` (Micrometer), `risk_evaluations_total` y `risk_evaluation_duration_seconds` (prometheus_client), `http_server_requests_seconds` (Actuator). |

### Pendiente de verificación

| Comprobación | Motivo |
| --- | --- |
| Construcción y arranque con **Docker Compose** de esta versión, con Prometheus y Grafana | Sin motor Docker en el entorno. Lo ejecuta el job `compose` de GitHub Actions al publicar la rama, que requiere autorización del autor para el push. |
| Prueba de interfaz con **observabilidad real** (targets `up`, métricas y Grafana) | Lo ejecuta el CI tras `verify`; las capturas se publican como artefacto `hexaorders-ui-screenshots`. |
| Despliegue en un clúster Kubernetes | Fuera del alcance: solo validación de esquema. |

---

## Versión 1 (19 de octubre de 2026)


| Comprobación | Resultado observado |
|---|---|
| Java 21.0.2 / Maven 3.9.11: `mvn verify` | **32 pruebas**, cero fallos/errores; JAR ejecutable generado. |
| Python 3.12.14: `python -m pytest` | **59 pruebas aprobadas**. |
| Arquitectura Java | ArchUnit: núcleo sin frameworks/adaptadores, dominio sin aplicación, entrada sin salida. |
| Arquitectura Python | AST: dominio y aplicación importan únicamente biblioteca estándar y capas internas permitidas. |
| Contratos y reglas | Umbrales, decimales exactos, validación REST, manejo de errores HTTP, persistencia mediante puerto y cero inserts si falla riesgo. |
| Docker Compose CLI oficial 2.39.4 | `config --quiet` correcto; binario contrastado con checksums de su release oficial. |
| Entrega de un archivo | `config --quiet` correcto desde directorio aislado que contiene solo el YAML y variables de validación. |
| Compose Specification | Validación JSON Schema oficial correcta. |
| Fuentes y configs embebidos | Coinciden con archivos originales, incluidos escapes `$`; sin COPY de archivos del host ni bind mounts; order-api, risk-engine, Prometheus y Grafana con `read_only: true`. |
| Kubernetes | **14 objetos** válidos según OpenAPI oficial Kubernetes 1.34.1; referencias, selectores, puertos, probes, ConfigMap, PVC y Secret revisados. |
| Grafana | JSON del dashboard válido; datasource, UID, métricas y rutas de provisioning revisados. |

El motor Docker no está instalado en este entorno. Por ello **no se ejecutaron las imágenes ni una prueba integral de los cinco contenedores**, ni se inició un clúster Kubernetes. Las comprobaciones de configuración no sustituyen esas ejecuciones. No se han creado recursos cloud ni repositorios remotos.

**Actualización (1 de noviembre de 2026):** el workflow `.github/workflows/verify.yml` se ejecutó en GitHub Actions y terminó en verde. El job **compose** ejecuta `tools/hexaorders.ps1 verify`: construye desde un directorio que contiene solo el YAML y sus secretos, ejecuta los tests dentro de los builds, comprueba la configuración con `promtool`, espera los cinco servicios sanos y ejecuta `tools/smoke.py`. Verifica pedidos, lectura posterior desde PostgreSQL, errores de validación, targets Prometheus y existencia del dashboard Grafana. También detiene temporalmente Python para comprobar la respuesta 503. El job **windows** comprueba en Windows Server el lanzador `hexaorders.cmd`, los scripts con Windows PowerShell 5.1, la generación de secretos, el entregable generado y los tests unitarios de Python y Java.

## Repetir las comprobaciones

Prueba completa con Docker Desktop iniciado, la misma que ejecuta GitHub, en un entorno aislado que se elimina al terminar (PowerShell o CMD en Windows):

```powershell
.\hexaorders.cmd verify
```

Para revisar el empaquetado con Python local:

```powershell
py -m pip install -r requirements-tools.txt
.\hexaorders.cmd check
```

Equivale a `py tools\generate_delivery.py --check` seguido de `py tools\validate_delivery.py`. En Linux o macOS se usan los mismos comandos con PowerShell 7: `pwsh ./tools/hexaorders.ps1 verify`.

Los argumentos opcionales `--compose-schema` y `--kubernetes-schema` aceptan copias locales de [compose-spec.json](https://github.com/compose-spec/compose-spec/blob/main/schema/compose-spec.json) y [swagger Kubernetes 1.34.1](https://github.com/kubernetes/kubernetes/blob/v1.34.1/api/openapi-spec/swagger.json). El validador de Kubernetes comprueba el esquema publicado, no admisión, permisos, capacidad ni estado de un clúster. Para estos últimos usa el contexto correcto y `kubectl apply --dry-run=server` con namespace y Secret preparados.

Para repetir los tests fuera de Docker instala JDK 21, Maven 3.9 y Python 3.12, ejecuta `py -m pip install -r services\risk-engine\requirements-dev.txt` y después `.\hexaorders.cmd test`, que lanza `pytest` en `services/risk-engine` y `mvn test` en `services/order-api`. Las pruebas Python HTTP locales pueden necesitar permitir los sockets internos del loop de eventos en un sandbox restrictivo.
