package com.hexaorders.application.port.out;

import com.hexaorders.domain.OrderDraft;

public interface RiskAssessmentPort {
    /** Returns 0..100 or throws RiskUnavailableException. */
    int assess(OrderDraft draft);
}
