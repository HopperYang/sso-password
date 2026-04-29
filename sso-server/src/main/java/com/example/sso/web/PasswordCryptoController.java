package com.example.sso.web;

import com.example.sso.crypto.PasswordCryptoService;
import com.example.sso.web.dto.CreatePasswordSessionRequest;
import com.example.sso.web.dto.PasswordSubmitRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/crypto/password")
public class PasswordCryptoController {

    private final PasswordCryptoService crypto;

    public PasswordCryptoController(PasswordCryptoService crypto) {
        this.crypto = crypto;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(@Valid @RequestBody CreatePasswordSessionRequest body)
            throws Exception {
        byte[] spki = Base64.getDecoder().decode(body.clientPublicKeySpki());
        Map<String, String> envelope = crypto.createSessionEnvelope(spki);
        return ResponseEntity.ok(envelope);
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody PasswordSubmitRequest body)
            throws Exception {
        var dec = Base64.getDecoder();
        crypto.decryptAndValidateSubmit(
                body.sessionId(),
                dec.decode(body.encryptedKey()),
                dec.decode(body.iv()),
                dec.decode(body.ciphertext()),
                dec.decode(body.tag()));
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
