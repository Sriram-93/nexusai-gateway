package com.llm.nexusai_gateway.Service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class ResponseCacheService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String CACHE_PREFIX = "cache:response:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public ResponseCacheService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get a cached LLM response for the given model and prompt.
     */
    public Mono<String> getCachedResponse(String model, String prompt) {
        String key = buildCacheKey(model, prompt);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Cache an LLM response for the given model and prompt with a 1-hour TTL.
     */
    public Mono<Void> cacheResponse(String model, String prompt, String response) {
        String key = buildCacheKey(model, prompt);
        return redisTemplate.opsForValue().set(key, response, CACHE_TTL).then();
    }

    /**
     * Evict a specific cached entry.
     */
    public Mono<Boolean> evict(String model, String prompt) {
        String key = buildCacheKey(model, prompt);
        return redisTemplate.opsForValue().delete(key);
    }

    /**
     * Flush all cached response keys in Redis.
     */
    public Mono<Void> clearCache() {
        return redisTemplate.keys(CACHE_PREFIX + "*")
                .flatMap(redisTemplate::delete)
                .then();
    }

    /**
     * Compute a unique Redis cache key using SHA-256 hash of the prompt.
     */
    private String buildCacheKey(String model, String prompt) {
        String sanitizedModel = model.toLowerCase().replaceAll("[^a-zA-Z0-9.-]", "_");
        String promptHash = sha256(prompt);
        return CACHE_PREFIX + sanitizedModel + ":" + promptHash;
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            // Fallback to hashCode in the extremely unlikely event SHA-256 is missing
            return String.valueOf(base.hashCode());
        }
    }
}
