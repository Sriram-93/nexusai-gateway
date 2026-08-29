package com.llm.nexusai_gateway.Service;

import com.llm.nexusai_gateway.Context.ContextExtractor;
import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Decision.ExplainedDecision;
import com.llm.nexusai_gateway.Decision.RoutingEngineManager;
import com.llm.nexusai_gateway.Governance.BudgetService;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Policy.PolicyFilter;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import com.llm.nexusai_gateway.Provider.StreamingLlmProvider;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Telemetry.RequestTracingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Streaming Chat Orchestration Service — Phase 7.
 *
 * Mirrors the core AEDF pipeline but returns a Flux<String> of raw token deltas
 * instead of a buffered Mono<ChatResponse>.
 *
 * The sequence:
 *   1. Budget enforcement (synchronous pre-check)
 *   2. Context extraction → policy filter → decision engine
 *   3. Provider streaming call (StreamingLlmProvider) or fallback simulation
 *   4. Each token forwarded immediately to the SSE wire
 *   5. Budget spend recorded after stream completes
 *   6. Async tracing: GATEWAY_REQUEST + ROUTING_DECISION events fired
 */
@Service
public class StreamingOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(StreamingOrchestrationService.class);

    private final ContextExtractor contextExtractor;
    private final PolicyFilter policyFilter;
    private final RoutingEngineManager decisionEngine;
    private final ProviderRegistry providerRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ModelRegistry modelRegistry;
    private final ReputationService reputationService;
    private final BudgetService budgetService;
    private final RequestTracingService tracingService;
    private final LoggingService loggingService;

    public StreamingOrchestrationService(ContextExtractor contextExtractor,
                                          PolicyFilter policyFilter,
                                          RoutingEngineManager decisionEngine,
                                          ProviderRegistry providerRegistry,
                                          CircuitBreakerRegistry circuitBreakerRegistry,
                                          ModelRegistry modelRegistry,
                                          ReputationService reputationService,
                                          BudgetService budgetService,
                                          RequestTracingService tracingService,
                                          LoggingService loggingService) {
        this.contextExtractor = contextExtractor;
        this.policyFilter = policyFilter;
        this.decisionEngine = decisionEngine;
        this.providerRegistry = providerRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.modelRegistry = modelRegistry;
        this.reputationService = reputationService;
        this.budgetService = budgetService;
        this.tracingService = tracingService;
        this.loggingService = loggingService;
    }

    /**
     * Stream token deltas for a given request.
     * Returns Flux<String> where each item is a raw delta content string.
     * The caller (controller) wraps these into SSE data: lines.
     */
    public Flux<String> streamTokens(ChatRequest request) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";

        return contextExtractor.extract(request).flatMapMany(context -> {
            String tenantId = context != null && context.tenantId() != null ? context.tenantId() : "global";

            // Budget enforcement — same pre-check as blocking path
            BudgetService.BudgetCheckResult budgetCheck = budgetService.checkBudgetAllowed("ORGANIZATION", tenantId);
            if (!budgetCheck.allowed()) {
                tracingService.traceBudgetEnforcement(tenantId, userId,
                    budgetCheck.currentDailySpendUsd(), budgetCheck.dailyCapUsd());
                return Flux.just("[BUDGET_EXCEEDED] " + budgetCheck.message());
            }

            // Async tracing
            tracingService.traceGatewayRequest(tenantId, userId,
                request.getMessage(), request.getModel() != null ? request.getModel() : "auto");

            List<String> allProviders = modelRegistry.getEnabledArmKeys();
            List<String> eligibleProviders = policyFilter.filter(allProviders, context);

            if (eligibleProviders.isEmpty()) {
                return Flux.just("[ERROR] No providers available. Connect a provider in the Provider Hub.");
            }

            ExplainedDecision decision = decisionEngine.select(context, eligibleProviders);

            tracingService.traceRoutingDecision(tenantId, userId,
                decision.selectedProvider() + ":" + decision.selectedModel(),
                decision.strategy().name(), decision.reason());

            log.info("[STREAM] Routing to arm={} strategy={}", 
                decision.selectedProvider() + ":" + decision.selectedModel(), decision.strategy());

            LlmProvider provider = providerRegistry.getProviderWithTenantKey(
                decision.selectedProvider(), tenantId.equals("global") ? null : tenantId);

            if (provider == null) {
                return Flux.just("[ERROR] Provider '" + decision.selectedProvider() + "' not available.");
            }

            String runtimeKey = providerRegistry.resolveRuntimeKey(decision.selectedProvider(),
                tenantId.equals("global") ? null : tenantId);

            // Use streaming provider if available, otherwise simulate streaming from buffered response
            long start = System.currentTimeMillis();
            if (provider instanceof StreamingLlmProvider streamingProvider) {
                StringBuilder accumulated = new StringBuilder();
                return streamingProvider
                    .streamChat(decision.selectedProvider(), request.getMessage(), decision.selectedModel(), runtimeKey)
                    .doOnNext(token -> accumulated.append(token))
                    .doOnComplete(() -> {
                        long latency = System.currentTimeMillis() - start;
                        int inputTokens = Math.max(1, request.getMessage().length() / 4);
                        int outputTokens = Math.max(1, accumulated.length() / 4);
                        double estCost = modelRegistry.computeCostUsd(
                            decision.selectedProvider() + ":" + decision.selectedModel(), inputTokens, outputTokens);
                        if (estCost > 0) budgetService.recordSpend("ORGANIZATION", tenantId, estCost);
                        reputationService.update(
                            decision.selectedProvider() + ":" + decision.selectedModel(),
                            0.85, latency, estCost, true);
                        logToDb(context, request, accumulated.toString(), decision, latency, "SUCCESS", inputTokens, outputTokens);
                    })
                    .onErrorResume(err -> {
                        log.warn("[STREAM] Provider {} streaming error: {}", decision.selectedProvider(), err.getMessage());
                        return Flux.just("[STREAM_ERROR] " + err.getMessage());
                    });
            } else {
                // Fallback: buffer full response and simulate word-by-word streaming
                return provider.chatWithKey(decision.selectedProvider(), request.getMessage(),
                        decision.selectedModel(), runtimeKey)
                    .flatMapMany(resp -> simulateWordStream(resp.content())
                        .doOnComplete(() -> {
                            long latency = System.currentTimeMillis() - start;
                            logToDb(context, request, resp.content(), decision, latency, "SUCCESS", resp.inputTokens(), resp.outputTokens());
                            log.debug("[STREAM] Simulated stream complete for arm={}", decision.selectedProvider());
                        })
                    );
            }
        });
    }

    private void logToDb(RequestContext context, ChatRequest request, String answer, ExplainedDecision decision,
                         long latencyMs, String status, int inputTokens, int outputTokens) {
        String tenantId = (context != null && context.tenantId() != null) ? context.tenantId() : "default";
        com.llm.nexusai_gateway.Model.RequestLog logEntry = new com.llm.nexusai_gateway.Model.RequestLog(
            tenantId,
            request.getUserId() != null ? request.getUserId() : "anonymous",
            request.getMessage(), answer,
            decision.selectedProvider(), decision.selectedModel(),
            decision.strategy().name(),
            latencyMs, null, null, null, status
        );
        loggingService.saveLog(logEntry, inputTokens, outputTokens)
            .doOnError(err -> log.error("Failed to write streaming request log: {}", err.getMessage()))
            .subscribe();
    }

    /**
     * Simulates word-by-word streaming for providers that don't natively support SSE.
     * Splits the buffered response into word chunks and emits them with small gaps.
     */
    private Flux<String> simulateWordStream(String fullContent) {
        if (fullContent == null || fullContent.isBlank()) return Flux.empty();

        // Split into word-level chunks to approximate token streaming
        String[] words = fullContent.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(words)
            .filter(w -> !w.isEmpty());
    }
}
