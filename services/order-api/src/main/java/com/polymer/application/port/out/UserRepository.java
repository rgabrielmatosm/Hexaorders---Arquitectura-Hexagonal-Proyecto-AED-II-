package com.polymer.application.port.out;

import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository {
    Optional<UserAccount> findById(long id);
    Optional<StoredCredentials> credentials(String username);
    Optional<StoredCredentials> credentials(long id);
    /** v7: cuenta por correo (sin distinguir mayúsculas), para vincular el inicio de sesión con Google. */
    Optional<UserAccount> findByEmail(String email);
    boolean usernameExists(String username);
    boolean emailExists(String email, Long excludingId);
    UserAccount create(String username, String displayName, String email, String passwordHash, Set<Role> roles);
    List<UserAccount> list();
    List<UserAccount> withRole(Role role);
    Optional<UserAccount> updateRoles(long id, Set<Role> roles);
    Optional<UserAccount> setEnabled(long id, boolean enabled);
    Optional<UserAccount> updateProfile(long id, String displayName, String email);
    void updatePasswordHash(long id, String passwordHash);
}
