"""Driven adapter for distributed tracing (OpenTelemetry → OTLP/HTTP → otel-collector → Tempo).

Only the composition root calls it. It is enabled when OTEL_EXPORTER_OTLP_ENDPOINT is set and
POLYMER_INTEGRATIONS is "on", or "auto" and the collector host resolves in Docker's DNS (full
profile). Otherwise the app runs exactly as before, without exporting anything.
"""

from __future__ import annotations

import os
import socket
from urllib.parse import urlparse

from fastapi import FastAPI


def tracing_endpoint(environ: dict[str, str] | None = None) -> str | None:
    env = os.environ if environ is None else environ
    endpoint = env.get("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    mode = env.get("POLYMER_INTEGRATIONS", "off").strip().lower()
    if not endpoint or mode not in ("on", "auto"):
        return None
    if mode == "auto":
        try:
            socket.getaddrinfo(urlparse(endpoint).hostname or "", None)
        except OSError:
            return None
    return endpoint.rstrip("/")


def instrument(app: FastAPI, environ: dict[str, str] | None = None, exporter=None) -> bool:
    """Instrument the FastAPI app. Health and metrics endpoints are not traced."""
    endpoint = tracing_endpoint(environ)
    if endpoint is None and exporter is None:
        return False
    from opentelemetry import trace
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor, SimpleSpanProcessor

    env = os.environ if environ is None else environ
    provider = TracerProvider(resource=Resource.create({"service.name": env.get("OTEL_SERVICE_NAME", "risk-engine")}))
    if exporter is not None:
        provider.add_span_processor(SimpleSpanProcessor(exporter))
    else:
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

        provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{endpoint}/v1/traces", timeout=5)))
    trace.set_tracer_provider(provider)
    FastAPIInstrumentor.instrument_app(app, tracer_provider=provider, excluded_urls="health/live,health/ready,metrics")
    return True
