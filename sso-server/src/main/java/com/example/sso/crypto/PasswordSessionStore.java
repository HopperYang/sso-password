package com.example.sso.crypto;

import java.security.KeyPair;
import java.util.Optional;

/** Password encryption session storage (in-memory or Redis, selected by config). */
public interface PasswordSessionStore {

    PasswordSession create(KeyPair serverKeyPair);

    /** Validates, atomically removes, and returns the session if still valid. */
    Optional<PasswordSession> consumeIfValid(String id);
}
