package com.example.sso.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sessionRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionReturnsOidcUser() throws Exception {
        mockMvc.perform(get("/api/auth/session")
                        .with(oidcLogin().idToken(token -> token
                                .claim("employeeId", "E0001")
                                .claim("name", "Ada Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("E0001"))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    }

    @Test
    void meCanReturnSessionUser() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin().idToken(token -> token
                                .claim("preferred_username", "ada@example.com")
                                .claim("name", "Ada Lovelace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value("ada@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    }
}
