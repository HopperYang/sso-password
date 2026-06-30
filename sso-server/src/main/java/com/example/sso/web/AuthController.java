package com.example.sso.web;

import com.example.sso.config.SsoProperties;
import com.example.sso.security.JwtService;
import com.example.sso.web.dto.TokenIssueRequest;
import com.example.sso.web.dto.TokenIssueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = auth.substring(7).trim();
        return jwt.parseAndValidate(token)
                .map(jwt::claimsToUser)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
