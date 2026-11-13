# Guía para armar la PPT de la exposición

Tema: **arquitectura hexagonal aplicada a POLYMER** (tienda tecnológica con pedidos, pagos y logística).
Cada diapositiva indica qué decir, **qué enlace abrir** (el mismo que muestra Docker Desktop o
[`ENLACES.html`](../../ENLACES.html)) y qué captura pegar. Los enlaces funcionan con POLYMER en marcha
(`.\polymer.cmd start`). La contraseña de las cuentas está en `secrets\demo_password.txt`.

> **Antes de capturar:** `.\polymer.cmd enlaces` (todos los enlaces en verde) y `.\polymer.cmd demo -Scenario preparar`.
> En `ENLACES.html` escribe en «Expone» el nombre del integrante de cada slice: se guarda en tu navegador.

## Reparto sugerido por slice

Cada slice tiene un **rol que lo verifica** dentro del sistema. Ese rol es la cuenta con la que se demuestra:

| Bloque de la exposición | Slices (ENLACES.html) | Cuenta con la que se verifica | Contenedores que se muestran |
| --- | --- | --- | --- |
| 1. Arquitectura hexagonal | 01 | `observador` | `order-api` (8081), `frontend` (8080), `risk-engine` (8000) |
| 2. Tienda y compra | 02, 03, 06, 07 | visitante y `cliente` | `frontend`, `order-api`, `risk-engine`, `db`, `redis` |
| 3. Google, código y pagos (v7) | 04, 05 | `cliente` | `order-api`, `mailpit`, `redis`, `rabbitmq` |
| 4. Operación interna | 08, 09, 10, 13 | `operador`, `logistica`, `admin` | `order-api`, `db`, `rabbitmq` |
| 5. Integración y observabilidad | 11, 12 | `observador` | `rabbitmq`, `mailpit`, `grafana`, `prometheus`, `otel-collector`, `alloy` |

## Diapositivas

| # | Título | Qué decir (idea central) | Abrir / capturar |
| --- | --- | --- | --- |
| 1 | POLYMER · Arquitectura hexagonal | Tienda tecnológica real: catálogo, carrito, pagos, logística y monitoreo | Portada http://localhost:8080 (video de fondo) |
| 2 | El problema | Si el negocio depende del framework, de la base de datos o de una pasarela, cambiar cualquiera rompe todo | — |
| 3 | La idea: puertos y adaptadores | El **núcleo** (dominio y casos de uso) define **puertos**; el exterior aporta **adaptadores**. Las dependencias apuntan hacia dentro | Diagrama de `README.md` §4 o http://localhost:8080/arquitectura/overview.html (cuenta `observador`) |
| 4 | El código lo respeta | `domain` y `application` sin Spring, JDBC ni HTTP; 16 puertos de entrada y 33 de salida; 8 reglas ArchUnit lo comprueban en cada build | Árbol de `services/order-api/src/main/java/com/polymer/` · `.\polymer.cmd demo -Scenario arquitectura` |
| 5 | 13 contenedores, un núcleo | El gateway Nginx es solo un adaptador de borde: el mismo sistema responde en 8080 (gateway) y en 8081 (núcleo directo) | Docker Desktop → *Containers* con los enlaces · `ENLACES.html` sección «Contenedores» |
| 6 | Un pedido atraviesa el hexágono | `ShopController` → `CommerceUseCases` → `CommerceService` → `RiskAssessmentPort` (Python) → `CommerceOrderRepository` (PostgreSQL) → outbox → RabbitMQ → correo | http://localhost:8080/arquitectura/flujo-pedidos.html · Grafana http://localhost:3000/d/polymer |
| 7 | Cambiar un adaptador sin tocar el núcleo | El mismo puerto `RiskAssessmentPort` con otro adaptador (puntaje fijo) y la caché `CatalogCache` sin Redis | `.\polymer.cmd demo -Scenario adaptador` · `docs/final/DEMOSTRACION_HEXAGONAL.md` |
| 8 | Microservicio de riesgo | `risk-engine` (FastAPI) también es hexagonal: dominio `risk.py`, caso de uso `ScoreRisk`, adaptador HTTP | http://localhost:8000/docs → `POST /score` con `amount` 1500 → `REVIEW` |
| 9 | Compra y riesgo (cliente) | Carrito, checkout, evaluación de riesgo y pedido aprobado o en revisión | ENLACES.html → slice 03 → «Entrar como cliente» |
| 10 | Google, código de 6 dígitos y «Recordarme» | Nuevo adaptador de entrada (OIDC) sin cambiar el dominio de cuentas; «Iniciar sesión» rechaza a quien no tiene cuenta y «Registrarse» la crea; código por correo; dispositivo de confianza 30 días | http://localhost:8080/login.html · código en Mailpit http://localhost:8025 (o tu Gmail) |
| 11 | Pagos: tarjeta al instante, Yape con 60 s | Puertos `CardGateway`, `WalletGateway`, `PaymentWindow` (TTL en Redis); boleta con IGV por correo | `.\polymer.cmd demo -Scenario pagos` · `/cliente/pago.html` · Mailpit |
| 12 | Operación interna por rol | Operador revisa riesgo, logística agenda entregas, admin gestiona catálogo y auditoría: permisos en el servidor | Slices 08, 09 y 10 → «Entrar como…» |
| 13 | Eventos asíncronos | Outbox transaccional → RabbitMQ → consumidor idempotente → correo; si RabbitMQ cae, nada se pierde | http://localhost:15672 (cola `hexaorders.notifications`) · `.\polymer.cmd demo -Scenario rabbitmq-caida` |
| 14 | Observabilidad | Métricas de cada puerto, logs y trazas Java → Python | Grafana http://localhost:3000 · Prometheus http://localhost:9091/targets · Alloy http://localhost:12345 |
| 15 | Calidad verificada | 163 pruebas Java, 63 Python, E2E en navegador y CI con Docker real (4 jobs en verde) | `docs/final/RESULTADOS_VALIDACION.md` (ejecución del CI) |
| 15 bis | El equipo (testimonios) | Slice nuevo hecho igual que los demás: dominio `Testimonial`, puerto `TestimonialUseCases`, adaptador JDBC; carrusel con nuestras fotos y LinkedIn y formulario para clientes | http://localhost:8080/nosotros.html#testimonios |
| 16 | Conclusiones | El núcleo no cambió para añadir Google, pagos, Redis o RabbitMQ: solo se añadieron puertos y adaptadores | — |

## Capturas recomendadas

1. Docker Desktop con los 13 contenedores en verde y sus enlaces (puertos).
2. `ENLACES.html` con todos los puntos en verde (y los nombres en «Expone»).
3. Grafana `POLYMER · Puertos, adaptadores y métricas` tras ejecutar `.\polymer.cmd demo -Scenario preparar`.
4. `risk-engine` `/docs` con la respuesta `{"score": 75, "decision": "REVIEW"}`.
5. QR de Yape con la cuenta regresiva y el correo de la boleta en Mailpit.

Si una captura debe mostrar Google real, primero `.\polymer.cmd google` (ver `GUIA_GOOGLE_Y_PAGOS.md`).
