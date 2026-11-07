# syntax=docker/dockerfile:1.7
# Loki (perfil "full"): logs centralizados de los contenedores, recibidos de Grafana Alloy.
FROM busybox:1.37.0-musl AS tools
# Directorio de datos con el propietario correcto: el volumen con nombre hereda estos permisos.
RUN mkdir -p /data-dir
FROM grafana/loki:3.7.9
COPY --from=tools /bin/busybox /usr/local/bin/busybox
COPY --from=tools --chown=10001:10001 /data-dir /loki
COPY loki.yaml /etc/loki/hexaorders.yaml
CMD ["-config.file=/etc/loki/hexaorders.yaml"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=10 CMD ["/usr/local/bin/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:3100/ready"]
