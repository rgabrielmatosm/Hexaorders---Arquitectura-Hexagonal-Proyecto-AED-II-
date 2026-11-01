# Validación efectuada

Fecha: **19 de octubre de 2026**.

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
