package com.llm.nexusai_gateway.Provider;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Secure Converter for Provider API Credentials.
 * Upgraded to AES-GCM (Authenticated Encryption with Associated Data).
 * Prevents tampering and ensures confidentiality.
 */
@Converter
public class EncryptedMapToJsonConverter implements AttributeConverter<Map<String, String>, String> {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    
    // In production, inject this securely (e.g., from env or Vault). 
    // Must be exactly 32 bytes for AES-256.
    private static final String SECRET_ENV = System.getenv("NEXUS_MASTER_KEY");
    private static final String DEFAULT_SECRET = "nexusai_secure_master_key_32byte"; // Exactly 32 chars
    private static final String SECRET = (SECRET_ENV != null && SECRET_ENV.length() >= 32) 
        ? SECRET_ENV.substring(0, 32) 
        : DEFAULT_SECRET; 
        
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits is recommended for GCM
    private static final int GCM_TAG_LENGTH = 128;

    @Override
    public String convertToDatabaseColumn(Map<String, String> meta) {
        if (meta == null) return null;
        try {
            String json = objectMapper.writeValueAsString(meta);
            return encrypt(json);
        } catch (JsonProcessingException ex) {
            return null;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to encrypt credentials", ex);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        try {
            String json;
            if (dbData.trim().startsWith("{")) {
                json = dbData; // Legacy plaintext fallback
            } else {
                json = decrypt(dbData);
            }
            return objectMapper.readValue(json, new TypeReference<HashMap<String, String>>() {});
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private String encrypt(String value) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
        byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

        // Prepend IV to ciphertext
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);
        
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    private String decrypt(String encrypted) throws Exception {
        // Fallback for old ECB encrypted strings in dev DB
        if (!encrypted.contains("==") && encrypted.length() < 50) { 
            return fallbackDecryptEcb(encrypted); 
        }

        byte[] decoded = Base64.getDecoder().decode(encrypted);
        
        // Extract IV and Ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(decoded, 0, iv, 0, iv.length);
        
        byte[] cipherText = new byte[decoded.length - iv.length];
        System.arraycopy(decoded, iv.length, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
        byte[] plainText = cipher.doFinal(cipherText);
        
        return new String(plainText, StandardCharsets.UTF_8);
    }
    
    // Fallback for old AES-ECB data seeded in the database before the GCM upgrade
    private String fallbackDecryptEcb(String encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec("nexusai-secureky".getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decodedBytes = Base64.getDecoder().decode(encrypted);
            byte[] original = cipher.doFinal(decodedBytes);
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed ECB fallback decryption", e);
        }
    }
}
