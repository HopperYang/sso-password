package com.example.sso.web.dto;

public record TokenIssueResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
