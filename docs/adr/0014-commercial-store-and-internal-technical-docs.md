# ADR 0014 — Tienda comercial y documentación técnica interna

- **Estado:** aceptada (v6)
- **Contexto:** hasta la v5, el sitio mezclaba la tienda con contenido académico: arquitectura hexagonal, laboratorio de la API, diagramas y explicaciones de Docker o PostgreSQL. La v6 exige que `localhost:8080` sea una tienda de tecnología realista, sin explicar cómo está programada. La documentación técnica debe seguir disponible para evaluarla y para el personal autorizado.

## Decisión

1. **La tienda es solo comercial.** Las páginas públicas y las del cliente (portada, catálogo, producto, ofertas, carrito, checkout, ayuda, nosotros, contacto, cuenta) no contienen términos técnicos ni académicos. Ocultarlos con CSS no basta: las páginas y la navegación se reescribieron.
2. **La documentación técnica es interna.** `/arquitectura/*.html` y `/laboratorio.html` se conservan sin cambios funcionales, pero Spring Security los restringe a `ADMIN` y `OBSERVABILITY_VIEWER`: un visitante va al inicio de sesión y un cliente recibe 403. Se llega a ellos desde la barra lateral de **Monitoreo**.
3. **La documentación para evaluar** el proyecto vive en el repositorio: `README.md`, `docs/` y `docs/adr/`.
4. **Se comprueba automáticamente:**
   - `tools/check_frontend.py` revisa los títulos y descripciones de las páginas de la tienda y los textos de sus vistas JavaScript;
   - `tools/ui-e2e/pages-e2e.js` revisa el texto que realmente ve el visitante o el cliente en cada página.

## Consecuencias

- La API original `/api/orders` sigue pública, por compatibilidad con la v1. Solo su página de laboratorio pasa a ser interna.
- Las pruebas que usaban el laboratorio sin sesión (`smoke.py`, `ui-e2e.js`) ahora comprueban la redirección e inician sesión como observador.
- Los portales del personal (administración, operaciones, logística, monitoreo) pueden usar términos operativos, como «revisión de riesgo» o «Prometheus», porque su público es interno.
