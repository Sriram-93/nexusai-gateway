package com.llm.nexusai_gateway.Benchmark;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable result report for a completed benchmark suite run (Priority 11).
 */
public record BenchmarkReport(
    Instant runAt,
    int totalScenarios,
    int passed,
    int failed,
    int skipped,
    long totalDurationMs,
    double avgLatencyMs,
    double p95LatencyMs,
    List<ScenarioResult> results
) {

    /**
     * Per-scenario execution result.
     */
    public record ScenarioResult(
        String scenarioName,
        BenchmarkScenario.ExpectedOutcome expectedOutcome,
        String actualStatus,        // "success" | "blocked" | "error"
        boolean passed,
        long latencyMs,
        String providerSelected,
        String modelSelected,
        String routingStrategy,
        boolean ragHit,
        int ragChunks,
        String notes
    ) {}

    /** Compute summary stats from a list of latencies */
    public static double computeP95(List<Long> latencies) {
        if (latencies.isEmpty()) return 0.0;
        List<Long> sorted = latencies.stream().sorted().toList();
        int idx = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, idx));
    }

    public Map<String, Object> toSummaryMap() {
        return Map.of(
            "runAt",          runAt.toString(),
            "totalScenarios", totalScenarios,
            "passed",         passed,
            "failed",         failed,
            "skipped",        skipped,
            "totalDurationMs", totalDurationMs,
            "avgLatencyMs",   Math.round(avgLatencyMs),
            "p95LatencyMs",   Math.round(p95LatencyMs),
            "passRate",       String.format("%.1f%%", (passed * 100.0) / Math.max(1, totalScenarios))
        );
    }
}
