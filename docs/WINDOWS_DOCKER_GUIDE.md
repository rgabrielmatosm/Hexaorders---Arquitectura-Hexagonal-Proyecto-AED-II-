# Guía de instalación y uso en Windows 10/11 con Docker Desktop

HexaOrders se instala y se maneja **solo con Windows, Docker Desktop y PowerShell**. No necesitas Ubuntu, una terminal de Linux ni comandos Bash. Internamente Docker Desktop ejecuta contenedores Linux sobre WSL 2, que es su funcionamiento normal en Windows y no requiere que uses Linux.

> **Estado de la verificación.** Los scripts de Windows se ejecutan en cada push en un runner `windows-2022` de GitHub con Windows PowerShell 5.1: análisis de los `.ps1`, `hexaorders.cmd help`, generación idempotente de secretos, `check` y tests unitarios. El arranque completo con Docker se prueba en Ubuntu con el **mismo** `tools/hexaorders.ps1 verify`, porque los runners Windows de GitHub no ejecutan contenedores Linux. **No se ha probado en un PC con Docker Desktop**: la sección 9 lista las comprobaciones manuales recomendadas.

## 1. Requisitos

| Requisito | Detalle |
| --- | --- |
| Windows | Windows 10 de 64 bits 22H2 o Windows 11, en ediciones compatibles con Docker Desktop. |
| Virtualización | Activada en la BIOS/UEFI (Intel VT-x o AMD-V). Compruébalo en *Administrador de tareas → Rendimiento → CPU → Virtualización: Habilitado*. |
| WSL 2 | Docker Desktop lo instala o lo pide. Si no, en PowerShell como administrador: `wsl --install` y reinicia. |
| Hardware orientativo | **Modo completo** (13 contenedores): 4 núcleos, 16 GB de RAM en el PC (6 GB o más asignados a Docker Desktop) y unos 30 GB libres. **Modo básico** (6 contenedores): 2 núcleos y 8 GB de RAM. Son estimaciones según los límites de Compose, no mediciones en Windows. |
| Docker Desktop | Versión actual con *Use the WSL 2 based engine* activado y contenedores **Linux** (el modo por defecto). |
| Internet | Solo en la primera construcción (descarga de imágenes y dependencias). |
| Opcional | Visual Studio Code y Git para Windows; Python 3.12 solo para `check` (validación). |

No necesitas instalar Java, Maven, Python, PostgreSQL, Prometheus ni Grafana: todo se construye y se ejecuta dentro de Docker.

## 2. Instalar y comprobar Docker Desktop

1. Descarga Docker Desktop desde la web oficial de Docker e instálalo con las opciones por defecto (WSL 2).
2. Reinicia si lo pide y abre Docker Desktop hasta que muestre **Engine running**.
3. Comprueba en PowerShell:

```powershell
docker version
```

```powershell
docker compose version
```

Ambos deben responder sin errores (Compose 2.23.1 o superior).

## 3. Descargar el proyecto

Con Git:

```powershell
git clone https://github.com/rgabrielmatosm/Hexaorders---Arquitectura-Hexagonal-Proyecto-AED-II-.git HexaOrders
```

```powershell
cd HexaOrders
```

Sin Git: descarga el ZIP desde GitHub (*Code → Download ZIP*), descomprímelo (por ejemplo en `C:\Proyectos\HexaOrders`) y abre PowerShell en esa carpeta (*Shift + clic derecho → Abrir en Terminal*).

> Evita carpetas sincronizadas que bloquean archivos (por ejemplo OneDrive en modo «solo en la nube»).

## 4. Arrancar

HexaOrders tiene dos modos (detalle en [`DOCKER_ARCHITECTURE.md`](DOCKER_ARCHITECTURE.md)):

| Modo | Comando | Contenedores |
| --- | --- | --- |
| **Completo** (por defecto) | `.\hexaorders.cmd start` | 13: los 5 originales, el gateway Nginx y Redis, RabbitMQ, Mailpit, Loki, Tempo, OpenTelemetry Collector y Alloy |
| **Básico** (equipos con poca RAM) | `.\hexaorders.cmd start -Mode basic` | 6: db, risk-engine, order-api, prometheus, grafana y el gateway Nginx |

```powershell
.\hexaorders.cmd start
```

Si tu equipo tiene 8 GB de RAM o menos:

```powershell
.\hexaorders.cmd start -Mode basic
```

Si usas el modo completo, asigna al menos 6 GB de memoria a Docker Desktop. Con el motor WSL 2 se configura en `%UserProfile%\.wslconfig`, por ejemplo `[wsl2]` con `memory=6GB`, y después se ejecuta `wsl --shutdown`.

Qué hace, en orden:

1. Comprueba que Docker responde.
2. **Secretos**: crea `secrets\db_password.txt`, `grafana_password.txt`, `demo_password.txt` y `rabbitmq_password.txt` con valores aleatorios si no existen (sección 6).
3. Valida `docker-compose.yml`.
4. Construye las imágenes; la construcción **ejecuta los tests** de Java y Python.
5. Arranca los servicios del modo elegido y espera a que todos estén sanos (healthchecks).

