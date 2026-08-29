package com.llm.nexusai_gateway.Telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SyntheticTrafficGeneratorTest {

    @Autowired
    private SyntheticTrafficGenerator syntheticTrafficGenerator;

    @Test
    void runBenchmark_executesSyntheticLoadAndReturnsMetrics() {
        StepVerifier.create(syntheticTrafficGenerator.runBenchmark(5))
                .expectNextMatches(result -> {
                    assertThat(result.totalRequests()).isEqualTo(5);
                    assertThat(result.successfulRequests()).isGreaterThanOrEqualTo(0);
                    assertThat(result.avgLatencyMs()).isGreaterThanOrEqualTo(0);
                    assertThat(result.modelDistribution()).isNotNull();
                    return true;
                })
                .verifyComplete();
    }
}
