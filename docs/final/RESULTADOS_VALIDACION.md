# Resultados de validación

Solo se anota lo **ejecutado y observado**. Lo que no se pudo ejecutar figura en la sección 4 con su
comando exacto. Matriz completa: [`MATRIZ_PRUEBAS.md`](MATRIZ_PRUEBAS.md).

## 1. Local (12/11/2026)

Entorno: contenedor Linux con Java 21, Maven (sin red: dependencias en caché), Python 3 con las
dependencias del motor de riesgo y Nginx 1.24. Docker no puede construir imágenes aquí (sin red), por eso
el stack completo se valida en el CI.

| Prueba | Resultado observado |
| --- | --- |
| `mvn package` (order-api) | **131 pruebas, 0 fallos, 0 errores** (122 anteriores + 4 reglas ArchUnit nuevas + 4 de `AdapterSubstitutionTest` + 1 de regresión de la caché) |
| `HexagonalArchitectureTest` | 8 reglas en verde |
| Mutación: campo `JdbcTemplate` en `domain/OrderStatus.java` | **Detectada**: `CORE_HAS_NO_FRAMEWORK_OR_INFRASTRUCTURE` falla con `Field <com.polymer.domain.OrderStatus.DEMO> has type <org.springframework.jdbc.core.JdbcTemplate>`; archivo restaurado |
| Cambio del umbral Java 70 → 80 (análisis) | Fallan 3 pruebas (`OrderDomainTest.riskThresholdIsOwnedByDomain`, `OrderServiceTest.assessesRiskBeforePersistenceAndCountsOnlyPersistedOrders`, `CommerceServiceTest.pricesOnTheServerAsksPythonForRiskBeforePersistingAndRecordsHistory`); restaurado. Además chocaría con la restricción `orders_status_matches_risk` de PostgreSQL |
| Regresión de la caché (`redisCacheReadsBackWhatItWroteIncludingDerivedAccessors`) | **Falla** con el `RedisCatalogCache` anterior y pasa con la corrección |
| `pytest` (risk-engine) | **62 passed** |
| Regla de importe 1000 → 500 solo en `risk.py` | Fallan `test_risk_thresholds[500.00-40-APPROVED]` y `[999.99-40-APPROVED]` (las pruebas protegen la regla) |
| Parche `tools/demo/regla-revision-desde-500.patch` | Aplicado: 62 passed; revertido (`git apply -R`): 62 passed; `git apply --check` correcto |
| `generate_delivery.py --check`, `validate_delivery.py` | OK (Compose autosuficiente, 14 objetos Kubernetes) |
| `check_observability.py` | OK: 8 dashboards, 9 alertas, observabilidad previa conservada |
| `generate_pages.py --check`, `check_frontend.py`, `generate_brand.py --check`, `generate_catalog.py --check`, `check_i18n.py` | OK (45 páginas, 20 categorías y 170 subcategorías, 2471 textos en 5 idiomas) |
| Gateway `infra/frontend/nginx.conf` (`nginx -t` + servidor de eco) | Sintaxis correcta. Sin `X-Forwarded-Proto` → `http` (comportamiento local intacto); con `X-Forwarded-Proto: https` y `Host` del proxy → `https` y el dominio |
| Plantilla `infra/aws/nginx-host-polymer.conf` (Nginx 1.24 = Ubuntu 24.04, certificados autofirmados de prueba) | `nginx -t` correcto; HTTP → **301** a `https://`; tienda por TLS con `X-Forwarded-Proto: https`; `/api/orders` → **403** desde una IP no permitida; Grafana → **403** desde una IP no permitida; cabecera **HSTS** |
| `bash -n` de `infra/aws/*.sh` | Sintaxis correcta (no ejecutados: requieren la instancia) |
| Auditoría de secretos en Git | Ningún archivo de secretos rastreado ni en el historial (50 commits); solo valores efímeros del CI y plantillas |

## 2. CI con Docker real

RESULTADOS_CI

## 3. Errores encontrados por la validación y corregidos

- El script de escenarios tenía dos errores que el CI detectó: una referencia `$ComposeFile:` que
  PowerShell interpreta como ámbito (detectado por el análisis con PowerShell 5.1) y una contraseña de
  cliente ficticio que contenía parte de su usuario, que la política de contraseñas del servidor rechazó
  con razón. Ambos corregidos.
- **Error real de la aplicación** detectado por el escenario `redis` en el CI: la caché contaba un *hit*
  y a la vez un `error`, y tras la expiración solo había `bypass`. Causa: `Category.isMain()` se
  serializa como `"main"` y no se podía volver a leer, así que el árbol de categorías nunca se servía
  desde Redis. Corregido en `RedisCatalogCache` con prueba de regresión (ver `AUDITORIA_POLYMER.md`, error 6).
- Dos funciones del script con el mismo nombre (`Invoke-Redis`) provocaban recursión infinita; el
  ayudante se renombró a `Invoke-RedisCli`.
- `infra/frontend/nginx.conf` perdía `https` detrás de un proxy TLS (ver `AUDITORIA_POLYMER.md`, error 1).

## 4. Pendiente de verificación (no se afirma que funcione)

| Qué | Por qué no se ejecutó | Cómo ejecutarlo |
| --- | --- | --- |
| Docker Desktop en un Windows real | Este entorno no es Windows con Docker Desktop; el CI de Windows no ejecuta contenedores Linux | `.\polymer.cmd start`, `.\polymer.cmd health`, `.\polymer.cmd demo -Scenario todo -NoPause` |
| Escenario `arquitectura` con Maven en contenedor en Windows | Igual que arriba | `.\polymer.cmd demo -Scenario arquitectura` |
| `health`, `rebuild`, `restart` | Solo analizados sintácticamente (PowerShell 5.1) | `.\polymer.cmd health`, `.\polymer.cmd rebuild -Services risk-engine`, `.\polymer.cmd restart -Services order-api` |
| Despliegue en AWS con HTTPS | Requiere tu aprobación (genera costos), una cuenta y un dominio | `DESPLIEGUE_AWS_HTTPS.md`, sección 6 |
| Copias `backup.sh` / `backup-verify.sh` | Requieren el stack en marcha | `bash infra/aws/backup.sh` y `bash infra/aws/backup-verify.sh <archivo>` (también funcionan en Linux local con Docker) |
| Manifiestos de Kubernetes | Sin clúster | `kubectl apply -f k8s/` en un clúster de pruebas |
| Publicación en un repositorio GitHub nuevo | Requiere tu autorización | `GUIA_GITHUB.md` |
