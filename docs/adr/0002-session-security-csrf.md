# ADR 0002 — Sesión, CSRF y BCrypt en lugar de JWT

**Contexto.** La interfaz y la API se sirven desde el mismo origen (Spring Boot). Hacen falta cinco roles, cierre de sesión real y protección frente a CSRF.

**Decisión.** Spring Security 6 con sesión HTTP (cookie `HttpOnly`, regeneración del identificador al iniciar sesión), `HttpSessionCsrfTokenRepository` con el token servido en `GET /api/auth/csrf` y enviado en `X-CSRF-TOKEN`, contraseñas con BCrypt (coste 12), respuestas JSON `401`/`403`, CSP estricta y `denyAll` para rutas `/api/**` no declaradas. `/api/orders/**` queda pública y sin CSRF para no romper el contrato original; `/actuator/**` sigue accesible para Prometheus en el puerto de gestión interno.

**Alternativas.** JWT en `localStorage` (expuesto a XSS y sin revocación sencilla); OAuth2/OIDC (desproporcionado para una práctica sin proveedor de identidad).

**Consecuencias.** Estado de sesión en memoria de una única réplica (suficiente aquí; con varias réplicas haría falta Spring Session). Falta limitar intentos de inicio de sesión (documentado como limitación, con métrica y alerta).
