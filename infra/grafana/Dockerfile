# syntax=docker/dockerfile:1.7
# Imagen de configuración: datasource, proveedor y dashboard dentro de la imagen para
# funcionar con read_only: true. El dashboard vive fuera de /var/lib/grafana (volumen
# de datos) para que una nueva versión de la imagen nunca quede tapada por el volumen.
FROM grafana/grafana:12.4.1
COPY provisioning/datasource.yml /etc/grafana/provisioning/datasources/prometheus.yml
COPY provisioning/dashboards.yml /etc/grafana/provisioning/dashboards/default.yml
COPY dashboards/polymer.json /etc/grafana/dashboards/polymer.json
# v3: dashboards añadidos (el original polymer.json se conserva sin cambios).
COPY dashboards/polymer-business.json /etc/grafana/dashboards/polymer-business.json
COPY dashboards/polymer-risk.json /etc/grafana/dashboards/polymer-risk.json
COPY dashboards/polymer-api.json /etc/grafana/dashboards/polymer-api.json
COPY dashboards/polymer-infrastructure.json /etc/grafana/dashboards/polymer-infrastructure.json
COPY dashboards/polymer-security.json /etc/grafana/dashboards/polymer-security.json
COPY dashboards/polymer-logistics.json /etc/grafana/dashboards/polymer-logistics.json
# v5: fuentes Loki y Tempo (archivo nuevo; datasource.yml no cambia) y dashboard de integraciones.
COPY provisioning/datasource-logs-traces.yml /etc/grafana/provisioning/datasources/logs-traces.yml
COPY dashboards/polymer-integrations.json /etc/grafana/dashboards/polymer-integrations.json
