package com.hexaorders.adapter.in.web.security;

import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.domain.identity.Role;
import java.io.Serial;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security principal stored in the HTTP session. Converts to the core's AuthenticatedUser. */
public final class HexaUserPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final Set<Role> roles;
    private final boolean enabled;

    public HexaUserPrincipal(long userId, String username, String displayName, String passwordHash, Set<Role> roles, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        this.enabled = enabled;
    }

    public AuthenticatedUser toUser() { return new AuthenticatedUser(userId, username, roles); }

    public String displayName() { return displayName; }

    public Set<Role> roles() { return EnumSet.copyOf(roles.isEmpty() ? EnumSet.noneOf(Role.class) : roles); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isEnabled() { return enabled; }

    /** The password hash is not needed after authentication and is not kept in the session. */
    public HexaUserPrincipal withoutCredentials() {
        return new HexaUserPrincipal(userId, username, displayName, null, roles, enabled);
    }

    public static List<String> roleNames(Collection<Role> roles) { return roles.stream().map(Enum::name).sorted().toList(); }
}
