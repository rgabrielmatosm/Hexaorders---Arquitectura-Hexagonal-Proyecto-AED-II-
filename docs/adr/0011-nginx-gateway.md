# ADR 0011 — Nginx como gateway sin sacar las páginas protegidas de Spring Security

**Contexto.** La v5 pide un contenedor `frontend` con Nginx. El [ADR 0009](0009-multipage-frontend-served-by-spring.md) había descartado Nginx porque servir los HTML desde un servidor estático rompería la protección de páginas por rol, el mismo origen de la sesión y las pruebas que comparan los archivos servidos con el repositorio.

**Opciones evaluadas.**

| Opción | Resultado |
| --- | --- |
| A. Nginx sirve TODOS los HTML y CSS/JS y reenvía `/api` | **Descartada**: las páginas de `/admin`, `/cliente`, `/operaciones`, `/logistica` y `/monitoreo` quedarían públicas. Para protegerlas habría que duplicar la autenticación en Nginx (`auth_request`), con otra fuente de verdad para los roles. |
| B. Nginx como gateway: sirve los recursos públicos (`/css`, `/js`, `/img`) y reenvía las páginas HTML, `/api` y las URLs limpias a Spring Boot | **Elegida** |
| C. Mantener solo Spring Boot (ADR 0009) | Válida, pero no aporta el gateway pedido |

**Decisión.** Opción B. Nginx se publica en `127.0.0.1:8080` (la URL de siempre) y `order-api` deja de publicarse en el host.

- **Páginas y API.** Spring Security sigue decidiendo cada página (302 al login con `next`, 403 por rol) y cada llamada a la API (sesión, CSRF, roles).
- **Cabeceras.** Nginx reenvía `Host`, de modo que las redirecciones conservan el puerto, además de `X-Forwarded-*` y `X-Request-Id`. Re-resuelve `order-api` cada 10 s para sobrevivir a reinicios.
- **Recursos estáticos.** Son los mismos archivos de `services/order-api/src/main/resources/static`, copiados en la imagen con la directiva `@repo/` del generador. Nginx añade las mismas cabeceras de seguridad que Spring: CSP, `nosniff`, `X-Frame-Options` y `Referrer-Policy`. Además añade compresión y `Cache-Control` de 10 minutos.

**Lo que esto NO es.** Nginx no sirve de forma independiente todo el frontend: los HTML siguen saliendo de Spring Boot por seguridad. Es una limitación documentada y deliberada.

**Consecuencias.**

- El ADR 0009 queda **sustituido** en lo relativo a Nginx; su razonamiento sobre la seguridad de las páginas se mantiene.
- `smoke.py` sigue comparando byte a byte todos los archivos estáticos servidos por el puerto 8080.
- `integration_smoke.py` comprueba la cabecera propia de Nginx y el 302 de una página protegida detrás del gateway.
- `pages-e2e.js` recorre las 39 páginas con los 5 roles a través del gateway.
- Las métricas de Nginx llegan a Prometheus mediante el collector (receptor `nginx`).
