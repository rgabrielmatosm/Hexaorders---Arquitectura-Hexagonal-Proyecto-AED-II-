# Gestión de credenciales locales (`secrets/`)

Las contraseñas de POLYMER son **archivos locales** en `secrets/`, junto a `docker-compose.yml`. Docker los monta como secretos de solo lectura en `/run/secrets/`.

- No están en Git, ni en el ZIP, ni en el README.
- **Ningún comando las imprime.**

## 1. Auditoría: qué servicios usan credenciales

| Servicio | ¿Inicio de sesión? | Credencial | Exposición |
| --- | --- | --- | --- |
| PostgreSQL | Sí (scram-sha-256) | `secrets/db_password.txt` (usuario `orders`) | Solo red interna `data`: nunca publicado |
| Grafana | Sí | `secrets/grafana_password.txt` (usuario `admin`) | `127.0.0.1:3000` |
| RabbitMQ | Sí | `secrets/rabbitmq_password.txt` (usuario heredado `hexaorders`) | Consola en `127.0.0.1:15672`; AMQP solo interno |
| Tienda: cuentas de demostración | Sí (BCrypt en PostgreSQL) | `secrets/demo_password.txt`, **solo al crearlas** | `127.0.0.1:8080` |
| Clientes de la tienda | Sí (BCrypt) | La elige cada cliente, con la política de la sección 6 | `127.0.0.1:8080` |
| Redis | No | — | Solo red interna `cache` |
| Prometheus | No | — | `127.0.0.1:9091` (solo este equipo) |
| Mailpit | No | — | `127.0.0.1:8025` (solo este equipo) |
| Loki, Tempo, OpenTelemetry Collector, Alloy | No | — | Solo redes internas |

**Por qué no se añaden contraseñas al resto:**

- Los puertos publicados solo escuchan en `127.0.0.1`: otros equipos de la red no los alcanzan.
- Los servicios sin puerto publicado solo son accesibles desde las redes internas de Docker.
- Añadir autenticación a Redis, Prometheus o Loki obligaría a cambiar los healthchecks, los exportadores, las fuentes de datos de Grafana y los scrapers, y aumentaría el riesgo de dejar la observabilidad sin datos.
- **Si alguna vez se publicara POLYMER fuera de este equipo**, lo recomendable es poner autenticación en el gateway (Nginx) y no abrir esos puertos.

## 2. Ver y editar las credenciales desde VS Code

1. Abre la carpeta del proyecto en VS Code (`code .`).
2. En el explorador, abre `secrets/`. Cada archivo contiene **una sola línea**: la contraseña, sin salto de línea al final.
3. Para copiar una contraseña, por ejemplo para entrar en Grafana, ábrela y cópiala. **No la pegues** en el README, en issues ni en mensajes.

**Editar a mano un archivo NO cambia la contraseña del servicio**: el servicio la guardó al crear su volumen. Para cambiarla usa `change-password` (sección 5).

Plantillas sin credenciales reales: [`secrets.example/`](../secrets.example/).

## 3. Instalación nueva

Recomendado: contraseñas aleatorias de 48 caracteres, generadas automáticamente al arrancar.

```powershell
.\polymer.cmd start
```

**Si prefieres contraseñas que puedas recordar**, elígelas **antes** del primer arranque:

```powershell
.\polymer.cmd secrets -Memorable
```

Para cada servicio puedes elegir:

- **[1] Frase memorable generada:** cinco palabras al azar unidas por guiones, más un número y un símbolo (con el formato `Palabra-Palabra-Palabra-Palabra-Palabra-NN#`).
- **[2] Escribirla tú:** se pide dos veces, de forma oculta.
- **[Enter] Aleatoria de 48 caracteres.**

**Reglas** (las comprueba el script):

- entre 16 y 64 caracteres;
- sin espacios, comillas ni `$`, porque RabbitMQ y los archivos de configuración no los admiten bien;
- **una contraseña distinta para cada servicio**;
- no uses ejemplos de documentación ni datos personales.

`-Memorable` solo actúa sobre servicios **sin datos**: si ya existe un volumen con datos, se niega y te remite a la sección 4.

## 4. Recuperar las credenciales de una instalación anterior

Si descomprimes POLYMER en otra carpeta, `start` detecta que hay datos (volúmenes `polymer_*` o los heredados `hexaorders_*`) y busca la carpeta `secrets/` anterior junto al proyecto. Te pide confirmación y copia solo lo que falta. Si está en otro sitio:

```powershell
.\polymer.cmd import-secrets -SecretsFrom "C:\ruta\de\la\carpeta\anterior"
```

**Nunca** se generan contraseñas nuevas para servicios con datos: dejarían los datos inaccesibles. Detalle: [`MIGRACION_POLYMER.md`](MIGRACION_POLYMER.md).

## 5. Cambiar una contraseña existente

Con POLYMER en marcha (`.\polymer.cmd start`), ejecuta el comando del servicio que quieras cambiar:

```powershell
.\polymer.cmd change-password -Service db
```

```powershell
.\polymer.cmd change-password -Service grafana
```

```powershell
.\polymer.cmd change-password -Service rabbitmq
```

El comando hace, en orden:

1. Pide la nueva contraseña dos veces, de forma oculta, y comprueba las reglas de la sección 3.
2. Pide confirmación.
3. **Copia de seguridad:** guarda la credencial actual en `secrets/backup/<archivo>.<fecha>.bak`. Ninguno de estos cambios toca los datos.
4. Cambia la contraseña **en el servicio**:
   - PostgreSQL, con `ALTER ROLE` por stdin;
   - Grafana, con su API autenticada con la contraseña actual;
   - RabbitMQ, con su API de gestión.

   La contraseña nunca va en la línea de comandos.
5. Comprueba que el servicio acepta la nueva contraseña, actualiza el archivo de `secrets/` y, si hace falta (PostgreSQL y RabbitMQ), reinicia `order-api` y espera a que esté sano.
6. Si algo falla, **restaura la contraseña anterior**.

**Cuentas de demostración:** `demo_password.txt` solo se usa al crearlas. Para cambiar la de una cuenta, entra con ella y ve a **Mi cuenta → Perfil y seguridad**.

## 6. Contraseñas de los clientes de la tienda

Al registrarse y al cambiar la contraseña, el **servidor** aplica `PasswordPolicy`, siguiendo NIST SP 800-63B. El indicador del navegador es solo una ayuda.

| Regla | Detalle |
| --- | --- |
| Longitud | De 12 a 64 caracteres, y como máximo 72 bytes (límite de BCrypt) |
| Frases de contraseña | Admitidas, con espacios incluidos; **no** se exigen símbolos arbitrarios |
| Contraseñas conocidas | Se rechazan las de la lista local `static/security/common-passwords.txt` y sus variantes («Password2026!») |
| Datos personales | No puede contener el usuario, el nombre ni el correo |
| Repeticiones | Se rechazan `aaaaaaaaaaaa`, `123456789012`, `abcdefghijkl`… |
| Fuerza | **Débil** (rechazada), **aceptable** o **fuerte**. Recomendación: frase larga o combinación de letras, números y símbolos |

- **Hash:** BCrypt (coste 12), sin cambios.
- **Cuentas existentes:** conservan su contraseña y su hash. La política se aplica solo a contraseñas nuevas.
- **Contraseñas filtradas:** no se consultan servicios externos como Have I Been Pwned. La tienda funciona sin Internet y nunca envía contraseñas fuera; por eso se usa la lista local.
