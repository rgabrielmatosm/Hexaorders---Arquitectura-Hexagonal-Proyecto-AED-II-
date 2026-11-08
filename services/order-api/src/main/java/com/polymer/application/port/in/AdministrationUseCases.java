package com.polymer.application.port.in;

import com.polymer.domain.audit.AuditEvent;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import java.util.List;
import java.util.Set;

public interface AdministrationUseCases {
    AdminDashboard dashboard(AuthenticatedUser admin);
    List<UserAccount> users(AuthenticatedUser admin);
    UserAccount updateRoles(AuthenticatedUser admin, long userId, Set<Role> roles);
    UserAccount setEnabled(AuthenticatedUser admin, long userId, boolean enabled);
    List<AuditEvent> auditTrail(AuthenticatedUser admin, int limit);
    SystemHealth health(AuthenticatedUser viewer);
}
