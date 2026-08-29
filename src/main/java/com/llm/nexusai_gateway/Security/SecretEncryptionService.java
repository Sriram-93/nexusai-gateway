package com.llm.nexusai_gateway.Security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Secret Encryption Service for Provider API Keys & Credentials.
 * Ensures plaintext keys are NEVER saved to the database.
 */
@Service
public class SecretEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKey secretKey;

    public SecretEncryptionService(@Value("${nexusai.security.encryption-key:nexusai-default-master-encryption-key-32bytes}") String masterKeyStr) {
        this.secretKey = deriveKey(masterKeyStr);
    }

    private SecretKey deriveKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES secret key", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return plainText;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, combined, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt secret payload: {}", e.getMessage());
            throw new RuntimeException("Encryption error", e);
        }
    }

    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isBlank()) return cipherTextBase64;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherTextBase64);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload size");
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt secret payload: {}", e.getMessage());
            throw new RuntimeException("Decryption error", e);
        }
    }

    /**
     * Compute SHA-256 Hash of an API Key for fast, non-reversible key authentication.
     */
    public static String hashApiKey(String rawApiKey) {
        if (rawApiKey == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawApiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }
}
