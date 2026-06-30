package com.example.sso.sso;

import java.util.List;

public record SsoUser(
        String subject,
        String employeeId,
        String displayName,
        String email,
        List<String> groups
) {}
