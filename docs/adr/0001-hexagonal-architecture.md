# ADR 0001 — Mantener la arquitectura hexagonal y ampliarla

**Contexto.** La v3 multiplica el alcance (usuarios, catálogo, ciclo de vida, logística, incidencias, auditoría). El objetivo académico del proyecto es demostrar la arquitectura hexagonal, y ya hay pruebas (ArchUnit, AST) que la vigilan.

**Decisión.** Cada capacidad nueva se modela como subdominio en `domain/` (`identity`, `catalog`, `fulfillment`, `logistics`, `incident`, `audit`), con casos de uso detrás de puertos de entrada (`CatalogUseCases`, `CommerceUseCases`, `OperationsUseCases`, `LogisticsUseCases`, `AdministrationUseCases`, `AccountUseCases`) y dependencias externas detrás de puertos de salida. El checkout reutiliza el puerto `RiskAssessmentPort` y la telemetría `OrderTelemetry` existentes. Se añade una regla ArchUnit: los adaptadores de entrada solo dependen de puertos de entrada.

**Consecuencias.** Más interfaces y records; a cambio, los servicios se prueban con dobles sin Spring ni base de datos (`CommerceServiceTest`, `OperationsAndLogisticsTest`) y el motor de riesgo es el mismo para el laboratorio y la tienda.
