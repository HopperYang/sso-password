package com.example.sso.crypto;

import java.security.KeyPair;
import java.time.Instant;

public record PasswordSession(String id, KeyPair serverKeyPair, Instant expiresAt) {

    public boolean expired() {
        return Instant.now().isAfter(expiresAt);
    }
}