La primera vez tarda varios minutos. Después es mucho más rápido.

## 5. Usar la aplicación

| Dirección | Qué hay |
| --- | --- |
| <http://127.0.0.1:8080> | Sitio web: inicio, catálogo, portales por rol, monitoreo, arquitectura |
| <http://127.0.0.1:8080/laboratorio.html> | Laboratorio original (crear pedidos, explorador de arquitectura) |
| <http://127.0.0.1:3000> | Grafana (usuario `admin`, contraseña en `secrets\grafana_password.txt`) |
| <http://127.0.0.1:9091> | Prometheus (targets y alertas) |
| <http://127.0.0.1:8025> | **Mailpit** (modo completo): correos de notificación capturados; no se envían a Internet |
| <http://127.0.0.1:15672> | **RabbitMQ** (modo completo): usuario `hexaorders`, contraseña en `secrets\rabbitmq_password.txt` |
| Grafana → *Explore* → Loki / Tempo | **Logs y trazas** (modo completo); dashboard `/d/hexaorders-integrations` |

La entrada `http://127.0.0.1:8080` es ahora el gateway Nginx, que reenvía a Spring Boot. La URL es la misma de siempre.

**Cuentas de demostración**: `admin`, `operador`, `logistica`, `logistica2`, `observador` y `cliente`. Todas usan la contraseña de `secrets\demo_password.txt`:

```powershell
Get-Content .\secrets\demo_password.txt
```

También puedes crear tu propia cuenta de cliente en <http://127.0.0.1:8080/registro.html>.

### Comandos diarios

```powershell
.\hexaorders.cmd status
```

```powershell
.\hexaorders.cmd logs
```

```powershell
.\hexaorders.cmd stop
```

```powershell
.\hexaorders.cmd check
```

```powershell
.\hexaorders.cmd smoke
```

| Comando | Qué hace |
| --- | --- |
| `start` | Secretos, validación, construcción y arranque. |
| `status` | Estado de todos los contenedores del proyecto (equivale a `docker compose --profile full ps`). |
| `logs` | Últimas 100 líneas de cada servicio. |
| `stop` | Detiene los servicios **conservando los datos**. |
| `stop -RemoveData` | Detiene y **borra** pedidos, métricas y Grafana. Solo si quieres empezar de cero. |
| `check` | Comprueba que el entregable, la observabilidad y el sitio multipágina están coherentes (requiere Python 3.12 y `py -m pip install -r requirements-tools.txt`). |
| `test` | Tests unitarios fuera de Docker (requiere Java 21, Maven y Python). |
| `smoke` | Pruebas HTTP contra tu entorno en marcha: pedidos, plataforma y, en el modo completo, Redis, RabbitMQ, Mailpit, Loki, Tempo y OpenTelemetry (requiere Python 3.12). |
| `verify` | La prueba integral de GitHub en un entorno aislado que no toca tus datos (`-Mode basic` o `full`). |

Si prefieres usar Docker directamente, las órdenes equivalentes funcionan igual en PowerShell. Primero crea los secretos:

```powershell
.\tools\init-env.ps1
```

Modo básico:

```powershell
docker compose up --build -d
```

Modo completo (el perfil `full` añade los 7 servicios de infraestructura):

```powershell
$env:COMPOSE_PROFILES='full'; docker compose up --build -d
```

```powershell
docker compose ps
```

## 6. Secretos: cómo se crean y cómo se protegen

`tools\init-env.ps1` (lo llama `start`):

- comprueba cada ruta (`secrets\*.txt`) y **distingue archivo de directorio** con `Test-Path -PathType Leaf/Container`;
- si encuentra una **carpeta** con el nombre del secreto (pasa cuando Docker la crea al montar un archivo que no existía) y está vacía, la elimina y avisa; si no está vacía, se detiene sin borrar nada;
- **nunca sobrescribe** un secreto existente y se detiene si uno está vacío;
- toma el valor, por este orden, de un `.env` antiguo, de las variables `DB_PASSWORD`, `GRAFANA_PASSWORD`, `DEMO_PASSWORD` y `RABBITMQ_PASSWORD`, o genera 48 caracteres hexadecimales aleatorios;
- es idempotente: ejecutarlo dos veces no cambia nada.

**Protección de los datos.** PostgreSQL, Grafana y RabbitMQ guardan su contraseña al inicializar sus volúmenes (`hexaorders_postgres_data`, `hexaorders_grafana_data` y `hexaorders_rabbitmq_data`). Si un volumen existe pero falta su secreto, `start` **se detiene** y explica cómo restaurarlo. No genera una contraseña nueva, que dejaría el servicio sin acceso a sus datos. Nunca se usa `docker compose down -v` como solución automática.

Los secretos están en `.gitignore` y no se publican.

## 7. Persistencia y reinicios

