package com.polymer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.polymer.adapter.out.http.HttpRiskAssessmentAdapter;
import com.polymer.application.exception.RiskUnavailableException;
import com.polymer.domain.OrderDraft;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpRiskAssessmentAdapterTest {
    private MockRestServiceServer server;
    private HttpRiskAssessmentAdapter adapter;
    private final OrderDraft draft = new OrderDraft("cliente_1", new BigDecimal("125.50"), "USD");

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://risk-engine:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpRiskAssessmentAdapter(builder.build());
    }

    @Test
    void translatesPortRequestToPythonJsonAndUsesScore() {
        server.expect(requestTo("http://risk-engine:8000/score"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"customerId\":\"cliente_1\",\"amount\":125.50}"))
                .andRespond(withSuccess("{\"score\":70,\"decision\":\"APPROVED\"}", MediaType.APPLICATION_JSON));
        assertThat(adapter.assess(draft)).isEqualTo(70);
        server.verify();
    }

    @Test
    void mapsUpstreamErrorsToApplicationFailure() {
        server.expect(requestTo("http://risk-engine:8000/score")).andRespond(withServerError());
        assertThatThrownBy(() -> adapter.assess(draft)).isInstanceOf(RiskUnavailableException.class);
    }

    @Test
    void rejectsInvalidUpstreamContract() {
        server.expect(requestTo("http://risk-engine:8000/score"))
                .andRespond(withSuccess("{\"score\":101,\"decision\":\"REVIEW\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.assess(draft)).isInstanceOf(RiskUnavailableException.class);
    }
}
