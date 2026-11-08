"""Tracing adapter: disabled by default, propagates the W3C trace context from Java when enabled."""

from fastapi.testclient import TestClient
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from risk_engine.adapters.http import create_app
from risk_engine.adapters.tracing import instrument, tracing_endpoint
from risk_engine.application.score_risk import ScoreRisk


def test_tracing_is_off_unless_explicitly_enabled():
    assert tracing_endpoint({}) is None
    assert tracing_endpoint({"OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-collector:4318"}) is None
    assert tracing_endpoint({"OTEL_EXPORTER_OTLP_ENDPOINT": "http://otel-collector:4318/", "POLYMER_INTEGRATIONS": "on"}) == "http://otel-collector:4318"
    assert tracing_endpoint({"OTEL_EXPORTER_OTLP_ENDPOINT": "http://no-such-host.invalid:4318", "POLYMER_INTEGRATIONS": "auto"}) is None
    assert instrument(create_app(ScoreRisk()), environ={}) is False


def test_requests_continue_the_trace_started_by_java_and_carry_no_customer_data():
    exporter = InMemorySpanExporter()
    app = create_app(ScoreRisk())
    assert instrument(app, environ={"OTEL_SERVICE_NAME": "risk-engine"}, exporter=exporter) is True
    trace_id = "4bf92f3577b34da6a3ce929d0e0e4736"
    response = TestClient(app).post("/score", json={"customerId": "secret-customer", "amount": 1500},
                                    headers={"traceparent": f"00-{trace_id}-00f067aa0ba902b7-01"})
    assert response.status_code == 200
    spans = exporter.get_finished_spans()
    names = [span.name for span in spans]
    assert "ScoreRisk.execute" in names
    assert all(format(span.context.trace_id, "032x") == trace_id for span in spans)
    use_case = next(span for span in spans if span.name == "ScoreRisk.execute")
    assert use_case.attributes["risk.decision"] == "REVIEW" and use_case.attributes["risk.score"] == 75
    assert all("secret-customer" not in str(dict(span.attributes or {})) for span in spans)
    assert all(span.resource.attributes["service.name"] == "risk-engine" for span in spans)
