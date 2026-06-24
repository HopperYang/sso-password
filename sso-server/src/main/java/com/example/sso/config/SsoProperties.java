package com.example.sso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "sso")
public record SsoProperties(
        Jwt jwt,
        String apiKey,
        Azure azure,
        PasswordSessionSettings passwordSession
) {
    public record Jwt(String secret, String issuer, long accessTokenTtlSeconds) {}

    public record Azure(
            boolean enabled,
            String authorityHost,
            String tenantId,
            String clientId,
            String clientSecret,
            String redirectUri,
            String webSuccessRedirectUri,
            String postLogoutRedirectUri,
            List<String> scopes,
            List<String> requiredGroups,
            long stateTtlSeconds,
            long sessionTtlSeconds,
            boolean secureCookies
    ) {
        public Azure {
            authorityHost = blankToDefault(authorityHost, "https://login.microsoftonline.com");
            scopes = normalize(scopes);
            if (scopes.isEmpty()) {
                scopes = List.of("openid", "profile", "email");
            }
            requiredGroups = normalize(requiredGroups);
            stateTtlSeconds = stateTtlSeconds > 0 ? stateTtlSeconds : 300;
            sessionTtlSeconds = sessionTtlSeconds > 0 ? sessionTtlSeconds : 3600;
        }

        public String issuer() {
            return authorityBase() + "/v2.0";
        }

        public String authorizeEndpoint() {
            return authorityBase() + "/oauth2/v2.0/authorize";
        }

        public String tokenEndpoint() {
            return authorityBase() + "/oauth2/v2.0/token";
        }

        public String logoutEndpoint() {
            return authorityBase() + "/oauth2/v2.0/logout";
        }

        private String authorityBase() {
            return authorityHost.replaceAll("/+$", "") + "/" + tenantId;
        }

        private static List<String> normalize(List<String> values) {
            if (values == null) {
                return List.of();
            }
            return values.stream()
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .toList();
        }

        private static String blankToDefault(String value, String defaultValue) {
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }

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
