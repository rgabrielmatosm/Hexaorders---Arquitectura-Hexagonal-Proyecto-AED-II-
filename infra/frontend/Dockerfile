# syntax=docker/dockerfile:1.7
# Gateway web de POLYMER (ADR 0011): Nginx sirve los recursos estáticos públicos (css, js, img, media)
# y reenvía TODO lo demás (páginas HTML y /api) a order-api, que conserva Spring Security, la
# sesión, el CSRF y la protección de páginas por rol. Imagen sin privilegios (puerto 8080 interno).
FROM busybox:1.37.0-musl AS tools
FROM nginxinc/nginx-unprivileged:1.31.7-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY @repo/services/order-api/src/main/resources/static/css /usr/share/nginx/html/css
COPY @repo/services/order-api/src/main/resources/static/js /usr/share/nginx/html/js
COPY @repo/services/order-api/src/main/resources/static/img /usr/share/nginx/html/img
# v7: video de la portada (pieza original generada con tools/media/hero-scene.html) y su póster.
COPY media /usr/share/nginx/html/media
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/nginx-health"]
