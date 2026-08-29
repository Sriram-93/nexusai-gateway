package com.llm.nexusai_gateway.Health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Provider Health Monitor implementing the Circuit Breaker pattern (Priority 8).
 *
 * Responsibilities:
 *  1. Track per-provider health using ProviderHealthStatus (failure count, EMA latency, error rate).
 *  2. Open the circuit breaker after 3 consecutive failures, blocking routing to that provider.
 *  3. Transition to HALF_OPEN after 30s cooldown, allowing a single probe.
 *  4. Close the circuit on a successful probe.
 *  5. Expose filtered provider lists to RoutingAgent, excluding OPEN-circuit providers.
 *  6. Provide a health summary endpoint for observability.
 */
@Service
public class ProviderHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthMonitor.class);

    /** Circuit breaker cooldown: 30 seconds */
    private static final long COOLDOWN_MS = 30_000L;

    /** Known provider arms — auto-initialised on first use */
    private final ConcurrentHashMap<String, ProviderHealthStatus> registry = new ConcurrentHashMap<>();

    private final com.llm.nexusai_gateway.Telemetry.TelemetryService telemetryService;

    public ProviderHealthMonitor(com.llm.nexusai_gateway.Telemetry.TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    // For tests without Spring context
    public ProviderHealthMonitor() {
        this.telemetryService = null;
    }

    private ProviderHealthStatus getOrCreate(String providerArm) {
        return registry.computeIfAbsent(providerArm, ProviderHealthStatus::new);
    }

    /**
     * Record a successful call to a provider arm.
     * @param providerArm e.g. "gemini:gemini-2.5-flash"
     * @param latencyMs   actual observed end-to-end latency
     */
    public void recordSuccess(String providerArm, long latencyMs) {
        ProviderHealthStatus status = getOrCreate(providerArm);
        CircuitBreakerState before = status.getState();
        status.recordSuccess(latencyMs);
        if (before != CircuitBreakerState.CLOSED) {
            log.info("HealthMonitor: Circuit CLOSED for '{}' after successful probe (latency={}ms)", providerArm, latencyMs);
            emitCircuitState(providerArm, CircuitBreakerState.CLOSED);
        }
    }

    /**
     * Record a failed call to a provider arm.
     * @param providerArm e.g. "gemini:gemini-2.5-flash"
     */
    public void recordFailure(String providerArm) {
        ProviderHealthStatus status = getOrCreate(providerArm);
        status.recordFailure();
        if (status.getState() == CircuitBreakerState.OPEN) {
            log.warn("HealthMonitor: Circuit OPENED for '{}' (consecutive failures={})", providerArm, status.getConsecutiveFailures());
            emitCircuitState(providerArm, CircuitBreakerState.OPEN);
        }
    }

    private void emitCircuitState(String providerArm, CircuitBreakerState state) {
        if (telemetryService != null) {
            telemetryService.recordCircuitBreakerState(providerArm, state);
        }
    }

    /**
     * Filter the given list of provider arms to only include healthy (CLOSED or HALF_OPEN) providers.
     * If ALL providers are OPEN, falls back to the full list (degraded mode) to avoid total failure.
     */
    public List<String> filterHealthy(List<String> candidates) {
        List<String> healthy = candidates.stream()
            .filter(arm -> getOrCreate(arm).isAvailable(COOLDOWN_MS))
            .collect(Collectors.toList());

        if (healthy.isEmpty()) {
            log.warn("HealthMonitor: All provider circuits are OPEN — degraded mode, bypassing circuit breakers");
            return candidates;
        }

        if (healthy.size() < candidates.size()) {
            List<String> blocked = candidates.stream()
                .filter(a -> !healthy.contains(a))
                .toList();
            log.info("HealthMonitor: Filtered out {} OPEN-circuit provider(s): {}", blocked.size(), blocked);
        }

        return healthy;
    }

    /**
     * Returns a reactive health snapshot for the /api/health/providers endpoint.
     */
    public Mono<Map<String, Object>> getHealthSnapshot(List<String> allProviderArms) {
        // Ensure all known arms are initialised
        allProviderArms.forEach(this::getOrCreate);

        Map<String, Object> snapshot = registry.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Map.of(
                    "state",             e.getValue().getState().name(),
                    "consecutiveFailures", e.getValue().getConsecutiveFailures(),
                    "totalSuccesses",    e.getValue().getTotalSuccesses(),
                    "totalFailures",     e.getValue().getTotalFailures(),
                    "avgLatencyMs",      Math.round(e.getValue().getAvgLatencyMs()),
                    "errorRate",         String.format("%.2f%%", e.getValue().getErrorRate() * 100),
                    "totalCalls",        e.getValue().getTotalCallCount()
                )
            ));

        return Mono.just(snapshot);
    }

    /** Raw access for testing and advanced integrations */
    public ProviderHealthStatus getStatus(String providerArm) {
        return getOrCreate(providerArm);
    }

    public Map<String, ProviderHealthStatus> getAllStatuses() {
        return new java.util.HashMap<>(registry);
    }
}
