# POLYMER v7 — Google, verificación por correo, pagos, boleta, mapa, horario y video

Guía de lo que añade la v7, cómo se configura con **tus** cuentas reales y cómo demostrarlo. Todo funciona
sin configurar nada (con Mailpit, pasarelas de prueba y una dirección de demostración); las integraciones
reales con Google y Gmail se activan rellenando archivos de `secrets\` y, opcionalmente, un `.env`.

## 1. Qué se implementó y dónde vive (arquitectura hexagonal)

| Funcionalidad | Dominio (reglas) | Puerto | Adaptadores |
| --- | --- | --- | --- |
| Código de 6 dígitos por correo | `domain/identity/VerificationPolicy`, `VerificationChallenge` (10 min, 5 intentos, reenvío cada 60 s, máx. 5 envíos) | `port/in/SignInUseCases`, `port/out/VerificationChallenges`, `MailSender` | `JdbcVerificationChallenges` (solo el hash BCrypt), `SmtpMailSender` (Mailpit o Gmail) |
| «Continuar con Google» | `domain/identity/ExternalIdentity` (correo verificado por Google, usuario sugerido) | `SignInUseCases.withExternalIdentity`, `port/out/AccountSecurity` | Entrada: `GoogleSignInHandlers` (Spring Security OAuth2/OIDC). Salida: `JdbcAccountSecurity` (`user_identities`) |
| «Recordarme durante 30 días» | `VerificationPolicy.REMEMBER_FOR` | `port/out/DeviceTokens` | `RememberedLoginFilter` + `AuthCookies` (entrada), `JdbcDeviceTokens` (salida, SHA-256 de la ficha) |
| Pago con tarjeta (inmediato) | `domain/payment/CardDetails` (Luhn, marca, vencimiento, CVV), `Payment` | `port/in/PaymentUseCases`, `port/out/CardGateway` | `SandboxCardGateway` (pruebas) |
| Yape y Plin (60 s) | `PaymentPolicy.WALLET_TTL = 60 s`, límite S/ 2000 | `port/out/WalletGateway`, `PaymentWindow` | `SandboxWalletGateway`, `RedisPaymentWindow` (clave con TTL), `ClockPaymentWindow` (sin Redis) |
| Boleta con IGV | `domain/payment/Receipt` (B001-00000001, base + IGV 18 % = total) | `port/out/PaymentRepository`, `ReceiptSource` | `JdbcPaymentRepository` (secuencia `receipt_number_seq`), correo por el outbox → RabbitMQ → `NotificationService` |
| Mapa y horario de hoy | `domain/store/BusinessHours`, `PeruHolidays` (incluye Semana Santa), `StoreProfile` | `port/in/StoreUseCases` | `StoreController` (público), iframes de Google Maps / Calendar en la interfaz |

Las 8 reglas ArchUnit siguen en verde con todo lo anterior: el dominio y los casos de uso no conocen Spring,
OAuth, SMTP, Redis ni PostgreSQL.

## 2. Configurar «Continuar con Google» (gratis)

> **Forma rápida (v8):** con POLYMER en marcha ejecuta `.\polymer.cmd google`. El asistente te muestra los
> pasos de Google Cloud, pide el **ID de cliente** y el **secreto** (oculto) y, si quieres, tu **Gmail** y su
> **contraseña de aplicación** (oculta). Los guarda en `secrets\` y `.env` sin mostrarlos, reinicia `order-api`,
> comprueba que el botón redirige a `accounts.google.com` con tu ID y envía un correo de prueba a tu Gmail.
> Los pasos manuales equivalentes son los de abajo.
>
> Registra **las dos** URI de retorno: `http://localhost:8080/login/oauth2/code/google` y
> `http://127.0.0.1:8080/login/oauth2/code/google`, y entra a la tienda con la misma dirección que registraste.

1. Entra a <https://console.cloud.google.com/> con tu cuenta de Google y crea un proyecto (p. ej. «POLYMER»).
2. **APIs y servicios → Pantalla de consentimiento de OAuth**: tipo *Externo*; nombre de la app «POLYMER»;
   tu correo como soporte y contacto. En **Usuarios de prueba** añade tu Gmail (mientras la app esté en
   modo *Prueba*, solo esos usuarios pueden entrar). Ámbitos: `openid`, `email`, `profile` (los básicos).
3. **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth**: tipo *Aplicación web*.
   - **URI de redireccionamiento autorizados**: `http://localhost:8080/login/oauth2/code/google`
     (en AWS añade `https://TU_DOMINIO/login/oauth2/code/google`).
