# syntax=docker/dockerfile:1.7
# OpenTelemetry Collector (perfil "full"): recibe OTLP de order-api y risk-engine, elimina datos
# sensibles de los spans y exporta las trazas a Tempo; además convierte el estado de Nginx en métricas.
FROM busybox:1.37.0-musl AS tools
FROM otel/opentelemetry-collector-contrib:0.164.0
COPY --from=tools /bin/busybox /busybox
COPY config.yaml /etc/otelcol-contrib/config.yaml
HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=10 CMD ["/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:13133/"]
