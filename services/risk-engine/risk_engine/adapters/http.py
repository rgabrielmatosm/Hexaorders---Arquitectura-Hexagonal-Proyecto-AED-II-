"""Driving adapter: exact JSON decimals, validation and Prometheus exposition."""

from decimal import Decimal, InvalidOperation
import json
from time import perf_counter
from typing import Annotated, Literal

from fastapi import FastAPI, HTTPException, Request, Response
from opentelemetry import trace
from prometheus_client import CollectorRegistry, Counter, Histogram, CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from risk_engine.application.ports import ScoreRiskPort
from risk_engine.domain.risk import Evaluation


class ScoreRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    customerId: Annotated[str, Field(strict=True, min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$")]
    amount: Annotated[Decimal, Field(gt=0, le=1000000, allow_inf_nan=False)]

    @field_validator("amount", mode="before")
    @classmethod
    def exact_money(cls, value: object) -> Decimal:
        if isinstance(value, bool) or not isinstance(value, (str, int, float, Decimal)):
            raise ValueError("amount must be a decimal string or JSON number")
        try:
            result = Decimal(str(value))
        except InvalidOperation as exc:
            raise ValueError("amount must be a valid decimal") from exc
        if not result.is_finite() or result.as_tuple().exponent < -2:
            raise ValueError("amount must be finite with at most two decimal places")
        return result


class ScoreResponse(BaseModel):
    score: Annotated[int, Field(ge=0, le=100)]
    decision: Literal["APPROVED", "REVIEW"]


def _invalid_json_constant(value: str) -> None:
    raise ValueError(f"Invalid JSON constant: {value}")


def create_app(score_risk: ScoreRiskPort) -> FastAPI:
    app = FastAPI(title="POLYMER risk engine", version="1.0.0")
    # A private registry lets each test instantiate a clean application.
    registry = CollectorRegistry()
    evaluations = Counter(
        "risk_evaluations_total", "Successfully evaluated orders", ["decision"], registry=registry
    )
    duration = Histogram(
        "risk_evaluation_duration_seconds", "Risk use-case execution time", registry=registry
    )
    for decision in ("APPROVED", "REVIEW"):
        evaluations.labels(decision=decision)
    # v3: requests rejected before reaching the use case (low-cardinality reasons, pre-registered).
    rejected = Counter(
        "risk_requests_rejected_total", "Requests rejected by validation", ["reason"], registry=registry
    )
    for reason in ("unsupported_media_type", "invalid_payload"):
        rejected.labels(reason=reason)

    @app.post(
        "/score",
        response_model=ScoreResponse,
        openapi_extra={"requestBody": {"required": True, "content": {
            "application/json": {"schema": ScoreRequest.model_json_schema()}
        }}},
    )
    async def score(request: Request) -> ScoreResponse:
        if request.headers.get("content-type", "").split(";", 1)[0].strip().lower() != "application/json":
            rejected.labels(reason="unsupported_media_type").inc()
            raise HTTPException(status_code=415, detail="Content-Type must be application/json")
        try:
            # JSON -> Decimal directly: a float intermediate would lose monetary precision.
            payload = json.loads(await request.body(), parse_float=Decimal, parse_constant=_invalid_json_constant)
            dto = ScoreRequest.model_validate(payload)
            evaluation = Evaluation(dto.customerId, dto.amount)
        except (json.JSONDecodeError, UnicodeDecodeError, ValueError) as exc:
            rejected.labels(reason="invalid_payload").inc()
            detail = exc.errors(include_input=False, include_context=False) if isinstance(exc, ValidationError) else "Invalid JSON body"
            raise HTTPException(status_code=422, detail=detail) from exc
        started = perf_counter()
        # Span of the use case (no-op unless tracing is configured). No customer data in attributes.
        with trace.get_tracer("risk_engine.adapters.http").start_as_current_span("ScoreRisk.execute") as span:
            result = score_risk.execute(evaluation)
            span.set_attribute("risk.decision", result.decision.value)
            span.set_attribute("risk.score", result.score)
        duration.observe(perf_counter() - started)
        evaluations.labels(decision=result.decision.value).inc()
        return ScoreResponse(score=result.score, decision=result.decision.value)

    @app.get("/health/live", include_in_schema=False)
    def live() -> dict[str, str]:
        return {"status": "UP"}

    @app.get("/health/ready", include_in_schema=False)
    def ready() -> dict[str, str]:
        # No external dependencies: ready as soon as this application can serve HTTP.
        return {"status": "UP"}

    @app.get("/metrics", include_in_schema=False)
    def metrics() -> Response:
        return Response(content=generate_latest(registry), headers={"Content-Type": CONTENT_TYPE_LATEST})

    return app
