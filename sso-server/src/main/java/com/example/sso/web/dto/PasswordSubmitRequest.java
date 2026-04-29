package com.example.sso.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordSubmitRequest(
        @NotBlank String sessionId,
        @NotBlank String encryptedKey,
        @NotBlank String iv,
        @NotBlank String ciphertext,
        @NotBlank String tag
) {}
