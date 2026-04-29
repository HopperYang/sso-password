package com.example.sso.web.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenIssueRequest(
        @NotBlank String employeeId,
        @NotBlank String displayName
) {}
