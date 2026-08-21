package com.llm.nexusai_gateway.Telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryServiceTest {

    private MeterRegistry meterRegistry;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        meterRegistry   = new SimpleMeterRegistry();
        telemetryService = new TelemetryService(meterRegistry);
    }

    @Test
    void testPipelineRequestCounterIncrements() {
        telemetryService.recordPipelineRequest("DEFAULT", "success");
        telemetryService.recordPipelineRequest("DEFAULT", "success");
        telemetryService.recordPipelineRequest("DEFAULT", "blocked");

        double success = meterRegistry.counter("nexusai.pipeline.requests",
            "pipeline", "DEFAULT", "status", "success").count();
        double blocked = meterRegistry.counter("nexusai.pipeline.requests",
            "pipeline", "DEFAULT", "status", "blocked").count();

        assertEquals(2.0, success, 0.001);
        assertEquals(1.0, blocked, 0.001);
    }

    @Test
    void testTokenUsageCounter() {
        telemetryService.recordTokenUsage("gemini", "gemini-2.5-flash", 150, 300);
        telemetryService.recordTokenUsage("gemini", "gemini-2.5-flash", 50,  100);

        double inputTokens = meterRegistry.counter("nexusai.llm.tokens",
            "provider", "gemini", "model", "gemini-2.5-flash", "type", "input").count();
        double outputTokens = meterRegistry.counter("nexusai.llm.tokens",
            "provider", "gemini", "model", "gemini-2.5-flash", "type", "output").count();

        assertEquals(200.0, inputTokens,  0.001);
        assertEquals(400.0, outputTokens, 0.001);
    }

    @Test
    void testRagRetrievalHitCounter() {
        telemetryService.recordRagRetrieval(true, 3);
        telemetryService.recordRagRetrieval(true, 2);
        telemetryService.recordRagRetrieval(false, 0);

        double hits   = meterRegistry.counter("nexusai.rag.retrievals", "result", "hit").count();
        double misses = meterRegistry.counter("nexusai.rag.retrievals", "result", "miss").count();

        assertEquals(2.0, hits,   0.001);
        assertEquals(1.0, misses, 0.001);
    }

    @Test
    void testRoutingDecisionCounter() {
        telemetryService.recordRoutingDecision("gemini", "gemini-2.5-flash", "ADAPTIVE_BANDIT");
        telemetryService.recordRoutingDecision("groq",   "llama-3.1-8b-instant", "LOWEST_COST");

        double bandit = meterRegistry.counter("nexusai.routing.decisions",
            "provider", "gemini", "model", "gemini-2.5-flash", "policy", "ADAPTIVE_BANDIT").count();
        assertEquals(1.0, bandit, 0.001);
    }

    @Test
    void testCircuitBreakerStateGauge() {
        telemetryService.recordCircuitBreakerState(
            "gemini:gemini-3.5-flash",
            com.llm.nexusai_gateway.Health.CircuitBreakerState.OPEN
        );

        double state = meterRegistry.get("nexusai.circuit.breaker.state")
            .tag("provider_arm", "gemini:gemini-3.5-flash")
            .gauge().value();

        assertEquals(1.0, state, 0.001, "OPEN state should be encoded as 1");
    }
}
