package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderConfig;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import com.llm.nexusai_gateway.Provider.RegisteredModel;
import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import com.llm.nexusai_gateway.Security.GatewaySecurityFilter;
import com.llm.nexusai_gateway.Tenant.TenantConfig;
import com.llm.nexusai_gateway.Telemetry.RequestTracingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * LLM-as-a-Judge implementation for Quality Evaluation.
 * This provides mathematically sound ground-truth labels for the LinUCB bandit
 * compared to simplistic heuristic checks.
 *
 * Dynamically selects the fastest available model based on the tenant's API keys
 * and global configurations, rather than hardcoding any specific provider.
 */
@Service
@Primary
public class LlmAsAJudgeEvaluator implements QualityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmAsAJudgeEvaluator.class);
    
    private final ProviderRegistry providerRegistry;
    private final HeuristicQualityEvaluator heuristicFallback;
    private final RegisteredModelRepository registeredModelRepository;
    private final ProviderConfigRepository providerConfigRepository;
    private final RequestTracingService tracingService;

    public LlmAsAJudgeEvaluator(ProviderRegistry providerRegistry, 
                                HeuristicQualityEvaluator heuristicFallback,
                                RegisteredModelRepository registeredModelRepository,
                                ProviderConfigRepository providerConfigRepository,
                                RequestTracingService tracingService) {
        this.providerRegistry = providerRegistry;
        this.heuristicFallback = heuristicFallback;
        this.registeredModelRepository = registeredModelRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.tracingService = tracingService;
    }

    @Override
    public Mono<QualityScore> evaluate(String prompt, String response, TaskCategory taskCategory) {
        if (response == null || response.isBlank()) {
            return Mono.just(QualityScore.of(0.0, 0.0, 0.0));
        }

        return Mono.deferContextual(ctx -> {
            TenantConfig tenant = ctx.getOrDefault(GatewaySecurityFilter.TENANT_CONTEXT_KEY, null);
            String tenantId = tenant != null ? tenant.getTenantId() : "global";

            // 1. Get all active models sorted by fastest latency
            List<RegisteredModel> fastModels = registeredModelRepository.findEnabledOrderByLatencyAsc();
            
            // 2. Get tenant's explicit provider configurations
            List<ProviderConfig> tenantProviders = (tenantId != null && !"global".equals(tenantId)) ? 
                providerConfigRepository.findByTenantIdAndEnabledTrue(tenantId) : Collections.emptyList();

            String providerSlug = null;
            String modelName = null;

            // 3. Find the first fast model that the tenant has permission/keys to use
            for (RegisteredModel model : fastModels) {
                String slug = model.getProviderSlug();
                
                boolean tenantHasKey = tenantProviders.stream()
                    .anyMatch(c -> c.getSlug().equalsIgnoreCase(slug) && c.getApiKey() != null && !c.getApiKey().isBlank());
                
                // If tenant hasn't configured ANY keys, we fallback to globally available providers
                boolean useGlobalFallback = tenantProviders.isEmpty() && (providerRegistry.getProvider(slug) != null);

                if (tenantHasKey || useGlobalFallback) {
                    providerSlug = slug;
                    modelName = model.getModelId();
                    break; // Found the fastest available model for this tenant!
                }
            }

            if (providerSlug == null) {
                log.debug("No external LLM judge provider configured for tenant. Using heuristic quality evaluation.");
                return heuristicFallback.evaluate(prompt, response, taskCategory)
                    .doOnNext(score -> tracingService.traceQualityEvaluation(tenantId, "heuristic", "HEURISTIC",
                        score.compositeScore(), score.completeness(), score.relevance(), score.formatCompliance()));
            }

            LlmProvider judge = providerRegistry.getProvider(providerSlug);
            if (judge == null) {
                return heuristicFallback.evaluate(prompt, response, taskCategory)
                    .doOnNext(score -> tracingService.traceQualityEvaluation(tenantId, "heuristic", "HEURISTIC",
                        score.compositeScore(), score.completeness(), score.relevance(), score.formatCompliance()));
            }

            String gradingPrompt = buildGradingPrompt(prompt, response, taskCategory);

            final String activeProvider = providerSlug;
            final String activeModel = modelName;
            return judge.chat(activeProvider, gradingPrompt, activeModel)
                .timeout(Duration.ofSeconds(5))
                .map(judgeResponse -> parseScores(judgeResponse.content()))
                .doOnNext(score -> {
                    log.info("LLM-as-a-Judge ({}:{}) evaluated composite={:.3f}", activeProvider, activeModel, score.compositeScore());
                    tracingService.traceQualityEvaluation(tenantId, activeProvider + ":" + activeModel, "LLM_JUDGE",
                        score.compositeScore(), score.completeness(), score.relevance(), score.formatCompliance());
                })
                .onErrorResume(err -> {
                    log.warn("LLM-as-a-Judge ({}:{}) failed: {}. Falling back to heuristic evaluation.", activeProvider, activeModel, err.getMessage());
                    return heuristicFallback.evaluate(prompt, response, taskCategory)
                        .doOnNext(score -> tracingService.traceQualityEvaluation(tenantId, "heuristic_fallback", "HEURISTIC_FALLBACK",
                            score.compositeScore(), score.completeness(), score.relevance(), score.formatCompliance()));
                });
        });
    }

    private String buildGradingPrompt(String prompt, String response, TaskCategory category) {
        return """
            You are an expert AI evaluator. Grade the provided LLM response to the user's prompt based on the task category: %s.
            
            Evaluate on 3 dimensions, returning ONLY a comma-separated list of 3 decimals between 0.00 and 1.00. Do not include any other text.
            1. Completeness (Is the answer detailed enough for the task?)
            2. Relevance (Does it directly address the prompt?)
            3. Format Compliance (Does it structure the answer well for the given task?)
            
            Example Output:
            0.95,0.80,1.00
            
            User Prompt:
            %s
            
            LLM Response:
            %s
            """.formatted(category.name(), prompt, response);
    }

    private QualityScore parseScores(String responseText) {
        try {
            String[] parts = responseText.trim().split(",");
            if (parts.length >= 3) {
                double comp = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[0].trim())));
                double rel = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[1].trim())));
                double form = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[2].trim())));
                return QualityScore.of(comp, rel, form);
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM Judge response: '{}'", responseText);
        }
        return QualityScore.of(0.5, 0.5, 0.5);
    }
}
