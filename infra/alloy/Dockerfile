# syntax=docker/dockerfile:1.7
# Grafana Alloy (perfil "full"): lee los logs de los contenedores de HexaOrders mediante la API de
# Docker (socket en solo lectura), elimina secretos y los envía a Loki. Docker Desktop para Windows
# expone /var/run/docker.sock a los contenedores Linux, así que funciona igual que en Linux.
FROM busybox:1.37.0-musl AS tools
# Directorio de datos con el propietario correcto: el volumen con nombre hereda estos permisos.
RUN mkdir -p /data-dir
FROM grafana/alloy:v1.21.3
COPY --from=tools /bin/busybox /usr/local/bin/busybox
COPY --from=tools --chown=0:0 /data-dir /var/lib/alloy
COPY config.alloy /etc/alloy/config.alloy
CMD ["run", "--server.http.listen-addr=0.0.0.0:12345", "--storage.path=/var/lib/alloy/data", "--disable-reporting", "/etc/alloy/config.alloy"]
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 CMD ["/usr/local/bin/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:12345/-/ready"]
