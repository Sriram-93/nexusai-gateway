package com.llm.nexusai_gateway.Benchmark;

import com.llm.nexusai_gateway.Agent.AgentChatResponse;
import com.llm.nexusai_gateway.Agent.AgentOrchestrationService;
import com.llm.nexusai_gateway.Model.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * Benchmark Runner — executes BenchmarkScenarios against the live pipeline and produces a BenchmarkReport (Priority 11).
 *
 * Design decisions:
 *  - Runs scenarios sequentially (not in parallel) to avoid resource contention skewing latency measurements.
 *  - Classifies each result against the scenario's ExpectedOutcome.
 *  - Collects latency, routing, RAG hit, and scenario pass/fail into a structured BenchmarkReport.
 */
@Service
public class BenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final AgentOrchestrationService orchestrationService;

    public BenchmarkRunner(AgentOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Execute the given scenarios sequentially and produce a full BenchmarkReport.
     */
    public Mono<BenchmarkReport> run(List<BenchmarkScenario> scenarios) {
        Instant startedAt = Instant.now();
        long suiteStart = System.currentTimeMillis();

        return Flux.fromIterable(scenarios)
            .concatMap(this::runScenario)           // sequential execution
            .collectList()
            .map(results -> buildReport(results, startedAt, System.currentTimeMillis() - suiteStart));
    }

    private Mono<BenchmarkReport.ScenarioResult> runScenario(BenchmarkScenario scenario) {
        log.info("BenchmarkRunner → [{}]", scenario.name());
        long start = System.currentTimeMillis();

        ChatRequest request = buildRequest(scenario);

        return orchestrationService.process(request)
            .map(response -> classify(scenario, response, System.currentTimeMillis() - start))
            .onErrorResume(err -> {
                long latency = System.currentTimeMillis() - start;
                log.error("BenchmarkRunner: Scenario '{}' threw exception: {}", scenario.name(), err.getMessage());
                return Mono.just(new BenchmarkReport.ScenarioResult(
                    scenario.name(), scenario.expectedOutcome(),
                    "error", false, latency,
                    "none", "none", "NONE", false, 0,
                    "Exception: " + err.getMessage()
                ));
            });
    }

    private ChatRequest buildRequest(BenchmarkScenario scenario) {
        ChatRequest req = new ChatRequest();
        req.setMessage(scenario.message());
        req.setUserId("benchmark-runner");
        if (scenario.tenantId()     != null) req.setTenantId(scenario.tenantId());
        if (scenario.routingPolicy() != null) req.setRoutingPolicy(scenario.routingPolicy());
        if (scenario.pipelineName() != null) req.setPipelineName(scenario.pipelineName());
        return req;
    }

    private BenchmarkReport.ScenarioResult classify(BenchmarkScenario scenario,
                                                     AgentChatResponse response,
                                                     long latencyMs) {
        // Determine actual outcome
        boolean isBlocked = response.getAnswer() != null
            && (response.getAnswer().startsWith("Request Blocked:")
                || response.getAnswer().contains("Security Threat")
                || response.getAnswer().contains("Compliance Risk")
                || response.getAnswer().contains("Budget limit"));

        String actualStatus = isBlocked ? "blocked" : "success";

        // Determine RAG hit
        boolean ragHit = response.getContext() != null
            && response.getContext().getRelevantDocuments() != null
            && !response.getContext().getRelevantDocuments().isEmpty();
        int ragChunks = ragHit ? response.getContext().getRelevantDocuments().size() : 0;

        // Routing info
        String provider = response.getRouting() != null ? response.getRouting().getProvider() : "none";
        String model    = response.getRouting() != null ? response.getRouting().getModel()    : "none";
        String strategy = response.getRouting() != null ? response.getRouting().getStrategy() : "NONE";

        // Pass/fail classification
        boolean passed = switch (scenario.expectedOutcome()) {
            case SUCCESS           -> !isBlocked;
            case BLOCKED           -> isBlocked;
            case SUCCESS_WITH_RAG  -> !isBlocked && ragHit;
            case SUCCESS_LOWEST_COST -> !isBlocked && ("LOWEST_COST".equals(strategy) || !"none".equals(provider));
            case ANY               -> true;
        };

        String notes = passed ? "OK"
            : String.format("Expected=%s actual=%s", scenario.expectedOutcome(), actualStatus);

        log.info("BenchmarkRunner ← [{}] {} latency={}ms provider={}:{} ragHit={}",
            scenario.name(), passed ? "PASS" : "FAIL", latencyMs, provider, model, ragHit);

        return new BenchmarkReport.ScenarioResult(
            scenario.name(), scenario.expectedOutcome(),
            actualStatus, passed, latencyMs,
            provider, model, strategy,
            ragHit, ragChunks, notes
        );
    }

    private BenchmarkReport buildReport(List<BenchmarkReport.ScenarioResult> results,
                                        Instant runAt, long totalDurationMs) {
        int passed   = (int) results.stream().filter(BenchmarkReport.ScenarioResult::passed).count();
        int failed   = results.size() - passed;
        int skipped  = 0;

        List<Long> latencies = results.stream()
            .map(BenchmarkReport.ScenarioResult::latencyMs)
            .collect(Collectors.toList());

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double p95Latency = BenchmarkReport.computeP95(latencies);

        log.info("BenchmarkRunner: Suite complete — {}/{} passed, avg={}ms, p95={}ms",
            passed, results.size(), Math.round(avgLatency), Math.round(p95Latency));

        return new BenchmarkReport(
            runAt, results.size(), passed, failed, skipped,
            totalDurationMs, avgLatency, p95Latency, results
        );
    }
}
