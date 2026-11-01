# HexaOrders — Presentation Guide (English)

**HexaOrders — Order Management & Risk Assessment System.** This guide prepares the oral defense: concepts, a short and a full speaking script, the live demonstration and difficult questions with answers grounded in the code. The explanation is verbal (no slides); the technical part is explained on `docker-compose.yml` and on the running application.

> Tip: keep `docker-compose.yml`, the web UI (<http://127.0.0.1:8080>) and Grafana (<http://127.0.0.1:3000/d/hexaorders>) open in three tabs before you start.

---

## 1. What is Hexagonal Architecture?

Hexagonal Architecture, also called **Ports and Adapters**, was described by **Alistair Cockburn in 2005**. It separates the **inside** of an application (business rules and use cases) from the **outside** (HTTP, databases, other services, frameworks).

- A **port** is a contract (an interface) defined by the inside. *Input (driving) ports* describe what the application offers; *output (driven) ports* describe what the application needs.
- An **adapter** connects a port to a concrete technology: a REST controller, a JDBC repository, an HTTP client.
- **Dependency rule:** source-code dependencies point **towards the core**. The domain depends on nothing external.
- The hexagon is only a drawing. It does **not** require six components, six layers or microservices.

## 2. Why did we choose it?

1. The course evaluates architecture, and this pattern makes the architecture **testable**: we have automated tests that fail if the rule is broken (ArchUnit in Java, an AST test in Python).
2. Our use case has **two external dependencies** (a risk service and a database) plus metrics: exactly the situation where ports isolate the business logic.
3. It lets us use **two languages** while keeping one clear contract between them.

## 3. Advantages

- **Testability:** `OrderService` is tested with mocks of its ports, without Spring, HTTP or PostgreSQL (`OrderServiceTest`).
- **Replaceable integrations:** replacing the Python engine or the database means writing a new adapter for the same port; the use case does not change.
- **Clear responsibilities:** controllers translate HTTP, repositories translate SQL, and the domain decides.
- **Framework independence of the core:** verified by `HexagonalArchitectureTest`.

## 4. Disadvantages

- **More code:** extra interfaces, records and mappings (for example `OrderDraft` and DTO records in adapters).
- **Discipline is required:** without tests, business rules tend to leak into controllers or SQL.
- **Overkill for tiny CRUD apps.**
- It organizes dependencies only; it does **not** solve availability, security or distributed failures by itself. Switching databases is still real work (queries, migrations, transactions).

## 5. Real-world applications (documented, with scope)

| Source | What it documents | Scope / limitation |
| --- | --- | --- |
| [Netflix TechBlog, *Ready for changes with Hexagonal Architecture* (2020)](https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749) | The Studio Workflows team used hexagonal principles to integrate data from several systems and swap data sources coming from a monolith. | One team and one application in 2020; it does not describe all of Netflix today. |
| [Allegro Tech Blog, *Hexagonal Architecture by example* (2020)](https://blog.allegro.tech/2020/05/hexagonal-architecture-by-example.html) | An Allegro engineer explains daily use and a worked example with an independent domain and REST/outgoing adapters. | An explanatory example; not a description of Allegro's whole platform. |
| [AWS Prescriptive Guidance, *Hexagonal architecture pattern*](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/hexagonal-architecture.html) | Definition, motivation and application with AWS Lambda integrations. | Vendor guidance; it does not claim every AWS service is built this way. |

Do not claim more than these sources say.

## 6. Technologies used

| Area | Technology | Role |
| --- | --- | --- |
| Main service | Java 21, Spring Boot 3.5 | REST API, dependency injection, JDBC, Actuator metrics; also serves the web UI. |
| Risk engine | Python 3.12, FastAPI, Pydantic | `POST /score` with exact decimals. |
| Database | PostgreSQL 17, Flyway | `orders` table, versioned migration, persistent volume. |
| Monitoring | Micrometer, prometheus_client, Prometheus 3.5, Grafana 12.4 | Business and technical metrics, alert rules, provisioned dashboard. |
| Front end | HTML, CSS, vanilla JavaScript modules | No framework; all data comes from the API. |
| Containers | Docker Compose (BuildKit) | Five services, internal networks, secrets, health checks, resource limits. |
| Optional | Kubernetes | 14 manifests, validated against the official schema. |
| Quality | JUnit 5, Mockito, ArchUnit, pytest, Playwright, GitHub Actions | Unit, architecture, API, integration and UI tests; CI on Ubuntu and Windows. |

## 7. Project structure

```text
services/order-api/src/main/java/com/hexaorders/
  domain/              Order, OrderDraft, OrderStatus, OrderStatistics   (pure Java)
  application/port/in  OrderUseCases                                     (input port)
  application/port/out RiskAssessmentPort, OrderRepository, OrderTelemetry (output ports)
  application/service  OrderService                                      (use case)
  adapter/in/web       OrderController, ApiExceptionHandler              (driving adapters)
  adapter/out/...      HttpRiskAssessmentAdapter, JdbcOrderRepository, MicrometerOrderTelemetry
  config/              HexagonalConfiguration                            (composition root)
services/risk-engine/risk_engine/
  domain/risk.py  application/ports.py  application/score_risk.py  adapters/http.py  bootstrap.py
infra/      compose.base.yml (template), Prometheus and Grafana configuration
k8s/        optional Kubernetes manifests
tools/      generator, validator, smoke test, PowerShell CLI, UI end-to-end test
```

## 8. Domain

- `OrderDraft` validates the request in business terms: customer id `[A-Za-z0-9_-]{1,64}`, amount greater than 0 and at most 1,000,000.00 with **at most two decimals (no rounding)**, currency USD.
- `OrderStatus.fromRiskScore` owns the decision: **score ≥ 70 → REVIEW, otherwise APPROVED**.
- `Order`'s constructor guarantees that the status always matches the score.
- Python's domain (`risk.py`) is a **deterministic** policy: amount < 500 → 10, 500–999.99 → 40, ≥ 1000 → 75. It is **not** AI.

## 9. Use cases

`OrderService.create(draft)`:

1. Ask the risk port for a score.
2. If it fails or the score is outside 0..100: record a metric and stop. **Nothing is saved.**
3. Create the `Order` (the domain decides the status).
4. Save it with **one atomic INSERT**.
5. Record `orders_created_total{status}`.

We call the risk service **before** touching the database, so we never keep a database transaction open during a network call. The other use cases are `get(id)`, `latest()` (50 most recent) and `statistics()`.

## 10. Input ports

`OrderUseCases` is the only thing `OrderController` knows about the application. Python mirrors this with `ScoreRiskPort`, a `typing.Protocol` used by the FastAPI adapter.

## 11. Output ports

- `RiskAssessmentPort`: "give me a score 0..100 or throw `RiskUnavailableException`". It does not mention HTTP or Python.
- `OrderRepository`: `save`, `findById`, `latest`, `statistics`. It does not mention SQL.
- `OrderTelemetry`: `orderCreated`, `riskFailure`. It does not mention Micrometer.

## 12. Adapters

| Adapter | Type | Implements / uses |
| --- | --- | --- |
| `OrderController` | Driving (REST) | uses `OrderUseCases` |
| `ApiExceptionHandler` | Driving | maps exceptions to 400 / 404 / 503 `ProblemDetail` |
| `HttpRiskAssessmentAdapter` | Driven (HTTP client) | implements `RiskAssessmentPort` |
| `JdbcOrderRepository` | Driven (JDBC) | implements `OrderRepository` |
| `MicrometerOrderTelemetry` | Driven (metrics) | implements `OrderTelemetry` |
| `adapters/http.py` | Driving (FastAPI) | uses `ScoreRiskPort` |

`HexagonalConfiguration` is the **composition root**: the only place that picks concrete adapters and builds `OrderService`.

## 13. Java and Python communication

- Contract: `POST http://risk-engine:8000/score` with `{"customerId", "amount"}` and response `{"score", "decision"}`.
- Exact money: Python parses JSON numbers directly into `Decimal` (`parse_float=Decimal`).
- Timeouts: 2 s connect, 3 s read. Java validates the response.
- Java keeps the final decision even if Python returns its own `decision`.
- Failure: HTTP **503**, no order stored, `orders_risk_failures_total` increases.
- Network: the internal Docker network `compute`; the risk engine is not published to the host.

## 14. Docker Compose

`docker-compose.yml` is the **single-file deliverable**. It embeds the Dockerfiles, source code, tests, web UI and monitoring configuration (`build.dockerfile_inline` plus BuildKit heredocs), so copying this one file and creating two secret files is enough to run everything. It is generated from `infra/compose.base.yml` by `tools/generate_delivery.py`, and CI checks that it is up to date. Points to show in the YAML:

- five services: `db`, `risk-engine`, `order-api`, `prometheus` and `grafana`;
- `depends_on: condition: service_healthy` and health checks;
- four networks, three of them `internal`, with ports published only on `127.0.0.1`;
- file secrets in `/run/secrets`, plus `read_only`, `cap_drop: ALL`, memory and CPU limits;
- volumes `postgres_data`, `prometheus_data` and `grafana_data`.

## 15. Kubernetes

An optional adaptation: Namespace, ConfigMap, StatefulSet for PostgreSQL, Deployments, Services and PVCs (14 objects). It was **validated statically** against the Kubernetes 1.34.1 OpenAPI schema. **Say clearly that it was not deployed to a cluster** as part of this work. Docker Compose remains the main deployment.

## 16. PostgreSQL

The Flyway migration `V1__create_orders.sql` creates `orders` with `CHECK` constraints that repeat the domain invariants: defense in depth. Data lives in the `postgres_data` volume, so it survives container restarts and `docker compose down`, but not `down -v`.

## 17. Prometheus

Prometheus scrapes Java (`/actuator/prometheus` on the internal port 9090) and Python (`/metrics`) every 10 s.

- Business metrics: `orders_created_total{status}`, `orders_risk_failures_total` and `risk_evaluations_total{decision}`.
- Technical metrics: `http_server_requests_seconds_*` and `risk_evaluation_duration_seconds_*`.
- Alert rules: `HexaOrdersServiceDown` and `RiskRequestsFailing`.

## 18. Grafana

The datasource and dashboard are provisioned as code. Panels:
- service availability;
- orders by status;
- risk evaluations;
- p95 latency in Java and Python;
- HTTP responses by status code;
- 5xx errors.

The 5xx panel shows the 503s when we stop the risk engine.

## 19. Live demonstration (script)

| Step | Say | Do |
| --- | --- | --- |
| 1 | "Everything runs from one YAML file." | `.\hexaorders.cmd start` (better: start it before the session). |
| 2 | "This order goes through every layer." | Create an order of **125.50** → APPROVED, risk 10. |
| 3 | "The threshold is a domain rule in Java." | Create **1500.00** → REVIEW, risk 75. |
| 4 | "The domain validates, not the browser." | Try **0** in *Architecture → Run a real request* → 400, nothing stored. |
| 5 | "Here are the real classes and files." | *Architecture*: switch between *code dependencies* and *execution flow*. |
| 6 | "What if Python is down?" | `docker compose stop risk-engine`, create an order → **503**, total unchanged. |
| 7 | "Recovery." | `docker compose start risk-engine` → orders work again. |
| 8 | "We can observe all of this." | *Observability* tab, then Grafana: orders, latency, the 5xx spike. |
| 9 | "Data is persistent." | `.\hexaorders.cmd stop` then `start` → orders are still there. |

## 20. Conclusions

HexaOrders shows that hexagonal architecture is a **verifiable rule**, not a folder name. The domain and the use cases do not know Spring, FastAPI, PostgreSQL or Grafana. Each external technology sits behind a port and can be tested or replaced independently. The cost is more interfaces and discipline, which is justified here by two external integrations and two languages.

---

## Short speaking script (≈ 3–4 minutes)

> Good morning. I'm going to present **HexaOrders**, an order management and risk assessment system. A customer creates an order; a Java service asks a Python service for a risk score; the Java domain decides whether the order is approved or needs review; and the result is stored in PostgreSQL. Everything runs with Docker Compose and is monitored with Prometheus and Grafana.
>
> The core idea is **hexagonal architecture**, also called ports and adapters, described by Alistair Cockburn in 2005. We separate the inside of the application, the business rules and use cases, from the outside: HTTP, databases and other services. The inside defines **ports**, which are interfaces. The outside provides **adapters** that implement or use them. Source-code dependencies always point towards the core.
>
> In our Java service, the domain classes `Order`, `OrderDraft` and `OrderStatus` are plain Java. The use case, `OrderService`, depends on three output ports: one for risk, one for persistence and one for metrics. It does not know that risk is an HTTP call to Python or that persistence is PostgreSQL. A REST controller is the input adapter. This is not just a convention: an **ArchUnit** test fails if the domain imports Spring or an adapter, and a similar test checks the Python service.
>
> The advantages are testability, replaceable integrations and clear responsibilities. The disadvantages are more interfaces and code, and the discipline needed to keep rules out of controllers; for a tiny CRUD application it would be too much. Companies have documented this pattern: for example, a Netflix team in 2020 and an Allegro engineer the same year, each for specific applications.
>
> Our deliverable is **one YAML file** that contains the Dockerfiles, the code and the monitoring configuration for five services. I'll now create orders, show the architecture explorer with the real classes, stop the Python service to show a controlled 503 with no data stored, and show the metrics in Grafana. Thank you.

## Full speaking script (≈ 10 minutes)

**Opening (30 s).** Good morning. Today I'll present HexaOrders, an order management and risk assessment system built to demonstrate hexagonal architecture with Java, Python, PostgreSQL, Docker Compose, Prometheus and Grafana. First the concepts, then the code, then a live demonstration.

**Concept (1.5 min).** Hexagonal architecture, or ports and adapters, was described by Alistair Cockburn in 2005. The application has an inside and an outside. Inside: the domain, meaning the business rules, and the use cases. Outside: everything technical, such as web frameworks, databases, message brokers and other services. The inside defines ports. An input port says what the application can do; an output port says what it needs from the world. Adapters connect ports to technologies. The key rule is that source-code dependencies point inwards: the domain depends on nothing external. The hexagon is just a drawing; it does not mean six services.

**Our use case (1.5 min).** When a customer creates an order, `OrderController`, the input adapter, builds an `OrderDraft`. The domain validates it: a valid customer id, a positive amount with at most two decimals and no rounding, and USD. Then the controller calls the input port `OrderUseCases`, implemented by `OrderService`. The service asks the output port `RiskAssessmentPort` for a score. Its implementation, `HttpRiskAssessmentAdapter`, calls the Python service over HTTP with a two-second connection timeout and a three-second read timeout. Python returns a score; the Java domain, in `OrderStatus`, decides: 70 or more means review. Only then do we save the order with one atomic INSERT through `OrderRepository`, implemented with JDBC. Finally we record a metric through `OrderTelemetry`.

**Dependency inversion (1 min).** At runtime, the service calls the HTTP adapter. In the code, however, the adapter depends on the port, not the other way round. That is dependency inversion, and it is why we can test `OrderService` with simple mocks and no servers. `HexagonalConfiguration` is the composition root, the only place that wires the concrete adapters.

**Python (45 s).** The risk engine is a second, smaller hexagon. FastAPI is its input adapter; `ScoreRiskPort` is its input port; `ScoreRisk` is the use case; and `risk.py` is the domain: a deterministic rule with three amount bands. It is not artificial intelligence. Money is parsed directly as `Decimal`, so there is no floating-point error.

**Proof (45 s).** How do we know the rule is respected? ArchUnit tests fail if the domain or application packages import Spring, JDBC, Micrometer, adapters or configuration. In Python, a test parses the source code and fails if the core imports anything outside the standard library.

**Pros and cons (1 min).** Pros: testability, replaceable integrations, clear responsibilities. Cons: more code, more interfaces, and discipline. It is not worth it for a tiny CRUD app, and it does not solve availability or security by itself. Changing a database is still work. As documented examples, a Netflix Studio Workflows team in 2020, an Allegro engineer in 2020 and AWS's prescriptive guidance describe this pattern, each with a limited scope.

**Docker Compose and infrastructure (1.5 min).** Let me open `docker-compose.yml`. It is generated, and it is the single deliverable: it contains the Dockerfiles, the source code with its tests, which run during the build, and the monitoring configuration. Five services. Four networks: only `edge` publishes ports, and only on localhost. The database and the risk engine are internal. Secrets are files mounted at `/run/secrets`. Containers are read-only, drop all Linux capabilities and have memory limits. Health checks and `depends_on` make the API wait for a healthy database and risk engine. Volumes keep the data.

**Observability (45 s).** Prometheus scrapes both services every ten seconds: business metrics such as orders created by status and risk failures, and technical ones such as HTTP latency. Grafana is provisioned as code with a dashboard. Kubernetes manifests are included as an optional extra; they were validated against the official schema, not deployed to a cluster.

**Demo and closing (rest).** Now the live demonstration [follow section 19]. To conclude: hexagonal architecture here is a rule we can verify, not just folder names, and it allowed us to integrate two languages and several technologies while keeping the business rules clean. Thank you; I'm happy to answer questions.

---

## Difficult questions and answers (grounded in the code)

**Q1. Where exactly is the business rule, and how do you prove the domain is independent?**
The threshold is in `OrderStatus.fromRiskScore` (≥ 70 → REVIEW) and the validations in `OrderDraft`, both in `domain/`. `HexagonalArchitectureTest` (ArchUnit) forbids `domain` and `application` from depending on `org.springframework`, `java.sql`, `com.zaxxer`, `io.micrometer`, `com.fasterxml`, `..adapter..` and `..config..`. It runs in every build.

**Q2. Python already returns a decision. Why does Java decide again?**
Because the approval policy belongs to the order domain. `HttpRiskAssessmentAdapter` only uses Python's score. It checks that `decision` is a known value as a sanity check, and the comment in the code says that the Java domain owns the decision.

**Q3. What happens if Python is down or slow?**
After 2 s (connect) or 3 s (read) the adapter throws `RiskUnavailableException`. `OrderService` records `riskFailure()` and rethrows, and `ApiExceptionHandler` returns 503. Because risk is evaluated before persistence, **no row is inserted**. `OrderServiceTest.neverPersistsWhenRiskEngineIsUnavailable` and the CI `verify` step, which really stops the container, both check this.

**Q4. Why not a database transaction around the whole operation?**
We avoid holding a database transaction open during a network call. The only write is a single INSERT, which is atomic in PostgreSQL autocommit, executed after we already have the score.

**Q5. Isn't Spring's `@RestController` in the controller a violation?**
No. The controller is an adapter, and adapters are allowed to depend on frameworks. The restriction applies to `domain` and `application`, and ArchUnit checks exactly those packages.

**Q6. How would you replace PostgreSQL?**
Write another `OrderRepository` implementation and select it in `HexagonalConfiguration`. `OrderService` and the domain do not change. Honestly, the SQL, the migration and the transaction behavior must still be rewritten and tested. The pattern limits the impact; it does not make the change free.

**Q7. Is the risk engine AI?**
No. `risk.py` is a deterministic three-band rule (10 / 40 / 75), chosen to be explainable for this course.

**Q8. How do you avoid money rounding errors?**
Java uses `BigDecimal` and rejects more than two decimals instead of rounding. Python parses JSON numbers directly as `Decimal`, and the database column is `NUMERIC(12,2)` with a `CHECK` constraint.

**Q9. Why is the YAML so large? Isn't it better to reference the source folders?**
The assignment asks for a YAML deliverable. Embedding the sources makes the single file self-contained and reproducible. To avoid editing it by hand, it is generated, and `generate_delivery.py --check` in CI fails if it diverges from the sources.

**Q10. Why are containers read-only, and how do configuration and secrets get in?**
It is security hardening. Compose cannot copy inline configs or environment-based secrets into read-only containers, so the Prometheus and Grafana configuration is baked into small images built inline, and secrets are files mounted read-only at `/run/secrets`.

**Q11. Does a healthy container mean the system works?**
No. Health checks only show that a process answers. That is why we also run a smoke test that creates real orders, checks persistence and metrics, and verifies the 503 path, plus a browser test of the UI.

**Q12. Did you deploy Kubernetes?**
No. The manifests were validated against the official Kubernetes 1.34.1 schema, which checks structure, not runtime behavior. The main deployment is Docker Compose.

**Q13. How does Prometheus know about orders? And why could Grafana show different totals from the dashboard?**
`MicrometerOrderTelemetry` increments counters behind the `OrderTelemetry` port, and Prometheus scrapes them. Counters reset when a process restarts, while the web dashboard reads totals from PostgreSQL via `/api/orders/statistics`. That difference is expected and documented.

**Q14. Is the "real request" animation in the UI a real trace?**
The HTTP status, the latency measured in the browser, the response and the re-read from PostgreSQL are real observations of that request. The numbered path is the conceptual code path for that outcome, and the UI labels it as such; we did not add distributed tracing.

**Q15. What would you improve for production?**
Authentication, retries with a circuit breaker for the risk call, pagination, distributed tracing (OpenTelemetry), separate database roles for migration and application, image digests, backups and multiple replicas.
