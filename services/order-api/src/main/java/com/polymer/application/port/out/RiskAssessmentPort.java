package com.polymer.application.port.out;

import com.polymer.domain.OrderDraft;

public interface RiskAssessmentPort {
    /** Returns 0..100 or throws RiskUnavailableException. */
    int assess(OrderDraft draft);
}
