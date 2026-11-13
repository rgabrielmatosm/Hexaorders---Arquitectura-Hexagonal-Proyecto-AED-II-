package com.polymer.application.port.out;

import com.polymer.domain.identity.UserAccount;

public record StoredCredentials(UserAccount account, String passwordHash) {}