- `.\hexaorders.cmd stop` y después `start` → los pedidos, usuarios, entregas, métricas y dashboards siguen ahí (volúmenes `postgres_data`, `prometheus_data` y `grafana_data`).
- Reiniciar Windows o Docker Desktop no borra los volúmenes.
- Las migraciones de base de datos (Flyway) se aplican solas al arrancar y **no son destructivas**.

## 8. Problemas frecuentes

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `docker` no se reconoce | Docker Desktop no instalado o terminal abierta antes de instalarlo | Instálalo, ábrelo y abre una terminal nueva. |
| *Cannot connect to the Docker daemon* | Docker Desktop cerrado o iniciando | Ábrelo y espera a **Engine running**. |
| Error de WSL al iniciar Docker | WSL 2 no instalado o virtualización desactivada | `wsl --install` (como administrador), reinicia y revisa la BIOS. |
| `scripts is disabled on this system` | Política de ejecución de PowerShell | Usa `.\hexaorders.cmd …`: aplica `-ExecutionPolicy Bypass` solo a ese proceso. |
| `secrets\db_password.txt` es una carpeta | Docker la creó porque el archivo no existía | `.\hexaorders.cmd start` la corrige si está vacía; si no, revisa su contenido y bórrala a mano. |
| `start` dice que existe `hexaorders_postgres_data` pero falta el secreto | Se borró o movió `secrets\db_password.txt` | Restaura el archivo original. Solo si aceptas perder los datos: `.\hexaorders.cmd stop -RemoveData`. |
| `dependency order-api failed to start`; los logs de `order-api` dicen `password authentication failed for user "orders"` | `secrets\db_password.txt` no es el mismo con el que se creó el volumen. **Todas las carpetas de HexaOrders comparten el volumen `hexaorders_postgres_data`** (proyecto Compose `hexaorders`), así que generar secretos nuevos en otra carpeta (por ejemplo, al pasar de la v4 a la v5) lo provoca. Desde esta versión, `start` lo detecta antes de arrancar. | Copia el `db_password.txt` original de la carpeta anterior, o ejecuta `.\hexaorders.cmd repair-db-password`: cambia la contraseña de PostgreSQL al valor actual del archivo, pide confirmación y **no borra datos**. |
| Grafana no acepta la contraseña de `secrets\grafana_password.txt` | El mismo caso con el volumen `hexaorders_grafana_data` | Copia el `grafana_password.txt` original de la carpeta anterior. |
| Puerto 8080, 3000 o 9091 ocupado | Otro programa usa el puerto | Ciérralo o detén el otro contenedor (`docker ps`). |
| La construcción falla al descargar | Sin Internet o proxy corporativo | Configura el proxy en Docker Desktop → *Settings → Resources → Proxies*. |
| El monitoreo web dice «Prometheus no responde» | Prometheus todavía arrancando o detenido | `.\hexaorders.cmd status`; espera unos segundos y recarga. |

Para diagnosticar: `.\hexaorders.cmd logs`, o el panel *Containers* de Docker Desktop.

## 9. Comprobaciones manuales recomendadas en Docker Desktop

Lo siguiente lo cubren las pruebas automáticas en GitHub (Ubuntu con Docker y Windows sin Docker), pero conviene confirmarlo una vez en tu PC:

1. `.\hexaorders.cmd start` termina con los 13 contenedores `healthy` (`.\hexaorders.cmd status`). Con `-Mode basic`, los 6 del modo básico.
2. <http://127.0.0.1:8080> carga la portada; inicia sesión como `cliente` y compra un producto con dirección.
3. Como `operador`, confirma el pedido; como `logistica`, prográmalo en el calendario y márcalo en tránsito y entregado.
4. Como `observador`, abre *Monitoreo → Servicios*: los targets en UP (9 en el modo completo).
5. Grafana: el dashboard original (`/d/hexaorders`) y los siete nuevos, incluido `/d/hexaorders-integrations`, cargan con datos.
6. `.\hexaorders.cmd stop` y `.\hexaorders.cmd start`: el pedido sigue ahí.
7. `docker compose stop risk-engine`: un pedido nuevo responde 503 y no se guarda; `docker compose start risk-engine` lo recupera.
8. **Modo completo:** tras la compra del paso 2, <http://127.0.0.1:8025> muestra los correos «Pedido … recibido» y «… aprobado».
9. **Modo completo:** la cabecera `X-Trace-Id` de una respuesta de la API (herramientas de desarrollo del navegador → Red) se abre en *Grafana → Explore → Tempo*, con spans de order-api y risk-engine.
10. **Modo completo:** en *Grafana → Explore → Loki*, la consulta `{service="order-api"}` muestra logs recientes.
11. **Modo completo:** `docker compose stop redis rabbitmq` → el catálogo y las compras siguen funcionando; `docker compose start redis rabbitmq` → llegan los correos pendientes.
12. `.\hexaorders.cmd smoke` termina con «Pruebas HTTP correctas».
