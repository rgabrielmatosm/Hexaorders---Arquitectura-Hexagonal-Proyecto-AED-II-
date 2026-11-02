# Guía de instalación y uso en Windows 10/11 con Docker Desktop

HexaOrders se instala y se maneja **solo con Windows, Docker Desktop y PowerShell**. No necesitas Ubuntu, una terminal de Linux ni comandos Bash. Internamente Docker Desktop ejecuta contenedores Linux sobre WSL 2, que es su funcionamiento normal en Windows y no requiere que uses Linux.

> **Estado de la verificación.** Los scripts de Windows se ejecutan en cada push en un runner `windows-2022` de GitHub con Windows PowerShell 5.1: análisis de los `.ps1`, `hexaorders.cmd help`, generación idempotente de secretos, `check` y tests unitarios. El arranque completo con Docker se prueba en Ubuntu con el **mismo** `tools/hexaorders.ps1 verify`, porque los runners Windows de GitHub no ejecutan contenedores Linux. **No se ha probado en un PC con Docker Desktop**: la sección 9 lista las comprobaciones manuales recomendadas.

## 1. Requisitos

| Requisito | Detalle |
| --- | --- |
| Windows | Windows 10 de 64 bits 22H2 o Windows 11, en ediciones compatibles con Docker Desktop. |
| Virtualización | Activada en la BIOS/UEFI (Intel VT-x o AMD-V). Compruébalo en *Administrador de tareas → Rendimiento → CPU → Virtualización: Habilitado*. |
| WSL 2 | Docker Desktop lo instala o lo pide. Si no, en PowerShell como administrador: `wsl --install` y reinicia. |
| Hardware orientativo | 2 vCPU, 8 GB de RAM y unos 30 GB libres. |
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

```powershell
.\hexaorders.cmd start
```

Qué hace, en orden:

1. Comprueba que Docker responde.
2. **Secretos**: crea `secrets\db_password.txt`, `secrets\grafana_password.txt` y `secrets\demo_password.txt` con valores aleatorios si no existen (sección 6).
3. Valida `docker-compose.yml`.
4. Construye las imágenes; la construcción **ejecuta los tests** de Java y Python.
5. Arranca los cinco servicios y espera a que todos estén sanos.

La primera vez tarda varios minutos. Después es mucho más rápido.

## 5. Usar la aplicación

| Dirección | Qué hay |
| --- | --- |
| <http://127.0.0.1:8080> | Sitio web: inicio, catálogo, portales por rol, monitoreo, arquitectura |
| <http://127.0.0.1:8080/laboratorio.html> | Laboratorio original (crear pedidos, explorador de arquitectura) |
| <http://127.0.0.1:3000> | Grafana (usuario `admin`, contraseña en `secrets\grafana_password.txt`) |
| <http://127.0.0.1:9091> | Prometheus (targets y alertas) |

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

| Comando | Qué hace |
| --- | --- |
| `start` | Secretos, validación, construcción y arranque. |
| `status` | Estado de los cinco contenedores (equivale a `docker compose ps`). |
| `logs` | Últimas 100 líneas de cada servicio. |
| `stop` | Detiene los servicios **conservando los datos**. |
| `stop -RemoveData` | Detiene y **borra** pedidos, métricas y Grafana. Solo si quieres empezar de cero. |
| `check` | Comprueba que el entregable, la observabilidad y el sitio multipágina están coherentes (requiere Python 3.12 y `py -m pip install -r requirements-tools.txt`). |
| `test` | Tests unitarios fuera de Docker (requiere Java 21, Maven y Python). |
| `verify` | La prueba integral de GitHub, en un entorno aislado que no toca tus datos. |

Si prefieres usar Docker directamente, las órdenes equivalentes funcionan igual en PowerShell. Primero crea los secretos:

```powershell
.\tools\init-env.ps1
```

```powershell
docker compose up --build -d
```

```powershell
docker compose ps
```

## 6. Secretos: cómo se crean y cómo se protegen

`tools\init-env.ps1` (lo llama `start`):

- comprueba cada ruta (`secrets\*.txt`) y **distingue archivo de directorio** con `Test-Path -PathType Leaf/Container`;
- si encuentra una **carpeta** con el nombre del secreto (pasa cuando Docker la crea al montar un archivo que no existía) y está vacía, la elimina y avisa; si no está vacía, se detiene sin borrar nada;
- **nunca sobrescribe** un secreto existente y se detiene si uno está vacío;
- toma el valor, por este orden, de un `.env` antiguo, de las variables `DB_PASSWORD`, `GRAFANA_PASSWORD` y `DEMO_PASSWORD`, o genera 48 caracteres hexadecimales aleatorios;
- es idempotente: ejecutarlo dos veces no cambia nada.

**Protección de la base de datos.** PostgreSQL guarda su contraseña al inicializar el volumen `hexaorders_postgres_data`. Si ese volumen existe pero falta `secrets\db_password.txt`, `start` **se detiene** y explica cómo restaurarlo, en lugar de generar una contraseña nueva que dejaría la API sin acceso a los datos. Nunca se usa `docker compose down -v` como solución automática.

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
| Puerto 8080, 3000 o 9091 ocupado | Otro programa usa el puerto | Ciérralo o detén el otro contenedor (`docker ps`). |
| La construcción falla al descargar | Sin Internet o proxy corporativo | Configura el proxy en Docker Desktop → *Settings → Resources → Proxies*. |
| El monitoreo web dice «Prometheus no responde» | Prometheus todavía arrancando o detenido | `.\hexaorders.cmd status`; espera unos segundos y recarga. |

Para diagnosticar: `.\hexaorders.cmd logs`, o el panel *Containers* de Docker Desktop.

## 9. Comprobaciones manuales recomendadas en Docker Desktop

Lo siguiente lo cubren las pruebas automáticas en GitHub (Ubuntu con Docker y Windows sin Docker), pero conviene confirmarlo una vez en tu PC:

1. `.\hexaorders.cmd start` termina con los cinco servicios `healthy` (`.\hexaorders.cmd status`).
2. <http://127.0.0.1:8080> carga la portada; inicia sesión como `cliente` y compra un producto con dirección.
3. Como `operador`, confirma el pedido; como `logistica`, prográmalo en el calendario y márcalo en tránsito y entregado.
4. Como `observador`, abre *Monitoreo → Servicios*: los tres targets en UP.
5. Grafana: el dashboard original (`/d/hexaorders`) y los seis nuevos cargan con datos.
6. `.\hexaorders.cmd stop` y `.\hexaorders.cmd start`: el pedido sigue ahí.
7. `docker compose stop risk-engine`: un pedido nuevo responde 503 y no se guarda; `docker compose start risk-engine` lo recupera.
