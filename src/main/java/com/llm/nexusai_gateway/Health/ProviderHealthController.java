package com.llm.nexusai_gateway.Health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint exposing live provider health and circuit breaker status (Priority 8).
 */
@RestController
@RequestMapping("/api/health")
public class ProviderHealthController {

    private final ProviderHealthMonitor healthMonitor;
    private final com.llm.nexusai_gateway.Provider.ModelRegistry modelRegistry;

    public ProviderHealthController(ProviderHealthMonitor healthMonitor, com.llm.nexusai_gateway.Provider.ModelRegistry modelRegistry) {
        this.healthMonitor = healthMonitor;
        this.modelRegistry = modelRegistry;
    }

    /**
     * GET /api/health/providers
     * Returns live circuit breaker state, error rates, and average latency for all provider arms.
     */
    @GetMapping("/providers")
    public Mono<Map<String, Object>> getProviderHealth() {
        return healthMonitor.getHealthSnapshot(modelRegistry.getEnabledArmKeys());
    }
}
