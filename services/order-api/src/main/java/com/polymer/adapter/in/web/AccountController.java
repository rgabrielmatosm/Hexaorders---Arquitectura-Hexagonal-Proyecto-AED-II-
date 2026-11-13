package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.domain.identity.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public final class AccountController {
    private final AccountUseCases accounts;
    private final SignInUseCases signIn;

    public AccountController(AccountUseCases accounts, SignInUseCases signIn) {
        this.accounts = accounts;
        this.signIn = signIn;
    }

    /** v7: correo verificado, verificación en dos pasos y cuenta de Google vinculada. */
    @GetMapping("/security")
    public SignInUseCases.SecuritySettings security() { return signIn.securitySettings(CurrentUser.require()); }

    @PutMapping("/security")
    public SignInUseCases.SecuritySettings updateSecurity(@RequestBody SecurityRequest request) {
        signIn.setTwoFactor(CurrentUser.require(), Boolean.TRUE.equals(request.twoFactor()));
        return signIn.securitySettings(CurrentUser.require());
    }

    /** v7: cierra las sesiones recordadas y olvida los dispositivos de confianza (pedirá el código de nuevo). */
    @PostMapping("/security/forget-devices")
    public ResponseEntity<Void> forgetDevices() {
        signIn.forgetAllDevices(CurrentUser.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserAccount me() { return accounts.me(CurrentUser.require()); }

    @PutMapping("/profile")
    public UserAccount profile(@RequestBody ProfileRequest request) {
        return accounts.updateProfile(CurrentUser.require(), request.displayName(), request.email());
    }

    @PutMapping("/locale")
    public ResponseEntity<Void> locale(@RequestBody LanguageRequest request) {
        accounts.setLanguage(CurrentUser.require(), request.language());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@RequestBody PasswordRequest request) {
        accounts.changePassword(CurrentUser.require(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ProfileRequest(String displayName, String email) {}
    public record PasswordRequest(String currentPassword, String newPassword) {}
    public record LanguageRequest(String language) {}
    public record SecurityRequest(Boolean twoFactor) {}
}
