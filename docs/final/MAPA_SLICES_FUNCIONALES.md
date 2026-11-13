# Mapa de slices funcionales

Fuente: `config/SecurityConfiguration.java` (permisos), `adapter/in/web/*Controller.java` (endpoints),
`config/*Configuration.java` (qué servicio implementa cada puerto), `static/i18n/routes.json` (páginas y
roles) y las migraciones Flyway (tablas). Rutas Java relativas a
`services/order-api/src/main/java/com/polymer/`. Los permisos se comprueban **en el servidor** (Spring
Security por ruta + `application/service/Guards` en los casos de uso); ocultar botones no basta.

## Roles (`domain/identity/Role.java`)

| Rol | Cuenta de demostración | Páginas |
| --- | --- | --- |
| Visitante (sin sesión) | — | `/`, `/catalogo.html`, `/producto.html`, `/ofertas.html`, `/carrito.html`, `/ayuda.html`, `/contacto.html`, `/empresa.html`, `/nosotros.html`, `/legal.html`, `/libro-reclamaciones.html`, `/login.html`, `/registro.html` |
| `CUSTOMER` | `cliente` | `/checkout.html`, `/cliente/dashboard.html`, `/cliente/pedidos.html`, `/cliente/pedido-detalle.html`, `/cliente/seguimiento.html`, `/cliente/pago.html`, `/cliente/boleta.html` (+ `/cliente/perfil.html`, cualquier usuario autenticado) |
| `ADMIN` | `admin` | `/admin/*` (10 páginas) y además todas las áreas internas |
| `ORDER_OPERATOR` | `operador` | `/operaciones/dashboard.html`, `pedidos.html`, `revisiones.html`, `incidencias.html` |
| `LOGISTICS_OPERATOR` | `logistica`, `logistica2` | `/logistica/dashboard.html`, `calendario.html`, `entregas.html`, `asignaciones.html` |
| `OBSERVABILITY_VIEWER` | `observador` | `/monitoreo/*` (4), `/arquitectura/*` (2) y `/laboratorio.html` (documentación técnica interna) |

Las 45 páginas también existen en `/en/`, `/fr/`, `/it/`, `/pt/` y `/de/` con los mismos permisos
(`adapter/in/web/i18n/LocalizedPagesController`). La documentación técnica está separada del sitio
comercial: solo la ven `observador` y `admin`.

## Enlaces y verificadores por slice (v8)

[`ENLACES.html`](../../ENLACES.html) (generado por `tools/generate_links.py`; `.\polymer.cmd enlaces` lo abre y comprueba
cada enlace) muestra, para cada slice: **quién lo verifica** (rol y cuenta de demostración), el botón «Entrar como…»
(login con `?next=` a su página), las páginas, los **contenedores** que intervienen con su enlace local y estado en vivo,
la evidencia (dashboard de Grafana, consulta de Prometheus, cola de RabbitMQ, Mailpit) y el escenario de
`polymer.cmd demo`. Enlace de cada contenedor (solo `127.0.0.1`, clicable en Docker Desktop):

| Contenedor | Enlace | Slices que lo usan como evidencia |
| --- | --- | --- |
| `frontend` | http://localhost:8080 | Todos (tienda y portales por rol) |
| `order-api` | http://localhost:8081 (núcleo sin gateway) | Arquitectura hexagonal; todos los de negocio |
| `risk-engine` | http://localhost:8000 → `/docs` | Checkout y riesgo, Operaciones |
| `grafana` | http://localhost:3000 | Monitoreo y uno por slice (`polymer`, `polymer-business`, `polymer-risk`, `polymer-logistics`, `polymer-security`, `polymer-integrations`…) |
| `prometheus` | http://localhost:9091 | Monitoreo, pagos, caché |
| `rabbitmq` | http://localhost:15672 | Notificaciones asíncronas, pagos (boleta) |
| `mailpit` | http://localhost:8025 | Acceso (código de 6 dígitos), pagos (boleta), notificaciones |
| `otel-collector` | http://localhost:13133 | Monitoreo (trazas) |
| `alloy` | http://localhost:12345 | Monitoreo (logs) |
| `db`, `redis`, `loki`, `tempo` | Privados por diseño | Se demuestran con `polymer.cmd demo` (postgres, redis), Grafana → Explore (Loki, Tempo) o *Exec* en Docker Desktop |

## Slices

