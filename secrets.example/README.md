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

Cómo crearlas, verlas, recuperarlas o cambiarlas: [`docs/SECRETOS.md`](../docs/SECRETOS.md).
