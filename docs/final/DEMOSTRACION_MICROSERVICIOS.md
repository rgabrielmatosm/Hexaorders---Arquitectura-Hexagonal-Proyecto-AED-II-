# Microservicios en POLYMER: qué hay realmente

## 1. Diagnóstico honesto

POLYMER ejecuta **13 contenedores**, pero solo **2 son servicios de negocio desarrollados en el proyecto**:

| Servicio | Lenguaje | Responsabilidad | Datos propios |
| --- | --- | --- | --- |
| `order-api` | Java 21, Spring Boot | Tienda, catálogo, carrito, pedidos, ciclo de vida, operaciones, logística, administración, notificaciones, portal de monitoreo | **Todos** los datos de negocio, en PostgreSQL (`db`) |
| `risk-engine` | Python 3.12, FastAPI | Puntuar el riesgo de un pedido (`POST /score`) | **Ninguno** (sin estado, sin base de datos) |

Los otros 11 contenedores son infraestructura: `frontend` (gateway Nginx), `db` (PostgreSQL), `redis`,
`rabbitmq`, `mailpit`, `prometheus`, `grafana`, `loki`, `tempo`, `otel-collector` y `alloy`.

Por tanto, POLYMER es un **monolito modular hexagonal (`order-api`) más un microservicio de evaluación de
riesgo**, rodeado de servicios de infraestructura. No es una arquitectura de muchos microservicios, y no
debe presentarse así. RabbitMQ se usa **dentro** de `order-api` (el publicador y el consumidor están en el
mismo servicio) para desacoplar en el tiempo el envío de notificaciones, no para comunicar microservicios.

## 2. Comunicación entre `order-api` y `risk-engine`

| Aspecto | Detalle |
| --- | --- |
| Protocolo | HTTP síncrono dentro de la red Docker `compute` (`http://risk-engine:8000`) |
| Contrato | `POST /score` · petición `{"customerId": "...", "amount": 123.45}` · respuesta `{"score": 0..100, "decision": "APPROVED"|"REVIEW"}` |
| Validación en Python | `risk_engine/domain/risk.py` (`Evaluation`): `customerId` `[A-Za-z0-9_-]{1,64}`, importe > 0, ≤ 1 000 000, máximo 2 decimales; si no, 422 |
| Validación en Java | `adapter/out/http/HttpRiskAssessmentAdapter`: `score` entero 0–100 y `decision` válida; si no, `RiskUnavailableException` |
| Quién decide | El **dominio Java** (`OrderStatus.fromRiskScore`, ≥ 70 → REVIEW). La `decision` de Python es informativa |
| Timeouts | Conexión 2 s, lectura 3 s (`config/HexagonalConfiguration`) |
| Reintentos | **No** hay |
| Si falla | `RiskUnavailableException` → `ApiExceptionHandler` → **HTTP 503**; el pedido **no** se guarda (*fail-closed*) |
| Trazas | Micrometer Tracing (Java) y OpenTelemetry (Python) propagan el contexto W3C; Tempo muestra una sola traza con spans de ambos (comprobado en el CI: «Traza completa en Tempo: … spans de order-api y risk-engine») |

## 3. ¿Se pueden desplegar por separado?

Sí, en lo técnico: imágenes distintas (`polymer/order-api`, `polymer/risk-engine`), contrato HTTP estable y
sin base de datos compartida. `.\polymer.cmd rebuild -Services risk-engine` actualiza solo el motor de
riesgo (ver `DEMOSTRACION_HEXAGONAL.md`, sección 2).

**Acoplamientos que existen:**

- **Temporal:** crear un pedido requiere que `risk-engine` responda (sin él, 503). Es una decisión de
  negocio: no aprobar pedidos sin evaluar.
- **De contrato:** si cambia el JSON de `/score`, hay que cambiar el adaptador Java.
- **De arranque:** en Compose, `order-api` espera a que `risk-engine` esté sano (`depends_on: service_healthy`).

## 4. Resiliencia: timeout, retry, fallback, backoff y circuit breaker

| Mecanismo | ¿Existe? | Dónde |
| --- | --- | --- |
| **Timeout** | Sí | Riesgo (2 s / 3 s), Redis (300 ms), SMTP (3–5 s), PostgreSQL (conexión 3 s, socket 5 s), confirmación de RabbitMQ (5 s) |
| **Retry** | Sí, solo en el consumidor de RabbitMQ | `listener.simple.retry`: 4 intentos con backoff exponencial (1 s, ×2, máx. 10 s) y después DLQ. El relay del outbox reintenta cada 2 s |
| **Fallback** | Sí, solo en la caché | `RedisCatalogCache`: si Redis falla, responde PostgreSQL (fuente de verdad) |
| **Backoff** | Sí | Redis: se omite 30 s tras un error (`polymer.cache.backoff`); consumidor RabbitMQ: exponencial |
| **Circuit breaker** | **No** | No hay Resilience4j ni equivalente; ninguna dependencia ni configuración lo implementa |

El *bypass* temporal de Redis se parece a un circuito «abierto» con tiempo de espera, pero no tiene
estados CLOSED/OPEN/HALF_OPEN ni umbrales de fallos: no se presenta como circuit breaker.

**¿Conviene añadir un circuit breaker al motor de riesgo?** No en este proyecto:

- El fallback correcto para el riesgo es **rechazar** (503), que es lo que ya ocurre en ≤ 3 s; un circuit
  breaker solo cambiaría la latencia del rechazo, no el resultado.
- Un fallback que aprobara pedidos sin evaluar cambiaría la regla de negocio.
- Añadiría una dependencia (Resilience4j) y estados que probar sin resolver un problema observado.

Si en el futuro el motor de riesgo tuviera latencias altas frecuentes, un circuit breaker con
`HALF_OPEN` evitaría acumular hilos esperando: sería entonces una mejora justificada.

## 5. Demostración desde el sitio web y con evidencias

```powershell
.\polymer.cmd demo -Scenario riesgo
```

1. Cliente ficticio compra un producto de menos de S/ 500 → `riskScore` 10, `APPROVED`.
2. Compra uno de S/ 1000 o más → `riskScore` 75, `REVIEW` (aparece en **Operaciones → Revisión de riesgo**).
3. Muestra las líneas `POST /score` en los logs de `risk-engine` y el `X-Trace-Id` de la respuesta para
   buscar la traza en **Grafana → Explore → Tempo** (spans `CommerceUseCases.placeOrder`,
   `risk-assessment-port` y `ScoreRisk.execute`).

```powershell
.\polymer.cmd demo -Scenario riesgo-caida
```

Detiene `risk-engine`, comprueba **503** y que no se guarda ningún pedido, y lo vuelve a arrancar.

**Desde la web (manual):** con un cliente, compra un producto caro → «En revisión»; con `operador`, en
**Operaciones → Revisión de riesgo**, confírmalo o recházalo (decisión humana auditada).
