package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.AccountUseCases;
import com.hexaorders.domain.identity.UserAccount;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public final class AccountController {
    private final AccountUseCases accounts;

    public AccountController(AccountUseCases accounts) { this.accounts = accounts; }

    @GetMapping("/me")
    public UserAccount me() { return accounts.me(CurrentUser.require()); }

    @PutMapping("/profile")
    public UserAccount profile(@RequestBody ProfileRequest request) {
        return accounts.updateProfile(CurrentUser.require(), request.displayName(), request.email());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@RequestBody PasswordRequest request) {
        accounts.changePassword(CurrentUser.require(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ProfileRequest(String displayName, String email) {}
    public record PasswordRequest(String currentPassword, String newPassword) {}
}
