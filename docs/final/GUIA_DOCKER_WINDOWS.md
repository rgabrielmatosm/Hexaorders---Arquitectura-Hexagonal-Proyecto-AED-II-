# POLYMER en Windows con Docker Desktop

Guía de instalación detallada (requisitos, WSL 2, memoria, migración desde la v6):
[`docs/WINDOWS_DOCKER_GUIDE.md`](../WINDOWS_DOCKER_GUIDE.md). Este documento resume la operación diaria.

POLYMER usa **contenedores Linux** sobre Docker Desktop (motor WSL 2). No se usan contenedores Windows.

## 1. Requisitos

- Windows 10/11 con Docker Desktop (WSL 2), **6 GB de RAM o más** asignados a Docker
  (`%UserProfile%\.wslconfig` → `[wsl2]` `memory=6GB`, después `wsl --shutdown`).
- PowerShell 5.1 (incluido en Windows) o PowerShell 7. `polymer.cmd` funciona aunque la política de
  ejecución bloquee los `.ps1`.
- Python 3 solo para `check` y `smoke`. **No** hace falta Java, Maven ni Node: se compila dentro de Docker.

## 2. Contenedores y responsabilidades

| # | Servicio | Imagen | Responsabilidad | Puerto en el equipo | Volumen |
| --- | --- | --- | --- | --- | --- |
| 1 | `frontend` | `polymer/frontend` (Nginx) | Gateway: estáticos y proxy a `order-api` | `127.0.0.1:8080` | — |
| 2 | `order-api` | `polymer/order-api` (Java 21) | Tienda, pedidos, roles, API, eventos | — (gestión 9090 interna) | — |
| 3 | `risk-engine` | `polymer/risk-engine` (Python) | Puntuación de riesgo | — | — |
| 4 | `db` | `postgres:17` | Datos de negocio | — | `polymer_postgres_data` |
| 5 | `redis` | `redis:7.4` | Caché del catálogo (TTL 60 s, LRU 48 MB) | — | `polymer_redis_data` |
| 6 | `rabbitmq` | `polymer/rabbitmq` | Eventos de negocio (outbox → cola → notificaciones) | `127.0.0.1:15672` (consola) | `polymer_rabbitmq_data` |
| 7 | `mailpit` | `axllent/mailpit` | Captura los correos de notificación | `127.0.0.1:8025` | — |
| 8 | `prometheus` | `polymer/prometheus` | Métricas y 9 alertas | `127.0.0.1:9091` | `polymer_prometheus_data` |
| 9 | `grafana` | `polymer/grafana` | 8 dashboards, Loki y Tempo | `127.0.0.1:3000` | `polymer_grafana_data` |
| 10 | `loki` | `polymer/loki` | Logs centralizados | — | `polymer_loki_data` |
| 11 | `tempo` | `polymer/tempo` | Trazas distribuidas | — | `polymer_tempo_data` |
| 12 | `otel-collector` | `polymer/otel-collector` | Recibe trazas OTLP y métricas de Nginx | — | — |
| 13 | `alloy` | `polymer/alloy` | Recoge los logs de los contenedores hacia Loki | — | `polymer_alloy_data` |

Todos tienen *healthcheck*, `restart: unless-stopped`, límite de memoria y (salvo `db`) sistema de
archivos de solo lectura. Redes internas: `gateway`, `data`, `compute`, `cache`, `messaging`, `telemetry`.
El modo `basic` arranca solo los 6 primeros de la lista del README (sin Redis, RabbitMQ, Mailpit, Loki,
Tempo, Collector ni Alloy).

## 3. Comandos (PowerShell, en la carpeta del proyecto)

