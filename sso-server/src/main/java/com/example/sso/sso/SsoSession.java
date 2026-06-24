package com.example.sso.sso;

import java.time.Instant;

public record SsoSession(
        String sessionId,
        SsoUser user,
        String accessToken,
        Instant expiresAt
) {
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
