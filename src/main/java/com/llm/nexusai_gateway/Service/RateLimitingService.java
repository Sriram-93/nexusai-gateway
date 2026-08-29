package com.llm.nexusai_gateway.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Exception.RateLimitException;

/**
 * Production Rate Limiting Service — Token Bucket algorithm only.
 *
 * Design decision (Fix 1): Only the Token Bucket algorithm runs in the live request path.
 * Rationale: Token Bucket provides burst tolerance with a mathematically clean, constant
 * refill rate (limit tokens/minute). It is the industry-standard choice for API gateway
 * rate limiting (used by AWS API Gateway, Google Cloud Endpoints, Stripe).
 *
 * The Sliding Window and Leaky Bucket algorithms exist in RateLimitExperimentService
 * and are used ONLY during benchmarking experiments. Running multiple algorithms
 * simultaneously on every live request creates unpredictable rejection behavior and
 * cannot be cleanly explained in the paper or viva.
 *
 * Applied at three levels:
 *   1. User level      — prevents single-user abuse (default: 5 req/min)
 *   2. Tenant level    — enforces enterprise quota agreements (default: 20 req/min)
 *   3. Provider level  — respects upstream API rate limits (Gemini: 15, Groq: 30)
 */
@Service
public class RateLimitingService {

    private final ReactiveStringRedisTemplate redisTemplate;

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
     * Check rate limits at User, Tenant, and Provider levels using Token Bucket.
     * Returns Mono<Void> on success. Throws RateLimitException if any limit is exceeded.
     */
    public Mono<Void> checkRateLimits(ChatRequest request, String targetProvider) {
        String userId   = request.getUserId()   != null ? request.getUserId()   : "anonymous";
        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default-tenant";
        int providerLimit = getProviderLimit(targetProvider);

        return applyTokenBucket("user:"     + userId,                      userLimit)
          .then(applyTokenBucket("tenant:"  + tenantId,                    tenantLimit))
          .then(applyTokenBucket("provider:" + targetProvider.toLowerCase(), providerLimit))
          .onErrorResume(RateLimitException.class, Mono::error)
          .onErrorResume(e -> {
              if (e instanceof RateLimitException) {
                  return Mono.error(e);
              }
              org.slf4j.LoggerFactory.getLogger(RateLimitingService.class)
                  .warn("Redis connection failed in RateLimitingService; bypassing rate limiting: {}", e.getMessage());
              return Mono.empty();
          });
    }

    private int getProviderLimit(String provider) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> geminiLimit;
            case "groq"   -> groqLimit;
            case "openai" -> openaiLimit;
            case "claude" -> claudeLimit;
            case "ollama" -> ollamaLimit;
            default       -> 30;
        };
    }

    /**
     * Token Bucket Algorithm — the sole production rate limiting algorithm.
     *
     * Stores per-key "tokens remaining" and "last_updated" timestamp in Redis.
     * Refill rate: limit tokens per minute → limit/60000.0 tokens per millisecond.
     * A request consumes 1 token. When the bucket is empty, HTTP 429 is returned
     * with a Retry-After header computed from the refill math.
     */
    private Mono<Void> applyTokenBucket(String keySuffix, int limit) {
        String key = "rate:token:" + keySuffix;
        long now = System.currentTimeMillis();

        return redisTemplate.opsForValue().multiGet(java.util.List.of(key + ":tokens", key + ":last_updated"))
            .flatMap(values -> {
                double tokens;
                long lastUpdated;

                if (values.get(0) == null || values.get(1) == null) {
                    tokens = limit;          // Initialize full bucket on first request
                    lastUpdated = now;
                } else {
                    double oldTokens = Double.parseDouble(values.get(0));
                    long oldLastUpdated = Long.parseLong(values.get(1));
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
}

