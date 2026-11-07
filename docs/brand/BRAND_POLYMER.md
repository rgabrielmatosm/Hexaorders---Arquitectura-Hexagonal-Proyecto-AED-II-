# POLYMER — manual de marca e implementación

> **Tecnología que conecta contigo.**

POLYMER es la marca comercial de la tienda. HexaOrders es el **nombre técnico** del sistema: siguen igual
los paquetes Java (`com.hexaorders`), las tablas, las claves de configuración, los contenedores, las rutas
de la API, los dashboards y las métricas (`hexaorders_*`), los números de pedido (`HX-…`) y las claves de
almacenamiento del navegador. El cambio de marca afecta solo a la **capa de presentación**.

## 1. Logotipo

### Tres conceptos evaluados (`docs/brand/concepts/`)

| Concepto | Idea | Decisión |
| --- | --- | --- |
| **A · Enlace** (`concepto-a-cadena.svg`) | La P se dibuja como una **pista de circuito** con esquinas biseladas. Tres nodos enlazados forman una cadena: un **polímero**, unidades pequeñas que juntas forman algo más fuerte. | **Elegido** |
| B · Prisma (`concepto-b-prisma.svg`) | Dos planos facetados forman una P con volumen, como una lámina de silicio doblada. | Descartado: más genérico y pierde detalle en tamaño de favicon. |
| C · Órbita (`concepto-c-orbita.svg`) | Un anillo segmentado con un nodo en tránsito. | Descartado: se lee más como icono de «cargando» que como marca. |

**Por qué A.** Cuenta la historia del nombre (cadena de monómeros), del eslogan (conectar) y del
producto (circuitos), en una sola línea continua. No usa hexágonos ni letras dentro de figuras, y no se
parece a las marcas citadas en el encargo (Coolbox, Razer, ASUS, NVIDIA): no tiene garras, triángulos
agresivos, ojos ni la forma de esas marcas. Su tono es el de una marca tecnológica de consumo, no el de
una marca *gamer*.

### Versión final: `tools/generate_brand.py`

Todos los archivos salen de **una sola definición geométrica**, así nunca se desalinean. `--check` lo
verifica en `.\hexaorders.cmd check` y en el CI.

| Archivo (`static/img/`) | Uso |
| --- | --- |
| `logo.svg` | Isotipo sobre losa oscura: **favicon** y referencias existentes |
| `polymer-mark.svg` | Isotipo sin fondo (cabecera móvil) |
| `polymer-mark-animated.svg` | Versión **animada** con SMIL: trazos que se dibujan, nodos que aparecen y una señal que recorre el enlace (sin `<style>`, compatible con la CSP) |
| `polymer-logo.svg` | Logotipo horizontal para **fondo oscuro** (cabecera y pie) |
| `polymer-logo-light.svg` | Logotipo horizontal para **fondo claro** |
| `polymer-logo-mono.svg` / `polymer-logo-mono-dark.svg` | **Monocromo** claro y oscuro (sellos, documentos, impresión) |

El logotipo «POLYMER» también está dibujado con trazos geométricos, sin depender de una fuente: las
letras repiten los chaflanes del isotipo, como la P y la R, y la O es octogonal.

## 2. Paleta y tokens

| Token | Color | Uso |
| --- | --- | --- |
| `--pm-ink` | `#101827` | Fondo principal |
| `--pm-graphite` | `#171D2B` | Superficies, pie de página |
| `--pm-teal` | `#3DE2D0` | Acento primario, foco, éxito de marca |
| `--pm-peri` | `#A6B8FF` | Acento secundario, enlaces |
| `--pm-snow` | `#F8FAFC` | Texto |
| `--pm-gradient` | turquesa → periwinkle | Botones primarios (texto oscuro: contraste AA) |

`css/polymer.css` se carga **al final** y redefine los tokens existentes (`--violet*` pasa a turquesa y
periwinkle, `--bg*` a tinta y grafito), así que todos los componentes heredan la marca sin reescribirse.

- **Tienda:** degradados suaves y una cuadrícula técnica casi imperceptible.
- **Paneles internos** (admin, operaciones, logística, monitoreo, cliente): fondo liso, sin texturas detrás
  de tablas, formularios ni gráficos. Cada área tiene un color de acento (`--area-accent`) en la barra lateral.

## 3. Intro cinematográfica (`js/intro-gate.js` + `js/intro.js`)

1. `intro-gate.js` es un script clásico en `<head>`, permitido por la CSP (`script-src 'self'`). **Antes de
   pintar** decide si toca la intro y, si toca, oculta la página para que no haya destello. Una red de
   seguridad la vuelve visible a los 2,5 s si la intro no llega a arrancar.
2. La intro (unos 3,4 s, configurable en `brand.js → INTRO.durationMs`) sigue estas fases:
   - textura técnica oscura;
   - el isotipo se **arma con líneas de luz** (`stroke-dashoffset` con la Web Animations API);
   - los nodos se encienden;
   - las letras **P O L Y M E R** aparecen escalonadas (desplazamiento, opacidad y desenfoque);
   - aparece el eslogan;
   - **se abre el telón**: dos paneles desde el centro;
   - entrada escalonada de la cabecera, el héroe y las secciones.
3. **Reglas:**
   - una vez por sesión (`sessionStorage`), no se repite al navegar, recargar ni abrir modales;
   - **«Omitir intro»**, `Esc` o un clic en el fondo la cierran;
   - **sin sonido** salvo que se pulse «Entrar con sonido» (respeta las políticas de reproducción automática);
   - con `prefers-reduced-motion` no se muestra;
   - nunca aparece en los paneles internos;
   - `?intro=1` (el enlace «Ver la intro» del pie) la repite a demanda.
