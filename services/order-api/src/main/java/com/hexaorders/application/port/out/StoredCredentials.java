package com.hexaorders.application.port.out;

import com.hexaorders.domain.identity.UserAccount;

public record StoredCredentials(UserAccount account, String passwordHash) {}
