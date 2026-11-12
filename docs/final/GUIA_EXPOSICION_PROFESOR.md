# Guía de la exposición técnica (Windows + VS Code + Docker Desktop)

Cada escenario tiene un comando que **comprueba sus evidencias** y falla si alguna no se cumple (el mismo
script se ejecuta en el CI contra Docker real: `tools/demo/polymer-demo.ps1`). En la exposición, el script
se detiene en cada paso hasta pulsar Enter; con `-NoPause` corre seguido.

## 0. Antes de empezar (10 minutos antes)

```powershell
.\polymer.cmd start
```

```powershell
.\polymer.cmd demo -Scenario preparar
```

Comprueba que los 13 contenedores están en marcha y sanos y muestra las URL. Ten abiertas estas pestañas:

| Interfaz | URL | Acceso |
| --- | --- | --- |
| Tienda | http://localhost:8080 | cuentas `cliente`, `operador`, `logistica`, `admin`, `observador` (contraseña en `secrets\demo_password.txt`) |
| Grafana | http://localhost:3000 | `admin` / `secrets\grafana_password.txt` |
| Prometheus | http://localhost:9091 | — |
| RabbitMQ | http://localhost:15672 | `hexaorders` / `secrets\rabbitmq_password.txt` |
| Mailpit | http://localhost:8025 | — |

