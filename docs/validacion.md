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
| Fuentes y configs embebidos | Coinciden con archivos originales, incluidos escapes `$`; sin COPY de archivos del host ni bind mounts. |
| Kubernetes | **14 objetos** válidos según OpenAPI oficial Kubernetes 1.34.1; referencias, selectores, puertos, probes, ConfigMap, PVC y Secret revisados. |
| Grafana | JSON del dashboard válido; datasource, UID, métricas y rutas de provisioning revisados. |

El motor Docker no está instalado en este entorno. Por ello **no se ejecutaron las imágenes ni una prueba integral de los cinco contenedores**, ni se inició un clúster Kubernetes. Las comprobaciones de configuración no sustituyen esas ejecuciones. No se han creado recursos cloud ni repositorios remotos.

La acción `.github/workflows/verify.yml` queda preparada para construir desde un directorio que contiene solo el YAML, ejecutar los tests dentro de los builds, comprobar configuración con `promtool`, esperar los cinco servicios y ejecutar `tools/smoke.py`. Verifica pedidos, lectura posterior desde PostgreSQL, errores de validación, targets Prometheus y existencia del dashboard Grafana. También detiene temporalmente Python para comprobar la respuesta 503. El resultado del workflow está pendiente hasta que el usuario lo ejecute en GitHub.

## Repetir las comprobaciones

Prueba completa con Docker disponible, después de cargar los secretos:

```bash
docker compose config --quiet
docker compose build --pull
docker compose run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose up -d --wait --wait-timeout 240
python tools/smoke.py
```

Para revisar el empaquetado con Python local:

```bash
python -m pip install -r requirements-tools.txt
python tools/generate_delivery.py --check
python tools/validate_delivery.py
```

Los argumentos opcionales `--compose-schema` y `--kubernetes-schema` aceptan copias locales de [compose-spec.json](https://github.com/compose-spec/compose-spec/blob/main/schema/compose-spec.json) y [swagger Kubernetes 1.34.1](https://github.com/kubernetes/kubernetes/blob/v1.34.1/api/openapi-spec/swagger.json). El validador de Kubernetes comprueba el esquema publicado, no admisión, permisos, capacidad ni estado de un clúster. Para estos últimos usa el contexto correcto y `kubectl apply --dry-run=server` con namespace y Secret preparados.

Para repetir Java fuera de Docker: JDK 21 y Maven 3.9, `mvn -B -ntp verify` desde `services/order-api`. Para Python fuera de Docker: Python 3.12, instala `requirements-dev.txt` en un entorno virtual y ejecuta `python -m pytest -q -p no:cacheprovider` desde `services/risk-engine`. Las pruebas Python HTTP locales pueden necesitar permitir los sockets internos del loop de eventos en un sandbox restrictivo.
