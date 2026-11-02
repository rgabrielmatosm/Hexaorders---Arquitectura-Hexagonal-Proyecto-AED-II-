# HexaOrders v3 — Interview Explanation (5–8 minutes)

A spoken script to present HexaOrders in a technical interview or portfolio review. It runs **about 6–7 minutes** at a calm pace (roughly 130 words per minute). The section times are guides. The demo cues in *italics* are optional: if you cannot share your screen, skip them.

> Before you start: run `.\hexaorders.cmd start` and open the shop (`http://127.0.0.1:8080/#/tienda`), the admin area (log in as `admin`) and Grafana (`http://127.0.0.1:3000/d/hexaorders-business`). The demo password is in `secrets\demo_password.txt`.

---

## 1. Opening — what it is (≈ 45 s)

"HexaOrders started as a university project to demonstrate **hexagonal architecture**, also called ports and adapters. It began as a small lab: a Java service receives an order, asks a Python risk engine for a score, and stores the decision in PostgreSQL.

For version 3, I evolved it into a small **commerce and operations platform**. It has a public shop with a catalog, cart and demo checkout, five security roles, an order lifecycle with ten states, a logistics calendar, incidents, an audit log and six Grafana dashboards. The rule I set for myself was: **keep everything that already existed and only add**. The original API, the lab UI, the original dashboard, its metrics and its alerts all still work, and automated checks prove it."

## 2. Architecture (≈ 1 min 15 s)

"The core idea is that business rules do not depend on frameworks. In the Java service, the `domain` package holds pure rules: for example, `TransitionPolicy` says which lifecycle transitions exist and which roles may perform them. The `application` package holds the use cases and the **ports**: interfaces such as `CommerceOrderRepository`, `RiskAssessmentPort` or `BusinessTelemetry`.

The **adapters** live outside: REST controllers and Spring Security on the input side; JDBC repositories, the HTTP client for Python, Micrometer metrics and BCrypt on the output side.

This is not just a convention. **ArchUnit tests fail the build** if the domain imports Spring or JDBC, or if a controller skips the input ports. In Python, a test parses the source code and checks the same rule.

The best evidence that it works is reuse: the new shop checkout calls **the same risk port and the same Python engine** as the original lab. I did not touch the risk logic at all."

*Demo cue: open `docs/adr/` or the Mermaid diagrams in the README.*

## 3. A request from start to finish (≈ 1 min)

"Let me follow a checkout. The browser sends `POST /api/shop/orders` with a session cookie, a CSRF token and an `Idempotency-Key` header.

The service first checks whether that key has already been used. If it has, it returns the original order, so a double click never creates two orders. Then it prices the cart **on the server**. The client never sends prices. Next, it calls the Python engine over HTTP with timeouts, *before* opening a database transaction, so we never hold locks while waiting on the network.

Inside one transaction it inserts the order, its lines, the status history and an audit event, and it decreases stock with a conditional update, `WHERE stock >= quantity`. Stock can never go negative, and a race between two customers ends in a clean `409 OUT_OF_STOCK`."

*Demo cue: add a product to the cart, confirm the order, and show the tracking timeline.*

## 4. Roles, lifecycle and operations (≈ 1 min 15 s)

"There are five roles. A **customer** shops and tracks orders. An **order operator** reviews risky orders and moves orders through confirmation and preparation. A **logistics operator** schedules deliveries in a month, week or day calendar and marks dispatch and delivery. An **observability viewer** sees system health only. The **admin** manages products, stock, users, roles, incidents, reports and the audit log.

Authorization is enforced twice: URL rules in Spring Security, and role guards inside the use cases. So even a bug in a controller cannot let a customer confirm an order.

Every transition is idempotent and uses **optimistic locking** with a version column. If two operators click at the same time, one wins and the other receives `409 CONCURRENT_UPDATE`. The calendar also rejects overlapping deliveries for the same driver with `409 SCHEDULE_CONFLICT`."

*Demo cue: log in as `operador`, open an order and show that only the allowed buttons appear; then show the logistics calendar.*

## 5. Data and safe migrations (≈ 45 s)

"Real data matters, so the database changes are **non-destructive**. The Flyway migration only adds tables and columns with defaults, and it backfills existing orders as lab orders. I tested the upgrade for real: I ran the old version, created orders, started the new version, and the old orders were still there with their history. Lab orders and shop orders share one table, but the public legacy API filters by channel, so customer orders are never exposed through it."

## 6. Observability (≈ 1 min)

"The system exports business metrics, such as orders by lifecycle status, checkout failures by reason, transitions, deliveries and low stock, plus security metrics like failed logins and denied access. There are five new dashboards: business, risk, API performance, infrastructure and security, plus seven new alerts.

While doing this I found a **real pre-existing bug**. The Prometheus Java client renames counters that end in `_created`, so the original `orders_created_total` panels showed no data. Instead of editing the original panels, I added a recording rule that republishes the series under its original name.

To make sure nothing is lost in the future, I captured a **baseline** of every original panel, query, alert, job and metric, and a script fails the build if any of them disappears or changes."

*Demo cue: open the business dashboard in Grafana.*

## 7. Quality, delivery and honest limitations (≈ 45 s)

"Testing is layered: domain and use-case tests, security tests for each role, a 35-check smoke test against the real stack, and Playwright tests that drive the UI as each role. GitHub Actions runs everything on Ubuntu with Docker, and it also runs the Windows tooling on a Windows runner, because the project must run on Windows with a single command.

There are limitations, and I document them. The checkout is a demo with no payments. There is no login rate limiting yet. Kubernetes manifests are validated against the schema but not deployed. There are no container-level metrics. Those would be my next steps, together with OpenTelemetry tracing between Java and Python."

## 8. Closing (≈ 15 s)

"In short, HexaOrders shows that hexagonal architecture is not just a diagram. It let me grow a small lab into a multi-role platform while reusing the core, protecting existing data, and proving with automated checks that nothing that already worked was broken. Thank you. I'm happy to go deeper into any part."

---

## Likely follow-up questions

| Question | Short answer |
| --- | --- |
| Why sessions instead of JWT? | Same origin, easy logout and revocation, no token in `localStorage`. CSRF is handled with a synchronizer token. See ADR 0002. |
| Why not JPA? | Explicit SQL for conditional stock updates and optimistic locking, and no ORM entities leaking into the domain. See ADR 0008. |
| How do you prevent duplicate orders? | A required `Idempotency-Key` header with a unique column; a replay returns the original order with `200` and `Idempotent-Replay: true`. |
| Where do transactions live if services don't know Spring? | Inside the persistence adapters, which expose atomic port operations such as `placeOrder` and `transition`. |
| What if the risk engine is down? | Lab orders return `503`. Checkout fails cleanly with `risk_unavailable`, nothing is stored, and an alert fires after repeated failures. |
| How do you know the old dashboard still works? | `check_observability.py` compares against the captured baseline, and the CI smoke test loads all six dashboards from a real Grafana. |
| What would change for production? | Login rate limiting, Spring Session for multiple replicas, OpenTelemetry, Alertmanager, backups, cAdvisor, and a real Kubernetes deployment with TLS. |

## Useful vocabulary

| English | Spanish |
| --- | --- |
| ports and adapters | puertos y adaptadores |
| driving / driven adapter | adaptador de entrada / de salida |
| idempotency key | clave de idempotencia |
| optimistic locking | bloqueo optimista |
| backfill | relleno de datos existentes |
| recording rule | regla de registro (Prometheus) |
| role-based access control (RBAC) | control de acceso basado en roles |
| non-destructive migration | migración no destructiva |
| smoke test | prueba de humo |
