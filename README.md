# HexaOrders

Proyecto final: **arquitectura hexagonal estricta con Java y Python, íntegramente orquestado con Docker Compose**. Una interfaz web registra pedidos, consulta riesgo por HTTP y conserva sus decisiones en PostgreSQL. Prometheus y Grafana muestran disponibilidad, volumen, errores y latencias.

**Entrega principal: [docker-compose.yml](docker-compose.yml).** Incluye los fuentes y tests en Dockerfiles inline, y la configuración de monitoreo (Prometheus, alertas, datasource y dashboard Grafana) dentro de imágenes también construidas inline, para que los cuatro servicios propios (Java, Python, Prometheus y Grafana) funcionen con `read_only: true`. Puedes copiar únicamente este archivo a otra máquina; genera los secretos en el destino. No necesita los archivos `services/` ni `infra/` para desplegarlo. Requiere Docker con contenedores Linux, Compose >= 2.23.1 y BuildKit. Primera construcción con Internet; capacidad orientativa para la demo: 2 vCPU, 8 GB RAM y 30 GB disco.

## Parte 1 — Exposición sin diapositivas

[Guía para enseñar el funcionamiento a otra persona](docs/guia-para-explicar.md): recorrido de pedidos, conceptos, archivos, demostración, evidencias y preguntas de defensa.

[Guion verbal de 3–4 minutos](docs/presentacion.md), con definición, ventajas, costes, límites y casos empresariales documentados. [Fuentes primarias y alcance de las afirmaciones](docs/fuentes.md). Los casos Netflix Studio Workflows y Allegro están documentados en 2020: no se atribuye la misma arquitectura a toda la empresa ni se presenta como una auditoría de su estado en 2026.

## Parte 2 — YAML y arquitectura

- [YAML completo y autosuficiente](docker-compose.yml).
- [Explicación técnica siguiendo el orden del YAML](docs/arquitectura.md).
- [Estructura completa de archivos](docs/estructura.txt).
- Java: `services/order-api/{pom.xml,Dockerfile,src/main,src/test}`.
- Python: `services/risk-engine/{requirements.txt,Dockerfile,risk_engine,tests}`.
- Configuración editable: `infra/{compose.base.yml,prometheus,grafana}`.
- Adaptación Kubernetes: [`k8s/README.md`](k8s/README.md).
- Evidencia de revisión local: [`docs/validacion.md`](docs/validacion.md).

| Servicio | Responsabilidad | Comunicación |
|---|---|---|
| `order-api` | REST/UI Java 21 + Spring Boot; casos de uso y dominio independientes | HTTP Python + JDBC PostgreSQL |
| `risk-engine` | Python 3.12 + FastAPI; reglas deterministas con Decimal | `POST /score`; sin acceso a DB |
| `db` | PostgreSQL 17.11; esquema Flyway y restricciones | Red interna, sin puerto publicado |
| `prometheus` | Scrape, TSDB y reglas de alerta | Java `:9090/actuator/prometheus`, Python `:8000/metrics` |
| `grafana` | Dashboard provisionado | Datasource `http://prometheus:9090` |

La hexagonalidad se comprueba con ArchUnit e imports AST, no con el número de contenedores. El núcleo no importa frameworks, bases de datos ni adaptadores. El motor de riesgo responde con score; Java conserva la regla de estado del pedido. Importe <500: score10; 500–<1000: score40; >=1000: score75. Score>=70 implica `REVIEW`.

## Parte 3 — Arranque y repositorio

En Windows, instala Docker Desktop y activa contenedores Linux. Desde PowerShell en esta carpeta:

```powershell
.\tools\init-env.ps1
docker compose version
docker compose config --quiet
docker compose build --pull
docker compose up -d --wait --wait-timeout 240
docker compose ps
```

Si una política local bloquea scripts, abre el script para revisarlo y genera/carga `DB_PASSWORD` y `GRAFANA_PASSWORD` manualmente en el entorno de la sesión. No es necesario cambiar la política de ejecución de todo el equipo. En Linux y en la modalidad de entrega de un solo YAML, usa el procedimiento exacto de [despliegue en VM](docs/despliegue-cloud.md).

Accesos locales:

- Aplicación: <http://127.0.0.1:8080>.
- Grafana: <http://127.0.0.1:3000/d/hexaorders>; usuario `admin`, contraseña `GRAFANA_PASSWORD` de `.env`.
- Prometheus: <http://127.0.0.1:9091/targets> y <http://127.0.0.1:9091/alerts>.

Prueba desde PowerShell:

```powershell
$body = @{ customerId = 'demo-001'; amount = 125.50 } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/orders -Method Post -ContentType 'application/json' -Body $body
$body = @{ customerId = 'demo-001'; amount = 1500 } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/orders -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod http://127.0.0.1:8080/api/orders
```

Los dos pedidos deben quedar `APPROVED/10` y `REVIEW/75`. Genera tráfico unos 30 segundos para observar tasas e histogramas. Si tienes Python local, `python tools/smoke.py` comprueba creación, consulta, validación, scrapes y dashboard.

Para evidenciar el manejo de fallos:

```powershell
docker compose stop risk-engine
# Repite un POST: HTTP 503; el caso de uso no inserta el pedido.
docker compose start risk-engine
docker compose up -d --wait --wait-timeout 120
```

[Subir a tu cuenta GitHub: comandos exactos](docs/github.md). [Google Cloud: creación de VM, instalación, SCP, secretos y túnel](docs/despliegue-cloud.md). AWS y Azure utilizan el mismo procedimiento dentro de una VM Ubuntu equivalente. GitHub CLI, BuildKit y Compose V2 cubren este flujo; no se necesita un asistente de programación como dependencia operativa.

## Editar, verificar y reconstruir la entrega

No necesitas Java ni Python instalados en el host para desplegar: los builds Docker ejecutan sus respectivas pruebas. Para editar el repositorio y actualizar el YAML sí puedes usar Python 3.12 local:

```bash
python tools/generate_delivery.py
python tools/generate_delivery.py --check
docker compose config --quiet
docker compose build
docker compose up -d --wait --wait-timeout 240
```

La acción `.github/workflows/verify.yml` copia únicamente el YAML a un directorio vacío, lo construye, ejecuta las pruebas de ambos lenguajes, valida Prometheus con `promtool`, arranca los cinco servicios y comprueba HTTP, persistencia y dashboard. Su resultado solo se conocerá cuando ejecutes el workflow en GitHub.

Para detener sin eliminar datos: `docker compose down`. Conserva `.env` para volver a arrancar con las mismas contraseñas. `docker compose down -v` elimina los volúmenes y se reserva para un reinicio deliberado. Consulta los límites y decisiones de la práctica en [arquitectura](docs/arquitectura.md).
