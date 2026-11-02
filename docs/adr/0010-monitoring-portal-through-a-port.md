# ADR 0010 — Portal de monitoreo a través de un puerto (MonitoringPort)

**Contexto.** El rol OBSERVABILITY_VIEWER necesita páginas web con la salud de los servicios, indicadores y alertas, sin sustituir a Grafana ni crear dashboards falsos.

**Decisión.** Un puerto de salida `MonitoringPort` en el núcleo y un adaptador `PrometheusMonitoringAdapter` que consulta la API HTTP de Prometheus **desde order-api, por la red interna de Docker** (`PROMETHEUS_URL=http://prometheus:9090`). El caso de uso `MonitoringService` exige el rol y degrada con elegancia (`available=false`) si Prometheus no responde. Las consultas PromQL son fijas y viven en el adaptador: el navegador no puede enviar consultas arbitrarias. Los valores sin datos se devuelven como `null` («sin datos»), nunca como 0.

**Alternativas.** Consultar Prometheus directamente desde el navegador (dependía del puerto publicado 9091 y de CORS; el laboratorio lo sigue haciendo y se conserva) o replicar paneles de Grafana en HTML (duplicaría la herramienta y podría divergir).

**Consecuencias.** Las páginas de monitoreo complementan a Grafana y enlazan a sus 7 dashboards. El adaptador se prueba con `MockRestServiceServer` (`PrometheusMonitoringAdapterTest`) y, contra un Prometheus real, en `pages-e2e.js` dentro del CI.
