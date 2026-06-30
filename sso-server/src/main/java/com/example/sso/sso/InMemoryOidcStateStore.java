package com.example.sso.sso;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryOidcStateStore {

    private final ConcurrentHashMap<String, OidcStateSession> sessions = new ConcurrentHashMap<>();

    public void save(OidcStateSession session) {
        sessions.put(session.state(), session);
    }

    public Optional<OidcStateSession> consume(String state) {
        OidcStateSession session = sessions.remove(state);
        if (session == null || session.expired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(session);
    }
}
