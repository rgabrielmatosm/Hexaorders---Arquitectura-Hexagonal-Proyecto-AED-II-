# syntax=docker/dockerfile:1.7
# Tempo (perfil "full"): almacena las trazas que le envía el OpenTelemetry Collector.
FROM busybox:1.37.0-musl AS tools
# Directorio de datos con el propietario correcto: el volumen con nombre hereda estos permisos.
RUN mkdir -p /data-dir
FROM grafana/tempo:2.8.4
COPY --from=tools /bin/busybox /usr/local/bin/busybox
COPY --from=tools --chown=10001:10001 /data-dir /var/tempo
COPY tempo.yaml /etc/tempo/polymer.yaml
CMD ["-config.file=/etc/tempo/polymer.yaml"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 CMD ["/usr/local/bin/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:3200/ready"]
