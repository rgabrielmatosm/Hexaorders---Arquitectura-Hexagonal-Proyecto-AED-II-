# Registro de decisiones de arquitectura (ADR)

Formato ligero (contexto, decisión, consecuencias). Estado de todas: **aceptada** (noviembre de 2026; 0001–0008 en la versión 3, 0009–0010 en la versión 4).

| ADR | Decisión |
| --- | --- |
| [0001](0001-hexagonal-architecture.md) | Mantener la arquitectura hexagonal y ampliarla con nuevos subdominios |
| [0002](0002-session-security-csrf.md) | Spring Security con sesión, CSRF y BCrypt en lugar de JWT |
| [0003](0003-lifecycle-in-domain.md) | Ciclo de vida del pedido como política pura en el dominio |
| [0004](0004-non-destructive-migrations.md) | Migraciones Flyway no destructivas y tabla `orders` compartida |
| [0005](0005-idempotency-and-concurrency.md) | Idempotencia con `Idempotency-Key` y bloqueo optimista |
| [0006](0006-observability-additive.md) | Observabilidad solo aditiva con línea base verificada |
| [0007](0007-vanilla-frontend.md) | Frontend sin framework ni build, con router por hash |
| [0008](0008-jdbc-without-orm.md) | JDBC (`JdbcTemplate`) en lugar de JPA |
| [0009](0009-multipage-frontend-served-by-spring.md) | Sitio multipágina servido por Spring Boot (Nginx evaluado y descartado) |
| [0010](0010-monitoring-portal-through-a-port.md) | Portal de monitoreo a través de `MonitoringPort` |
