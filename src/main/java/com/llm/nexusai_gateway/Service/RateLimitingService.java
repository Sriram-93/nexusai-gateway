package com.llm.nexusai_gateway.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.domain.Range;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Exception.RateLimitException;

import java.util.Map;
import java.util.UUID;

@Service
public class RateLimitingService {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${gateway.rate-limiter.algorithm:TOKEN_BUCKET}")
    private String rateLimitingAlgorithm;

    @Value("${gateway.rate-limiter.user.limit:5}")
    private int userLimit;

    @Value("${gateway.rate-limiter.tenant.limit:20}")
    private int tenantLimit;

    @Value("${gateway.rate-limiter.provider.gemini.limit:15}")
    private int geminiLimit;

    @Value("${gateway.rate-limiter.provider.groq.limit:30}")
    private int groqLimit;

    @Value("${gateway.rate-limiter.provider.openai.limit:20}")
    private int openaiLimit;

    @Value("${gateway.rate-limiter.provider.claude.limit:20}")
    private int claudeLimit;

    @Value("${gateway.rate-limiter.provider.ollama.limit:100}")
    private int ollamaLimit;

    public RateLimitingService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check rate limits at User, Tenant, and Provider levels.
     * Throws RateLimitException if any rate limit is exceeded.
     */
    public Mono<Void> checkRateLimits(ChatRequest request, String targetProvider) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default-tenant";
        int providerLimit = getProviderLimit(targetProvider);

