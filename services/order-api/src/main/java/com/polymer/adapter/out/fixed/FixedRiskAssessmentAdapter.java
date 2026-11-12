package com.polymer.adapter.out.fixed;

import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.domain.OrderDraft;

/**
 * Adaptador de SALIDA alternativo del puerto {@link RiskAssessmentPort}: devuelve siempre el mismo
 * puntaje, sin red ni motor de riesgo. Existe para demostrar la inversión de dependencias: los casos de
 * uso (OrderService, CommerceService) y el dominio siguen idénticos; solo cambia qué implementación del
 * puerto conecta la raíz de composición (HexagonalConfiguration).
 *
 * Solo se activa con la propiedad polymer.risk.adapter=fixed (variable POLYMER_RISK_ADAPTER), que nunca
 * está en docker-compose.yml: la pone el override de demostración infra/demo/compose.demo.yml. No hay
 * ningún endpoint para activarlo. Ver docs/final/DEMOSTRACION_HEXAGONAL.md.
 */
public final class FixedRiskAssessmentAdapter implements RiskAssessmentPort {
    private final int score;

    public FixedRiskAssessmentAdapter(int score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException("polymer.risk.fixed-score debe estar entre 0 y 100");
        this.score = score;
    }

    @Override
    public int assess(OrderDraft draft) {
        return score;
    }
}
