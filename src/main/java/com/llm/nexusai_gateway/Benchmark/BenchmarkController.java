package com.llm.nexusai_gateway.Benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for on-demand benchmark suite execution (Priority 11).
 *
 *  POST /api/benchmark/run           — run the full standard suite
 *  POST /api/benchmark/run/security  — run only SECURITY scenarios
 *  POST /api/benchmark/run/routing   — run only ROUTING scenarios
 *  POST /api/benchmark/run/rag       — run only RAG scenarios
 *  GET  /api/benchmark/scenarios     — list all available scenario names
 */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkController.class);

    private final BenchmarkRunner runner;
    private final BenchmarkSuite  suite;

    public BenchmarkController(BenchmarkRunner runner, BenchmarkSuite suite) {
        this.runner = runner;
        this.suite  = suite;
    }

    /** Run the full standard benchmark suite */
    @PostMapping("/run")
    public Mono<Map<String, Object>> runFullSuite() {
        log.info("BenchmarkController: Full suite requested");
        List<BenchmarkScenario> scenarios = suite.getStandardSuite();
        return runner.run(scenarios)
            .map(report -> Map.of(
                "summary", report.toSummaryMap(),
                "results", report.results().stream()
                    .map(this::resultToMap)
                    .toList()
            ));
    }

    /** Run only security-related scenarios */
    @PostMapping("/run/security")
    public Mono<Map<String, Object>> runSecuritySuite() {
        List<BenchmarkScenario> filtered = suite.getStandardSuite().stream()
            .filter(s -> s.name().startsWith("SEC-"))
            .toList();
        return runner.run(filtered).map(r -> Map.of("summary", r.toSummaryMap(),
            "results", r.results().stream().map(this::resultToMap).toList()));
    }

    /** Run only RAG scenarios */
    @PostMapping("/run/rag")
    public Mono<Map<String, Object>> runRagSuite() {
        List<BenchmarkScenario> filtered = suite.getStandardSuite().stream()
            .filter(s -> s.name().startsWith("RAG-"))
            .toList();
        return runner.run(filtered).map(r -> Map.of("summary", r.toSummaryMap(),
            "results", r.results().stream().map(this::resultToMap).toList()));
    }

    /** Run only routing-policy scenarios */
    @PostMapping("/run/routing")
    public Mono<Map<String, Object>> runRoutingSuite() {
        List<BenchmarkScenario> filtered = suite.getStandardSuite().stream()
            .filter(s -> s.name().startsWith("ROUTE-"))
            .toList();
        return runner.run(filtered).map(r -> Map.of("summary", r.toSummaryMap(),
            "results", r.results().stream().map(this::resultToMap).toList()));
    }

    /** List all available scenario names and expected outcomes */
    @GetMapping("/scenarios")
    public Mono<List<Map<String, String>>> listScenarios() {
        return Mono.just(suite.getStandardSuite().stream()
            .map(s -> Map.of(
                "name",            s.name(),
                "expectedOutcome", s.expectedOutcome().name(),
                "routingPolicy",   s.routingPolicy()  != null ? s.routingPolicy().name()  : "ADAPTIVE_BANDIT",
                "pipelineName",    s.pipelineName()   != null ? s.pipelineName()          : "auto",
                "tenantId",        s.tenantId()       != null ? s.tenantId()               : "global"
            ))
            .toList());
    }

    private Map<String, Object> resultToMap(BenchmarkReport.ScenarioResult r) {
        return Map.of(
            "scenario",       r.scenarioName(),
            "expected",       r.expectedOutcome().name(),
            "actual",         r.actualStatus(),
            "passed",         r.passed(),
            "latencyMs",      r.latencyMs(),
            "provider",       r.providerSelected(),
            "model",          r.modelSelected(),
            "strategy",       r.routingStrategy(),
            "ragHit",         r.ragHit(),
            "notes",          r.notes()
        );
    }
}
