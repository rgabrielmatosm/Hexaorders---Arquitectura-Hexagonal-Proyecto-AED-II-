package com.hexaorders.application.port.out;

import com.hexaorders.domain.audit.AuditEvent;
import java.util.List;

public interface AuditLog {
    void record(String actor, String action, String target, String details);
    List<AuditEvent> recent(int limit);
}