| Comando | Qué hace |
| --- | --- |
| `.\polymer.cmd start` | Arranca los 13 contenedores (genera o recupera secretos, construye con pruebas y espera a que estén sanos) |
| `.\polymer.cmd stop` | Detiene **conservando los datos** |
| `.\polymer.cmd status` | Estado de los contenedores |
| `.\polymer.cmd health` | Estado + salud de cada contenedor y respuesta HTTP de tienda, API, Grafana, Prometheus, Mailpit y RabbitMQ; falla si algo no está sano |
| `.\polymer.cmd logs` | Últimas 100 líneas de cada servicio |
| `.\polymer.cmd rebuild -Services risk-engine` | Reconstruye solo ese servicio (sus pruebas se ejecutan en el build) y lo recrea sin tocar datos. Sin `-Services`: todos |
| `.\polymer.cmd restart -Services order-api` | Reinicia servicios concretos y espera a que estén sanos |
| `.\polymer.cmd test` | Pruebas unitarias locales (requiere Java/Maven y Python en el equipo) |
| `.\polymer.cmd check` | Validaciones sin Docker (YAML, páginas, marca, catálogo, idiomas, observabilidad) |
| `.\polymer.cmd smoke` | Pruebas HTTP contra tu entorno en marcha |
| `.\polymer.cmd verify` | Prueba integral en un entorno **aislado** (proyecto `polymer-verify`), igual que el CI |
| `.\polymer.cmd demo -Scenario preparar` | Prepara y comprueba la exposición (ver `GUIA_EXPOSICION_PROFESOR.md`) |
| `.\polymer.cmd demo-on` / `demo-off` | Modo demostración (TTL corto, adaptador de riesgo fijo) y vuelta a la normalidad |
| `.\polymer.cmd change-password -Service db` | Cambia una contraseña de forma segura (copia, cambio, verificación, restauración si falla) |
| `.\polymer.cmd help` | Ayuda completa |

Con Docker directamente (equivalentes):

```powershell
docker compose ps
```

```powershell
docker compose logs -f order-api
```

```powershell
docker compose up -d --build --wait risk-engine
```

## 4. Contraseñas

Archivos en `secrets\` (Docker Secrets), excluidos de Git y del ZIP. Se editan y consultan desde VS Code,
pero **editar el archivo no cambia la contraseña del servicio** (PostgreSQL, Grafana y RabbitMQ la guardan
en su volumen): usa `change-password`. Detalle: [`docs/SECRETOS.md`](../SECRETOS.md).

```
POLYMER/
├── .env.example            (configuración NO sensible; en local no hace falta .env)
├── docker-compose.yml
├── polymer.cmd
├── secrets/                (NO en Git)
│   ├── db_password.txt
│   ├── grafana_password.txt
│   ├── rabbitmq_password.txt
│   └── demo_password.txt
└── secrets.example/        (plantillas sin valores reales)
```

## 5. Reconstruir y actualizar sin perder datos

1. `git pull` (o descomprime la versión nueva **junto a** la anterior).
2. `.\polymer.cmd start`: reconstruye lo que cambió y conserva los volúmenes. Las migraciones Flyway son
   aditivas y se aplican solas.
3. `.\polymer.cmd health`.

Para un solo servicio: `.\polymer.cmd rebuild -Services order-api,frontend`.

## 6. Restaurar el entorno

| Situación | Acción (sin borrar datos) |
| --- | --- |
| Un contenedor *unhealthy* | `.\polymer.cmd logs`, luego `.\polymer.cmd restart -Services <servicio>` |
| `password authentication failed` en `order-api` | Restaura el `secrets\db_password.txt` original, o `.\polymer.cmd repair-db-password` (no borra datos) |
| Quedó activo el modo demostración | `.\polymer.cmd demo-off` |
| Quedó detenido un servicio tras un escenario de caída | `.\polymer.cmd start` (o `docker compose up -d --wait <servicio>`) |
| Cambios de una demostración de código | `git status`, `git apply -R tools/demo/regla-revision-desde-500.patch` y `.\polymer.cmd rebuild -Services risk-engine` |
| Docker Desktop no arranca | Reinicia Docker Desktop; comprueba WSL 2 (`wsl --status`) |

**Nunca** uses `docker compose down -v` ni `.\polymer.cmd stop -RemoveData` salvo que quieras borrar los
datos a propósito.

## 7. Errores frecuentes

| Error | Causa | Solución |
| --- | --- | --- |
| `dependency failed to start` | Un servicio del que depende otro no llegó a *healthy* | `.\polymer.cmd health` y `logs` del servicio dependiente; suele ser memoria insuficiente |
| `container is unhealthy` | Poca memoria o arranque lento | 6 GB para Docker; `restart -Services <servicio>` |
| `connection refused` en 8080 | `frontend` u `order-api` aún arrancando | Espera a `healthy` (`start` ya espera) |
| `no data` en Grafana | Sin tráfico reciente en esa serie | Genera tráfico (navega o `demo -Scenario observabilidad`); «no data» nunca es un cero inventado |
| `port is already allocated` | Otro programa usa 8080/3000/… | Ciérralo; `start` detiene HexaOrders v6 si está en marcha |
| `password authentication failed` | El archivo de secreto no coincide con el volumen | Ver sección 6 |
