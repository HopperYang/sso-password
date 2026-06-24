package com.example.sso.web.dto;

import java.util.List;

public record SsoUserResponse(
        String subject,
        String employeeId,
        String displayName,
        String email,
        List<String> groups
) {}
