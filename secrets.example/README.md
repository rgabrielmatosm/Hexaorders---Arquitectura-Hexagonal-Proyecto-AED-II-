# Plantillas de `secrets/` (sin credenciales reales)

POLYMER guarda sus contraseñas en la carpeta **`secrets/`**, junto a `docker-compose.yml`. Esa carpeta:

- está excluida de Git (`.gitignore`) y de los ZIP que genera `git archive`;
- la crea `.\polymer.cmd start` (o `.\polymer.cmd secrets`) **solo si faltan** archivos: nunca sobrescribe uno existente.

Los archivos de esta carpeta son **ejemplos**: muestran el nombre y el formato esperado (una sola línea, sin salto final), pero **no son credenciales válidas**. No los copies tal cual.

| Archivo en `secrets/` | Servicio | Cuándo se usa |
| --- | --- | --- |
| `db_password.txt` | PostgreSQL (usuario `orders`) | Al crear el volumen y en cada conexión de `order-api` |
| `grafana_password.txt` | Grafana (usuario `admin`) | Al crear el volumen de Grafana |
| `rabbitmq_password.txt` | RabbitMQ (usuario heredado `hexaorders`) | Al crear el volumen de RabbitMQ y en cada conexión de `order-api` |
| `demo_password.txt` | Las seis cuentas de demostración de la tienda | Solo la primera vez, al crear esas cuentas |
| `google-oauth.json` *(v8, opcional, recomendado)* | «Continuar con Google»: el JSON del cliente OAuth (*Aplicación web*) que descargas de Google Cloud | En cada arranque de `order-api` (Docker secret `google_oauth`). Vacío = botón oculto. `.\polymer.cmd google` lo detecta (también una descarga `client_secret_*.json` en `secrets\`, en la carpeta del proyecto o en Descargas) y lo valida sin mostrar valores. **Nunca** en Git ni en los ZIP |
| `google_client_id.txt` *(v7, opcional)* | «Continuar con Google» (OpenID Connect) | En cada arranque de `order-api`. Vacío = botón oculto |
| `google_client_secret.txt` *(v7, opcional)* | «Continuar con Google» | Igual que el anterior; nunca se muestra en la interfaz ni en los logs |
| `smtp_password.txt` *(v7, opcional)* | Relé SMTP real (p. ej. contraseña de aplicación de Gmail) | Vacío = Mailpit (los correos se ven en http://127.0.0.1:8025) |

Los cuatro archivos opcionales se crean **vacíos** automáticamente (vacío = integración desactivada) y nunca se
sobrescriben. Cómo obtener sus valores: [`docs/final/GUIA_GOOGLE_Y_PAGOS.md`](../docs/final/GUIA_GOOGLE_Y_PAGOS.md).

Cómo crearlas, verlas, recuperarlas o cambiarlas: [`docs/SECRETOS.md`](../docs/SECRETOS.md).