4. Copia el **ID de cliente** en `secrets\google_client_id.txt` y el **Secreto del cliente** en
   `secrets\google_client_secret.txt` (una sola línea, sin comillas). Estos archivos están fuera de Git.
5. Aplica el cambio sin tocar datos:

   ```powershell
   .\polymer.cmd restart -Services order-api
   ```

6. Abre <http://localhost:8080/login.html>: aparece **Continuar con Google**. Tras elegir tu cuenta, POLYMER
   te envía un **código de 6 dígitos** (Mailpit por defecto, o tu Gmail si configuras la sección 3).

Qué hace el servidor: Spring Security valida el token de Google (firma, emisor, audiencia y *nonce*); el
núcleo vincula la cuenta por el identificador estable de Google (`sub`) o, la primera vez, por el correo
verificado; si no existe, crea una cuenta de cliente. La sesión **no** queda abierta hasta verificar el
código. Con «Recordarme» el dispositivo queda de confianza 30 días y no vuelve a pedir el código.

## 3. Enviar los correos a tu Gmail (opcional)

Por defecto los correos (códigos, boletas, avisos de pedidos) los captura **Mailpit** en
<http://127.0.0.1:8025>, sin salir a Internet. Para recibirlos en tu bandeja real con Gmail:

1. En tu cuenta de Google activa la **verificación en 2 pasos** y crea una **contraseña de aplicación**
   (<https://myaccount.google.com/apppasswords>).
2. Pega esa contraseña (16 letras) en `secrets\smtp_password.txt`.
3. Crea un archivo `.env` junto a `docker-compose.yml` (plantilla: `.env.example`) con:

   ```dotenv
   POLYMER_SMTP_HOST=smtp.gmail.com
   POLYMER_SMTP_PORT=587
   POLYMER_SMTP_USERNAME=tu.cuenta@gmail.com
   POLYMER_MAIL_FROM=POLYMER <tu.cuenta@gmail.com>
   ```

4. Aplica el cambio:

   ```powershell
   .\polymer.cmd restart -Services order-api
   ```

Gmail limita los envíos (unos 500 al día en cuentas personales). Las contraseñas nunca van en `.env`.

## 4. Código de verificación y «Recordarme»

| Regla | Valor |
| --- | --- |
| Cuándo se pide | Al entrar con Google desde un dispositivo no recordado; al registrarse con contraseña (confirmar el correo); y al entrar con contraseña si activas la **verificación en dos pasos** en *Perfil y seguridad* |
| Código | 6 dígitos aleatorios (`SecureRandom`), guardado solo como hash BCrypt |
| Vigencia e intentos | 10 minutos; 5 intentos; reenvío cada 60 s; 5 envíos como máximo |
| «Recordarme durante 30 días» | Cookie `POLYMER_REMEMBER` (sesión persistente) y `POLYMER_DEVICE` (dispositivo de confianza): HttpOnly, SameSite=Lax, Secure con HTTPS. En la base solo se guarda el SHA-256 |
| Robo de cookie | La ficha rota en cada uso; si alguien reutiliza una antigua, se cierran **todas** las sesiones recordadas |
| Perder un equipo | *Perfil y seguridad → Olvidar todos mis dispositivos* |
| Modo `basic` (sin servidor de correo) | No se piden códigos (nadie queda bloqueado) |

Las cuentas que ya existían (incluidas las de demostración) conservan su acceso: la migración V11 las
marca con el correo verificado.

## 5. Pagos y boleta

Al confirmar el pedido con **Tarjeta**, **Yape** o **Plin**, la tienda abre la página de pago.

| Método | Comportamiento | Pruebas (no cobran nada) |
| --- | --- | --- |
| Tarjeta | Aprobación inmediata. Se valida Luhn, vencimiento y CVV; solo se guardan marca y 4 últimos dígitos | Aprobadas: `4111 1111 1111 1111`, `5555 5555 5555 4444`, `3782 822463 10005`. Rechazadas: `4000 0000 0000 0002` (fondos insuficientes), `4000 0000 0000 0069` (bloqueada), `5105 1051 0510 5100` |
| Yape / Plin | Se genera una solicitud con **QR que vence en 60 s**. Con Redis es la clave `polymer:payment:window:<id>` con `TTL 60` (`docker compose exec redis redis-cli TTL …`); sin Redis se compara el reloj con `expires_at` | Cualquier celular `9XXXXXXXX` y cualquier código de 6 dígitos; `000000` simula un rechazo |
| Boleta | Serie `B001` y correlativo de la base de datos; base imponible + IGV 18 % = total; página imprimible (PDF desde el navegador) y **correo con la boleta** (outbox → RabbitMQ → consumidor idempotente → SMTP) | — |

Garantías: nunca dos pagos aprobados del mismo pedido (índice único parcial en PostgreSQL); 5 rechazos
bloquean el pago en línea del pedido; Yape/Plin admiten hasta S/ 2000.

**Para producción**: sustituir `SandboxCardGateway` y `SandboxWalletGateway` por adaptadores de una
pasarela real (Niubiz, Izipay, Culqi, Mercado Pago…) que **tokenice la tarjeta en el navegador** (el número
no debe pasar por el servidor: PCI DSS SAQ A). El núcleo y la interfaz del puerto no cambian. La boleta es
un **comprobante interno**: emitir boletas electrónicas válidas ante SUNAT requiere un OSE/PSE y queda fuera
de este proyecto.

## 6. Tienda física: mapa, horario de hoy y Google Calendar

- **Mapa**: iframe de Google Maps sin clave de API y botón **Cómo llegar** (portada, Contacto, Empresa y recojo
  en el checkout).
- **Horario de hoy**: fecha y estado (abierto/cerrado) en hora de Lima, calendario del mes con el día de hoy,
  semana completa y feriados nacionales (incluidos Jueves y Viernes Santo calculados).
- **Google Calendar**: botón **Agregar visita a Google Calendar** (evento con fecha, hora, dirección y zona).
  Opcional: un calendario **público** incrustado con `POLYMER_GOOGLE_CALENDAR_ID` en `.env`.
- **Cambiar la dirección**: la de fábrica es de **demostración** (un centro comercial conocido de Lima para
  que el mapa muestre un lugar real). Pon la tuya en `.env` (`POLYMER_STORE_ADDRESS`, `POLYMER_STORE_DISTRICT`,
  `POLYMER_STORE_LATITUDE`, `POLYMER_STORE_LONGITUDE`) y reinicia `order-api`.

## 7. Video de la portada

`infra/frontend/media/polymer-hero.mp4` (10 s, 1280×720, H.264, 414 KB, sin audio, en bucle) es una pieza
**original** de la marca: se genera con `tools/media/hero-scene.html` (animación de canvas) y
`tools/media/render-hero.js` (Playwright + ffmpeg). Se reproduce silenciado, tiene botón de pausa y, con
«reducir movimiento», se muestra el póster. Para usar un video propio de la empresa, reemplaza
`polymer-hero.mp4` y `polymer-hero.jpg` (máx. ~2 MB) y regenera el entregable:

```powershell
py tools\generate_delivery.py
```

## 8. Demostrarlo en la exposición

```powershell
.\polymer.cmd demo -Scenario pagos
```

Muestra la clave de Yape en Redis con su TTL, cómo vence (TTL = -2 y el servidor responde 409), un QR nuevo
aprobado, la tabla `payments` con la boleta, una tarjeta rechazada y otra aprobada, y el correo con la boleta
en Mailpit. En el navegador: **Iniciar sesión → Continuar con Google** (con tus credenciales, sección 2),
el código en Mailpit, una compra con tarjeta y la boleta impresa.

## 9. Qué se probó y qué no

| Probado | Dónde |
| --- | --- |
| Reglas de dominio y casos de uso (código, intentos, vencimientos, Google, «Recordarme», robo de cookie, tarjeta, Yape 60 s, boleta, IGV, horario y feriados) | Pruebas Java (`SignInPaymentStoreDomainTest`, `SignInServiceTest`, `PaymentsAndReceiptsTest`, `SignInWebTest`) |
| Flujo completo en el navegador: Google (con un proveedor OpenID Connect **de pruebas**), código por correo, «Recordarme», tarjeta, Yape con vencimiento real a los 60 s, boleta, correo de la boleta, video, mapa, horario, móvil | `tools/ui-e2e/v7-e2e.js` en el CI con Docker (`infra/ci/compose.google-mock.yml`) |
| API de pagos y tienda; boleta por correo y métrica `polymer_payments_total` | `tools/store_smoke.py`, `tools/integration_smoke.py` |

**No probado aquí**: el inicio de sesión con los servidores reales de Google y el envío a un Gmail real
(requieren tus credenciales; los pasos están en las secciones 2 y 3); cobros reales (las pasarelas son de
prueba por diseño).
