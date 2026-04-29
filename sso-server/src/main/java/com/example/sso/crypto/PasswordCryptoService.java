package com.example.sso.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

@Service
public class PasswordCryptoService {

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private final ObjectMapper json = new ObjectMapper();
    private final PasswordSessionStore sessions;

    public PasswordCryptoService(PasswordSessionStore sessions) {
        this.sessions = sessions;
    }

    public Map<String, String> createSessionEnvelope(byte[] clientPublicKeySpki)
            throws GeneralSecurityException, JsonProcessingException {
        PublicKey clientPub = CryptoSupport.publicKeyFromSpki(clientPublicKeySpki);
        KeyPair serverPair = CryptoSupport.generateRsa2048();
        PasswordSession session = sessions.create(serverPair);

        byte[] serverSpki = serverPair.getPublic().getEncoded();
        String inner = json.writeValueAsString(Map.of(
                "sessionId", session.id(),
                "publicKeySpki", B64.encodeToString(serverSpki),
                "alg", "RSA-OAEP-256"));

        byte[] aesKey = CryptoSupport.randomAesKey();
        byte[] iv = CryptoSupport.randomIv();
        var seal = CryptoSupport.aesGcmEncrypt(aesKey, iv, inner.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] encKey = CryptoSupport.rsaOaepEncrypt(clientPub, aesKey);

        return Map.of(
                "sessionId", session.id(),
                "encryptedKey", B64.encodeToString(encKey),
                "iv", B64.encodeToString(iv),
                "ciphertext", B64.encodeToString(seal.ciphertext()),
                "tag", B64.encodeToString(seal.tag()));
    }

    public void decryptAndValidateSubmit(
            String sessionId,
            byte[] encryptedKey,
            byte[] iv,
            byte[] ciphertext,
            byte[] tag) throws GeneralSecurityException {
        PasswordSession session = sessions.consumeIfValid(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("invalid or expired session"));

        try {
            byte[] aesKey = CryptoSupport.rsaOaepDecrypt(session.serverKeyPair().getPrivate(), encryptedKey);
            byte[] passwordBytes = CryptoSupport.aesGcmDecrypt(aesKey, iv, ciphertext, tag);
            java.util.Arrays.fill(passwordBytes, (byte) 0);
            org.slf4j.LoggerFactory.getLogger(PasswordCryptoService.class).info("password decrypt ok");
        } finally {
            sessions.remove(sessionId);
        }
    }

}
