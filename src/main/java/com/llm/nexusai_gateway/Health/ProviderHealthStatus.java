package com.llm.nexusai_gateway.Health;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, mutable health record for a single provider arm (Priority 8).
 *
 * Tracks:
 *  - Circuit breaker state (CLOSED / OPEN / HALF_OPEN)
 *  - Consecutive failure count
 *  - Rolling average latency (exponential moving average)
 *  - Total success/failure counts
 *  - Time of last failure (for cooldown expiry)
 */
public class ProviderHealthStatus {

    private final String providerArm;

    private final AtomicReference<CircuitBreakerState> state =
        new AtomicReference<>(CircuitBreakerState.CLOSED);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger totalSuccesses      = new AtomicInteger(0);
    private final AtomicInteger totalFailures       = new AtomicInteger(0);

    /** Exponential moving average latency in milliseconds */
    private volatile double avgLatencyMs = 0.0;
    private static final double EMA_ALPHA = 0.2;

    private volatile long openedAtMs = 0L;
    private final AtomicLong totalCallCount = new AtomicLong(0);

    public ProviderHealthStatus(String providerArm) {
        this.providerArm = providerArm;
    }

    public void recordSuccess(long latencyMs) {
        state.set(CircuitBreakerState.CLOSED);
        consecutiveFailures.set(0);
        totalSuccesses.incrementAndGet();
        totalCallCount.incrementAndGet();
        avgLatencyMs = (avgLatencyMs == 0.0) ? latencyMs
            : EMA_ALPHA * latencyMs + (1 - EMA_ALPHA) * avgLatencyMs;
    }

    public void recordFailure() {
        totalFailures.incrementAndGet();
        totalCallCount.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= 3) {
            openedAtMs = System.currentTimeMillis();
            state.set(CircuitBreakerState.OPEN);
        }
    }

    /**
     * Check if provider is available for routing. Handles HALF_OPEN cooldown transition.
     * @param cooldownMs milliseconds to wait before transitioning OPEN → HALF_OPEN
     */
    public boolean isAvailable(long cooldownMs) {
        CircuitBreakerState current = state.get();
        if (current == CircuitBreakerState.CLOSED) return true;
        if (current == CircuitBreakerState.OPEN) {
            if (System.currentTimeMillis() - openedAtMs >= cooldownMs) {
                state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN);
                return true; // Allow one probe request
            }
            return false;
        }
        // HALF_OPEN: allow probe
        return true;
    }

    public String getProviderArm()             { return providerArm; }
    public CircuitBreakerState getState()       { return state.get(); }
    public int getConsecutiveFailures()         { return consecutiveFailures.get(); }
    public int getTotalSuccesses()              { return totalSuccesses.get(); }
    public int getTotalFailures()               { return totalFailures.get(); }
    public double getAvgLatencyMs()             { return avgLatencyMs; }
    public long getTotalCallCount()             { return totalCallCount.get(); }

    public double getErrorRate() {
        long total = totalCallCount.get();
        return total == 0 ? 0.0 : (double) totalFailures.get() / total;
    }

    @Override
    public String toString() {
        return String.format("[%s] state=%s failures=%d avgLatency=%.1fms errorRate=%.2f",
            providerArm, state.get(), consecutiveFailures.get(), avgLatencyMs, getErrorRate());
    }
}
