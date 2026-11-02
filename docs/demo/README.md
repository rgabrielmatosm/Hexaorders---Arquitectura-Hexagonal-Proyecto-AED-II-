# Demostración técnica reproducible

Guion para demostrar HexaOrders de principio a fin. Cada paso indica **qué hacer**, **qué se espera** según el código y **qué se ha observado** realmente, con su fuente. No se altera el sistema para producir resultados: si algo no coincide, es un hallazgo y no se oculta.

Material: [colección de Postman](HexaOrders.postman_collection.json) (con pruebas automáticas) · [solicitudes `.http`](requests.http) (VS Code REST Client / JetBrains).

## Preparación

```powershell
.\hexaorders.cmd start
```

Abre <http://127.0.0.1:8080>, <http://127.0.0.1:3000/d/hexaorders> (usuario `admin`, contraseña en `secrets\grafana_password.txt`) y <http://127.0.0.1:9091/targets>.

## Pasos

| # | Acción | Resultado esperado (según el código) |
| --- | --- | --- |
| 1 | Crear pedido de **125.50** USD (formulario, Postman 1 o `.http` 1) | `201`, `riskScore` 10, `APPROVED`, cabecera `Location`. |
| 2 | Crear pedido de **750.00** USD | `201`, `riskScore` 40, `APPROVED`. |
| 3 | Crear pedido de **1500.00** USD | `201`, `riskScore` 75, `REVIEW` (75 ≥ 70). |
| 4 | Importe **10.001** o **0**; cliente `demo cliente!` | `400` «Solicitud inválida»; no aumenta el total. |
| 5 | Consultar el último pedido por ID; listar pedidos | `200` con el mismo pedido; listado de como máximo 50. |
| 6 | Persistencia: `docker compose exec -T db psql -U orders -d orders -c "SELECT customer_id, amount, risk_score, status FROM orders ORDER BY created_at DESC LIMIT 5;"` | Las filas de los pasos 1–3 están en la tabla `orders`. |
| 7 | Comunicación Java → Python: **Arquitectura → Ejecutar solicitud real** | 10 pasos completados; `riskScore` devuelto por Python; relectura idéntica desde PostgreSQL. |
| 8 | Indisponibilidad: `docker compose stop risk-engine` y crear un pedido (Postman 10) | `503` «Motor de riesgo no disponible»; el total de `/statistics` **no** cambia. |
| 9 | Recuperación: `docker compose start risk-engine` y `docker compose up -d --wait` | Un pedido nuevo vuelve a dar `201`. |
| 10 | Métricas: **Observabilidad** y <http://127.0.0.1:9091/targets> | Targets `order-api`, `risk-engine` y `prometheus` en `up`; `orders_created_total` y `risk_evaluations_total` con valores. |
| 11 | Grafana: dashboard «HexaOrders» | Paneles con datos tras ~30 s de tráfico; el panel 5xx muestra el 503 del paso 8. |
| 12 | Reinicio: `.\hexaorders.cmd stop` y `.\hexaorders.cmd start` | Los pedidos siguen en el panel y en el listado (volumen `postgres_data`). |

> El paso 12 usa `stop` **sin** `-RemoveData`. `-RemoveData` (equivale a `docker compose down -v`) borra los volúmenes.

## Resultados observados

Las ejecuciones automáticas reproducen la mayoría de los pasos sin intervención manual. Fuente: workflow `Verify HexaOrders` de GitHub Actions.

| Paso | Observado | Fuente |
| --- | --- | --- |
| 1–3 | `201` con riesgos 10, 40 y 75 y estados APPROVED, APPROVED y REVIEW; cada pedido se relee por ID. | `tools/smoke.py` (aserciones) — CI en verde el 01/11/2026 (commit `a71acf3`). |
| 4 | `400` para importe 0. | `tools/smoke.py` — mismo CI. |
| 6 | Persistencia comprobada mediante relectura por la API (no con `psql`). | `tools/smoke.py`. |
| 8–9 | Con `risk-engine` detenido: HTTP `503`; tras reiniciarlo, los servicios vuelven a estar sanos. | `tools/hexaorders.ps1 verify`, pasos 7 y 8 — mismo CI. |
| 10 | Targets `order-api`, `risk-engine` y `prometheus` en `up`; dashboard `hexaorders` aprovisionado en Grafana. | `tools/smoke.py` — mismo CI. |
| 5, 7 y estadísticas (versión 2) | Contra Java, Python y PostgreSQL 16 reales ejecutados en local sin Docker: consulta por ID `200`, solicitud real del explorador con 10 pasos y relectura idéntica, estadísticas coherentes (`total = approved + review`). | `tools/smoke.py` y `tools/ui-e2e/ui-e2e.js` — 01/11/2026, ver [`docs/validacion.md`](../validacion.md). |
| 8, 9 y 12 (versión 2) | `503` con el motor detenido y estadísticas sin cambios (12 → 12); `201` tras recuperarlo; 13 pedidos antes y después de reiniciar el proceso Java. | Ejecución local manual, 01/11/2026. |
| 10 con interfaz, Docker Compose de la versión 2 | Pendiente: lo ejecuta el CI al publicar la rama (Prometheus, Grafana y las capturas en el artefacto `hexaorders-ui-screenshots`). | CI pendiente de autorización para el push. |
| 11 | No automatizado: verificación visual de Grafana durante la demostración. | — |

## Plataforma v3 (portal y roles)

Contraseña de las cuentas demo: `secrets\demo_password.txt`.

| # | Acción | Resultado esperado (según el código) |
| --- | --- | --- |
| P1 | Abrir `#/tienda`, buscar y filtrar por categoría | Catálogo de PostgreSQL (18 productos, 6 categorías). |
| P2 | Añadir al carrito e ir a **Checkout** sin sesión | Pide iniciar sesión; el carrito se conserva. |
| P3 | Entrar como `cliente` y **Confirmar pedido** | `201`; seguimiento con `CREATED` → `RISK_APPROVED` o `RISK_REVIEW`; el stock baja. |
| P4 | Repetir `POST /api/shop/orders` con la misma `Idempotency-Key` (lo automatiza `tools/platform_smoke.py`) | `200` + `Idempotent-Replay: true`; no se crea otro pedido. |
| P5 | Entrar como `operador`, **Gestionar** el pedido → Confirmar → Preparar → Listo | Solo aparecen los botones permitidos; el historial crece. |
| P6 | Entrar como `logistica`, programar la entrega en el calendario; intentar otra a la misma hora para el mismo repartidor | La segunda da `409 SCHEDULE_CONFLICT`. |
| P7 | Marcar **En tránsito** y **Entregado** | El cliente ve el pedido entregado en `#/mis-pedidos`. |
| P8 | Entrar como `admin`: KPIs, usuarios y roles, auditoría, informe CSV | Los eventos de los pasos anteriores aparecen en la auditoría. |
| P9 | Entrar como `observador` e ir a `#/admin` | «Acceso restringido»; en su área ve la salud de PostgreSQL y del motor de riesgo. |
| P10 | Abrir Grafana `/d/hexaorders-business` y `/d/hexaorders-security` | Pedidos por estado, transiciones, inicios de sesión. El dashboard original `/d/hexaorders` sigue igual. |

Resultados observados: [`../validacion.md`](../validacion.md#versión-3--plataforma-comercial-y-operaciones-2-de-noviembre-de-2026).
