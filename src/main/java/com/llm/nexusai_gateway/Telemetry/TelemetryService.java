package com.llm.nexusai_gateway.Telemetry;

import com.llm.nexusai_gateway.Agent.AgentContext;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Central Telemetry Service for NexusAI Gateway (Priority 9).
 *
 * Registers and updates the following Micrometer metrics (scraped by Prometheus):
 *
 *  nexusai_pipeline_requests_total{pipeline,status}       — Counter: requests per pipeline and outcome
 *  nexusai_pipeline_duration_ms{pipeline}                 — Timer:   end-to-end pipeline latency histogram
 *  nexusai_agent_duration_ms{agent}                       — Timer:   per-agent execution latency
 *  nexusai_llm_tokens_total{provider,model,type}          — Counter: input/output token consumption
 *  nexusai_rag_retrievals_total{result}                   — Counter: RAG hits vs misses
 *  nexusai_routing_decisions_total{provider,model,policy} — Counter: routing decisions per arm and policy
 *  nexusai_quality_score{pipeline}                        — Gauge:   last quality score (rolling)
 *  nexusai_circuit_breaker_state{provider_arm}            — Gauge:   0=CLOSED, 1=OPEN, 2=HALF_OPEN
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final MeterRegistry registry;

    // Rolling quality score per pipeline (for Gauge)
    private final Map<String, Double> qualityScores = new ConcurrentHashMap<>();

    public TelemetryService(MeterRegistry registry) {
        this.registry = registry;
    }

    // -----------------------------------------------------------------------
    // Pipeline-level metrics
    // -----------------------------------------------------------------------

    public void recordPipelineRequest(String pipelineName, String status) {
        Counter.builder("nexusai.pipeline.requests")
            .description("Total pipeline requests by pipeline and status")
            .tag("pipeline", pipelineName)
            .tag("status", status)
            .register(registry)
            .increment();
    }

    public void recordPipelineDuration(String pipelineName, long durationMs) {
        Timer.builder("nexusai.pipeline.duration")
            .description("End-to-end pipeline execution duration")
            .tag("pipeline", pipelineName)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // -----------------------------------------------------------------------
    // Agent-level metrics
    // -----------------------------------------------------------------------

    public void recordAgentDuration(String agentName, long durationMs) {
        Timer.builder("nexusai.agent.duration")
            .description("Per-agent execution duration")
            .tag("agent", agentName)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // -----------------------------------------------------------------------
    // LLM / Token metrics
    // -----------------------------------------------------------------------

    public void recordTokenUsage(String provider, String model, int inputTokens, int outputTokens) {
        Counter.builder("nexusai.llm.tokens")
            .description("LLM token consumption by provider, model, and type")
            .tag("provider", provider)
            .tag("model", model)
            .tag("type", "input")
            .register(registry)
            .increment(inputTokens);

        Counter.builder("nexusai.llm.tokens")
            .description("LLM token consumption by provider, model, and type")
            .tag("provider", provider)
            .tag("model", model)
            .tag("type", "output")
            .register(registry)
            .increment(outputTokens);
    }

    // -----------------------------------------------------------------------
    // RAG metrics
    // -----------------------------------------------------------------------

    public void recordRagRetrieval(boolean hit, int chunksRetrieved) {
        Counter.builder("nexusai.rag.retrievals")
            .description("RAG semantic retrievals by result")
            .tag("result", hit ? "hit" : "miss")
            .register(registry)
            .increment();

        if (hit) {
            DistributionSummary.builder("nexusai.rag.chunks.retrieved")
                .description("Number of chunks retrieved per RAG hit")
                .register(registry)
                .record(chunksRetrieved);
        }
    }

    // -----------------------------------------------------------------------
    // Routing metrics
    // -----------------------------------------------------------------------

    public void recordRoutingDecision(String provider, String model, String policy) {
        Counter.builder("nexusai.routing.decisions")
            .description("Routing decisions per provider arm and policy")
            .tag("provider", provider)
            .tag("model", model)
            .tag("policy", policy)
            .register(registry)
            .increment();
    }

    // -----------------------------------------------------------------------
    // Quality metrics
    // -----------------------------------------------------------------------

    public void recordQualityScore(String pipelineName, double score) {
        qualityScores.put(pipelineName, score);
        Gauge.builder("nexusai.quality.score", qualityScores, m -> m.getOrDefault(pipelineName, 0.0))
            .description("Last observed quality score per pipeline")
            .tag("pipeline", pipelineName)
            .register(registry);
    }

    // -----------------------------------------------------------------------
    // Circuit breaker metrics
    // -----------------------------------------------------------------------

    public void recordCircuitBreakerState(String providerArm,
                                          com.llm.nexusai_gateway.Health.CircuitBreakerState state) {
        int numericState = switch (state) {
            case CLOSED    -> 0;
            case OPEN      -> 1;
            case HALF_OPEN -> 2;
        };

        Gauge.builder("nexusai.circuit.breaker.state",
                      new double[]{numericState}, arr -> arr[0])
            .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
            .tag("provider_arm", providerArm)
            .register(registry);
    }

    // -----------------------------------------------------------------------
    // Convenience: record full pipeline completion from AgentContext
    // -----------------------------------------------------------------------

    public void recordPipelineCompletion(AgentContext ctx, String pipelineName) {
        long totalMs = ctx.elapsedMs();
        String status = ctx.isTerminated() ? "blocked" : "success";

        recordPipelineRequest(pipelineName, status);
        recordPipelineDuration(pipelineName, totalMs);

        // Per-agent timings
        ctx.getAgentTimings().forEach(this::recordAgentDuration);

        // Routing decision
        if (ctx.getRoutingResult() != null) {
            recordRoutingDecision(
                ctx.getRoutingResult().getProvider(),
                ctx.getRoutingResult().getModel(),
                ctx.getRoutingResult().getStrategy()
            );
        }

        // RAG retrieval
        if (ctx.getContextResult() != null) {
            boolean ragHit = ctx.getContextResult().getRelevantDocuments() != null
                          && !ctx.getContextResult().getRelevantDocuments().isEmpty();
            int chunks = ragHit ? ctx.getContextResult().getRelevantDocuments().size() : 0;
            recordRagRetrieval(ragHit, chunks);
        }

        // Quality score
        if (ctx.getQualityResult() != null) {
            recordQualityScore(pipelineName, ctx.getQualityResult().getCompositeScore());
        }

        log.debug("TelemetryService: Recorded metrics for pipeline='{}' status='{}' duration={}ms",
                  pipelineName, status, totalMs);
    }
}
