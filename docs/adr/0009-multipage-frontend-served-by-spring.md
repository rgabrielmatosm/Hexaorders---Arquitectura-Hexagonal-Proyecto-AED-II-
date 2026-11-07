# ADR 0009 — Sitio multipágina servido por Spring Boot (sin contenedor Nginx)

**Contexto.** La v4 pide un sitio con muchas páginas HTML reales y propone **evaluar** un sexto contenedor Nginx para el frontend, siempre que no rompa la aplicación.

**Opciones evaluadas.**

| Opción | A favor | En contra |
| --- | --- | --- |
| A. Nginx delante (proxy de `/api` a order-api) | Separación física del frontend; caché de estáticos | Un servicio más (healthcheck, `read_only`, límites, ConfigMap y manifiestos de Kubernetes); cambia el origen de las cookies de sesión y del CSRF; la seguridad de las páginas por rol habría que duplicarla en Nginx o perderla; rompe `smoke.py` y la prueba «el contenedor sirve los archivos idénticos al repositorio» |
| B. Spring Boot sirve los HTML (elegida) | Mismo origen (sin CORS); Spring Security protege también las páginas (302/403); un solo contenedor por responsabilidad de negocio; sin cambios de puertos | El JAR incluye los estáticos (pocos KB) |

**Decisión.** Opción B. Las páginas viven en `src/main/resources/static/`, se generan desde un registro (`tools/generate_pages.py`) y Spring Security aplica las reglas por rol a las rutas de los portales. Las URLs limpias (`/admin`, `/catalogo`…) son redirecciones de Spring MVC.

**Consecuencias.** Se mantienen los cinco servicios, sus puertos, healthchecks y volúmenes. Si en el futuro se añadiera Nginx, el registro de páginas y los HTML se pueden servir tal cual; habría que trasladar las reglas de `SecurityConfiguration` para páginas o mantener el proxy hacia order-api también para los HTML protegidos.

**Actualización v5.** Sustituido en lo relativo a Nginx por el [ADR 0011](0011-nginx-gateway.md): Nginx se incorpora como *gateway* (recursos estáticos y proxy), pero las páginas HTML siguen saliendo de Spring Boot, por las mismas razones de seguridad descritas aquí.
