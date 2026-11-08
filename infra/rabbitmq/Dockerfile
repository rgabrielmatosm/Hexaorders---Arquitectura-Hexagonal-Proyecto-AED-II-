# syntax=docker/dockerfile:1.7
# RabbitMQ con gestión (UI en 127.0.0.1:15672) y métricas Prometheus (:15692, red interna).
# La contraseña llega como secreto de archivo; el envoltorio la escribe en un tmpfs al arrancar.
FROM rabbitmq:4.1.8-management-alpine
COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf
COPY enabled_plugins /etc/rabbitmq/enabled_plugins
COPY polymer-entrypoint.sh /usr/local/bin/polymer-entrypoint.sh
RUN chmod 0755 /usr/local/bin/polymer-entrypoint.sh
ENTRYPOINT ["/usr/local/bin/polymer-entrypoint.sh"]
CMD ["rabbitmq-server"]
HEALTHCHECK --interval=10s --timeout=10s --start-period=40s --retries=10 CMD ["rabbitmq-diagnostics", "-q", "ping"]
