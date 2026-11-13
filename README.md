<div align="center">

<img src="services/order-api/src/main/resources/static/img/polymer-logo-light.svg" alt="POLYMER" width="340">

# POLYMER — Sistema de Gestión de Pedidos de Productos Tecnológicos

**Tecnología que conecta contigo.**

Tienda de tecnología y plataforma de gestión de pedidos con **arquitectura hexagonal** (Java 21 + Python), **PostgreSQL**, **13 contenedores Docker** y observabilidad completa (Prometheus, Grafana, Loki, Tempo y OpenTelemetry).

[Instalación en Windows](#8-instalación-en-windows) · [Ejecución](#10-ejecución) · [Arquitectura](#4-arquitectura-hexagonal) · [Roles](#12-roles) · [Pruebas](#13-pruebas) · [Errores frecuentes](#15-solución-de-errores-frecuentes)

<img src="docs/assets/screenshots/polymer-inicio.png" alt="Portada de la tienda POLYMER" width="820">

</div>

---

## Índice

1. [Presentación del proyecto](#1-presentación-del-proyecto)
2. [Objetivos](#2-objetivos)
3. [Características](#3-características)
4. [Arquitectura hexagonal](#4-arquitectura-hexagonal)
5. [Tecnologías utilizadas](#5-tecnologías-utilizadas)
6. [Estructura de carpetas](#6-estructura-de-carpetas)
7. [Servicios Docker (13 contenedores)](#7-servicios-docker-13-contenedores)
8. [Instalación en Windows](#8-instalación-en-windows)
9. [Configuración de secretos](#9-configuración-de-secretos)
10. [Ejecución](#10-ejecución)
11. [Acceso a la tienda](#11-acceso-a-la-tienda)
12. [Roles](#12-roles)
13. [Pruebas](#13-pruebas)
14. [Observabilidad](#14-observabilidad)
15. [Solución de errores frecuentes](#15-solución-de-errores-frecuentes)
16. [Idiomas](#16-idiomas)
17. [Seguridad de contraseñas](#17-seguridad-de-contraseñas)
18. [Exposición, demostraciones y documentación final](#18-exposición-demostraciones-y-documentación-final)
19. [Novedades v7: Google, código por correo, pagos, boleta, mapa, horario y video](#19-novedades-v7-google-código-por-correo-pagos-boleta-mapa-horario-y-video)

Anexos: [API](#anexo-a-api) · [Documentación técnica](#anexo-b-documentación-técnica) · [Historial y migración](#anexo-c-historial-y-migración)

---

## 1. Presentación del proyecto

**POLYMER** es una tienda virtual de tecnología con la plataforma interna que la opera. En ella:

- **el cliente** compra en un catálogo de **556 productos** (20 categorías y 170 subcategorías);
- **un motor de riesgo** en Python evalúa cada pedido;
- **operaciones** lo revisa y lo hace avanzar por su ciclo de vida;
- **logística** programa y entrega los pedidos;
- **administración** gestiona productos, promociones, usuarios y reportes;
- **monitoreo** observa la salud técnica con métricas, logs y trazas.

El proyecto está construido para demostrar en una sustentación universitaria que la **arquitectura hexagonal** mantiene las reglas de negocio independientes de la tecnología:

- el dominio no conoce Spring, JDBC, HTTP ni RabbitMQ;
- cambiar un adaptador (base de datos, caché, mensajería) no toca las reglas;
- pruebas automáticas, incluidas pruebas de arquitectura con ArchUnit, lo comprueban en cada cambio.

<p align="center"><img src="docs/assets/screenshots/polymer-intro.png" alt="Intro de POLYMER" width="640"></p>

## 2. Objetivos

1. **Separar el negocio de la tecnología.** Reglas de precios, promociones, stock, transiciones de pedidos, entregas y riesgo en el dominio, con puertos y adaptadores (Cockburn, 2005).
2. **Ofrecer una tienda realista**: búsqueda con filtros técnicos, carrito persistente, checkout con métodos de entrega y pago, y seguimiento del pedido.
3. **Cubrir el ciclo operativo completo** con roles y permisos: cliente, administración, operaciones, logística (dos operadores) y monitoreo.
4. **Garantizar la consistencia**:
   - transacciones;
   - idempotencia del checkout (`Idempotency-Key`);
   - control de concurrencia;
   - outbox transaccional para los eventos.
5. **Hacerlo observable**: métricas de negocio y técnicas, 9 alertas, 8 dashboards, logs centralizados y trazas distribuidas.
6. **Ejecutarse con un solo comando** en Windows (`.\polymer.cmd start`), sin perder nunca los datos del usuario.

## 3. Características

| Área | Qué incluye |
| --- | --- |
| **Tienda** | Portada, megamenú, buscador por nombre, marca, modelo o SKU; filtros (marca, precio, disponibilidad, ofertas y atributos técnicos); ficha técnica; ofertas; precios en soles con promociones aplicadas en el servidor |
| **Compra** | Carrito guardado en PostgreSQL, que fusiona el del visitante; checkout con entrega estándar, express o recojo, y pago contra entrega, por transferencia o en tienda; número de pedido `HX-…`; seguimiento paso a paso; cancelación |
| **Identidad POLYMER** | Logotipo propio; **intro cinematográfica** con apertura de telón; animación del nombre; sonido de bienvenida opcional (Web Audio); transiciones entre páginas; fondos premium; diseño adaptable a móvil; paneles con identidad común |
| **Pie comercial** | 5 columnas, redes sociales, métodos de pago (disponibles y «próximamente»), páginas de empresa y legales, y **Libro de reclamaciones** funcional |
| **Administración** | Productos con ficha completa, categorías en árbol, inventario con ajustes auditados, promociones con vigencia, usuarios y roles, pedidos, reportes por moneda, auditoría y mensajes |
| **Operaciones** | Bandeja de pedidos, revisión de riesgo, incidencias y cambios de estado validados por el dominio |
| **Logística** | Calendario mensual, semanal y diario; asignaciones por responsable (`logistica`, `logistica2`); programación y reprogramación sin choques; despacho y entrega |
| **Monitoreo** | Salud de dependencias; targets, alertas e indicadores leídos de Prometheus; enlaces a Grafana; documentación técnica interna |
| **Integraciones** | Caché del catálogo en Redis, eventos de negocio en RabbitMQ (outbox, DLQ, consumidor idempotente), correos en Mailpit, trazas OTLP a Tempo y logs a Loki |
| **Seguridad** | Sesión con CSRF, BCrypt, permisos por rol en las páginas y en la API, secretos como archivos de solo lectura, contenedores `read_only`, PostgreSQL, Redis y AMQP no publicados en el equipo |
| **Idiomas** | Español (por defecto), inglés, francés, italiano, portugués y alemán, con rutas reales (`/es/productos`, `/en/products`, `/fr/produits`…), selector en el pie y en la cabecera, SEO multilingüe y correos en el idioma del cliente ([sección 16](#16-idiomas)) |
| **Contraseñas** | Mínimo 12 caracteres, frases de contraseña admitidas, indicador débil/aceptable/fuerte, mostrar/ocultar, detección de contraseñas comunes y validación en el servidor ([sección 17](#17-seguridad-de-contraseñas)) |

<p align="center"><img src="docs/assets/screenshots/polymer-confirmacion.png" alt="Confirmación de pedido" width="640"></p>

## 4. Arquitectura hexagonal

El **interior** (dominio y casos de uso) define **puertos**, que son interfaces. El **exterior** aporta **adaptadores** que los implementan (salida) o los usan (entrada). Las dependencias del código siempre apuntan hacia dentro.

| Capa | Paquete (`com.polymer`) | Ejemplos |
| --- | --- | --- |
| **Dominio** | `domain` | `CommerceOrder`, `TransitionPolicy`, `PriceTag`, `CatalogSearch`, `CheckoutSummary`, `Delivery`, `Promotion` |
| **Aplicación**: casos de uso | `application.service` | `CommerceService`, `CartService`, `PricingService`, `OperationsService`, `LogisticsService`, `AdministrationService` |
| **Puertos de entrada** | `application.port.in` | `CommerceUseCases`, `CatalogUseCases`, `LogisticsUseCases`… |
| **Puertos de salida** | `application.port.out` | `CommerceOrderRepository`, `RiskAssessmentPort`, `CatalogCache`, `EventPublisher`, `MailSender`, `MonitoringPort` |
| **Adaptadores de entrada** | `adapter.in` | Controladores REST, oyente de RabbitMQ, planificador del outbox |
| **Adaptadores de salida** | `adapter.out` | JDBC (PostgreSQL), HTTP al motor de riesgo, Redis, RabbitMQ, SMTP, Micrometer, Prometheus |
| **Composición** | `config` | Ensambla los adaptadores con los casos de uso (único lugar que conoce ambos lados) |

```mermaid
flowchart LR
    subgraph IN["Adaptadores de entrada"]
        Web["Controladores REST<br/>(tienda, admin, operaciones, logística, monitoreo)"]
        Listener["Oyente de RabbitMQ"]
    end
    subgraph CORE["Núcleo: aplicación + dominio (Java puro)"]
        PortsIn[["Puertos de entrada"]]
        Services["Casos de uso"]
        Domain["Dominio: pedidos, precios, catálogo,<br/>transiciones, entregas"]
        PortsOut[["Puertos de salida"]]
    end
    subgraph OUT["Adaptadores de salida"]
        Jdbc["JDBC → PostgreSQL"]
        Http["HTTP → motor de riesgo (Python)"]
        Cache["Redis"]
        Mq["RabbitMQ / SMTP"]
        Tel["Micrometer / Prometheus"]
    end
    Web --> PortsIn
    Listener --> PortsIn
    Services -- implementa --> PortsIn
    Services --> Domain
    Services --> PortsOut
    Jdbc & Http & Cache & Mq & Tel -- implementan --> PortsOut
```

**Flujo de una compra:**

1. `ShopController` recibe el checkout y llama al puerto de entrada `CommerceUseCases`.
2. `CommerceService` precia con `PricingService`, valida stock y entrega (`CheckoutSummary`) y consulta el riesgo por `RiskAssessmentPort`.
3. Guarda el pedido y el evento (outbox) en una sola transacción a través de `CommerceOrderRepository`.
4. Un planificador publica el evento en RabbitMQ, y el oyente envía el correo por `MailSender`.

El motor de riesgo en Python repite el mismo patrón: `domain/risk.py`, el caso de uso `ScoreRisk` y el adaptador FastAPI.

**La arquitectura se verifica automáticamente:**

- `HexagonalArchitectureTest` (ArchUnit) falla si `domain` o `application` importan Spring, JDBC, Jackson, HTTP o Micrometer, o si un adaptador de entrada usa uno de salida;
- `test_architecture.py` hace lo mismo en Python.

Más detalle en [`docs/HEXAGONAL_ARCHITECTURE.md`](docs/HEXAGONAL_ARCHITECTURE.md) y en las [ADR](docs/adr/).

## 5. Tecnologías utilizadas

| Capa | Tecnología |
| --- | --- |
| Servicio principal | Java 21 · Spring Boot 3.5 (Web, JDBC, Security, AMQP, Actuator) · Flyway |
| Motor de riesgo | Python 3.12 · FastAPI · Pydantic · prometheus_client |
| Datos | PostgreSQL 17 (fuente de verdad) · Redis 7 (caché) |
| Mensajería y correo | RabbitMQ 4 (exchange topic, DLQ) · Mailpit |
| Frontend | HTML5, CSS3 y JavaScript nativo (módulos ES), sin frameworks; Web Animations, View Transitions, IntersectionObserver y Web Audio; servido por Nginx |
| Observabilidad | Micrometer · Prometheus · Grafana · Loki · Tempo · OpenTelemetry Collector · Grafana Alloy |
| Contenedores | Docker · Docker Compose (perfiles, healthchecks, secretos, redes internas, `read_only`) · Kubernetes (manifiestos opcionales) |
| Pruebas | JUnit 5 · Mockito · ArchUnit · pytest · Playwright · pruebas de humo en Python |
| Automatización | PowerShell (`polymer.cmd`) · GitHub Actions (Ubuntu con Docker y Windows) |

## 6. Estructura de carpetas

```
.
├── polymer.cmd                 ← lanzador para Windows (hexaorders.cmd es un alias)
├── docker-compose.yml          ← entregable autosuficiente (GENERADO por tools/generate_delivery.py)
├── services/
│   ├── order-api/              ← Java 21 · Spring Boot
│   │   └── src/main/java/com/polymer/
│   │       ├── domain/         ← reglas de negocio puras
│   │       ├── application/    ← casos de uso y puertos (port/in, port/out)
│   │       ├── adapter/        ← adaptadores de entrada y de salida
│   │       └── config/         ← composición de dependencias
│   │   └── src/main/resources/
│   │       ├── db/migration/   ← Flyway V1–V9 (no destructivas)
│   │       └── static/         ← 45 páginas, CSS y módulos JavaScript (identidad POLYMER)
│   └── risk-engine/            ← Python · FastAPI (dominio, caso de uso, adaptador)
├── infra/                      ← compose.base.yml, Nginx, Prometheus, Grafana, Loki, Tempo, OTel, Alloy, RabbitMQ
├── k8s/                        ← manifiestos de Kubernetes (namespace polymer)
├── tools/                      ← polymer.ps1, generadores, validadores, pruebas de humo y e2e
└── docs/                       ← arquitectura, Docker, Windows, ADR, marca, migración e historial
```

## 7. Servicios Docker (13 contenedores)

Proyecto de Compose **`polymer`** (contenedores `polymer-<servicio>-1`, imágenes `polymer/*`).

| # | Servicio | Función | Acceso desde el equipo |
| --- | --- | --- | --- |
| 1 | `frontend` | Gateway Nginx: archivos estáticos y reenvío a Spring | http://localhost:8080 |
| 2 | `order-api` | Núcleo hexagonal (Java 21 / Spring Boot) | (a través de `frontend`) |
| 3 | `risk-engine` | Motor de riesgo (FastAPI) | — (red interna) |
| 4 | `db` | PostgreSQL 17 | — (nunca publicado) |
| 5 | `prometheus` | Métricas y 9 alertas | http://localhost:9091 |
| 6 | `grafana` | 8 dashboards (Prometheus, Loki, Tempo) | http://localhost:3000 |
| 7 | `redis` | Caché del catálogo | — |
| 8 | `rabbitmq` | Eventos de negocio y cola de notificaciones con DLQ | http://localhost:15672 (consola) |
| 9 | `mailpit` | Captura los correos (no envía a Internet) | http://localhost:8025 |
| 10 | `loki` | Logs centralizados | — |
| 11 | `tempo` | Trazas distribuidas | — |
| 12 | `otel-collector` | Recepción OTLP y métricas de Nginx | — |
| 13 | `alloy` | Recolector de logs de Docker hacia Loki | — |

**Volúmenes persistentes:** `polymer_postgres_data`, `polymer_grafana_data`, `polymer_prometheus_data`, `polymer_redis_data`, `polymer_rabbitmq_data`, `polymer_loki_data`, `polymer_tempo_data` y `polymer_alloy_data`.

**Redes internas:** `gateway`, `data`, `compute`, `cache`, `messaging` y `telemetry`.

El modo `-Mode basic` arranca solo los 6 primeros. Detalle en [`docs/DOCKER_ARCHITECTURE.md`](docs/DOCKER_ARCHITECTURE.md) y [`docs/DOCKER_SERVICES.md`](docs/DOCKER_SERVICES.md).

## 8. Instalación en Windows

1. **Docker Desktop:**
   - instálalo con el motor WSL 2 y contenedores Linux;
   - asígnale al menos **6 GB de RAM** para los 13 contenedores. Con WSL 2 se configura en `%UserProfile%\.wslconfig` con `[wsl2]` y `memory=6GB`, y después se ejecuta `wsl --shutdown`;
   - ábrelo y espera a ver **Engine running**.
2. **Python 3** (https://www.python.org/downloads/, marca *Add python.exe to PATH*). Solo lo usan los comandos `check` y `smoke`.
3. **Descomprime el proyecto**, por ejemplo en `C:\Users\TU_USUARIO\Downloads\POLYMER`, y abre PowerShell en esa carpeta.

No necesitas instalar Java, Maven ni Node: las imágenes se construyen dentro de Docker y ejecutan las pruebas al construirse. Guía completa: [`docs/WINDOWS_DOCKER_GUIDE.md`](docs/WINDOWS_DOCKER_GUIDE.md).

> **¿Venías de HexaOrders v6?** Descomprime POLYMER **junto a** la carpeta anterior y ejecuta `.\polymer.cmd start`.
>
> - El script encuentra las contraseñas originales y te pide confirmación para copiarlas.
> - Después copia y verifica tus volúmenes, sin borrar los originales.
>
> Guía: [`docs/MIGRACION_POLYMER.md`](docs/MIGRACION_POLYMER.md).

## 9. Configuración de secretos

Las contraseñas son **archivos** en `secrets\`. Esa carpeta está excluida de Git y del ZIP del proyecto, y Docker la monta como secreto de solo lectura.

| Archivo | Para qué |
| --- | --- |
| `db_password.txt` | PostgreSQL |
| `grafana_password.txt` | Usuario `admin` de Grafana |
| `demo_password.txt` | Las seis cuentas de demostración de la tienda |
| `rabbitmq_password.txt` | Consola y conexión de RabbitMQ |

- **Primera instalación:** `start` genera valores aleatorios de 48 caracteres. Para crearlos sin arrancar: `.\polymer.cmd secrets`.
- **Nunca se sobrescribe** un archivo existente, porque PostgreSQL, Grafana y RabbitMQ guardan la contraseña al inicializar sus volúmenes.
- **Si hay datos pero falta la contraseña**, `start` se detiene antes de tocar nada y explica cómo recuperarla. Busca automáticamente la carpeta `secrets\` de una instalación anterior, o puedes indicarla con `.\polymer.cmd import-secrets -SecretsFrom "C:\ruta"`.
- **Para ver una contraseña**, abre el archivo con el Bloc de notas o con VS Code (`code .` y luego la carpeta `secrets\`). No la publiques ni la subas a Git.
- **Instalación nueva con contraseñas que puedas recordar:** antes del primer `start`, ejecuta `.\polymer.cmd secrets -Memorable`. Para cada servicio ofrece una frase generada al azar (cinco palabras, un número y un símbolo), escribir la tuya o dejar la aleatoria. Exige una contraseña **distinta para cada servicio** y **nunca la muestra en pantalla**. Solo actúa sobre servicios sin datos.
- **Plantillas sin credenciales reales:** [`secrets.example/`](secrets.example/).
- **Qué servicios usan contraseña:** PostgreSQL, Grafana, RabbitMQ y las cuentas de la tienda. Redis, Prometheus, Mailpit, Loki, Tempo, el Collector y Alloy no tienen inicio de sesión y solo escuchan en `127.0.0.1` o en redes internas de Docker. Auditoría completa en [`docs/SECRETOS.md`](docs/SECRETOS.md#1-auditoría-qué-servicios-usan-credenciales).

**Cambiar una contraseña de forma segura** (con POLYMER en marcha):

```powershell
.\polymer.cmd change-password -Service db
```

Usa `-Service grafana` o `-Service rabbitmq` para los otros servicios. El comando:

1. pide la nueva contraseña dos veces, oculta, y pide confirmación;
2. guarda una copia de la actual en `secrets\backup\`;
3. la cambia en el servicio, comprueba que funciona y solo entonces actualiza el archivo;
4. si algo falla, restaura la anterior.

Editar el archivo a mano **no** cambia la contraseña del servicio. Detalle en [`docs/SECRETOS.md`](docs/SECRETOS.md#5-cambiar-una-contraseña-existente).

## 10. Ejecución

```powershell
.\polymer.cmd start
```

Arranca la **plataforma completa con los 13 contenedores**: genera o recupera los secretos, migra los datos anteriores si los hay, valida el YAML, construye las imágenes (con sus pruebas) y espera a que los 13 servicios estén sanos.

| Comando | Qué hace |
| --- | --- |
| `.\polymer.cmd start` | Arranca la plataforma completa (13 contenedores) |
| `.\polymer.cmd start -Mode basic` | Solo los 6 servicios esenciales (equipos con poca RAM) |
| `.\polymer.cmd status` | Estado de los contenedores |
| `.\polymer.cmd health` | Salud real de cada contenedor y de las interfaces (tienda, API, Grafana, Prometheus, Mailpit, RabbitMQ) |
| `.\polymer.cmd rebuild -Services risk-engine` | Reconstruye solo esos servicios (con sus pruebas) y los recrea **sin tocar datos** |
| `.\polymer.cmd restart -Services order-api` | Reinicia servicios concretos conservando los datos |
| `.\polymer.cmd demo -Scenario preparar` | Escenarios de la exposición con evidencias (sección 18) |
| `.\polymer.cmd demo-on` / `demo-off` | Modo demostración: TTL corto de la caché y adaptador de riesgo fijo; y vuelta a la normalidad |
| `.\polymer.cmd logs` | Últimas 100 líneas de cada servicio |
| `.\polymer.cmd stop` | Detiene todo **conservando los datos** |
| `.\polymer.cmd smoke` | Pruebas HTTP contra tu entorno en marcha |
| `.\polymer.cmd check` | Validación de archivos sin Docker (YAML, páginas, marca, catálogo, observabilidad) |
| `.\polymer.cmd verify` | Prueba integral en un entorno aislado, igual que el CI |
| `.\polymer.cmd import-secrets` / `migrate` | Recuperar contraseñas o migrar volúmenes de una instalación anterior |
| `.\polymer.cmd repair-db-password` | Alinea la contraseña de PostgreSQL con `db_password.txt`, sin borrar datos y con confirmación |
| `.\polymer.cmd help` | Ayuda completa |

**Verificar los 13 contenedores:**

```powershell
.\polymer.cmd status
```

Deben aparecer los 13 servicios (`db`, `risk-engine`, `order-api`, `frontend`, `prometheus`, `grafana`, `redis`, `rabbitmq`, `mailpit`, `otel-collector`, `tempo`, `loki` y `alloy`) con el estado **Up (healthy)**. `start` ya espera a que todos estén sanos y termina con error si alguno no lo consigue; en ese caso, `.\polymer.cmd logs` muestra la causa.

`.\polymer.cmd stop -RemoveData` **borra** los datos de POLYMER. Úsalo solo si quieres empezar de cero; nunca toca los volúmenes `hexaorders_*`.

En Linux o macOS: `pwsh ./tools/polymer.ps1 start`.

## 11. Acceso a la tienda

| URL | Qué hay |
| --- | --- |
| http://localhost:8080 | Tienda POLYMER (la intro aparece una vez por sesión; «Ver la intro» en el pie la repite) |
| http://localhost:8080/en/ · `/fr/` · `/it/` · `/pt/` · `/de/` | La tienda en inglés, francés, italiano, portugués o alemán (`/es/` en español) |
| http://localhost:8080/login.html | Inicio de sesión: cuentas de la sección 12, contraseña en `secrets\demo_password.txt` |
| http://localhost:3000 | Grafana: usuario `admin`, contraseña en `secrets\grafana_password.txt` |
| http://localhost:9091 | Prometheus: targets y alertas |
| http://localhost:8025 | Mailpit: correos de confirmación de pedidos |
| http://localhost:15672 | Consola de RabbitMQ |

## 12. Roles

| Cuenta | Rol | Qué puede hacer |
| --- | --- | --- |
| `cliente` | Cliente | Registrarse, comprar, carrito, checkout, mis pedidos, seguimiento, cancelar, perfil y contraseña |
| `admin` | Administrador | Productos, categorías, inventario, promociones, usuarios y roles, pedidos, reportes, auditoría, mensajes y Libro de reclamaciones; además, acceso a todas las áreas |
| `operador` | Operador de pedidos | Bandeja, revisión de riesgo, incidencias y transiciones de estado |
| `logistica` | Logística 1 (Ana) | Calendario, asignaciones, programación, reprogramación, despacho y entrega |
| `logistica2` | Logística 2 (Bruno) | Lo mismo, con su propia carga asignada |
| `observador` | Monitoreo | Salud técnica, servicios, métricas, alertas, Grafana y documentación técnica (solo lectura) |

Los permisos se comprueban en el servidor, en Spring Security y en el dominio. Ocultar un botón no basta: `SecurityRulesTest` y `pages-e2e.js` recorren las páginas con cada rol.

<p align="center"><img src="docs/assets/screenshots/polymer-panel.png" alt="Panel de monitoreo" width="640"></p>

## 13. Pruebas

| Tipo | Herramienta | Qué comprueba |
| --- | --- | --- |
| Dominio y casos de uso | JUnit 5 + Mockito (163 pruebas) | Precios y promociones, búsqueda, checkout, transiciones por rol, idempotencia, stock, calendario, notificaciones por idioma, política de contraseñas, rutas localizadas, seguridad |
| **Arquitectura** | ArchUnit (`HexagonalArchitectureTest`) y `test_architecture.py` | El núcleo no depende de frameworks; los adaptadores de entrada solo usan puertos de entrada |
| Motor de riesgo | pytest (62 pruebas) | Reglas, decimales exactos, validación y métricas |
| API real | `smoke.py`, `platform_smoke.py`, `store_smoke.py`, `integration_smoke.py` | Pedidos, roles, tienda, carrito, checkout, Redis, RabbitMQ, Mailpit, Loki, Tempo y Grafana |
| Interfaz | Playwright: `ui-e2e.js`, `pages-e2e.js` (las 45 páginas con los 6 tipos de usuario), `intro-e2e.js` (intro, animaciones, sonido opcional, movimiento reducido, móvil y pie) e `i18n-e2e.js` (seis idiomas, SEO, selector, carrito y checkout en inglés, contraseñas y móvil) | Navegador real contra el stack real |
| Entregable | `check` | YAML generado al día, Kubernetes, observabilidad conservada, páginas, marca, catálogo y traducciones completas (`check_i18n.py`) |
| Integral | `verify` | Los 13 contenedores sanos, todas las pruebas de humo, degradación sin Redis o RabbitMQ, persistencia tras reiniciar y HTTP 503 sin el motor de riesgo |
| **Migración** | Job del CI `migrate-from-v6` | Parte de HexaOrders v6 con datos y pasa a POLYMER desde otra carpeta sin `secrets\`: contraseñas recuperadas, volúmenes verificados, 13 contenedores y datos intactos |

```powershell
.\polymer.cmd check
```

```powershell
.\polymer.cmd verify
```

El workflow [`verify.yml`](.github/workflows/verify.yml) ejecuta todo en cada cambio, en Ubuntu con Docker y en Windows. Los resultados reales están en [`docs/validacion.md`](docs/validacion.md).

## 14. Observabilidad

- **Métricas** (Prometheus): de negocio (`polymer_*`: pedidos por estado, ventas, transiciones, entregas, incidencias, caché y eventos) y técnicas (JVM, HTTP, motor de riesgo, Nginx, RabbitMQ). Hay **9 alertas**, por ejemplo servicio caído, errores 5xx, latencia, outbox atascado o mensajes en la DLQ.
- **Dashboards** (Grafana, como código):
  - `polymer` (puertos y adaptadores);
  - negocio, riesgo, API, infraestructura, seguridad, logística e integraciones.
- **Logs** (Loki, a través de Alloy) y **trazas** (Tempo, a través de OpenTelemetry), correlacionados por `traceId`. La cabecera `X-Trace-Id` acompaña cada respuesta.
- **Portal de monitoreo** en la propia tienda (rol `observador`), que lee Prometheus a través de `MonitoringPort`.

Más detalle en [`docs/OBSERVABILITY_STACK.md`](docs/OBSERVABILITY_STACK.md) y [`docs/OBSERVABILITY_PRESERVATION.md`](docs/OBSERVABILITY_PRESERVATION.md).

## 15. Solución de errores frecuentes

| Síntoma | Causa | Solución |
| --- | --- | --- |
| «Existe el volumen … pero falta secrets\db_password.txt» | Hay datos de una instalación anterior y en esta carpeta no están sus contraseñas (el ZIP nunca las incluye) | Copia la carpeta `secrets\` anterior, o ejecuta `.\polymer.cmd import-secrets -SecretsFrom "C:\ruta\anterior"` y luego `start`. **No** borres volúmenes. Ver [`MIGRACION_POLYMER.md`](docs/MIGRACION_POLYMER.md) |
| `order-api` no arranca: «password authentication failed» | `db_password.txt` no coincide con la contraseña con que se creó la base | Restaura el archivo original, o `.\polymer.cmd repair-db-password` (no borra datos) |
| «Docker no responde» | Docker Desktop cerrado o iniciándose | Ábrelo y espera a *Engine running* |
| Un contenedor queda *unhealthy* o se reinicia | Poca memoria para los 13 contenedores | Asigna 6 GB o más a Docker (`.wslconfig`, sección 8), o usa `start -Mode basic` |
| «port is already allocated» (8080, 3000…) | Otro programa, o HexaOrders v6 en marcha | `start` detiene HexaOrders automáticamente; para otros programas, ciérralos o cambia su puerto |
| Docker crea una **carpeta** `secrets\db_password.txt` | Se arrancó Docker antes de crear los archivos | `.\polymer.cmd secrets` lo detecta y lo corrige sin borrar contenido |
| No se ve la intro | Ya se vio en esta sesión, o Windows tiene desactivados los *Efectos de animación* | Usa «Ver la intro» en el pie o http://localhost:8080/?intro=1. Con movimiento reducido se omite a propósito |
| No se oye la bienvenida | El sonido es opcional y está apagado por defecto | Pulsa «Entrar con sonido» o el botón de altavoz de la cabecera |
| Cambios que no se ven | Caché del navegador | `Ctrl + F5` |
| «Página no encontrada» al escribir una dirección en otro idioma | Cada idioma tiene sus propias rutas (`/en/products`, no `/en/productos`) | Usa el selector de idioma del pie o de la cabecera: lleva a la página equivalente. Lista de rutas en `static/i18n/routes.json` |
| Un texto aparece en español en otro idioma | Es un dato registrado que no se traduce (nombre o modelo de un producto, texto escrito por el administrador), o falta en el catálogo de traducciones | Los nombres propios se conservan a propósito. Para textos de la interfaz, `python tools/check_i18n.py --missing en` lista lo que falta |
| «La contraseña no es segura» al registrarse | La política del servidor la rechaza: menos de 12 caracteres, contraseña conocida, contiene tu usuario, nombre o correo, o es repetitiva | Usa una frase larga, por ejemplo cuatro o cinco palabras sin relación entre sí. El indicador muestra el motivo exacto |
| `change-password` termina con error | El servicio no estaba en marcha, o la contraseña no cumple las reglas | Arranca con `start` y repite. Si falló a mitad, el comando ya restauró la contraseña anterior; la copia está en `secrets\backup\` |

Diagnóstico: `.\polymer.cmd status` y `.\polymer.cmd logs`. Más casos en [`docs/WINDOWS_DOCKER_GUIDE.md`](docs/WINDOWS_DOCKER_GUIDE.md#8-problemas-frecuentes).

## 16. Idiomas

| Idioma | Código | Ejemplo de ruta |
| --- | --- | --- |
| Español (por defecto) | `es` | http://localhost:8080/es/productos |
| English | `en` | http://localhost:8080/en/products |
| Français | `fr` | http://localhost:8080/fr/produits |
| Italiano | `it` | http://localhost:8080/it/prodotti |
| Português | `pt` | http://localhost:8080/pt/produtos |
| Deutsch | `de` | http://localhost:8080/de/produkte |

**Cambiar de idioma.** Usa el menú de idiomas del pie (con el nombre de cada idioma) o el selector compacto de la cabecera (`ES`, `EN`…). El cambio:

- lleva a la **misma página** en el otro idioma y conserva los filtros y la búsqueda (`?marca=Lenovo`);
- conserva el **carrito**, la **sesión** y los **permisos**;
- **no repite la intro**;
- con sesión iniciada, guarda el idioma en tu cuenta para que los **correos** de tus pedidos lleguen en ese idioma.

**Estructura de rutas.** Cada página tiene una ruta real por idioma, que funciona al escribirla, al recargar y al navegar:

| Página | es | en | fr | it | pt | de |
| --- | --- | --- | --- | --- | --- | --- |
| Inicio | `/es/` | `/en/` | `/fr/` | `/it/` | `/pt/` | `/de/` |
| Catálogo | `/es/productos` | `/en/products` | `/fr/produits` | `/it/prodotti` | `/pt/produtos` | `/de/produkte` |
| Carrito | `/es/carrito` | `/en/cart` | `/fr/panier` | `/it/carrello` | `/pt/carrinho` | `/de/warenkorb` |
| Checkout | `/es/checkout` | `/en/checkout` | `/fr/paiement` | `/it/cassa` | `/pt/finalizar-compra` | `/de/kasse` |
| Iniciar sesión | `/es/iniciar-sesion` | `/en/login` | `/fr/connexion` | `/it/accedi` | `/pt/entrar` | `/de/anmelden` |

Las 45 páginas están en [`static/i18n/routes.json`](services/order-api/src/main/resources/static/i18n/routes.json). Las URL anteriores (`/catalogo.html`…) siguen funcionando en español.

- **Permisos:** una página protegida lleva al inicio de sesión **en el mismo idioma**; la de otro rol responde 403 traducido.
- **SEO:** `<html lang>`, título y descripción traducidos, `canonical`, `hreflang` de los seis idiomas más `x-default`, [`/sitemap.xml`](http://localhost:8080/sitemap.xml) multilingüe y `robots.txt`. Las páginas internas llevan `noindex`.
- **Qué no se traduce:** marcas, modelos, SKU, nombres propios, identificadores técnicos y los textos que escribe el administrador. Importes, reglas de negocio y validaciones son los mismos en todos los idiomas. Traducir la tienda **no** significa que haya envíos internacionales: POLYMER envía solo dentro del Perú.

**Gestión de traducciones.**

- **Un archivo por idioma:** [`static/i18n/en.json`, `fr.json`, `it.json`, `pt.json` y `de.json`](services/order-api/src/main/resources/static/i18n/). La clave es el texto en español y el valor, su traducción. El español no necesita archivo.
- **En el código:** los textos se escriben en español con `t('Texto con {0}', valor)`. Los datos del catálogo se traducen con `td()`; los valores con cifras usan plantillas (`"Hasta {0} h": "Up to {0} h"`).
- **Sin traducción automática en tiempo real.** Si falta una traducción, se muestra el texto en español.
- **Comprobación:**

  ```powershell
  python tools/check_i18n.py
  ```

  Reúne todos los textos (interfaz, páginas, catálogo y mensajes del servidor) y falla si a un idioma le falta alguno o si no coinciden los marcadores `{0}`. Para ver qué falta en un idioma: `python tools/check_i18n.py --missing fr`. Lo ejecutan `.\polymer.cmd check` y el CI.

**Agregar un idioma** (por ejemplo, japonés `ja`):

1. Añádelo a `LANGUAGES` en [`static/js/i18n.js`](services/order-api/src/main/resources/static/js/i18n.js) (código, nombre y locale), en `LANGUAGES` y en la tabla `SLUGS` de [`tools/generate_pages.py`](tools/generate_pages.py) (una ruta por página), en `LANGS` y `OG_LOCALES` de `LocalizedPagesController`, en el enum `Language` y en la restricción de `user_preferences` (con una migración Flyway nueva), y en `NotificationTexts` (correos).
2. Ejecuta `python tools/generate_pages.py` para regenerar `routes.json`.
3. Crea `static/i18n/ja.json` y complétalo hasta que `python tools/check_i18n.py` (con `ja` añadido a su lista `LANGUAGES`) termine en OK.

## 17. Seguridad de contraseñas

Al registrarse y al cambiar la contraseña:

| Requisito | Detalle |
| --- | --- |
| Longitud | **Mínimo 12 caracteres** (máximo 64) |
| Frases de contraseña | Permitidas, con espacios: «ancla verde cobre lluvia» es mejor que «P@ssw0rd!» |
| Indicador | **Débil** (no se acepta), **aceptable** o **fuerte**, con el motivo, mientras escribes |
| Mostrar u ocultar | Botón «Mostrar» en cada campo |
| Contraseñas comunes | Se detectan con una lista local, incluidas variantes como «Password2026!» |
| Datos personales | No puede contener tu usuario, nombre ni correo |
| Confirmación | Hay que escribirla dos veces; se avisa si no coinciden |
| Servidor | **Todas las reglas se validan en el servidor** (`PasswordPolicy`, en el dominio): saltarse la interfaz no sirve. Respuesta `400` con `code: WEAK_PASSWORD` y el motivo |

**Las cuentas existentes no cambian:** conservan su contraseña y su hash BCrypt. La política solo se aplica a contraseñas nuevas. Detalle técnico en [`docs/SECRETOS.md`](docs/SECRETOS.md#6-contraseñas-de-los-clientes-de-la-tienda).

## 18. Exposición, demostraciones y documentación final

Escenarios ejecutables contra tu entorno en marcha. Cada uno muestra y **comprueba** sus evidencias
(claves y TTL de Redis, filas de PostgreSQL, colas de RabbitMQ, correos de Mailpit, métricas); el mismo
script se ejecuta en el CI contra Docker real. Solo crean datos ficticios (`@example.test`) y nunca borran
nada.

| Escenario | Comando |
| --- | --- |
| Preparación (13 contenedores sanos y accesos) | `.\polymer.cmd demo -Scenario preparar` |
| A · Arquitectura hexagonal (mapa + ArchUnit) | `.\polymer.cmd demo -Scenario arquitectura` |
| B · Cambio de regla de negocio | `git apply tools/demo/regla-revision-desde-500.patch` + `.\polymer.cmd rebuild -Services risk-engine` |
| B2 · Sustitución de adaptador | `.\polymer.cmd demo-on -FixedRisk` + `.\polymer.cmd demo -Scenario adaptador` + `.\polymer.cmd demo-off` |
| C · Redis: miss, hit, TTL, expiración | `.\polymer.cmd demo -Scenario redis` (opcional: `demo-on -DemoTtl 20`) |
| D · RabbitMQ: outbox → cola → Mailpit | `.\polymer.cmd demo -Scenario rabbitmq` |
| E · PostgreSQL y persistencia tras reinicio | `.\polymer.cmd demo -Scenario postgres` |
| F · Microservicios Java ↔ Python | `.\polymer.cmd demo -Scenario riesgo` |
| G · Métricas | `.\polymer.cmd demo -Scenario observabilidad` |
| H · Resiliencia | `demo -Scenario redis-caida`, `rabbitmq-caida`, `riesgo-caida` |

Documentos en [`docs/final/`](docs/final/):

| Documento | Contenido |
| --- | --- |
| [`AUDITORIA_POLYMER.md`](docs/final/AUDITORIA_POLYMER.md) | Estado real de cada funcionalidad, errores encontrados, riesgos, credenciales, métricas → paneles |
| [`ARQUITECTURA_HEXAGONAL.md`](docs/final/ARQUITECTURA_HEXAGONAL.md) | Puertos y adaptadores con el código real |
| [`DEMOSTRACION_HEXAGONAL.md`](docs/final/DEMOSTRACION_HEXAGONAL.md) | Recorrido, cambio de regla reversible y sustitución de adaptadores |
| [`DEMOSTRACION_REDIS_TTL.md`](docs/final/DEMOSTRACION_REDIS_TTL.md) · [`DEMOSTRACION_RABBITMQ.md`](docs/final/DEMOSTRACION_RABBITMQ.md) · [`DEMOSTRACION_POSTGRESQL.md`](docs/final/DEMOSTRACION_POSTGRESQL.md) · [`DEMOSTRACION_MICROSERVICIOS.md`](docs/final/DEMOSTRACION_MICROSERVICIOS.md) | Una demostración por tecnología |
| [`GUIA_EXPOSICION_PROFESOR.md`](docs/final/GUIA_EXPOSICION_PROFESOR.md) | Guion de la exposición (escenarios A–H) |
| [`GUIA_DOCKER_WINDOWS.md`](docs/final/GUIA_DOCKER_WINDOWS.md) | Contenedores, comandos, reconstrucción, restauración y errores |
| [`MAPA_SLICES_FUNCIONALES.md`](docs/final/MAPA_SLICES_FUNCIONALES.md) | Rol → página → endpoint → caso de uso → puertos → tablas → métricas |
| [`GUIA_GITHUB.md`](docs/final/GUIA_GITHUB.md) | Revisión de seguridad y publicación en un repositorio nuevo |
| [`DESPLIEGUE_AWS_HTTPS.md`](docs/final/DESPLIEGUE_AWS_HTTPS.md) | EC2 + Docker Compose + Nginx con Let's Encrypt; costos, seguridad, copias, actualización y rollback |
| [`MATRIZ_PRUEBAS.md`](docs/final/MATRIZ_PRUEBAS.md) · [`RESULTADOS_VALIDACION.md`](docs/final/RESULTADOS_VALIDACION.md) | Qué se prueba, dónde y con qué resultado real |

---

## 19. Novedades v7: Google, código por correo, pagos, boleta, mapa, horario y video

| Novedad | Qué hace | Dónde verlo |
| --- | --- | --- |
| **Continuar con Google** | OpenID Connect con Spring Security; vincula o crea la cuenta y pide un **código de 6 dígitos** enviado al correo antes de abrir la sesión | `/login.html` (aparece al configurar `secrets\google_client_id.txt` y `google_client_secret.txt`) |
| **Verificación por correo** | Cuentas nuevas confirman su correo; verificación en dos pasos opcional; código de 10 min, 5 intentos, solo el hash en la base | `/registro.html`, *Perfil y seguridad* |
| **Recordarme durante 30 días** | Sesión persistente y dispositivo de confianza (cookies HttpOnly, ficha rotativa, detección de robo) | Casilla en el acceso y en el paso del código |
| **Pago con tarjeta** | Aprobación inmediata (pasarela de **prueba**: no cobra); Luhn, vencimiento y CVV; solo marca y 4 últimos dígitos | Checkout → `/cliente/pago.html` |
| **Yape y Plin** | Solicitud con **QR que vence en 60 s** (clave en Redis con TTL; sin Redis, el reloj) y cuenta regresiva sincronizada | `/cliente/pago.html`, `.\polymer.cmd demo -Scenario pagos` |
| **Boleta** | B001-00000001, base imponible + IGV 18 %; página imprimible y **correo con la boleta** (outbox → RabbitMQ → consumidor) | `/cliente/boleta.html`, Mailpit o tu Gmail |
| **Portada con video** | Video original de la marca en bucle, silenciado, con pausa y póster si se reduce el movimiento | `/` |
| **Mapa y horario** | Google Maps incrustado, «Cómo llegar», horario de hoy en hora de Lima con feriados, calendario del mes y **Agregar a Google Calendar** | Portada, Contacto, Empresa, recojo en el checkout, pie |

Configuración (Google, Gmail, dirección real de la tienda, calendario público) y demostración:
[`docs/final/GUIA_GOOGLE_Y_PAGOS.md`](docs/final/GUIA_GOOGLE_Y_PAGOS.md). Sin configurar nada, todo funciona con
Mailpit, las pasarelas de prueba y una dirección de demostración.

## Anexo A. API

Las peticiones que modifican datos usan sesión y cabecera `X-CSRF-TOKEN`, salvo la API pública de pedidos.

| Ruta | Acceso | Uso |
| --- | --- | --- |
| `POST/GET /api/orders`, `/api/orders/{id}`, `/api/orders/statistics` | público | API original de pedidos con evaluación de riesgo (USD) |
| `/api/auth/{csrf,me,login,logout,register}` | público | Sesión y registro |
| `GET /api/catalog/{categories,products,search,promotions}` | público | Catálogo, búsqueda con facetas y promociones vigentes |
| `/api/shop/cart…`, `/api/shop/orders…`, `/checkout-summary`, `/checkout-options` | cliente | Carrito, checkout idempotente, pedidos y cancelación |
| `/api/operations/…` | operador, admin | Bandeja y transiciones |
| `/api/logistics/…` | logística, admin | Entregas, calendario y asignaciones |
| `/api/incidents…` | operador, logística, admin | Incidencias |
| `/api/admin/…` | admin | Panel, productos, categorías, inventario, promociones, usuarios, auditoría, mensajes y reportes |
| `/api/ops/{health,monitoring}` | monitoreo, admin | Salud y Prometheus |
| `POST /api/contact` | público | Contacto y Libro de reclamaciones |

Códigos de respuesta:

- `400` validación;
- `401` sin sesión;
- `403` sin permiso o sin CSRF;
- `404` no existe;
- `409` conflicto de negocio (`OUT_OF_STOCK`, `CONCURRENT_UPDATE`, `SCHEDULE_CONFLICT`…);
- `503` dependencia no disponible.

Colección de Postman: [`docs/demo/POLYMER.postman_collection.json`](docs/demo/POLYMER.postman_collection.json).

## Anexo B. Documentación técnica

| Documento | Contenido |
| --- | --- |
| [`HEXAGONAL_ARCHITECTURE.md`](docs/HEXAGONAL_ARCHITECTURE.md) | Capas, puertos, adaptadores y reglas verificadas |
| [`TECH_STORE.md`](docs/TECH_STORE.md) | Catálogo, búsqueda, precios, carrito y checkout |
| [`DOCKER_ARCHITECTURE.md`](docs/DOCKER_ARCHITECTURE.md) · [`DOCKER_SERVICES.md`](docs/DOCKER_SERVICES.md) | Los 13 servicios, redes, recursos y verificación |
| [`ASYNCHRONOUS_EVENTS.md`](docs/ASYNCHRONOUS_EVENTS.md) · [`REDIS_INTEGRATION.md`](docs/REDIS_INTEGRATION.md) | Outbox, eventos, DLQ, correos y caché |
| [`OBSERVABILITY_STACK.md`](docs/OBSERVABILITY_STACK.md) | Métricas, logs y trazas |
| [`WINDOWS_DOCKER_GUIDE.md`](docs/WINDOWS_DOCKER_GUIDE.md) | Instalación, operación y problemas en Windows |
| [`brand/BRAND_POLYMER.md`](docs/brand/BRAND_POLYMER.md) | Logotipo, paleta, intro, movimiento, audio y pie |
| [`guia-para-explicar.md`](docs/guia-para-explicar.md) · [`presentacion.md`](docs/presentacion.md) | Guion para la sustentación |
| [`adr/`](docs/adr/) | 17 decisiones de arquitectura |
| [`ANTIGRAVITY_HANDOFF.md`](docs/ANTIGRAVITY_HANDOFF.md) | Guía para seguir desarrollando el proyecto |

## Anexo C. Historial y migración

POLYMER es la evolución de un proyecto que comenzó con otro nombre técnico, *HexaOrders*. Esta es la única sección donde se menciona.

- **Migración y nombres heredados:** [`docs/MIGRACION_POLYMER.md`](docs/MIGRACION_POLYMER.md) y la [ADR 0017](docs/adr/0017-polymer-technical-identity-and-safe-volume-migration.md). Explican cómo se migran los datos sin perderlos y qué identificadores heredados se conservan a propósito (usuario y colas de RabbitMQ, migraciones Flyway publicadas, volúmenes de respaldo).
- **Historial de versiones anteriores:** [`docs/historial/`](docs/historial/), con el README y el registro de validaciones originales.
