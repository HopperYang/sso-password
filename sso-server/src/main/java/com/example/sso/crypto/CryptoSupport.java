package com.example.sso.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class CryptoSupport {

    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private CryptoSupport() {}

    public static KeyPair generateRsa2048() throws GeneralSecurityException {
        var kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, RNG);
        return kpg.generateKeyPair();
    }

    public static PublicKey publicKeyFromSpki(byte[] spki) throws GeneralSecurityException {
        var spec = new X509EncodedKeySpec(spki);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public static PrivateKey privateKeyFromPkcs8(byte[] pkcs8) throws GeneralSecurityException {
        var spec = new PKCS8EncodedKeySpec(pkcs8);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static byte[] rsaOaepEncrypt(PublicKey pub, byte[] plaintext) throws GeneralSecurityException {
        var cipher = Cipher.getInstance(RSA_TRANSFORM);
        var oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, pub, oaep);
        return cipher.doFinal(plaintext);
    }

    public static byte[] rsaOaepDecrypt(PrivateKey priv, byte[] ciphertext) throws GeneralSecurityException {
        var cipher = Cipher.getInstance(RSA_TRANSFORM);
        var oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, priv, oaep);
        return cipher.doFinal(ciphertext);
    }

    public static byte[] randomAesKey() {
        var key = new byte[32];
        RNG.nextBytes(key);
        return key;
    }

    public static byte[] randomIv() {
        var iv = new byte[12];
        RNG.nextBytes(iv);
        return iv;
    }

    public static AesGcmSeal aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext)
            throws GeneralSecurityException {
        SecretKey aesKey = new SecretKeySpec(key, "AES");
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] combined = cipher.doFinal(plaintext);
        int tagLen = GCM_TAG_BITS / 8;
        int ctLen = combined.length - tagLen;
        byte[] ciphertext = Arrays.copyOfRange(combined, 0, ctLen);
        byte[] tag = Arrays.copyOfRange(combined, ctLen, combined.length);
        return new AesGcmSeal(ciphertext, tag);
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] tag)
            throws GeneralSecurityException {
        SecretKey aesKey = new SecretKeySpec(key, "AES");
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] combined = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
        return cipher.doFinal(combined);
    }

    public record AesGcmSeal(byte[] ciphertext, byte[] tag) {}
}