        // 1. Check User Level
        return applyRateLimit("user:" + userId, userLimit)
                // 2. Check Tenant Level
                .then(applyRateLimit("tenant:" + tenantId, tenantLimit))
                // 3. Check Provider Level
                .then(applyRateLimit("provider:" + targetProvider.toLowerCase(), providerLimit));
    }

    private int getProviderLimit(String provider) {
        switch (provider.toLowerCase()) {
            case "gemini": return geminiLimit;
            case "groq": return groqLimit;
            case "openai": return openaiLimit;
            case "claude": return claudeLimit;
            case "ollama": return ollamaLimit;
            default: return 30; // fallback default
        }
    }

    private Mono<Void> applyRateLimit(String keySuffix, int limit) {
        String algorithm = rateLimitingAlgorithm.toUpperCase();
        switch (algorithm) {
            case "SLIDING_WINDOW":
                return applySlidingWindow(keySuffix, limit);
            case "LEAKY_BUCKET":
                return applyLeakyBucket(keySuffix, limit);
            case "TOKEN_BUCKET":
            default:
                return applyTokenBucket(keySuffix, limit);
        }
    }

    /**
     * Token Bucket Algorithm:
     * Key stores "tokens" (current tokens remaining) and "last_updated" (timestamp in ms).
     */
    private Mono<Void> applyTokenBucket(String keySuffix, int limit) {
        String key = "rate:token:" + keySuffix;
        long now = System.currentTimeMillis();

        return redisTemplate.opsForValue().multiGet(java.util.List.of(key + ":tokens", key + ":last_updated"))
                .flatMap(values -> {
                    double tokens;
                    long lastUpdated;

                    if (values.get(0) == null || values.get(1) == null) {
                        // Initialize bucket
                        tokens = limit;
                        lastUpdated = now;
                    } else {
                        double oldTokens = Double.parseDouble(values.get(0));
                        long oldLastUpdated = Long.parseLong(values.get(1));

                        // Refill rate: limit tokens per minute -> limit / 60000.0 tokens per ms
                        double elapsedMs = Math.max(0, now - oldLastUpdated);
                        double refilledTokens = elapsedMs * (limit / 60000.0);
                        tokens = Math.min(limit, oldTokens + refilledTokens);
                        lastUpdated = now;
                    }

                    if (tokens >= 1.0) {
                        double remainingTokens = tokens - 1.0;
                        return redisTemplate.opsForValue().set(key + ":tokens", String.valueOf(remainingTokens))
                                .and(redisTemplate.opsForValue().set(key + ":last_updated", String.valueOf(lastUpdated)))
                                .then();
                    } else {
                        long retryAfter = Math.max(1, (long) Math.ceil((1.0 - tokens) / (limit / 60000.0) / 1000.0));
                        return Mono.error(new RateLimitException(keySuffix, retryAfter));
                    }
                });
    }

    /**
     * Sliding Window Algorithm:
     * Redis Sorted Set containing request timestamps within the active 60s window.
     */
    private Mono<Void> applySlidingWindow(String keySuffix, int limit) {
        String key = "rate:sliding:" + keySuffix;
        long now = System.currentTimeMillis();
        long clearBefore = now - 60000; // 60 seconds window
        String member = now + ":" + UUID.randomUUID().toString();

        return redisTemplate.opsForZSet().removeRangeByScore(key, Range.closed(0.0, (double) clearBefore))
                .then(redisTemplate.opsForZSet().size(key))
                .flatMap(count -> {
                    if (count >= limit) {
                        // Estimate retry after: count of requests exceeding limits will slide out in approx 1-60s
                        return redisTemplate.opsForZSet().rangeByScore(key, Range.closed(0.0, (double) now))
                                .collectList()
                                .flatMap(list -> {
                                    long retryAfter = 15; // default fallback retry-after
                                    if (!list.isEmpty()) {
                                        // Key is sorted, first element is the oldest within active window
                                        String oldestMember = list.get(0);
                                        try {
                                            long oldestTime = Long.parseLong(oldestMember.split(":")[0]);
                                            retryAfter = Math.max(1, (oldestTime + 60000 - now) / 1000);
                                        } catch (Exception e) {
                                            // Ignore parsing errors
                                        }
                                    }
                                    return Mono.error(new RateLimitException(keySuffix, retryAfter));
                                });
                    }

                    // Add request to sliding set
                    return redisTemplate.opsForZSet().add(key, member, now)
                            .then();
                });
    }

    /**
     * Leaky Bucket Algorithm:
     * Key stores "water_level" and "last_leaked" (timestamp in ms).
     */
    private Mono<Void> applyLeakyBucket(String keySuffix, int limit) {
        String key = "rate:leaky:" + keySuffix;
        long now = System.currentTimeMillis();

        return redisTemplate.opsForValue().multiGet(java.util.List.of(key + ":water", key + ":last_leaked"))
                .flatMap(values -> {
                    double waterLevel;
                    long lastLeaked;

                    if (values.get(0) == null || values.get(1) == null) {
                        waterLevel = 0.0;
                        lastLeaked = now;
                    } else {
                        double oldWater = Double.parseDouble(values.get(0));
                        long oldLastLeaked = Long.parseLong(values.get(1));

                        // Leak rate: limit requests per minute -> limit / 60000.0 water units per ms
                        double elapsedMs = Math.max(0, now - oldLastLeaked);
                        double leakedWater = elapsedMs * (limit / 60000.0);
                        waterLevel = Math.max(0.0, oldWater - leakedWater);
                        lastLeaked = now;
                    }

                    // If capacity (limit) is not full, add 1.0 unit of water
                    if (waterLevel + 1.0 <= limit) {
                        double newWater = waterLevel + 1.0;
                        return redisTemplate.opsForValue().set(key + ":water", String.valueOf(newWater))
                                .and(redisTemplate.opsForValue().set(key + ":last_leaked", String.valueOf(lastLeaked)))
                                .then();
                    } else {
                        // Calculate time until enough water leaks to allow one request
                        double excessWater = (waterLevel + 1.0) - limit;
                        long retryAfter = Math.max(1, (long) Math.ceil(excessWater / (limit / 60000.0) / 1000.0));
                        return Mono.error(new RateLimitException(keySuffix, retryAfter));
                    }
                });
    }
}
