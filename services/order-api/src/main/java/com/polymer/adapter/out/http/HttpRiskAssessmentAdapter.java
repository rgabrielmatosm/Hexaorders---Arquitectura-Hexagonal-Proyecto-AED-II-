package com.polymer.adapter.out.http;

import com.polymer.application.exception.RiskUnavailableException;
import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.domain.OrderDraft;
import java.math.BigDecimal;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class HttpRiskAssessmentAdapter implements RiskAssessmentPort {
    private final RestClient client;

    public HttpRiskAssessmentAdapter(RestClient client) { this.client = client; }

    @Override
    public int assess(OrderDraft draft) {
        try {
            ScoreResponse response = client.post().uri("/score")
                    .body(new ScoreRequest(draft.customerId(), draft.amount()))
                    .retrieve().body(ScoreResponse.class);
            if (response == null || response.score() == null
                    || !("APPROVED".equals(response.decision()) || "REVIEW".equals(response.decision()))) {
                throw new RiskUnavailableException("Respuesta inválida del motor de riesgo");
            }
            int score = response.score().intValueExact();
            if (score < 0 || score > 100) {
                throw new RiskUnavailableException("Puntaje de riesgo fuera del rango permitido");
            }
            // The Java domain owns the decision, even if the upstream decision differs.
            return score;
        } catch (RestClientException | ArithmeticException error) {
            throw new RiskUnavailableException("No se pudo consultar el motor de riesgo", error);
        }
    }

    private record ScoreRequest(String customerId, BigDecimal amount) {}
    private record ScoreResponse(BigDecimal score, String decision) {}
}