En VS Code, abre la carpeta del proyecto y una terminal PowerShell. **No muestres en pantalla** los
archivos de `secrets\`.

Orden recomendado (≈ 40 min): A → B → C → D → E → F → G → H. Los escenarios solo crean datos **ficticios**
(clientes `demo_xxxx` con correo `@example.test` y sus pedidos); no borran nada.

---

## Escenario A — Arquitectura hexagonal

- **Teoría:** el núcleo (dominio + casos de uso) define puertos (interfaces); los adaptadores implementan
  la tecnología; las dependencias apuntan hacia dentro. Ver `ARQUITECTURA_HEXAGONAL.md`.
- **Objetivo:** recorrer un pedido desde el controlador REST hasta los adaptadores y mostrar que la
  arquitectura está protegida por pruebas.
- **Archivos y métodos:** `ShopController.place` → `CommerceUseCases.placeOrder` →
  `CommerceService.place` → `OrderStatus.fromRiskScore` → `RiskAssessmentPort.assess` →
  `HttpRiskAssessmentAdapter.assess` / `CommerceOrderRepository.placeOrder` →
  `JdbcCommerceOrderRepository`; raíz de composición `HexagonalConfiguration`.
- **Comando:**

  ```powershell
  .\polymer.cmd demo -Scenario arquitectura
  ```

- **Resultado esperado:** 10 evidencias de archivos y «ArchUnit (8 reglas) y AdapterSubstitutionTest
  (4 pruebas) en verde» (en un contenedor Maven; la primera vez descarga dependencias).
- **Evidencia verificable:** salida de Maven `Tests run: …, Failures: 0`.
- **Posibles errores:** sin Internet la primera vez (descarga de Maven). Recuperación: `-SkipMavenTests`
  y mostrar que las mismas pruebas se ejecutan al construir la imagen (`.\polymer.cmd rebuild -Services order-api`).

## Escenario B — Cambio de una regla de negocio

- **Teoría:** una regla vive en el dominio; cambiarla no toca controladores, base de datos ni contratos.
- **Objetivo:** «los pedidos desde S/ 500 pasan a revisión» (hoy: desde S/ 1000).
- **Archivos:** `services/risk-engine/risk_engine/domain/risk.py` (`assess`) y
  `services/risk-engine/tests/test_domain.py`; parche `tools/demo/regla-revision-desde-500.patch`.
- **Comandos:**

  ```powershell
  git switch -c demo/regla-500
  ```

  ```powershell
  git apply tools/demo/regla-revision-desde-500.patch
  ```

  ```powershell
  .\polymer.cmd rebuild -Services risk-engine
  ```

- **Web:** como cliente, compra un producto de S/ 500–999 → **Seguimiento: en revisión**; como
  `operador`, **Operaciones → Revisión de riesgo** muestra el pedido con riesgo 75/100.
- **Evidencia:** el build ejecuta `pytest` (62 pruebas); el pedido queda en `RISK_REVIEW`.
- **Restaurar:**

  ```powershell
  git apply -R tools/demo/regla-revision-desde-500.patch
  ```

  ```powershell
  git switch -
  ```

  ```powershell
  git branch -D demo/regla-500
  ```

  ```powershell
  .\polymer.cmd rebuild -Services risk-engine
  ```

- **Posibles errores:** el build falla → es lo esperado si cambiaste la regla sin su prueba (momento
  didáctico). Recuperación: `git apply -R …` o `git checkout -- services/risk-engine`.
- **B2 (inversión de dependencias):** `.\polymer.cmd demo-on -FixedRisk`, `.\polymer.cmd demo -Scenario adaptador`,
  `.\polymer.cmd demo-off`. Detalle en `DEMOSTRACION_HEXAGONAL.md`, sección 3.

## Escenario C — Redis

- **Teoría:** caché *read-through* con TTL de 60 s, invalidación por versión, degradación a PostgreSQL.
  Ver `DEMOSTRACION_REDIS_TTL.md`.
- **Archivos:** `CatalogCache` (puerto), `RedisCatalogCache` (adaptador), `CatalogService.categories`.
- **Comandos** (TTL de 20 s solo durante la demostración):

  ```powershell
  .\polymer.cmd demo-on -DemoTtl 20
  ```

  ```powershell
  .\polymer.cmd demo -Scenario redis
  ```

- **Resultado esperado:** miss → clave en Redis con TTL → hit → TTL = -2 → miss → clave nueva;
  `polymer_cache_requests_total` por resultado en Prometheus.
- **Web:** Grafana → **POLYMER — Platform Integrations** → «Caché Redis del catálogo por resultado / s».
- **Restaurar:** `.\polymer.cmd demo-off` (al final de C o de la exposición).

## Escenario D — RabbitMQ

- **Teoría:** outbox transaccional, publisher confirms, cola durable, DLQ, consumidor idempotente; entrega
  **al menos una vez**. Ver `DEMOSTRACION_RABBITMQ.md`.
- **Comando:**

  ```powershell
  .\polymer.cmd demo -Scenario rabbitmq
  ```

- **Resultado esperado:** pedido → historial en PostgreSQL → `outbox_published` → `processed_events` →
  correo en Mailpit; topología durable en la API de RabbitMQ.
- **Web:** RabbitMQ → *Queues* → `hexaorders.notifications` (gráfico de mensajes); Mailpit → el correo.

## Escenario E — PostgreSQL

- **Comando:**

  ```powershell
  .\polymer.cmd demo -Scenario postgres
  ```

- **Resultado esperado:** consultas SQL de solo lectura del pedido, sus líneas y su historial; reinicio de
  `db` y `order-api`; la misma «huella» después del reinicio.
- **SQL manual:** `docker compose exec db psql -U orders -d orders` (consultas en
  `DEMOSTRACION_POSTGRESQL.md`).

## Escenario F — Microservicios (Java ↔ Python)

- **Teoría:** `order-api` consulta a `risk-engine` por HTTP (`POST /score`); la decisión es del dominio Java.
  Ver `DEMOSTRACION_MICROSERVICIOS.md` (incluye el análisis honesto: 1 monolito modular + 1 microservicio).
- **Comando** (con `demo-off`, adaptador real):

  ```powershell
  .\polymer.cmd demo -Scenario riesgo
  ```

- **Resultado esperado:** pedido barato → 10/APPROVED; pedido ≥ S/ 1000 → 75/REVIEW; líneas `POST /score`
  en los logs de `risk-engine`; `X-Trace-Id` para buscar la traza en **Grafana → Explore → Tempo**.

## Escenario G — Grafana y métricas

- **Comando:**

  ```powershell
  .\polymer.cmd demo -Scenario observabilidad
  ```

- **Resultado esperado:** valores reales de Prometheus: pedidos por decisión, peticiones HTTP, latencia p95,
  caché, eventos publicados y pendientes, correos, *targets* UP.
- **Web:** Grafana → dashboards **POLYMER — Business Operations**, **POLYMER — API Performance**, **POLYMER — Platform Integrations**, **POLYMER — Risk Engine** y **POLYMER · Puertos, adaptadores y métricas**;
  Explore → Loki `{service="order-api"}` y Tempo con el `X-Trace-Id` del escenario F.
  Relación métrica ↔ servicio ↔ panel: `AUDITORIA_POLYMER.md`, sección 7.

## Escenario H — Resiliencia

| Comando | Dependencia | Comportamiento demostrado |
| --- | --- | --- |
| `.\polymer.cmd demo -Scenario redis-caida` | Redis (no crítica) | El catálogo sigue desde PostgreSQL; `error`/`bypass`; recuperación automática |
| `.\polymer.cmd demo -Scenario rabbitmq-caida` | RabbitMQ (no crítica para comprar) | El pedido se guarda; el evento espera en el outbox; al volver se publica y llega el correo |
| `.\polymer.cmd demo -Scenario riesgo-caida` | risk-engine (crítica) | 503 y ningún pedido sin evaluar; al volver, 201 |

Cada escenario vuelve a arrancar la dependencia al terminar. Si una ejecución se interrumpe:
`.\polymer.cmd start` restaura todos los servicios sin tocar datos.

---

## Cierre y restauración

```powershell
.\polymer.cmd demo-off
```

```powershell
git status
```

(Debe estar limpio; si no, `git apply -R tools/demo/regla-revision-desde-500.patch`.)

```powershell
.\polymer.cmd health
```

## Preguntas probables

| Pregunta | Respuesta corta y dónde verlo |
| --- | --- |
| ¿Dónde está la regla de negocio? | Dominio: `domain/OrderStatus.java` (Java) y `risk_engine/domain/risk.py` (Python) |
| ¿Qué pasa si quito Spring? | El dominio y los casos de uso no lo importan (regla ArchUnit `CORE_HAS_NO_FRAMEWORK_OR_INFRASTRUCTURE`) |
| ¿Cómo cambiarías PostgreSQL por otra base? | Nuevo adaptador de `CommerceOrderRepository` y demás puertos de persistencia; los casos de uso no cambian |
| ¿Es «exactamente una vez»? | No: al menos una vez + consumidor idempotente (`processed_events`) |
| ¿Hay circuit breaker? | No; hay timeouts, reintentos con backoff en el consumidor, fallback y back-off en Redis. Justificación en `DEMOSTRACION_MICROSERVICIOS.md` |
| ¿Son microservicios? | Un monolito modular hexagonal + un microservicio de riesgo + infraestructura |
| ¿Dónde están las contraseñas? | Archivos en `secrets\` (Docker Secrets), fuera de Git; `change-password` para cambiarlas |
