package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.security.JwtService;
import com.example.sso.web.dto.TokenIssueRequest;
import com.example.sso.web.dto.TokenIssueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwt;
    private final SsoProperties props;

    public AuthController(JwtService jwt, SsoProperties props) {
        this.jwt = jwt;
        this.props = props;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenIssueResponse> issue(
            @RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody TokenIssueRequest body) {
        if (!props.apiKey().equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String access = jwt.issueAccessToken(body.employeeId(), body.displayName());
        return ResponseEntity.ok(new TokenIssueResponse(
                access,
                "Bearer",
                props.jwt().accessTokenTtlSeconds()));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            Authentication authentication) {
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return currentSessionUser(authentication)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String token = auth.substring(7).trim();
        return jwt.parseAndValidate(token)
                .map(jwt::claimsToUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, String>> session(Authentication authentication) {
        return currentSessionUser(authentication)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private Optional<Map<String, String>> currentSessionUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser user) {
            return Optional.of(userFromAttributes(user.getClaims(), user.getSubject()));
        }
        if (principal instanceof OAuth2User user) {
            return Optional.of(userFromAttributes(user.getAttributes(), authentication.getName()));
        }

        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Map.of("employeeId", name, "displayName", name));
    }

    private Map<String, String> userFromAttributes(Map<String, Object> attributes, String fallbackSubject) {
        String employeeId = firstText(attributes, fallbackSubject,
                "employeeId", "employee_id", "upn", "preferred_username", "email", "oid", "sub");
        String displayName = firstText(attributes, employeeId,
                "name", "displayName", "display_name", "preferred_username", "email");

        var user = new HashMap<String, String>();
        user.put("employeeId", employeeId);
        user.put("displayName", displayName);
        return user;
    }

    private String firstText(Map<String, Object> attributes, String fallback, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return fallback == null || fallback.isBlank() ? "unknown" : fallback;
    }
}
