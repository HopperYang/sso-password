package com.example.sso.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePasswordSessionRequest(
        @NotBlank String clientPublicKeySpki
) {}
