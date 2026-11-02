# ADR 0007 — Frontend sin framework ni build

**Contexto.** La interfaz se entrega dentro del `docker-compose.yml` autosuficiente y la sirve Spring Boot. Un build de Node añadiría una etapa, dependencias y superficie de mantenimiento.

**Decisión.** Módulos ES nativos, sin dependencias. Router por hash (`#/…`) que convive con las anclas del laboratorio (`#inicio`…) mediante `body[data-mode]`. Sin estilos ni scripts en línea para permitir una CSP estricta. Las vistas comprueban roles para la navegación, pero la autorización real está en el servidor.

**Consecuencias.** Más código DOM manual (`el()`, componentes propios); sin tipado. Se compensa con comprobación de sintaxis en el CI y pruebas Playwright contra el stack real.
