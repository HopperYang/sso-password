package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.sso.AzureOidcService;
import com.example.sso.sso.InMemorySsoSessionStore;
import com.example.sso.sso.SsoSession;
import com.example.sso.sso.SsoUser;
import com.example.sso.web.dto.SsoLoginUrlResponse;
import com.example.sso.web.dto.SsoLogoutResponse;
import com.example.sso.web.dto.SsoSessionResponse;
import com.example.sso.web.dto.SsoUserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private static final String STATE_COOKIE = "SSO_OIDC_STATE";
    private static final String SESSION_COOKIE = "SSO_SESSION";

    private final SsoProperties props;
    private final AzureOidcService azureOidc;
    private final InMemorySsoSessionStore sessionStore;

    public SsoController(SsoProperties props, AzureOidcService azureOidc, InMemorySsoSessionStore sessionStore) {
        this.props = props;
        this.azureOidc = azureOidc;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/login-url")
    public ResponseEntity<SsoLoginUrlResponse> loginUrl() {
        AzureOidcService.OidcLogin login = azureOidc.createLogin();
        ResponseCookie stateCookie = cookie(STATE_COOKIE, login.state(), Duration.ofSeconds(props.azure().stateTtlSeconds()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .body(new SsoLoginUrlResponse(login.loginUrl()));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            @CookieValue(name = STATE_COOKIE, required = false) String expectedState) {
        if (error != null && !error.isBlank()) {
            return redirect(frontendError(error, errorDescription));
        }

        try {
            SsoSession session = azureOidc.completeLogin(code, state, expectedState);
            ResponseCookie sessionCookie = cookie(
                    SESSION_COOKIE,
                    session.sessionId(),
                    Duration.between(Instant.now(), session.expiresAt()));
            ResponseCookie clearState = clearCookie(STATE_COOKIE);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(frontendSuccess(session))
                    .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, clearState.toString())
                    .build();
        } catch (ResponseStatusException e) {
            return redirect(frontendError("sso_failed", e.getReason()));
        }
    }

    @GetMapping("/session/me")
    public ResponseEntity<SsoSessionResponse> me(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return sessionStore.find(sessionId)
                .map(session -> ResponseEntity.ok(toResponse(session)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<SsoLogoutResponse> logout(
            @CookieValue(name = SESSION_COOKIE, required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionStore.delete(sessionId);
        }
        ResponseCookie clearSession = clearCookie(SESSION_COOKIE);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearSession.toString())
                .body(new SsoLogoutResponse(azureOidc.logoutUrl()));
    }

    private SsoSessionResponse toResponse(SsoSession session) {
        SsoUser user = session.user();
        long expiresIn = Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toSeconds());
        return new SsoSessionResponse(
                new SsoUserResponse(user.subject(), user.employeeId(), user.displayName(), user.email(), user.groups()),
                session.accessToken(),
                "Bearer",
                expiresIn);
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.azure().secureCookies())
                .sameSite("Lax")
                .path("/api/sso")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearCookie(String name) {
        return cookie(name, "", Duration.ZERO);
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private URI frontendSuccess(SsoSession session) {
        String fragment = "access_token=" + encode(session.accessToken())
                + "&token_type=Bearer"
                + "&expires_in=" + props.jwt().accessTokenTtlSeconds();
        return URI.create(props.azure().webSuccessRedirectUri() + "#" + fragment);
    }

    private URI frontendError(String error, String description) {
        return UriComponentsBuilder.fromUriString(props.azure().webSuccessRedirectUri())
                .queryParam("error", error)
                .queryParamIfPresent("error_description", java.util.Optional.ofNullable(description))
                .build()
                .encode()
                .toUri();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
