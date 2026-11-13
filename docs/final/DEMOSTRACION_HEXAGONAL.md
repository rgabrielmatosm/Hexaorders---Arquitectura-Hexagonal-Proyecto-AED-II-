# Demostración práctica de la arquitectura hexagonal

Tres demostraciones, todas reversibles y sin tocar datos existentes:

1. **Recorrido** del controlador al adaptador (`.\polymer.cmd demo -Scenario arquitectura`).
2. **Cambio de una regla de negocio** con pruebas, reconstrucción de un solo servicio y efecto en la web.
3. **Sustitución de un adaptador** (inversión de dependencias) sin cambiar el caso de uso.

Base teórica y fragmentos de código: [`ARQUITECTURA_HEXAGONAL.md`](ARQUITECTURA_HEXAGONAL.md).

---

## 1. Recorrido del código (VS Code)

Abre en este orden (`Ctrl+P` y pega la ruta):

| # | Archivo | Qué mostrar |
| --- | --- | --- |
| 1 | `services/order-api/src/main/java/com/polymer/adapter/in/web/ShopController.java` | `place(...)`: HTTP → `commerce.placeOrder(...)`. El campo `commerce` es una **interfaz** |
| 2 | `.../application/port/in/CommerceUseCases.java` | El puerto de entrada: lo que la aplicación ofrece |
| 3 | `.../application/service/CommerceService.java` | `place(...)`: riesgo → dominio → persistencia. Solo usa interfaces (`risk`, `orders`, `cache`) |
| 4 | `.../domain/OrderStatus.java` | `fromRiskScore`: REVIEW si el puntaje es ≥ 70. Java puro |
| 5 | `.../application/port/out/RiskAssessmentPort.java` | El puerto de salida: lo que el núcleo necesita |
| 6 | `.../adapter/out/http/HttpRiskAssessmentAdapter.java` | El adaptador real: `RestClient` → `POST /score` |
| 7 | `.../adapter/out/fixed/FixedRiskAssessmentAdapter.java` | Otro adaptador del mismo puerto |
| 8 | `.../config/HexagonalConfiguration.java` | La raíz de composición elige el adaptador |
| 9 | `services/order-api/src/test/java/com/polymer/architecture/HexagonalArchitectureTest.java` | Las 8 reglas que impiden romper la arquitectura |

Ejecución con evidencias (comprueba que cada archivo existe y ejecuta ArchUnit y la prueba de sustitución
en un contenedor Maven, sin instalar Java en Windows):

```powershell
.\polymer.cmd demo -Scenario arquitectura
```

**Mostrar que la arquitectura se protege sola** (opcional, revertir después):

1. En `domain/OrderStatus.java`, añade bajo `APPROVED, REVIEW;` la línea
   `static final org.springframework.jdbc.core.JdbcTemplate DEMO = null;`.
2. `.\polymer.cmd demo -Scenario arquitectura` → falla `CORE_HAS_NO_FRAMEWORK_OR_INFRASTRUCTURE` con
   `Field <com.polymer.domain.OrderStatus.DEMO> has type <org.springframework.jdbc.core.JdbcTemplate>`
   (resultado observado en este trabajo).
3. Revertir: `git checkout -- services/order-api/src/main/java/com/polymer/domain/OrderStatus.java`.

---

## 2. Cambio de una regla de negocio

**Petición del profesor:** «los pedidos a partir de cierto importe deben pasar a revisión».

### 2.1 Localizar la regla existente

La regla de importe está en el **dominio del microservicio de riesgo** (Python):
`services/risk-engine/risk_engine/domain/risk.py`, función `assess`:

```python
score = 75 if amount >= Decimal("1000") else 40 if amount >= Decimal("500") else 10
return RiskResult(score, Decision.REVIEW if score >= 70 else Decision.APPROVED)
```

Y el umbral de decisión está en el **dominio Java**: `domain/OrderStatus.fromRiskScore` (REVIEW si ≥ 70).

Hoy, por tanto: importe < S/ 500 → 10 (aprobado); S/ 500–999,99 → 40 (aprobado); ≥ S/ 1000 → 75 (revisión).

> **Por qué no se cambia el umbral 70 de Java:** el mismo umbral está protegido también en PostgreSQL por
> la restricción `orders_status_matches_risk` (`V1__create_orders.sql`). Cambiar solo Java a 80 haría que
> un pedido con puntaje 75 se guardara como APPROVED y PostgreSQL lo rechazaría. Ensayado en este trabajo:
> además, rompe 3 pruebas (`OrderDomainTest.riskThresholdIsOwnedByDomain`,
> `OrderServiceTest.assessesRiskBeforePersistenceAndCountsOnlyPersistedOrders`,
> `CommerceServiceTest.pricesOnTheServerAsksPythonForRiskBeforePersistingAndRecordsHistory`).

### 2.2 Capa, caso de uso y puerto

- Capa: **dominio** de `risk-engine` (Python). No cambia ningún contrato: el JSON de `/score` es el mismo.
- Caso de uso Python: `risk_engine/application/score_risk.py` (`ScoreRisk.execute`).
- Puerto Java que lo consume: `RiskAssessmentPort`, desde `CommerceService.place(...)`.
- Por eso solo hay que reconstruir **un** contenedor: `risk-engine`.

