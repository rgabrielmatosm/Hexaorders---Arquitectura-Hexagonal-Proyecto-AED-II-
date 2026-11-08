package com.polymer.adapter.out.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.polymer.application.exception.MonitoringUnavailableException;
import com.polymer.application.port.out.MonitoringPort;
import com.polymer.domain.monitoring.AlertRule;
import com.polymer.domain.monitoring.Indicator;
import com.polymer.domain.monitoring.ServiceTarget;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Prometheus HTTP API adapter (GET /api/v1/targets, /api/v1/rules, /api/v1/query). Runs inside
 * the Docker network (http://prometheus:9090), so the browser never needs CORS or a direct link.
 * Queries are fixed here: callers cannot send arbitrary PromQL.
 */
public final class PrometheusMonitoringAdapter implements MonitoringPort {
    private record Query(String label, String unit, String promql) {}

    private static final Map<String, Query> INDICATORS = new LinkedHashMap<>();

    static {
        INDICATORS.put("requests_per_second", new Query("Peticiones HTTP / s (order-api)", "req/s",
                "sum(rate(http_server_requests_seconds_count{job=\"order-api\"}[5m]))"));
        INDICATORS.put("error_ratio", new Query("Proporción de respuestas 5xx", "%",
                "100 * sum(rate(http_server_requests_seconds_count{job=\"order-api\",status=~\"5..\"}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count{job=\"order-api\"}[5m])), 1e-9)"));
        INDICATORS.put("latency_p95", new Query("Latencia p95 de la API", "s",
                "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job=\"order-api\",uri=~\"/api/.*\"}[5m])))"));
        INDICATORS.put("risk_evaluations", new Query("Evaluaciones de riesgo / min", "/min",
                "60 * sum(rate(risk_evaluations_total[5m]))"));
        INDICATORS.put("risk_latency_p95", new Query("Latencia p95 del motor de riesgo", "s",
                "histogram_quantile(0.95, sum by (le) (rate(risk_evaluation_duration_seconds_bucket[5m])))"));
        INDICATORS.put("orders_created", new Query("Pedidos creados desde el arranque", "",
                "sum(orders_created_total)"));
        INDICATORS.put("risk_failures", new Query("Fallos de riesgo desde el arranque", "",
                "sum(orders_risk_failures_total)"));
        INDICATORS.put("pending_review", new Query("Pedidos en revisión de riesgo", "",
                "sum(polymer_orders_by_status{status=\"RISK_REVIEW\"})"));
        INDICATORS.put("checkout_failures", new Query("Checkouts rechazados (1 h)", "",
                "sum(increase(polymer_checkout_failures_total[1h]))"));
        INDICATORS.put("incidents_open", new Query("Incidencias abiertas", "", "max(polymer_incidents_open)"));
        INDICATORS.put("low_stock", new Query("Productos con stock bajo", "", "max(polymer_products_low_stock)"));
        INDICATORS.put("failed_logins", new Query("Inicios de sesión fallidos (1 h)", "",
                "sum(increase(polymer_auth_logins_total{outcome=\"failure\"}[1h]))"));
        INDICATORS.put("jvm_heap", new Query("Memoria heap JVM usada", "MB",
                "sum(jvm_memory_used_bytes{job=\"order-api\",area=\"heap\"}) / 1048576"));
        INDICATORS.put("db_connections", new Query("Conexiones PostgreSQL activas", "", "sum(hikaricp_connections_active)"));
    }

    private final RestClient client;

    public PrometheusMonitoringAdapter(RestClient client) { this.client = client; }

    @Override
    public List<ServiceTarget> targets() {
        JsonNode active = get("/api/v1/targets?state=active").path("data").path("activeTargets");
        List<ServiceTarget> result = new ArrayList<>();
        for (JsonNode t : active) {
            result.add(new ServiceTarget(t.path("labels").path("job").asText(), t.path("labels").path("instance").asText(),
                    t.path("health").asText("unknown"), instant(t.path("lastScrape").asText(null)),
                    t.path("lastScrapeDuration").asDouble(0), blankToNull(t.path("lastError").asText(""))));
        }
        result.sort((a, b) -> a.job().compareTo(b.job()));
        return result;
    }

    @Override
    public List<AlertRule> alerts() {
        JsonNode groups = get("/api/v1/rules?type=alert").path("data").path("groups");
        List<AlertRule> result = new ArrayList<>();
        for (JsonNode group : groups) {
            for (JsonNode rule : group.path("rules")) {
                Instant since = null;
                for (JsonNode alert : rule.path("alerts")) {
                    Instant at = instant(alert.path("activeAt").asText(null));
                    if (at != null && (since == null || at.isBefore(since))) since = at;
                }
                result.add(new AlertRule(group.path("name").asText(), rule.path("name").asText(), rule.path("state").asText("inactive"),
                        rule.path("labels").path("severity").asText("none"), rule.path("annotations").path("summary").asText(""), since));
            }
        }
        return result;
    }

    @Override
    public List<Indicator> indicators() {
        List<Indicator> result = new ArrayList<>();
        for (Map.Entry<String, Query> entry : INDICATORS.entrySet()) {
            Query q = entry.getValue();
            JsonNode series = client.get().uri(uri -> uri.path("/api/v1/query").queryParam("query", "{query}").build(q.promql()))
                    .retrieve().body(JsonNode.class);
            result.add(new Indicator(entry.getKey(), q.label(), q.unit(), firstValue(series)));
        }
        return result;
    }

    private JsonNode get(String path) {
        try {
            JsonNode body = client.get().uri(path).retrieve().body(JsonNode.class);
            if (body == null || !"success".equals(body.path("status").asText())) {
                throw new MonitoringUnavailableException("Respuesta inesperada de Prometheus", null);
            }
            return body;
        } catch (RestClientException error) {
            throw new MonitoringUnavailableException("Prometheus no responde", error);
        }
    }

    private static Double firstValue(JsonNode body) {
        if (body == null) return null;
        JsonNode result = body.path("data").path("result");
        if (!result.isArray() || result.isEmpty()) return null;
        String raw = result.get(0).path("value").path(1).asText(null);
        if (raw == null) return null;
        double value = Double.parseDouble(raw);
        return Double.isFinite(value) ? value : null;
    }

    private static Instant instant(String text) {
        if (text == null || text.isBlank() || text.startsWith("0001-")) return null;
        try { return Instant.parse(text); } catch (RuntimeException ignored) { return null; }
    }

    private static String blankToNull(String text) { return text == null || text.isBlank() ? null : text; }
}
