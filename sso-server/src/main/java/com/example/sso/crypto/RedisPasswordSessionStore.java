package com.example.sso.crypto;

import com.example.sso.config.SsoProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RedisPasswordSessionStore implements PasswordSessionStore {

    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private final StringRedisTemplate redis;
    private final SsoProperties props;
    private final ObjectMapper json = new ObjectMapper();

    public RedisPasswordSessionStore(StringRedisTemplate redis, SsoProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public PasswordSession create(KeyPair serverKeyPair) {
        String id = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds());
        StoredSession payload;
        try {
            payload = new StoredSession(
                    Base64.getEncoder().encodeToString(serverKeyPair.getPrivate().getEncoded()),
                    Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()),
                    expiresAt.toEpochMilli());
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize password session key pair", e);
        }

        String key = redisKey(id);
        try {
            redis.opsForValue().set(key, json.writeValueAsString(payload), ttlSeconds(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to write password session to redis", e);
        }

        return new PasswordSession(id, serverKeyPair, expiresAt);
    }

    @Override
    public Optional<PasswordSession> consumeIfValid(String id) {
        String raw = redis.opsForValue().getAndDelete(redisKey(id));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            StoredSession stored = json.readValue(raw, StoredSession.class);
            Instant expiresAt = Instant.ofEpochMilli(stored.expiresAtEpochMilli());
            if (Instant.now().isAfter(expiresAt)) {
                return Optional.empty();
            }
            KeyPair keyPair = keyPairFromStored(stored);
            return Optional.of(new PasswordSession(id, keyPair, expiresAt));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private KeyPair keyPairFromStored(StoredSession stored) throws GeneralSecurityException {
        var privateKey = CryptoSupport.privateKeyFromPkcs8(B64_DEC.decode(stored.privatePkcs8B64()));
        var publicKey = CryptoSupport.publicKeyFromSpki(B64_DEC.decode(stored.publicKeySpkiB64()));
        return new KeyPair(publicKey, privateKey);
    }

    private String redisKey(String sessionId) {
        return props.passwordSession().redisKeyPrefix() + sessionId;
    }

    private long ttlSeconds() {
        return props.passwordSession().ttlMinutes() * 60L;
    }

    /** JSON payload stored in Redis (PKCS#8 + SPKI, Base64). */
    record StoredSession(String privatePkcs8B64, String publicKeySpkiB64, long expiresAtEpochMilli) {}
}
