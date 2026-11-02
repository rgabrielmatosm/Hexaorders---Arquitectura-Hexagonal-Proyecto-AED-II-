package com.hexaorders.application.port.out;

import com.hexaorders.domain.monitoring.AlertRule;
import com.hexaorders.domain.monitoring.Indicator;
import com.hexaorders.domain.monitoring.ServiceTarget;
import java.util.List;

/**
 * Read-only access to the monitoring system. The core does not know it is Prometheus or PromQL:
 * the adapter decides how each indicator is computed. Every method throws
 * MonitoringUnavailableException when the system does not answer.
 */
public interface MonitoringPort {
    List<ServiceTarget> targets();
    List<AlertRule> alerts();
    List<Indicator> indicators();
}
