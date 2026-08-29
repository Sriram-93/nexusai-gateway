package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Service.ResponseCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CacheControllerTest {

    @Autowired
    private CacheController cacheController;

    @Autowired
    private ResponseCacheService responseCacheService;

    @Test
    void getCacheStats_returnsCurrentMetrics() {
        ResponseEntity<ResponseCacheService.CacheStats> response = cacheController.getCacheStats().block();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseCacheService.CacheStats stats = response.getBody();
        assertThat(stats).isNotNull();
        assertThat(stats.hits()).isGreaterThanOrEqualTo(0);
        assertThat(stats.misses()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void flushCache_clearsCacheAndResetsCounters() {
        StepVerifier.create(cacheController.flushCache())
                .expectNextMatches(resp -> {
                    assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
                    Map<String, Object> body = resp.getBody();
                    return body != null && "SUCCESS".equals(body.get("status"));
                })
                .verifyComplete();

        ResponseCacheService.CacheStats statsAfter = responseCacheService.getStats();
        assertThat(statsAfter.hits()).isEqualTo(0);
        assertThat(statsAfter.misses()).isEqualTo(0);
    }
}