### 2.3 El cambio (parche reversible)

`tools/demo/regla-revision-desde-500.patch` baja el umbral a S/ 500 y actualiza su prueba:

```diff
-    score = 75 if amount >= Decimal("1000") else 40 if amount >= Decimal("500") else 10
+    score = 75 if amount >= Decimal("500") else 10  # DEMO: revisión desde S/ 500 (antes: 1000)
...
-        ("500.00", 40, Decision.APPROVED),
-        ("999.99", 40, Decision.APPROVED),
+        ("500.00", 75, Decision.REVIEW),
+        ("999.99", 75, Decision.REVIEW),
```

### 2.4 Pasos en la exposición

```powershell
git switch -c demo/regla-500
```

```powershell
git apply tools/demo/regla-revision-desde-500.patch
```

**Momento didáctico (opcional):** antes del parche completo, cambia solo la línea de `risk.py` y
reconstruye: el build **falla** porque las pruebas de dominio se ejecutan dentro del Dockerfile
(`RUN python -m pytest`). Ensayado: fallan `test_risk_thresholds[500.00-40-APPROVED]` y
`test_risk_thresholds[999.99-40-APPROVED]`. Las pruebas protegen la regla: hay que cambiar la regla **y**
su especificación.

Reconstruir **solo** el servicio afectado (las pruebas se ejecutan en el build; los datos no se tocan):

```powershell
.\polymer.cmd rebuild -Services risk-engine
```

**Demostrar en la web:**

1. `http://localhost:8080` → entra con una cuenta de cliente (o regístrate).
2. Compra un producto de entre S/ 500 y S/ 999 (por ejemplo, filtra el catálogo por precio).
3. En **Mi cuenta → Seguimiento** el pedido aparece **en revisión** (antes del cambio habría quedado aprobado).
4. Con `operador` o `admin`: **Operaciones → Revisión de riesgo** muestra el pedido con riesgo 75/100.

### 2.5 Restaurar la regla original

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

```powershell
.\polymer.cmd health
```

Comprobado en este trabajo (local): con el parche aplicado, `pytest` → **62 passed**; revertido →
**62 passed**; `git apply --check` confirma que el parche se aplica sobre el código actual.

---

## 3. Sustitución de adaptadores (inversión de dependencias)

**Idea:** el caso de uso depende de `RiskAssessmentPort`, no de HTTP. Si conectamos otra implementación,
el caso de uso y el dominio funcionan igual.

| Pieza | Cambia | Archivo |
| --- | --- | --- |
| Puerto `RiskAssessmentPort` | No | `application/port/out/RiskAssessmentPort.java` |
| Caso de uso `CommerceService` / `OrderService` | No | `application/service/` |
| Dominio `OrderStatus` | No | `domain/OrderStatus.java` |
| Adaptador conectado | **Sí**: `HttpRiskAssessmentAdapter` → `FixedRiskAssessmentAdapter` | `adapter/out/http/`, `adapter/out/fixed/` |
| Raíz de composición | Elige por la propiedad `polymer.risk.adapter` | `config/HexagonalConfiguration.java` |

**Cómo se activa (solo configuración, sin endpoints):**

```powershell
.\polymer.cmd demo-on -FixedRisk -FixedScore 10
```

Recrea **solo** `order-api` con `infra/demo/compose.demo.yml` (`POLYMER_RISK_ADAPTER=fixed`). El log de
`order-api` muestra:
`DEMOSTRACIÓN: RiskAssessmentPort usa FixedRiskAssessmentAdapter (puntaje fijo 10); risk-engine no se consulta`.

**Demostración con evidencias** (detiene `risk-engine`, compra un producto de más de S/ 1000 y comprueba
que el pedido se crea con puntaje 10 y aprobado; después vuelve a arrancar `risk-engine`):

```powershell
.\polymer.cmd demo -Scenario adaptador
```

**Cómo se desactiva:**

```powershell
.\polymer.cmd demo-off
```

`order-api` vuelve a `docker-compose.yml` (adaptador HTTP y TTL de 60 s). Con el adaptador real, el mismo
experimento (risk-engine detenido) devuelve **HTTP 503**: `.\polymer.cmd demo -Scenario riesgo-caida`.

**Por qué es seguro:** la propiedad no está en `docker-compose.yml` ni en ningún despliegue; no hay
endpoint para activarla; el adaptador fijo valida que el puntaje esté entre 0 y 100.

**Prueba automática:** `services/order-api/src/test/java/com/polymer/adapter/AdapterSubstitutionTest.java`
(4 pruebas): `OrderService` funciona con el adaptador fijo; la raíz de composición conecta el adaptador HTTP
por defecto y el fijo con `polymer.risk.adapter=fixed`; el adaptador fijo rechaza puntajes fuera de rango.

**Otra sustitución que ya existía:** `CatalogCache` tiene dos implementaciones, `RedisCatalogCache` (modo
`full`) y `CatalogCache.NONE` (modo `basic`), elegidas en `config/IntegrationConfiguration.java` con
`polymer.integrations.redis.enabled`. `CatalogService` es idéntico en ambos modos.
