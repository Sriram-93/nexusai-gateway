package com.llm.nexusai_gateway.Security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisRateLimiterTest {

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Test
    void testCheckRateLimitAndReset() {
        String testTenant = "test-tenant-rate-limit-unit";

        // Initial check should be allowed
        StepVerifier.create(redisRateLimiter.checkRateLimit(testTenant, 10))
                .assertNext(status -> {
                    assertThat(status.allowed()).isTrue();
                    assertThat(status.limit()).isEqualTo(10);
                })
                .verifyComplete();

        // Reset rate limit counter
        StepVerifier.create(redisRateLimiter.resetRateLimit(testTenant))
                .expectNextCount(1)
                .verifyComplete();
    }
}
