# ADR 0013 — Recolección de logs con Grafana Alloy (servicio número 13)

**Contexto.** Loki no lee logs por sí mismo: alguien tiene que enviárselos. Debe funcionar en **Docker Desktop para Windows** sin instalar nada en el host. Debe cubrir Spring Boot, FastAPI, Nginx, RabbitMQ y el resto de contenedores, y no enviar secretos.

**Opciones evaluadas.**

| Opción | Problema |
| --- | --- |
| Plugin `loki` de Docker como *logging driver* | Hay que instalarlo en el host de Docker. En Docker Desktop no es reproducible desde el repositorio y un fallo del plugin puede bloquear los contenedores. |
| Leer `/var/lib/docker/containers/*.log` | En Windows esa ruta vive dentro de la VM de Docker Desktop: no es portable. |
| OTLP de logs desde cada aplicación | No cubre Nginx, RabbitMQ ni PostgreSQL, y requiere librerías en cada servicio. |
| **Grafana Alloy con la API de Docker** (elegida) | Necesita el socket de Docker en solo lectura. |

**Decisión.** Contenedor `alloy` (perfil `full`) con `/var/run/docker.sock` montado en **solo lectura**. Docker Desktop lo expone a los contenedores Linux también en Windows.

- **Ámbito.** Solo descubre los contenedores con la etiqueta `com.polymer.logs=true`, añadida a todos los servicios del proyecto: nunca lee contenedores ajenos.
- **Etiquetas.** Añade `service`, `project`, `container` y `stream`.
- **Privacidad.** Sustituye por `<redacted>` cualquier valor que siga a `password`, `secret`, `token`, `authorization`, `cookie` o `jsessionid`, y envía el resultado a Loki.
- **Endurecimiento.** Se ejecuta como root sin capacidades (`cap_drop: ALL`), con `read_only` y sin puertos publicados.

**Riesgo aceptado.** Acceder al socket de Docker equivale a poder consultar el motor de Docker; el modo de solo lectura de la montura no restringe la API. Se mitiga así:

- Alloy solo existe en el perfil `full`.
- No publica puertos y está solo en la red interna `telemetry`.
- Su configuración solo usa endpoints de lectura (descubrimiento y logs).
- `validate_delivery.py` permite esta montura **únicamente** en `alloy` y en modo `:ro`.

**Consecuencias.** El modo completo tiene 13 contenedores (los 12 del enunciado más Alloy), lo que se considera aceptable: es la pieza que hace real la ingesta de Loki. `integration_smoke.py` verifica los logs de `order-api`, `risk-engine` y `frontend` en Loki, y que no aparece ningún secreto.