| Slice | Rol | Página | Endpoints | Controlador | Puerto de entrada → servicio | Puertos de salida | Adaptadores de salida | Tablas | Eventos / métricas |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Catálogo** | Público | `/catalogo.html`, `/producto.html`, `/ofertas.html` | `GET /api/catalog/categories`, `/products`, `/products/{id}`, `/products/{id}/related`, `/search`, `/promotions` | `CatalogController` | `CatalogUseCases` → `CatalogService`; `PromotionUseCases` → `PromotionService` | `CatalogRepository`, `CatalogCache`, `PromotionRepository` | `JdbcCatalogRepository`, `RedisCatalogCache`, `JdbcPromotionRepository` | `categories`, `products`, `promotions`, `promotion_targets` | `polymer_cache_requests_total`, `polymer_cache_invalidations_total` |
| **Carrito** | `CUSTOMER` (visitante: carrito local) | `/carrito.html` | `GET/DELETE /api/shop/cart`, `POST /api/shop/cart/items`, `PUT/DELETE /api/shop/cart/items/{productId}`, `POST /api/shop/cart/merge` | `ShopController` | `CartUseCases` → `CartService` | `CartRepository`, `CatalogRepository` | `JdbcCartRepository`, `JdbcCatalogRepository` | `cart_items` | — |
| **Checkout y pedido** | `CUSTOMER` (cotización también visitante) | `/checkout.html` | `POST /api/shop/quote`, `/checkout-summary` (públicos), `GET /api/shop/checkout-options`, `POST /api/shop/orders` | `ShopController` | `CommerceUseCases` → `CommerceService.placeOrder` | `RiskAssessmentPort`, `CommerceOrderRepository`, `CatalogRepository`, `CatalogCache`, `AuditLog`, `BusinessTelemetry`, `OrderTelemetry` | `HttpRiskAssessmentAdapter` (o `FixedRiskAssessmentAdapter` en demo), `JdbcCommerceOrderRepository`, `JdbcAuditLog`, `Micrometer*` | `orders`, `order_lines`, `order_status_history`, `order_shipping`, `order_checkout`, `products` (stock), `audit_events` | Evento `ORDER_*` (outbox); `polymer_shop_orders_placed_total`, `polymer_checkout_failures_total`, `orders_created_total` |
| **Mis pedidos y seguimiento** | `CUSTOMER` | `/cliente/pedidos.html`, `pedido-detalle.html`, `seguimiento.html` | `GET /api/shop/orders`, `GET /api/shop/orders/{id}`, `POST /api/shop/orders/{id}/cancel` | `ShopController` | `CommerceUseCases` → `CommerceService` (`myOrders`, `myOrder`, `cancelMyOrder`) | `CommerceOrderRepository`, `DeliveryRepository` | `JdbcCommerceOrderRepository`, `JdbcDeliveryRepository` | `orders`, `order_status_history`, `deliveries` | Evento de cancelación; `polymer_order_transitions_total` |
| **Cuenta** | Autenticado | `/cliente/perfil.html`, `/login.html`, `/registro.html` | `/api/auth/csrf`, `/me`, `/login`, `/logout`, `/register`; `/api/account/me`, `/profile`, `/password`, `/locale` | `AuthController`, `AccountController` | `AccountUseCases` → `AccountService` | `UserRepository`, `PasswordHasher`, `PasswordBlocklist`, `UserPreferences`, `AuditLog` | `JdbcUserRepository`, `BcryptPasswordHasher`, `ClasspathPasswordBlocklist`, `JdbcUserPreferences` | `app_users`, `app_user_roles`, `user_preferences`, `audit_events` | `polymer_auth_logins_total`, `polymer_access_denied_total` |
| **v7/v9 · Acceso con Google, código por correo y «Recordarme»** (v9: «Iniciar sesión con Google» rechaza a quien no tiene cuenta; «Registrarse con Google» la crea; Google muestra «Elige una cuenta») | Público / autenticado | `/login.html`, `/registro.html`, `/cliente/perfil.html` | `GET /api/auth/providers`, `GET /api/auth/google` → `/oauth2/authorization/google` → `/login/oauth2/code/google`; `POST /api/auth/login` (200 o **202** con código), `GET/POST /api/auth/verify`, `POST /verify/resend`, `/verify/cancel`; `GET/PUT /api/account/security`, `POST /security/forget-devices` | `AuthController`, `AccountController`, `GoogleSignInHandlers`, `RememberedLoginFilter` | `SignInUseCases` → `SignInService` | `AccountSecurity`, `VerificationChallenges`, `DeviceTokens`, `MailSender`, `UserRepository`, `UserPreferences`, `AuditLog` | `JdbcAccountSecurity`, `JdbcVerificationChallenges`, `JdbcDeviceTokens`, `SmtpMailSender` (Mailpit o Gmail) | `app_users` (`email_verified`, `two_factor`), `user_identities`, `verification_challenges`, `device_tokens` | `polymer_auth_sessions_total{method}`, `polymer_auth_verifications_total{outcome}` |
| **v7 · Pago en línea y boleta** | `CUSTOMER` | `/cliente/pago.html`, `/cliente/boleta.html` | `GET /api/shop/orders/{id}/payment`, `POST …/payment/card`, `POST …/payment/wallet`, `POST …/payment/wallet/{paymentId}/confirm`, `GET /api/shop/orders/{id}/receipt` | `PaymentController` | `PaymentUseCases` → `PaymentService` | `PaymentRepository`, `CardGateway`, `WalletGateway`, `PaymentWindow`, `CommerceOrderRepository`, `UserRepository`, `BusinessTelemetry`, `AuditLog` | `JdbcPaymentRepository`, `SandboxCardGateway`, `SandboxWalletGateway`, `RedisPaymentWindow` (TTL 60 s) o `ClockPaymentWindow` | `payments`, `receipt_number_seq`, `order_checkout` | Evento `PAYMENT_CONFIRMED` (outbox → boleta por correo); `polymer_payments_total{method,outcome}` |
| **v7 · Tienda física: mapa y horario** | Público | `/`, `/contacto.html`, `/empresa.html`, checkout (recojo), pie | `GET /api/store`, `GET /api/store/today` | `StoreController` | `StoreUseCases` → `StoreService` (`BusinessHours`, `PeruHolidays`) | — | Iframes de Google Maps y Google Calendar en la interfaz | — | — |
| **v9 · Testimonios** | Público (ver), `CUSTOMER` (dejar uno), `ADMIN` (moderar) | `/`, `/nosotros.html` (carrusel y formulario), `/admin/dashboard.html` | `GET /api/testimonials`, `GET/PUT /api/testimonials/mine`, `GET /api/admin/testimonials`, `PUT /api/admin/testimonials/{id}/visibility` | `TestimonialController` | `TestimonialUseCases` → `TestimonialService` (dominio `Testimonial`) | `TestimonialRepository`, `UserRepository`, `AuditLog` | `JdbcTestimonialRepository` | `testimonials` (Flyway V13; el equipo con su LinkedIn) | Auditoría `TESTIMONIAL_*` |
| **Contacto y reclamaciones** | Público | `/contacto.html`, `/libro-reclamaciones.html` | `POST /api/contact` | `ContactController` | `ContactUseCases` → `ContactService` | `ContactMessageRepository`, `AuditLog` | `JdbcContactMessageRepository` | `contact_messages` | — |
| **Operaciones: pedidos y revisión de riesgo** | `ORDER_OPERATOR`, `ADMIN` | `/operaciones/*` | `GET /api/operations/orders`, `/orders/{id}`, `POST /api/operations/orders/{id}/transitions` | `OperationsController` | `OperationsUseCases` → `OperationsService` (+ `LifecycleTransitions`, `domain/fulfillment/TransitionPolicy`) | `CommerceOrderRepository`, `DeliveryRepository`, `IncidentRepository`, `AuditLog`, `BusinessTelemetry` | `Jdbc*` | `orders`, `order_status_history`, `audit_events` | Evento por transición; `polymer_order_transitions_total`, `polymer_orders_by_status` |
| **Incidencias** | `ORDER_OPERATOR`, `LOGISTICS_OPERATOR`, `ADMIN` | `/operaciones/incidencias.html`, `/admin/incidencias.html` | `GET/POST /api/incidents`, `POST /api/incidents/{id}/resolve` | `IncidentController` | `OperationsUseCases` → `OperationsService` | `IncidentRepository`, `AuditLog` | `JdbcIncidentRepository` | `incidents` | `polymer_incidents_reported_total`, `polymer_incidents_open` |
| **Logística** | `LOGISTICS_OPERATOR`, `ADMIN` | `/logistica/*` | `GET /api/logistics/deliveries`, `/assignees`, `/schedulable-orders`, `/deliveries/{id}/history`; `POST /api/logistics/deliveries`, `PUT /deliveries/{id}`, `POST /orders/{id}/transitions` | `LogisticsController` | `LogisticsUseCases` → `LogisticsService` | `DeliveryRepository`, `CommerceOrderRepository`, `UserRepository`, `AuditLog`, `BusinessTelemetry` | `JdbcDeliveryRepository`, … | `deliveries`, `delivery_changes`, `orders` | Evento `DELIVERY_*` (outbox); `polymer_deliveries_total`, `polymer_delivery_conflicts_total` |
| **Administración: productos, categorías, inventario, promociones** | `ADMIN` | `/admin/productos.html`, `categorias.html`, `inventario.html`, `promociones.html` | `GET/POST/PUT /api/admin/products…`, `POST /api/admin/products/{id}/stock`, `GET/POST /api/admin/categories`, `GET/POST/PUT /api/admin/promotions…` | `AdminController` | `CatalogUseCases` → `CatalogService`; `AdministrationUseCases` → `AdministrationService` | `CatalogRepository`, `CatalogCache` (invalidación), `PromotionRepository`, `AuditLog` | `JdbcCatalogRepository`, `RedisCatalogCache`, … | `products`, `categories`, `promotions`, `audit_events` | `polymer_cache_invalidations_total`, `polymer_products_low_stock` |
| **Administración: usuarios, auditoría, panel, reportes** | `ADMIN` | `/admin/usuarios.html`, `auditoria.html`, `dashboard.html`, `reportes.html`, `pedidos.html` | `GET /api/admin/users`, `PUT /users/{id}/roles`, `PUT /users/{id}/enabled`, `GET /api/admin/audit`, `/dashboard`, `/contact-messages` | `AdminController` | `AdministrationUseCases` → `AdministrationService` | `UserRepository`, `AuditLog`, `CommerceOrderRepository`, `IncidentRepository`, `SystemHealthPort` | `JdbcUserRepository`, `JdbcAuditLog`, `DependencyHealthAdapter`, … | `app_users`, `app_user_roles`, `audit_events`, `contact_messages` | — |
| **Monitoreo** | `OBSERVABILITY_VIEWER`, `ADMIN` | `/monitoreo/*` | `GET /api/ops/monitoring`, `GET /api/ops/health` | `OpsController` | `MonitoringUseCases` → `MonitoringService`; `AdministrationUseCases` | `MonitoringPort`, `SystemHealthPort` | `PrometheusMonitoringAdapter` (consultas fijas a Prometheus), `DependencyHealthAdapter` | — | Lee las métricas de Prometheus |
| **Notificaciones (asíncrono)** | Sistema | — | (RabbitMQ) | `BusinessEventListener`, `EventRelayScheduler` | `NotificationUseCases` → `NotificationService`; `EventRelayUseCases` → `EventRelayService` | `EventOutbox`, `EventPublisher`, `ProcessedEvents`, `NotificationRecipients`, `ReceiptSource` (v7), `MailSender` | `JdbcEventOutbox`, `RabbitMqEventPublisher`, `JdbcProcessedEvents`, `JdbcNotificationRecipients`, `JdbcReceiptSource`, `SmtpMailSender` | `order_status_history`, `delivery_changes`, `payments` (v7), `outbox_published`, `processed_events` | `polymer_events_published_total`, `polymer_events_consumed_total`, `polymer_outbox_pending`, `polymer_notifications_sent_total` |
| **API original del laboratorio** | Público (API) / `OBSERVABILITY_VIEWER`, `ADMIN` (página) | `/laboratorio.html` | `POST /api/orders`, `GET /api/orders`, `/api/orders/{id}`, `/api/orders/statistics` | `OrderController` | `OrderUseCases` → `OrderService` | `OrderRepository`, `RiskAssessmentPort`, `OrderTelemetry` | `JdbcOrderRepository`, `HttpRiskAssessmentAdapter` | `orders` (`channel = 'LAB'`) | `orders_created_total`, `orders_risk_failures_total` |

> **Nota de seguridad para AWS:** `POST /api/orders` (API original del laboratorio) es pública por
> compatibilidad con la versión 1. En un despliegue público debe limitarse en el gateway
> (ver `DESPLIEGUE_AWS_HTTPS.md`).

## Cómo se verifica que los permisos siguen funcionando

- `services/order-api/src/test/java/com/polymer/security/SecurityRulesTest.java`: reglas por rol con la
  cadena de seguridad real (MockMvc).
- `tools/ui-e2e/pages-e2e.js`: recorre las 45 páginas como visitante y con `cliente`, `admin`, `operador`,
  `logistica` y `observador` (200, redirección al login o 403 según el rol).
- `tools/platform_smoke.py`: roles en la API (incluido `logistica2`) y ciclo de vida.
- `tools/ui-e2e/i18n-e2e.js`: permisos en las rutas localizadas (login localizado y 403).
