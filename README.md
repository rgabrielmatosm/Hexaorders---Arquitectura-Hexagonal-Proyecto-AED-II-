# HexaOrders

Proyecto final: **arquitectura hexagonal estricta con Java y Python, íntegramente orquestado con Docker Compose**. Una interfaz web registra pedidos, consulta riesgo por HTTP y conserva sus decisiones en PostgreSQL. Prometheus y Grafana muestran disponibilidad, volumen, errores y latencias.

**Entrega principal: [docker-compose.yml](docker-compose.yml).** Incluye los fuentes y tests en Dockerfiles inline, y la configuración de monitoreo (Prometheus, alertas, datasource y dashboard Grafana) dentro de imágenes también construidas inline, para que los cuatro servicios propios (Java, Python, Prometheus y Grafana) funcionen con `read_only: true`. Puedes copiar únicamente este archivo a otra máquina y generar en el destino los dos secretos en `secrets/db_password.txt` y `secrets/grafana_password.txt`. No necesita los archivos `services/` ni `infra/` para desplegarlo. Requiere Docker con contenedores Linux, Compose >= 2.23.1 y BuildKit. Primera construcción con Internet; capacidad orientativa para la demo: 2 vCPU, 8 GB RAM y 30 GB disco.

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

En Windows, instala [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) con contenedores Linux (opción predeterminada, con WSL 2) y ábrelo hasta que indique *Engine running*. Después, desde **PowerShell o CMD** en esta carpeta:

```powershell
.\hexaorders.cmd start
```

`hexaorders.cmd` ejecuta [`tools/hexaorders.ps1`](tools/hexaorders.ps1). Ese comando genera los secretos si faltan, valida el YAML, construye las imágenes (con los tests de Java y Python) y arranca los cinco servicios esperando a que estén sanos. Funciona con Windows PowerShell 5.1, que viene con Windows, y con PowerShell 7. El lanzador usa `-ExecutionPolicy Bypass` solo para ese proceso, así que no hace falta cambiar la política de ejecución del equipo.

| Comando | Qué hace |
| --- | --- |
| `.\hexaorders.cmd start` | Secretos + validación + build + arranque de los cinco servicios. |
| `.\hexaorders.cmd status` | Estado de los contenedores. |
| `.\hexaorders.cmd logs` | Últimas 100 líneas de log de cada servicio. |
| `.\hexaorders.cmd stop` | Detiene los servicios conservando los datos. |
| `.\hexaorders.cmd stop -RemoveData` | Detiene y **elimina** pedidos, métricas y datos de Grafana. |
| `.\hexaorders.cmd verify` | La misma prueba integral que ejecuta GitHub, en un entorno aislado que se limpia al terminar. |
| `.\hexaorders.cmd check` | Comprueba que el entregable generado está al día y es válido (requiere Python). |
| `.\hexaorders.cmd test` | Tests unitarios locales (requiere Python, Java 21 y Maven). |
| `.\hexaorders.cmd help` | Ayuda completa. |

Los secretos son `secrets\db_password.txt` y `secrets\grafana_password.txt`, con valores aleatorios y excluidos de Git. Si existe un `.env` de una versión anterior, el script conserva sus contraseñas. Si prefieres los comandos de Docker directamente, son equivalentes a `.\tools\init-env.ps1` seguido de `docker compose config --quiet`, `docker compose build --pull` y `docker compose up -d --wait --wait-timeout 240`. Los mismos comandos funcionan en Linux y macOS con PowerShell 7 (`pwsh ./tools/hexaorders.ps1 start`). Para desplegar en una VM Ubuntu en la nube usa el procedimiento de [despliegue en VM](docs/despliegue-cloud.md).

Accesos locales:

- Aplicación: <http://127.0.0.1:8080>.
- Grafana: <http://127.0.0.1:3000/d/hexaorders>; usuario `admin`, contraseña en `secrets\grafana_password.txt`.
- Prometheus: <http://127.0.0.1:9091/targets> y <http://127.0.0.1:9091/alerts>.

Prueba desde PowerShell:

```powershell
$body = @{ customerId = 'demo-001'; amount = 125.50 } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/orders -Method Post -ContentType 'application/json' -Body $body
$body = @{ customerId = 'demo-001'; amount = 1500 } | ConvertTo-Json
Invoke-RestMethod http://127.0.0.1:8080/api/orders -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod http://127.0.0.1:8080/api/orders
```

Los dos pedidos deben quedar `APPROVED/10` y `REVIEW/75`. Genera tráfico unos 30 segundos para observar tasas e histogramas. Si tienes Python local, `py tools\smoke.py` comprueba creación, consulta, validación, scrapes y dashboard (`.\hexaorders.cmd verify` lo ejecuta automáticamente).

Para evidenciar el manejo de fallos:

```powershell
docker compose stop risk-engine
# Repite un POST: HTTP 503; el caso de uso no inserta el pedido.
docker compose start risk-engine
docker compose up -d --wait --wait-timeout 120
```

[Subir a tu cuenta GitHub: comandos exactos](docs/github.md). [Google Cloud: creación de VM, instalación, SCP, secretos y túnel](docs/despliegue-cloud.md). AWS y Azure utilizan el mismo procedimiento dentro de una VM Ubuntu equivalente. GitHub CLI, BuildKit y Compose V2 cubren este flujo; no se necesita un asistente de programación como dependencia operativa.

## Editar, verificar y reconstruir la entrega

No necesitas Java ni Python instalados en el host para desplegar: los builds Docker ejecutan sus respectivas pruebas. Para editar el repositorio y regenerar el YAML necesitas [Python 3.12](https://www.python.org/downloads/) local. Desde PowerShell:

```powershell
py tools\generate_delivery.py
py -m pip install -r requirements-tools.txt
.\hexaorders.cmd check
.\hexaorders.cmd verify
```

Tras modificar `services/` o `infra/`, regenera siempre con `generate_delivery.py`. El archivo `.gitattributes` fija saltos de línea LF y el generador ordena los archivos igual en cualquier sistema operativo, de modo que el YAML generado en Windows es idéntico byte a byte al que comprueba GitHub.

La acción [`.github/workflows/verify.yml`](.github/workflows/verify.yml) tiene dos jobs:

- **compose** (Ubuntu): ejecuta `tools/hexaorders.ps1 verify`, el mismo script que usas en Windows. Copia únicamente el YAML a un directorio vacío, genera sus secretos, construye con los tests de ambos lenguajes, valida Prometheus con `promtool`, arranca los cinco servicios, comprueba HTTP, persistencia, métricas y dashboard, y verifica el HTTP 503 cuando cae risk-engine. GitHub solo ejecuta contenedores Linux en runners Ubuntu; es lo mismo que hace Docker Desktop en Windows con WSL 2.
- **windows** (Windows Server 2022): comprueba `hexaorders.cmd`, los scripts con Windows PowerShell 5.1, la generación de secretos, que el entregable generado en Windows coincide y es válido, y los tests unitarios de Python y Java.

Para detener sin eliminar datos: `.\hexaorders.cmd stop`. Conserva `secrets\` para volver a arrancar con las mismas contraseñas. `.\hexaorders.cmd stop -RemoveData` elimina los volúmenes y se reserva para un reinicio deliberado. Consulta los límites y decisiones de la práctica en [arquitectura](docs/arquitectura.md).
