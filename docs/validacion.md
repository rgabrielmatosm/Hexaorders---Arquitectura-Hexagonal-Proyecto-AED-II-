# Validación efectuada

Este registro separa cuatro tipos de evidencia: **ejecución real**, **pruebas automáticas**, **revisión estática** y **pendiente de verificación**. Un resultado esperado nunca se presenta como observado.

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
