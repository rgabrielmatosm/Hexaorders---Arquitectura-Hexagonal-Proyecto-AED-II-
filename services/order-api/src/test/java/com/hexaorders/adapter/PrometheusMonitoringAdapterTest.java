package com.hexaorders.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hexaorders.adapter.out.monitoring.PrometheusMonitoringAdapter;
import com.hexaorders.application.exception.MonitoringUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PrometheusMonitoringAdapterTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus:9090");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    private final PrometheusMonitoringAdapter adapter = new PrometheusMonitoringAdapter(builder.build());

    @Test
    void readsTargetsFromThePrometheusApi() {
        server.expect(requestTo("http://prometheus:9090/api/v1/targets?state=active")).andRespond(withSuccess("""
                {"status":"success","data":{"activeTargets":[
                  {"labels":{"job":"risk-engine","instance":"risk-engine:8000"},"health":"up","lastScrape":"2026-11-02T10:00:00Z","lastScrapeDuration":0.004,"lastError":""},
                  {"labels":{"job":"order-api","instance":"order-api:9090"},"health":"down","lastScrape":"2026-11-02T10:00:01Z","lastScrapeDuration":0.1,"lastError":"connection refused"}]}}
                """, MediaType.APPLICATION_JSON));
        var targets = adapter.targets();
        assertThat(targets).extracting(t -> t.job()).containsExactly("order-api", "risk-engine");
        assertThat(targets.get(0).lastError()).isEqualTo("connection refused");
        assertThat(targets.get(1).lastError()).isNull();
    }

    @Test
    void readsAlertRulesWithTheirStateAndSeverity() {
        server.expect(requestTo("http://prometheus:9090/api/v1/rules?type=alert")).andRespond(withSuccess("""
                {"status":"success","data":{"groups":[{"name":"hexaorders","rules":[
                  {"name":"HexaOrdersServiceDown","state":"firing","labels":{"severity":"critical"},"annotations":{"summary":"Servicio caído"},
                   "alerts":[{"activeAt":"2026-11-02T09:59:00Z"}]},
                  {"name":"RiskRequestsFailing","state":"inactive","labels":{"severity":"warning"},"annotations":{},"alerts":[]}]}]}}
                """, MediaType.APPLICATION_JSON));
        var alerts = adapter.alerts();
        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).state()).isEqualTo("firing");
        assertThat(alerts.get(0).activeSince()).hasToString("2026-11-02T09:59:00Z");
        assertThat(alerts.get(1).severity()).isEqualTo("warning");
    }

    @Test
    void indicatorsWithoutDataAreNullInsteadOfZero() {
        server.expect(ExpectedCount.manyTimes(), request -> { }).andRespond(withSuccess(
                "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}", MediaType.APPLICATION_JSON));
        var indicators = adapter.indicators();
        assertThat(indicators).isNotEmpty().allSatisfy(i -> assertThat(i.value()).isNull());
        assertThat(indicators).extracting(i -> i.key()).contains("orders_created", "latency_p95", "risk_evaluations");
    }

    @Test
    void anUnreachablePrometheusBecomesMonitoringUnavailable() {
        server.expect(requestTo("http://prometheus:9090/api/v1/targets?state=active")).andRespond(withServerError());
        assertThatThrownBy(adapter::targets).isInstanceOf(MonitoringUnavailableException.class);
    }
}
