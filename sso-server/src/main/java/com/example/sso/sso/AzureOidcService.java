package com.example.sso.sso;

import com.example.sso.config.SsoProperties;
import com.example.sso.security.JwtService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class AzureOidcService {

    private final SsoProperties props;
    private final JwtService jwtService;
    private final InMemoryOidcStateStore stateStore;
    private final InMemorySsoSessionStore sessionStore;
    private final RestClient restClient;
    private final SecureRandom random = new SecureRandom();

    private volatile JwtDecoder jwtDecoder;

    public AzureOidcService(
            SsoProperties props,
            JwtService jwtService,
            InMemoryOidcStateStore stateStore,
            InMemorySsoSessionStore sessionStore,
            RestClient.Builder restClientBuilder) {
        this.props = props;
        this.jwtService = jwtService;
        this.stateStore = stateStore;
        this.sessionStore = sessionStore;
        this.restClient = restClientBuilder.build();
    }

    public OidcLogin createLogin() {
        SsoProperties.Azure azure = enabledAzure();
        String state = randomToken();
        String nonce = randomToken();
        stateStore.save(new OidcStateSession(
                state,
                nonce,
                Instant.now().plusSeconds(azure.stateTtlSeconds())));

        String loginUrl = UriComponentsBuilder.fromUriString(azure.authorizeEndpoint())
                .queryParam("client_id", azure.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", azure.redirectUri())
                .queryParam("response_mode", "query")
                .queryParam("scope", String.join(" ", azure.scopes()))
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .build()
                .encode()
                .toUriString();
        return new OidcLogin(loginUrl, state);
    }

    public SsoSession completeLogin(String code, String state, String expectedState) {
        SsoProperties.Azure azure = enabledAzure();
        if (state == null || state.isBlank() || !state.equals(expectedState)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid oidc state");
        }

        OidcStateSession stateSession = stateStore.consume(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "expired oidc state"));
        TokenResponse token = exchangeCode(azure, code);
        Jwt idToken = decodeIdToken(token.idToken());
        validateIdToken(idToken, azure, stateSession.nonce());

        SsoUser user = userFrom(idToken);
        authorize(user, azure.requiredGroups());
        String accessToken = jwtService.issueAccessToken(user.employeeId(), user.displayName(), user.groups());
        SsoSession session = new SsoSession(
                randomToken(),
                user,
                accessToken,
                Instant.now().plusSeconds(azure.sessionTtlSeconds()));
        sessionStore.save(session);
        return session;
    }

    public String logoutUrl() {
        SsoProperties.Azure azure = enabledAzure();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(azure.logoutEndpoint());
        if (azure.postLogoutRedirectUri() != null && !azure.postLogoutRedirectUri().isBlank()) {
            builder.queryParam("post_logout_redirect_uri", azure.postLogoutRedirectUri());
        }
        return builder.build().encode().toUriString();
    }

    private TokenResponse exchangeCode(SsoProperties.Azure azure, String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing authorization code");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", azure.clientId());
        form.add("client_secret", azure.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", azure.redirectUri());
        form.add("scope", String.join(" ", azure.scopes()));

        TokenResponse token = restClient.post()
                .uri(azure.tokenEndpoint())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.idToken() == null || token.idToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "azure did not return an id_token");
        }
        return token;
    }

    private Jwt decodeIdToken(String idToken) {
        try {
            return decoder().decode(idToken);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid azure id_token", e);
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder current = jwtDecoder;
        if (current == null) {
            synchronized (this) {
                current = jwtDecoder;
                if (current == null) {
                    current = JwtDecoders.fromIssuerLocation(enabledAzure().issuer());
                    jwtDecoder = current;
                }
            }
        }
        return current;
    }

    private void validateIdToken(Jwt idToken, SsoProperties.Azure azure, String expectedNonce) {
        if (!idToken.getAudience().contains(azure.clientId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "id_token audience mismatch");
        }
        String nonce = idToken.getClaimAsString("nonce");
        if (!expectedNonce.equals(nonce)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "id_token nonce mismatch");
        }
    }

    private SsoUser userFrom(Jwt idToken) {
        List<String> groups = groupsFrom(idToken);
        String employeeId = firstClaim(idToken, "preferred_username", "upn", "email", "oid", "sub");
        String displayName = firstClaim(idToken, "name", "preferred_username", "sub");
        String email = firstClaim(idToken, "email", "preferred_username", "upn");
        return new SsoUser(idToken.getSubject(), employeeId, displayName, email, groups);
    }

    private List<String> groupsFrom(Jwt idToken) {
        Object rawGroups = idToken.getClaims().get("groups");
        if (rawGroups instanceof Collection<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .filter(v -> !v.isBlank())
                    .distinct()
                    .toList();
        }

        Map<String, Object> claims = idToken.getClaims();
        Object claimNames = claims.get("_claim_names");
        boolean overage = Boolean.TRUE.equals(claims.get("hasgroups"))
                || claimNames instanceof Map<?, ?> map && map.containsKey("groups");
        if (overage) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "azure group overage: configure optional group claims or add Microsoft Graph lookup");
        }
        return List.of();
    }

    private void authorize(SsoUser user, List<String> requiredGroups) {
        if (requiredGroups.isEmpty()) {
            return;
        }
        List<String> matched = new ArrayList<>(user.groups());
        matched.retainAll(requiredGroups);
        if (matched.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "user is not in a required Azure AD group");
        }
    }

    private String firstClaim(Jwt token, String... names) {
        for (String name : names) {
            String value = token.getClaimAsString(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "id_token is missing user identity claims");
    }

    private SsoProperties.Azure enabledAzure() {
        SsoProperties.Azure azure = props.azure();
        if (azure == null || !azure.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "azure sso is disabled");
        }
        if (blank(azure.tenantId()) || blank(azure.clientId()) || blank(azure.clientSecret()) || blank(azure.redirectUri())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "azure sso is not configured");
        }
        return azure;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record OidcLogin(String loginUrl, String state) {}

    private record TokenResponse(
            @JsonProperty("id_token") String idToken,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
