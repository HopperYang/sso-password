package com.example.sso.crypto;

import com.example.sso.config.SsoProperties;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPasswordSessionStore implements PasswordSessionStore {

    private final Map<String, PasswordSession> sessions = new ConcurrentHashMap<>();
    private final SsoProperties props;

    public InMemoryPasswordSessionStore(SsoProperties props) {
        this.props = props;
    }

    @Override
    public PasswordSession create(KeyPair serverKeyPair) {
        String id = UUID.randomUUID().toString();
        var session = new PasswordSession(
                id, serverKeyPair, Instant.now().plusSeconds(ttlSeconds()));
        sessions.put(id, session);
        return session;
    }

    @Override
    public Optional<PasswordSession> consumeIfValid(String id) {
        PasswordSession session = sessions.remove(id);
        if (session == null || session.expired()) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private long ttlSeconds() {
        return props.passwordSession().ttlMinutes() * 60L;
    }
}
