package com.llm.nexusai_gateway.Benchmark;

import com.llm.nexusai_gateway.Routing.RoutingPolicy;

/**
 * A single synthetic test scenario for the benchmarking suite (Priority 11).
 *
 * Each scenario declares:
 *  - A descriptive name (used in reports)
 *  - The prompt message
 *  - Optional tenant, routing policy, and pipeline overrides
 *  - Expected outcome tags for pass/fail classification
 */
public record BenchmarkScenario(
    String name,
    String message,
    String tenantId,
    RoutingPolicy routingPolicy,
    String pipelineName,
    ExpectedOutcome expectedOutcome
) {

    public enum ExpectedOutcome {
        /** Pipeline should complete and return a valid LLM response */
        SUCCESS,
        /** PolicyAgent should block this request (jailbreak / PII / budget) */
        BLOCKED,
        /** Pipeline should complete but with RAG context retrieved */
        SUCCESS_WITH_RAG,
        /** Routing should use cheapest model */
        SUCCESS_LOWEST_COST,
        /** Any outcome is acceptable (smoke test) */
        ANY
    }

    // -----------------------------------------------------------------------
    // Convenience factory methods for common scenario shapes
    // -----------------------------------------------------------------------

    public static BenchmarkScenario success(String name, String message) {
        return new BenchmarkScenario(name, message, null, null, null, ExpectedOutcome.SUCCESS);
    }

    public static BenchmarkScenario withRag(String name, String message) {
        return new BenchmarkScenario(name, message, null, null, null, ExpectedOutcome.SUCCESS_WITH_RAG);
    }

    public static BenchmarkScenario blocked(String name, String message) {
        return new BenchmarkScenario(name, message, null, null, null, ExpectedOutcome.BLOCKED);
    }

    public static BenchmarkScenario tenant(String name, String message, String tenantId) {
        return new BenchmarkScenario(name, message, tenantId, null, null, ExpectedOutcome.SUCCESS);
    }

    public static BenchmarkScenario policy(String name, String message, RoutingPolicy routingPolicy) {
        return new BenchmarkScenario(name, message, null, routingPolicy, null, ExpectedOutcome.SUCCESS_LOWEST_COST);
    }
}
