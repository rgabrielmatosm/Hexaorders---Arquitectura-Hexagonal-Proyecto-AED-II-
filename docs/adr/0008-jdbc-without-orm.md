# ADR 0008 — JDBC en lugar de JPA

**Contexto.** El proyecto original usa `JdbcTemplate` y SQL explícito. La v3 necesita actualizaciones condicionales de stock, bloqueo optimista y transacciones que abarcan varias tablas.

**Decisión.** Seguir con `JdbcTemplate` y `TransactionTemplate` dentro de los adaptadores de persistencia. Las transacciones no se declaran en los servicios (que no conocen Spring); los puertos ofrecen operaciones atómicas (`placeOrder`, `transition`).

**Consecuencias.** SQL visible y predecible, sin entidades JPA que se filtren al dominio; a cambio, más código de mapeo manual (`Jdbc` helper).
