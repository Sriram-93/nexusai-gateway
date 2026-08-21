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

    /**
     * Checks if the given tenant is allowed to make a request based on their limit.
     * Implements a sliding window / token bucket via Redis increment and expire.
     * 
     * @param tenantId The ID of the tenant.
     * @param maxRequestsPerMinute The allowed number of requests per minute.
     * @return Mono<Boolean> true if allowed, false if rate limited.
     */
    public Mono<Boolean> isAllowed(String tenantId, int maxRequestsPerMinute) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        String key = "rate_limit:tenant:" + tenantId + ":" + currentMinute;

        return redisTemplate.opsForValue().increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    return redisTemplate.expire(key, Duration.ofMinutes(2)).thenReturn(true);
                }
                return Mono.just(count <= maxRequestsPerMinute);
            });
    }
}
