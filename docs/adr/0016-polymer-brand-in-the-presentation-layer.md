# ADR 0016 — Marca POLYMER en la capa de presentación

- **Estado:** aceptada; el punto 1 fue **sustituido por la [ADR 0017](0017-polymer-technical-identity-and-safe-volume-migration.md)**, que extiende el nombre POLYMER a la identidad técnica
- **Contexto:** la tienda v6 debía convertirse en una marca comercial, **POLYMER** («Tecnología que conecta contigo.»), con estos elementos:
  - logotipo propio, intro cinematográfica, sistema de movimiento y sonidos;
  - pie comercial completo e identidad común para todos los roles.
  
  El encargo prohíbe renombrar paquetes, clases, tablas, claves de configuración, contenedores, rutas de la API o recursos de observabilidad, y exige conservar los números de pedido `HX-…` y todos los datos.

## Decisión

1. **Primera etapa: solo la capa de presentación.** Títulos, logotipo, textos, correos y pie pasaron a POLYMER sin tocar paquetes, contenedores ni métricas. *(Sustituido por la ADR 0017: después se migró también la identidad técnica.)*
2. **Configuración de marca en un único módulo** (`static/js/brand.js`): nombre, eslogan, contacto, datos legales, redes, métodos de pago e intro.
   - Los datos que no existen se dejan en `null` y se muestran como «no configurado»: RUC, razón social y cuentas oficiales.
   - Los de contacto usan valores de demostración marcados como tales.
3. **Generadores en lugar de HTML a mano:**
   - `tools/generate_brand.py` produce todos los SVG del logotipo desde una sola geometría;
   - `tools/generate_pages.py` añade a las 45 páginas el `<head>` común: favicon, `intro-gate.js` y `polymer.css`.
   
   Los dos tienen `--check` en `check` y en el CI.
4. **Movimiento sin dependencias.**
   - CSS, Web Animations API, `IntersectionObserver`, View Transitions entre documentos, SVG y Web Audio.
   - **No se usa GSAP:** las animaciones son pocas y declarativas, y una librería externa obligaría a ampliar la CSP o a incluir un archivo de terceros sin necesidad.
   - Todo respeta `prefers-reduced-motion`.
5. **La intro no forma parte del flujo de la página.**
   - Es una capa superpuesta que se decide antes de pintar y no espera a nada. La página carga sus datos detrás.
   - Se muestra una vez por sesión y nunca en los paneles internos.
6. **Migración V8 no destructiva** para lo que vive en la base:
   - la marca propia de los productos de demostración;
   - el valor por defecto de `products.brand`;
   - el tema `CLAIM`.
   
   No toca usuarios, contraseñas ni pedidos.

## Consecuencias

- **Revertir la marca** es acotado: V8 solo cambia textos de productos.
- **Comprobaciones automáticas de la marca:**
  - `check_frontend.py` falla si la marca anterior aparece en un texto visible de la tienda o si un enlace interno lleva a una página inexistente;
  - `pages-e2e.js` comprueba el texto visible con todos los roles;
  - `intro-e2e.js` cubre las reglas de la intro y del pie.
- **Correos y usuarios ya creados:**
  - los correos ya enviados y los nombres de los usuarios de demostración existentes no cambian;
  - las instalaciones nuevas crean «Administración Polymer».
