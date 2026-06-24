package com.example.sso.sso;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySsoSessionStore {

    private final ConcurrentHashMap<String, SsoSession> sessions = new ConcurrentHashMap<>();

    public void save(SsoSession session) {
        sessions.put(session.sessionId(), session);
    }

    public Optional<SsoSession> find(String sessionId) {
        SsoSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expired(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void delete(String sessionId) {
        sessions.remove(sessionId);
    }
}
