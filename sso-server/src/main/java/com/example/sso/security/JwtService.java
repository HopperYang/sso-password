package com.example.sso.security;

import com.example.sso.config.SsoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private final SsoProperties props;
    private final SecretKey key;

    public JwtService(SsoProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String employeeId, String displayName) {
        return issueAccessToken(employeeId, displayName, List.of());
    }

    public String issueAccessToken(String employeeId, String displayName, Collection<String> groups) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.jwt().accessTokenTtlSeconds());
        return Jwts.builder()
                .issuer(props.jwt().issuer())
                .subject(employeeId)
                .claim("name", displayName)
                .claim("groups", List.copyOf(groups))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parseAndValidate(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(c);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> claimsToUser(Claims c) {
        Object groups = c.get("groups");
        return Map.of(
                "employeeId", c.getSubject(),
                "displayName", String.valueOf(c.get("name", String.class)),
                "groups", groups instanceof List<?> ? groups : List.of());
    }
}
