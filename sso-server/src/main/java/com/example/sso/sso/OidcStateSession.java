package com.example.sso.sso;

import java.time.Instant;

public record OidcStateSession(
        String state,
        String nonce,
        Instant expiresAt
) {
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
