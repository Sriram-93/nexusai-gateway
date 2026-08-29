package com.llm.nexusai_gateway.Service;

import com.llm.nexusai_gateway.Context.ContextExtractor;
import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Decision.ExplainedDecision;
import com.llm.nexusai_gateway.Decision.RoutingEngineManager;
import com.llm.nexusai_gateway.Decision.RoutingStrategy;
import com.llm.nexusai_gateway.Evaluation.QualityEvaluator;
import com.llm.nexusai_gateway.Evaluation.QualityScore;
import com.llm.nexusai_gateway.Governance.BudgetService;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.ChatResponse;
import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Policy.PolicyFilter;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import com.llm.nexusai_gateway.Provider.ProviderResponse;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Reward.RewardCalculator;
import com.llm.nexusai_gateway.Telemetry.RequestTracingService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AEDF Orchestration Service — implements the complete closed-loop pipeline.
 *
 * Request → Context Extraction → Policy Filter → Adaptive Decision Engine
 * → Provider Execution → Quality Evaluation → Reward Calculation
 * → Reputation Update → Online Learning Update → Future Requests
 *
 * This is the heart of the NexusAI research contribution (Doc 06 Novelty 1).
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private final ContextExtractor contextExtractor;
    private final PolicyFilter policyFilter;
    private final RoutingEngineManager decisionEngine;
    private final ProviderRegistry providerRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final QualityEvaluator qualityEvaluator;
    private final ModelRegistry modelRegistry;
    private final RewardCalculator rewardCalculator;
    private final ReputationService reputationService;
    private final LoggingService loggingService;
    private final ResponseCacheService responseCacheService;
    private final RateLimitingService rateLimitingService;
    private final BudgetService budgetService;
    private final RequestTracingService tracingService;

    // Request collapsing for cache stampede protection (existing pattern, kept)
    private final ConcurrentHashMap<String, Mono<ProviderResponse>> inFlightRequests = new ConcurrentHashMap<>();

    public ChatOrchestrationService(
            ContextExtractor contextExtractor,
            PolicyFilter policyFilter,
            RoutingEngineManager decisionEngine,
            ProviderRegistry providerRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            QualityEvaluator qualityEvaluator,
            RewardCalculator rewardCalculator,
            ReputationService reputationService,
            LoggingService loggingService,
            ResponseCacheService responseCacheService,
            RateLimitingService rateLimitingService,
            ModelRegistry modelRegistry,
            BudgetService budgetService,
            RequestTracingService tracingService) {
        this.contextExtractor = contextExtractor;
        this.policyFilter = policyFilter;
        this.decisionEngine = decisionEngine;
        this.providerRegistry = providerRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.qualityEvaluator = qualityEvaluator;
        this.rewardCalculator = rewardCalculator;
        this.reputationService = reputationService;
        this.loggingService = loggingService;
        this.responseCacheService = responseCacheService;
        this.rateLimitingService = rateLimitingService;
        this.modelRegistry = modelRegistry;
        this.budgetService = budgetService;
        this.tracingService = tracingService;
    }

    /**
     * Execute the full AEDF pipeline for a chat request.
     */
    public Mono<ChatResponse> process(ChatRequest request) {
        long start = System.currentTimeMillis();
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";

        // Step 1: Context Extraction
        return contextExtractor.extract(request).flatMap(context -> {

        String tenantId = context != null && context.tenantId() != null ? context.tenantId() : "global";

        // Phase 4: Budget Enforcement — check cap before touching any provider
        BudgetService.BudgetCheckResult budgetCheck = budgetService.checkBudgetAllowed("ORGANIZATION", tenantId);
        if (!budgetCheck.allowed()) {
            tracingService.traceBudgetEnforcement(tenantId, userId, budgetCheck.currentDailySpendUsd(), budgetCheck.dailyCapUsd());
            return Mono.just(new ChatResponse(
                "Error: " + budgetCheck.message() + " — Daily spend $"
                    + String.format("%.2f", budgetCheck.currentDailySpendUsd()) + " of $"
                    + String.format("%.2f", budgetCheck.dailyCapUsd()) + " cap.",
                "system (budget-enforcer)", 0));
        }
        // Emit 80% warning trace asynchronously (non-blocking)
        if (budgetCheck.is80PercentWarningTriggered()) {
            log.warn("Budget 80% threshold reached for tenant={}: ${}/{}", tenantId,
                String.format("%.2f", budgetCheck.currentDailySpendUsd()),
                String.format("%.2f", budgetCheck.dailyCapUsd()));
        }

        // Phase 5: Trace incoming request (PII-redacted, async, non-blocking)
        tracingService.traceGatewayRequest(tenantId, userId, request.getMessage(),
            request.getModel() != null ? request.getModel() : "auto");

        // Step 2: Build eligible arm list from the registry — no hardcoded models.
        List<String> allProviders = modelRegistry.getEnabledArmKeys();

        List<String> eligibleProviders = policyFilter.filter(allProviders, context);

        if (eligibleProviders.isEmpty()) {
            return Mono.just(new ChatResponse("Error: No configured or allowed providers available. Please connect an AI provider in the Provider Hub.", "system", System.currentTimeMillis() - start));
        }

        // Step 3: Adaptive Decision or Manual Override
        ExplainedDecision decision;
        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            String reqProv = request.getProvider().toLowerCase();
            if ("rule_based".equals(reqProv)) {
                // Per-request override: temporarily route via rule-based (does not change global state)
                decision = new com.llm.nexusai_gateway.Decision.RuleBasedDecisionEngine(reputationService).select(context, eligibleProviders);
            } else if ("weighted".equals(reqProv)) {
                // Per-request override: switch to WEIGHTED then select (no hardcoded weights)
                decisionEngine.switchStrategy(RoutingStrategy.WEIGHTED, null);
                decision = decisionEngine.select(context, eligibleProviders);
            } else if ("adaptive".equals(reqProv)) {
                decision = decisionEngine.select(context, eligibleProviders);
            } else {
                // Static override
                String reqModel = request.getModel();
                if (reqModel == null || reqModel.isBlank()) {
                    String prefix = reqProv + ":";
                    reqModel = modelRegistry.getEnabledArmKeys().stream()
                        .filter(arm -> arm.toLowerCase().startsWith(prefix))
                        .map(arm -> arm.substring(prefix.length()))
                        .findFirst()
                        .orElse("default");
                }
                double health = 1.0;
                double quality = 0.5;
                double latency = 1000.0;
                String fullArmKey = reqProv + ":" + reqModel;
                if (reputationService.get(fullArmKey) != null) {
                    health = reputationService.get(fullArmKey).getHealthScore();
                    quality = reputationService.get(fullArmKey).getAvgQuality();
                    latency = reputationService.get(fullArmKey).getAvgLatencyMs();
                }
                decision = new ExplainedDecision(
                    reqProv, reqModel, health, quality, latency, health,
                    "Manual UI Override", java.util.Map.of(fullArmKey, health), com.llm.nexusai_gateway.Decision.RoutingStrategy.STATIC
                );
            }
        } else {
            decision = decisionEngine.select(context, eligibleProviders);
        }

        log.info("AEDF Decision: provider={}, model={}, strategy={}, reason={}",
                 decision.selectedProvider(), decision.selectedModel(), decision.strategy(), decision.reason());

        // Phase 5: Trace routing decision (async)
        tracingService.traceRoutingDecision(tenantId, userId,
            decision.selectedProvider() + ":" + decision.selectedModel(),
            decision.strategy().name(), decision.reason());

        LlmProvider provider = providerRegistry.getProviderWithTenantKey(
            decision.selectedProvider(),
            context != null ? context.tenantId() : null
        );
        if (provider == null) {
            long latency = System.currentTimeMillis() - start;
            return Mono.just(new ChatResponse(
                "Error: Provider '" + decision.selectedProvider() + "' not available.",
                decision.selectedProvider(), latency));
        }

        String fullArmKey = (decision.selectedProvider() + ":" + decision.selectedModel()).toLowerCase();
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(fullArmKey);

        // Step 4: Rate Limit → Cache Check → Provider Execution
        return rateLimitingService.checkRateLimits(request, decision.selectedProvider())
            .then(responseCacheService.getCachedResponse(decision.selectedModel(), request.getMessage()))
            .flatMap(cached -> {
                long latency = System.currentTimeMillis() - start;
                ChatResponse cacheResponse = new ChatResponse(cached,
                    decision.selectedProvider() + " (" + decision.selectedModel() + " - CACHE HIT)", latency);
                cacheResponse.setActiveEngine(decision.strategy().name());
                cacheResponse.setRoutingReason(decision.reason() + " [CACHE HIT]");
                cacheResponse.setArmScores(decision.armScores());
                return Mono.just(cacheResponse);
            })
            .switchIfEmpty(
                // Cache MISS → Execute provider call
                getCollapsedChat(provider, decision.selectedProvider(), request.getMessage(), decision.selectedModel(), cb)
                    .flatMap(response -> responseCacheService.cacheResponse(
                        decision.selectedModel(), request.getMessage(), response.content())
                        .thenReturn(response))
                    .flatMap(response -> {
                        long latency = System.currentTimeMillis() - start;
                        double costUsd = estimateCost(decision.selectedProvider(), decision.selectedModel(),
                                                      response.inputTokens(), response.outputTokens());

                        // Step 5-8: THE CLOSED LOOP — Quality → Reward → Reputation → Learning
                        return closeFeedbackLoopWithReward(context, decision,
                            request.getMessage(), response.content(), latency, costUsd, true)
                            .map(reward -> {
                                logToDb(context, request, response.content(), decision, latency, "SUCCESS",
                                        response.inputTokens(), response.outputTokens());

                                // Phase 4: Record actual spend in Budget ledger (async-safe, non-blocking)
                                if (costUsd > 0) {
                                    budgetService.recordSpend("ORGANIZATION", tenantId, costUsd);
                                }

                                ChatResponse chatResponse = new ChatResponse(response.content(),
                                    decision.selectedProvider() + " (" + decision.selectedModel() + ")", latency);
                                chatResponse.setActiveEngine(decision.strategy().name());
                                chatResponse.setRoutingReason(decision.reason());
                                chatResponse.setRewardScore(reward);
                                chatResponse.setArmScores(decision.armScores());
                                return chatResponse;
                            });
                    })
                    .onErrorResume(e -> handleFailureWithFallback(e, request, context, decision, start, tenantId, userId))
            );
        });
    }

    /**
     * THE CLOSED FEEDBACK LOOP — this is what makes NexusAI an AEDF, not a proxy.
     *
     * Doc 05 Gap 5: "Develop a closed-loop architecture where every completed
     * request contributes to future decision quality."
     *
     * Returns the computed reward so it can be included in the ChatResponse (Improvement 2).
     */
    private Mono<Double> closeFeedbackLoopWithReward(RequestContext context, ExplainedDecision decision,
                                               String prompt, String response, long latencyMs,
                                               double costUsd, boolean success) {
        // Step 5: Quality Evaluation (heuristic baseline — LLM-as-Judge is Phase 2)
        return qualityEvaluator.evaluate(prompt, response, context.taskCategory())
            .map(quality -> {
                // Step 6: Reward Calculation (normalized, all inputs in [0,1])
                double reward = rewardCalculator.calculate(quality, latencyMs, costUsd, success);
                double[] rewardComponents = rewardCalculator.calculateComponents(quality, latencyMs, costUsd, success);

                // Construct the composite arm key (provider:model)
                String armKey = decision.selectedProvider() + ":" + decision.selectedModel();

                // Step 7: Reputation Update (read-only for observability — Option A per Fix 2)
                reputationService.update(armKey, quality.compositeScore(), latencyMs, costUsd, success);

                // Step 8: Online Learning Update (only ADAPTIVE/FEDERATED engines actually learn from reward)
                decisionEngine.updateWithComponents(context, armKey, reward, rewardComponents);

                log.info("Feedback loop closed: arm={}, quality={:.3f}, reward={:.4f}, latency={}ms",
                         armKey, quality.compositeScore(), reward, latencyMs);
                return reward;
            });
    }

    /** Backward-compat wrapper for cache-hit path (reward not needed in response). */
    private void closeFeedbackLoop(RequestContext context, ExplainedDecision decision,
                                   String prompt, String response, long latencyMs,
                                   double costUsd, boolean success) {
        closeFeedbackLoopWithReward(context, decision, prompt, response, latencyMs, costUsd, success).subscribe();
    }

    /**
     * Phase 6: Multi-provider fallback with full tracing.
     * Handles circuit-breaker OPENs and transient provider failures.
     * Records failure in reputation, deprioritizes the arm, and cascades
     * to the next-best available candidate scored by the decision engine.
     */
    private Mono<ChatResponse> handleFailureWithFallback(Throwable error, ChatRequest request,
                                                          RequestContext context,
                                                          ExplainedDecision originalDecision, long start,
                                                          String tenantId, String userId) {
        boolean isCircuitOpen = error instanceof CallNotPermittedException;
        String failReason = isCircuitOpen ? "Circuit Breaker OPEN" : error.getMessage();

        String failedArmKey = originalDecision.selectedProvider() + ":" + originalDecision.selectedModel();
        log.warn("[FALLBACK] Provider {} failed: {}", failedArmKey, failReason);

        // Phase 5: Trace the failure event (async)
        if (isCircuitOpen) {
            tracingService.traceCircuitBreakerOpen(tenantId, failedArmKey);
        }

        // Record failure in reputation & learning engine
        reputationService.update(failedArmKey, 0.0, 0, 0.0, false);
        decisionEngine.updateWithComponents(context, failedArmKey, 0.0, new double[]{0.0, 0.0, 0.0, 0.0});

        // Build remaining candidates (excluding the failed arm)
        List<String> remaining = originalDecision.armScores().keySet().stream()
            .filter(p -> !p.equals(failedArmKey))
            .collect(Collectors.toList());

        if (!remaining.isEmpty()) {
            ExplainedDecision fallbackDecision = decisionEngine.select(context, remaining);
            String fallbackArmKey = (fallbackDecision.selectedProvider() + ":" + fallbackDecision.selectedModel()).toLowerCase();

            // Phase 5: Trace fallback cascade event
            tracingService.traceFallback(tenantId, userId, failedArmKey, fallbackArmKey, failReason);

            LlmProvider tenantFallbackProvider = providerRegistry.getProviderWithTenantKey(
                fallbackDecision.selectedProvider(),
                context != null ? context.tenantId() : null
            );
            if (tenantFallbackProvider == null) {
                tenantFallbackProvider = providerRegistry.getProvider(fallbackDecision.selectedProvider());
            }

            if (tenantFallbackProvider != null) {
                CircuitBreaker fallbackCb = circuitBreakerRegistry.circuitBreaker(fallbackArmKey);
                final LlmProvider finalProvider = tenantFallbackProvider;

                return getCollapsedChat(finalProvider, fallbackDecision.selectedProvider(),
                                        request.getMessage(), fallbackDecision.selectedModel(), fallbackCb)
                    .flatMap(response -> {
                        long latency = System.currentTimeMillis() - start;
                        double costUsd = estimateCost(fallbackDecision.selectedProvider(),
                            fallbackDecision.selectedModel(), response.inputTokens(), response.outputTokens());

                        return closeFeedbackLoopWithReward(context, fallbackDecision,
                            request.getMessage(), response.content(), latency, costUsd, true)
                            .map(reward -> {
                                logToDb(context, request, response.content(), fallbackDecision, latency,
                                        "FALLBACK_RECOVERY", response.inputTokens(), response.outputTokens());

                                // Phase 4: Record fallback cost in budget ledger
                                if (costUsd > 0) budgetService.recordSpend("ORGANIZATION", tenantId, costUsd);

                                ChatResponse chatResponse = new ChatResponse(response.content(),
                                    originalDecision.selectedProvider() + " (" + failReason +
                                    " → Fallback: " + fallbackDecision.selectedProvider() + ")", latency);
                                chatResponse.setActiveEngine(fallbackDecision.strategy().name());
                                chatResponse.setRoutingReason("FALLBACK: " + originalDecision.selectedProvider()
                                    + " failed → " + fallbackDecision.reason());
                                chatResponse.setRewardScore(reward);
                                chatResponse.setArmScores(fallbackDecision.armScores());
                                return chatResponse;
                            });
                    })
                    .onErrorResume(err -> {
                        log.warn("[FALLBACK] Candidate {} failed ({}), cascading to remaining candidates...", fallbackArmKey, err.getMessage());
                        return handleFailureWithFallback(err, request, context, fallbackDecision, start, tenantId, userId);
                    });
            }
        }

        long latency = System.currentTimeMillis() - start;
        log.error("[FALLBACK] All fallback candidate providers exhausted for tenant={}", tenantId);
        return Mono.just(new ChatResponse("Error: All available providers failed. Last error: " + failReason,
            originalDecision.selectedProvider() + " (Failed)", latency));
    }

    private Mono<ProviderResponse> getCollapsedChat(LlmProvider provider, String providerSlug, String message,
                                                     String model, CircuitBreaker cb) {
        String collapseKey = model + ":" + message;
        return inFlightRequests.computeIfAbsent(collapseKey, key ->
            provider.chat(providerSlug, message, model)
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .doFinally(signal -> inFlightRequests.remove(collapseKey))
                .share()
        );
    }

    private void logToDb(RequestContext context, ChatRequest request, String answer, ExplainedDecision decision,
                         long latencyMs, String status, int inputTokens, int outputTokens) {
        String tenantId = (context != null && context.tenantId() != null) ? context.tenantId() : "default";
        RequestLog logEntry = new RequestLog(
            tenantId,
            request.getUserId() != null ? request.getUserId() : "anonymous",
            request.getMessage(), answer,
            decision.selectedProvider(), decision.selectedModel(),
            decision.strategy().name(),
            latencyMs, null, null, null, status
        );
        loggingService.saveLog(logEntry, inputTokens, outputTokens)
            .doOnError(err -> log.error("Failed to write request log: {}", err.getMessage()))
            .subscribe();
    }

    private double estimateCost(String provider, String model, int inputTokens, int outputTokens) {
        // Delegate entirely to ModelRegistry — no hardcoded pricing here.
        String armKey = provider + ":" + model;
        return modelRegistry.computeCostUsd(armKey, inputTokens, outputTokens);
    }
}
