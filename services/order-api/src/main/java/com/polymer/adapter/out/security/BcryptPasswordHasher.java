package com.polymer.adapter.out.security;

import com.polymer.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** BCrypt (cost 12): salted, adaptive hashing. Raw passwords are never stored or logged. */
public final class BcryptPasswordHasher implements PasswordHasher {
    private final BCryptPasswordEncoder encoder;

    public BcryptPasswordHasher(BCryptPasswordEncoder encoder) { this.encoder = encoder; }

    @Override
    public String hash(String rawPassword) { return encoder.encode(rawPassword); }

    @Override
    public boolean matches(String rawPassword, String hash) { return hash != null && encoder.matches(rawPassword, hash); }
}
