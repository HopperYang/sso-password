package com.example.sso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sso")
public record SsoProperties(
        Jwt jwt,
        String apiKey,
        int passwordSessionTtlMinutes
) {
    public record Jwt(String secret, String issuer, long accessTokenTtlSeconds) {}
}
