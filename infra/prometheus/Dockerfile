# syntax=docker/dockerfile:1.7
# Imagen de configuración: la configuración queda dentro de la imagen para que el
# contenedor funcione con read_only: true (Compose no puede inyectar configs.content
# en servicios de solo lectura). generate_delivery.py la incorpora inline.
FROM prom/prometheus:v3.5.1
COPY prometheus.yml /etc/prometheus/prometheus.yml
COPY alerts.yml /etc/prometheus/alerts.yml
