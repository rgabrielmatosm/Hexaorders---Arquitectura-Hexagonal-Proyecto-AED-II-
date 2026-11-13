package com.polymer.application.port.in;

import com.polymer.domain.identity.UserAccount;

public interface AccountUseCases {
    UserAccount register(String username, String displayName, String email, String password);
    UserAccount me(AuthenticatedUser user);
    UserAccount updateProfile(AuthenticatedUser user, String displayName, String email);
    void changePassword(AuthenticatedUser user, String currentPassword, String newPassword);
    /** Idioma preferido (es, en, fr, it, pt, de): el de sus correos de notificación. */
    void setLanguage(AuthenticatedUser user, String language);
    /** Creates the demo accounts that do not exist yet; never modifies existing users. Returns how many were created. */
    int ensureDemoUsers(String password);
    /** Audit trail of login attempts (never stores the password). */
    void recordAuthentication(String username, boolean success);
}
