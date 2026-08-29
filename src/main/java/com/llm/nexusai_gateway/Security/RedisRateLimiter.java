package com.llm.nexusai_gateway.Security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.time.Duration;

@Service
public class RedisRateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record RateLimitStatus(
        boolean allowed,
        int limit,
        long remaining,
        long resetSeconds,
        long currentCount
    ) {}

    /**
     * Checks if the given tenant is allowed to make a request based on their limit.
     * Implements a sliding window rate limiter via Redis increment and expire.
     * 
     * @param tenantId The ID of the tenant.
     * @param maxRequestsPerMinute The allowed number of requests per minute.
     * @return Mono<Boolean> true if allowed, false if rate limited.
     */
    public Mono<Boolean> isAllowed(String tenantId, int maxRequestsPerMinute) {
        return checkRateLimit(tenantId, maxRequestsPerMinute).map(RateLimitStatus::allowed);
    }

    /**
     * Obtains detailed rate limit status including remaining quota and reset window.
     */
    public Mono<RateLimitStatus> checkRateLimit(String tenantId, int maxRequestsPerMinute) {
        long epochSecond = Instant.now().getEpochSecond();
        long currentMinute = epochSecond / 60;
        long resetSeconds = 60 - (epochSecond % 60);
        String key = "rate_limit:tenant:" + tenantId + ":" + currentMinute;

        return redisTemplate.opsForValue().increment(key)
            .flatMap(count -> {
                Mono<Boolean> setExpire = (count == 1) ? redisTemplate.expire(key, Duration.ofMinutes(2)) : Mono.just(true);
                return setExpire.map(ignored -> {
                    long remaining = Math.max(0, maxRequestsPerMinute - count);
                    boolean allowed = count <= maxRequestsPerMinute;
                    return new RateLimitStatus(allowed, maxRequestsPerMinute, remaining, resetSeconds, count);
                });
            })
            .onErrorResume(err -> {
                // Fallback on Redis error to maintain gateway availability
                return Mono.just(new RateLimitStatus(true, maxRequestsPerMinute, maxRequestsPerMinute, 60, 0));
            });
    }

    /**
     * Admin reset of rate limit counter for a specific tenant window.
     */
    public Mono<Boolean> resetRateLimit(String tenantId) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = "rate_limit:tenant:" + tenantId + ":" + currentMinute;
        return redisTemplate.delete(key).map(count -> count > 0);
    }
}
