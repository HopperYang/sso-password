package com.example.sso.crypto;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordSessionStore {

    private final Map<String, PasswordSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public PasswordSessionStore(com.example.sso.config.SsoProperties props) {
        this.ttl = Duration.ofMinutes(props.passwordSessionTtlMinutes());
    }

    public PasswordSession create(KeyPair serverKeyPair) {
        String id = UUID.randomUUID().toString();
        var session = new PasswordSession(id, serverKeyPair, Instant.now().plus(ttl));
        sessions.put(id, session);
        return session;
    }

    public Optional<PasswordSession> consumeIfValid(String id) {
        PasswordSession s = sessions.get(id);
        if (s == null || s.expired()) {
            if (s != null) {
                sessions.remove(id);
            }
            return Optional.empty();
        }
        return Optional.of(s);
    }

    public void remove(String id) {
        sessions.remove(id);
    }
}