4. La página **se prepara detrás** del telón: la intro no bloquea ni retrasa la carga de datos.

## 4. Sistema de movimiento (`js/motion.js`, `css/polymer.css`)

Solo se animan `transform` y `opacity`, y `stroke-dashoffset` en los SVG.

| Elemento | Técnica |
| --- | --- |
| Transiciones entre páginas | **View Transitions API** entre documentos (`@view-transition { navigation: auto }`). Es una mejora progresiva: los navegadores sin soporte navegan normal. El sitio sigue siendo multipágina y las URLs no cambian. La cabecera y los breadcrumbs tienen nombre de transición propio. |
| Indicador de navegación | Barra superior con el degradado de marca al pulsar un enlace interno o enviar el buscador |
| Aparición al desplazarse | `IntersectionObserver` + `MutationObserver` (también para el contenido que llega de la API), con escalonado mediante la variable `--i` |
| Tarjetas | Elevación al pasar el ratón, sin mover el resto de la página |
| Agregar al carrito | «Vuelo» de un punto luminoso hasta el carrito (WAAPI) y rebote del contador |
| Confirmación de pedido | Check animado que se dibuja y sonido de confirmación (opcional) |
| Parallax | Disponible para elementos con `data-parallax` (desactivado con movimiento reducido) |
| Movimiento reducido | Sin intro, sin apariciones, sin transiciones de página: todo visible y estático |

## 5. Audio (`js/audio.js`)

Los sonidos se **sintetizan con Web Audio** (osciladores y envolventes): no hay archivos de audio ni
música de terceros. Son cuatro: **bienvenida** (acorde ascendente de unos 1,3 s), **confirmación**,
**notificación** y **error**.

- Están **apagados por defecto** y hay un botón global de silencio en la cabecera, que se recuerda en `localStorage`.
- Nunca suenan al pasar el ratón ni al desplazarse.
- Si el navegador no tiene Web Audio, la página funciona igual.

## 6. Pie comercial y páginas informativas

- `layout.js → renderFooter()` dibuja el pie con **5 columnas**:
  - **POLYMER:** logotipo, eslogan, descripción, WhatsApp, teléfono, horario, correos y Libro de reclamaciones;
  - **Sobre nosotros**;
  - **Te informamos**;
  - **Servicio al cliente**;
  - **Destacados**.
- Debajo hay una franja de **métodos de pago**: los del checkout en verde; los no integrados, punteados y con la etiqueta «próximamente».
- La barra inferior tiene las redes sociales, los enlaces rápidos, el copyright con el año actual y los datos legales configurables.
- Todos los enlaces llevan a **páginas reales**:
  - `/nosotros.html#historia`;
  - `/empresa.html` (canales, compra segura, métodos de pago, tiendas, cobertura, disponibilidad y redes);
  - `/legal.html` (términos, privacidad, cookies, devoluciones, garantías y facturación);
  - `/libro-reclamaciones.html`: un formulario real que se guarda con el tema `CLAIM` y lo ve el administrador;
  - el catálogo filtrado, las ofertas y el área del cliente.
- `tools/check_frontend.py` comprueba que todo enlace `/….html` de la tienda exista, y `intro-e2e.js` / `pages-e2e.js` que respondan.

## 7. Datos de demostración (configurables en `js/brand.js`)

| Dato | Valor | Nota |
| --- | --- | --- |
| Teléfono y WhatsApp | +51 900 000 000 | Marcados como **DEMO** en el pie; no son enlaces que llamen a un número |
| Correos | `soporte@example.com`, `contacto@example.com` | Dominio reservado `example.com` (RFC 2606) |
| Horario | Lunes a sábado, de 9:00 a. m. a 6:00 p. m. | |
| Ubicación | Lima, Perú | Sin dirección exacta |
| Razón social y RUC | `null` → «no configurada / no configurado» | **No se inventa un RUC** |
| Redes sociales | `url: null` → enlazan a `/empresa.html#redes` | No hay cuentas oficiales y no se simula ninguna |
| Métodos de pago | Contra entrega, transferencia y pago en tienda | Tarjetas y billeteras: «próximamente». No se nombran Visa, Mastercard, Yape ni Plin |

Para producción basta con editar `brand.js`: los cambios se reflejan en el pie, en las páginas informativas
y en el Libro de reclamaciones.

## 8. Backend (cambios mínimos)

| Cambio | Archivo |
| --- | --- |
| Migración **V8** no destructiva: la marca propia de los productos pasa de `HexaOrders` a `Polymer`, el valor por defecto de `products.brand` y el tema de contacto `CLAIM`. No toca usuarios, contraseñas, pedidos ni líneas de pedido | `db/migration/V8__polymer_brand.sql` |
| Firma de los correos: «— Equipo Polymer · Tecnología que conecta contigo.» | `NotificationComposer` |
| Remitente por defecto: `Polymer <no-reply@hexaorders.local>` (la dirección técnica no cambia) | `IntegrationConfiguration` |
| Marca por defecto de un producto nuevo sin marca: `Polymer` | `ProductDetails` |
| Tema `CLAIM` (Libro de reclamaciones) | `ContactMessage` |
| Rutas cortas `/empresa`, `/legal`, `/libro-reclamaciones` | `WebPagesConfiguration` |

Los nombres de los usuarios de demostración ya creados no se modifican, porque la base existente se
respeta. Solo una instalación nueva crea al administrador como «Administración Polymer».
