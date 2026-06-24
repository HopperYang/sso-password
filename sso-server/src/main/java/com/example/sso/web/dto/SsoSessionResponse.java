package com.example.sso.web.dto;

public record SsoSessionResponse(
        SsoUserResponse user,
        String accessToken,
        String tokenType,
        long expiresIn
) {}
