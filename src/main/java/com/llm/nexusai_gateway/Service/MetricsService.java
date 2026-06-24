package com.llm.nexusai_gateway.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> latencySummaries = new ConcurrentHashMap<>();
    private final Map<String, Counter> tokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> costCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> fallbackCounters = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        
        // Register Circuit Breaker state gauges for key providers
        registerCircuitBreakerGauge("gemini");
        registerCircuitBreakerGauge("groq");
        registerCircuitBreakerGauge("openai");
        registerCircuitBreakerGauge("claude");
        registerCircuitBreakerGauge("ollama");
    }

    private void registerCircuitBreakerGauge(String provider) {
        meterRegistry.gauge("nexusai_circuit_breaker_state", 
            io.micrometer.core.instrument.Tags.of("provider", provider),
            this,
            inst -> {
                try {
                    CircuitBreaker cb = inst.circuitBreakerRegistry.circuitBreaker(provider.toLowerCase());
                    switch (cb.getState()) {
                        case CLOSED: return 0.0;
                        case OPEN: return 1.0;
                        case HALF_OPEN: return 2.0;
                        default: return -1.0;
                    }
                } catch (Exception e) {
                    return -1.0;
                }
            }
        );
    }

    /**
     * Record total requests incremented by route.
     */
    public void recordRequest(String provider, String model, String priority, String status) {
        String safeProvider = provider != null ? provider : "unknown";
        String safeModel = model != null ? model : "unknown";
        String safePriority = priority != null ? priority : "unknown";
        String safeStatus = status != null ? status : "unknown";

        String key = String.format("%s:%s:%s:%s", safeProvider, safeModel, safePriority, safeStatus);
        requestCounters.computeIfAbsent(key, k -> 
            Counter.builder("nexusai_requests_total")
                .tag("provider", safeProvider)
                .tag("model", safeModel)
                .tag("priority", safePriority)
                .tag("status", safeStatus)
                .description("Total number of LLM requests routed through NexusAI")
                .register(meterRegistry)
        ).increment();
    }

    /**
     * Record request latency.
     */
    public void recordLatency(String provider, double latencyMs) {
        String safeProvider = provider != null ? provider : "unknown";
        latencySummaries.computeIfAbsent(safeProvider, p -> 
            DistributionSummary.builder("nexusai_latency_ms")
                .tag("provider", safeProvider)
                .description("Latency distribution of LLM requests in milliseconds")
                .publishPercentileHistogram()
                .register(meterRegistry)
        ).record(latencyMs);
    }

    /**
     * Record total tokens consumed.
     */
    public void recordTokens(String provider, double tokens) {
        String safeProvider = provider != null ? provider : "unknown";
        tokenCounters.computeIfAbsent(safeProvider, p -> 
            Counter.builder("nexusai_tokens_used_total")
                .tag("provider", safeProvider)
                .description("Total tokens consumed by provider")
                .register(meterRegistry)
        ).increment(tokens);
    }

    /**
     * Record total cost in USD.
     */
    public void recordCost(String provider, double costUsd) {
        String safeProvider = provider != null ? provider : "unknown";
        costCounters.computeIfAbsent(safeProvider, p -> 
            Counter.builder("nexusai_cost_usd_total")
                .tag("provider", safeProvider)
                .description("Total cost incurred by provider in USD")
                .register(meterRegistry)
        ).increment(costUsd);
    }

    /**
     * Record fallback transitions (e.g. gemini -> groq).
     */
    public void recordFallback(String sourceProvider, String targetProvider) {
        String safeSource = sourceProvider != null ? sourceProvider : "unknown";
        String safeTarget = targetProvider != null ? targetProvider : "unknown";
        String key = safeSource + "->" + safeTarget;

        fallbackCounters.computeIfAbsent(key, k -> 
            Counter.builder("nexusai_fallbacks_total")
                .tag("source", safeSource)
                .tag("target", safeTarget)
                .description("Total number of circuit-breaker/error fallbacks triggered")
                .register(meterRegistry)
        ).increment();
    }
}
