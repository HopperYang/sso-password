package com.example.sso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sso")
public record SsoProperties(
        Jwt jwt,
        String apiKey,
        PasswordSessionSettings passwordSession
) {
    public record Jwt(String secret, String issuer, long accessTokenTtlSeconds) {}

    public record PasswordSessionSettings(
            int ttlMinutes,
            boolean redisEnabled,
            String redisKeyPrefix
    ) {
        public PasswordSessionSettings {
            if (redisKeyPrefix == null || redisKeyPrefix.isBlank()) {
                redisKeyPrefix = "sso:pwd-session:";
            }
        }
    }
}
