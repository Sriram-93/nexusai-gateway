package com.llm.nexusai_gateway.Health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderHealthMonitorTest {

    private ProviderHealthMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ProviderHealthMonitor();
    }

    @Test
    void testNewProviderStartsClosed() {
        ProviderHealthStatus status = monitor.getStatus("gemini:gemini-2.5-flash");
        assertEquals(CircuitBreakerState.CLOSED, status.getState());
    }

    @Test
    void testCircuitOpensAfterThreeConsecutiveFailures() {
        String arm = "groq:llama-3.1-8b-instant";
        monitor.recordFailure(arm);
        monitor.recordFailure(arm);
        assertEquals(CircuitBreakerState.CLOSED, monitor.getStatus(arm).getState());
        monitor.recordFailure(arm); // 3rd failure
        assertEquals(CircuitBreakerState.OPEN, monitor.getStatus(arm).getState());
    }

    @Test
    void testOpenCircuitIsFilteredOutByHealthyFilter() {
        String arm = "gemini:gemini-3.5-flash";
        monitor.recordFailure(arm);
        monitor.recordFailure(arm);
        monitor.recordFailure(arm); // opens circuit

        List<String> healthy = monitor.filterHealthy(
            List.of("gemini:gemini-2.5-flash", "gemini:gemini-3.5-flash")
        );

        assertEquals(1, healthy.size());
        assertEquals("gemini:gemini-2.5-flash", healthy.get(0));
    }

    @Test
    void testSuccessClosesCircuitAndClearsFailures() {
        String arm = "groq:llama-3.3-70b-versatile";
        monitor.recordFailure(arm);
        monitor.recordFailure(arm);
        monitor.recordFailure(arm); // OPEN

        monitor.recordSuccess(arm, 250L); // probe succeeds → CLOSED
        assertEquals(CircuitBreakerState.CLOSED, monitor.getStatus(arm).getState());
        assertEquals(0, monitor.getStatus(arm).getConsecutiveFailures());
    }

    @Test
    void testFallbackToAllProvidersIfAllAreOpen() {
        List<String> providers = List.of("gemini:gemini-2.5-flash", "groq:llama-3.1-8b-instant");
        for (String arm : providers) {
            monitor.recordFailure(arm);
            monitor.recordFailure(arm);
            monitor.recordFailure(arm);
        }

        // All open → degraded mode should return all providers rather than empty list
        List<String> result = monitor.filterHealthy(providers);
        assertEquals(2, result.size(), "Should fall back to full list in degraded mode");
    }

    @Test
    void testLatencyTrackingWithEma() {
        String arm = "gemini:gemini-2.5-flash";
        monitor.recordSuccess(arm, 300L);
        monitor.recordSuccess(arm, 200L);
        monitor.recordSuccess(arm, 100L);

        double avg = monitor.getStatus(arm).getAvgLatencyMs();
        assertTrue(avg > 100 && avg < 300, "EMA latency should be between 100ms and 300ms, was: " + avg);
    }
}
