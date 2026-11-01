# syntax=docker/dockerfile:1.7
# Imagen de configuración: datasource, proveedor y dashboard dentro de la imagen para
# funcionar con read_only: true. El dashboard vive fuera de /var/lib/grafana (volumen
# de datos) para que una nueva versión de la imagen nunca quede tapada por el volumen.
FROM grafana/grafana:12.4.1
COPY provisioning/datasource.yml /etc/grafana/provisioning/datasources/prometheus.yml
COPY provisioning/dashboards.yml /etc/grafana/provisioning/dashboards/default.yml
COPY dashboards/hexaorders.json /etc/grafana/dashboards/hexaorders.json
# v3: dashboards añadidos (el original hexaorders.json se conserva sin cambios).
COPY dashboards/hexaorders-business.json /etc/grafana/dashboards/hexaorders-business.json
COPY dashboards/hexaorders-risk.json /etc/grafana/dashboards/hexaorders-risk.json
COPY dashboards/hexaorders-api.json /etc/grafana/dashboards/hexaorders-api.json
COPY dashboards/hexaorders-infrastructure.json /etc/grafana/dashboards/hexaorders-infrastructure.json
COPY dashboards/hexaorders-security.json /etc/grafana/dashboards/hexaorders-security.json
