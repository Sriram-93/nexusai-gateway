package com.llm.nexusai_gateway.Experiment;

import com.llm.nexusai_gateway.Context.ContextExtractor;
import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Decision.ExplainedDecision;
import com.llm.nexusai_gateway.Decision.LinUcbDecisionEngine;
import com.llm.nexusai_gateway.Decision.RuleBasedDecisionEngine;
import com.llm.nexusai_gateway.Decision.StaticDecisionEngine;
import com.llm.nexusai_gateway.Decision.WeightedDecisionEngine;
import com.llm.nexusai_gateway.Evaluation.QualityEvaluator;
import com.llm.nexusai_gateway.Evaluation.QualityScore;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import com.llm.nexusai_gateway.Provider.ProviderResponse;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Reward.RewardCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Harness for executing controlled experiments across different routing strategies.
 *
 * Doc 07 Section 7: Experimental Design.
 * Compares Static, Rule-Based, Weighted, and Adaptive routing under
 * identical workload conditions.
 */
@Service
public class ExperimentRunner {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);

    private final ContextExtractor contextExtractor;
    private final ProviderRegistry providerRegistry;
    private final QualityEvaluator qualityEvaluator;
    private final RewardCalculator rewardCalculator;
    private final ReputationService reputationService;

    public ExperimentRunner(ContextExtractor contextExtractor,
                            ProviderRegistry providerRegistry,
                            QualityEvaluator qualityEvaluator,
                            RewardCalculator rewardCalculator,
                            ReputationService reputationService) {
        this.contextExtractor = contextExtractor;
        this.providerRegistry = providerRegistry;
        this.qualityEvaluator = qualityEvaluator;
        this.rewardCalculator = rewardCalculator;
        this.reputationService = reputationService;
    }

    /**
     * Run an experiment dataset through all 4 routing strategies sequentially.
     */
    public Mono<List<ExperimentResult>> runFullExperiment(List<ChatRequest> dataset) {
        log.info("Starting full AEDF experiment with {} requests", dataset.size());

        List<DecisionEngine> engines = List.of(
            new StaticDecisionEngine("gemini", "gemini-2.5-flash", reputationService),
            new RuleBasedDecisionEngine(reputationService),
            new WeightedDecisionEngine(Map.of("gemini", 0.5, "groq", 0.5), reputationService),
            new LinUcbDecisionEngine(1.0, reputationService)
        );

        return Flux.fromIterable(engines)
            .concatMap(engine -> runStrategy(engine, dataset))
            .collectList()
            .doOnNext(results -> log.info("Experiment complete."));
    }

    private Mono<ExperimentResult> runStrategy(DecisionEngine engine, List<ChatRequest> dataset) {
        log.info("Evaluating strategy: {}", engine.getStrategy());
        engine.reset();
        ExperimentResult result = new ExperimentResult(engine.getStrategy().name(), dataset.size());

        // Process sequentially to allow online learning to accumulate properly
        return Flux.fromIterable(dataset)
            .concatMap(request -> processRequestSimulated(engine, request, result))
            .then(Mono.just(result));
    }

    private Mono<Void> processRequestSimulated(DecisionEngine engine, ChatRequest request, ExperimentResult result) {
        long start = System.currentTimeMillis();
        RequestContext context = contextExtractor.extract(request);

        List<String> eligible = List.of(
            "gemini:gemini-2.5-flash",
            "gemini:gemini-2.5-pro",
            "groq:llama-3.3-70b-versatile",
            "groq:llama-3.1-8b-instant"
        );

        // 1. Select provider
        ExplainedDecision decision = engine.select(context, eligible);
        LlmProvider provider = providerRegistry.getProvider(decision.selectedProvider());

        if (provider == null) {
            result.recordRequest(0.0, 1.0, 0.0, 0, 0.0, false, false);
            return Mono.empty();
        }

        // 2. Execute provider (we use mock execution in experiments to run fast and avoid huge bills)
        return provider.chat(request.getMessage(), decision.selectedModel())
            .map(response -> {
                long latencyMs = System.currentTimeMillis() - start;
                double cost = (response.inputTokens() * 0.1 + response.outputTokens() * 0.5) / 1_000_000.0;

                QualityScore quality = qualityEvaluator.evaluate(request.getMessage(), response.content(), context.taskCategory());
                double reward = rewardCalculator.calculate(quality, latencyMs, cost, true);

                // Note: For regret, we need to know the optimal reward. In a real harness, we'd query ALL providers
                // to find the optimal. For this simulated harness, we estimate an optimal reward bound.
                double estimatedOptimalReward = Math.min(1.0, reward + 0.1);
                boolean wasOptimal = reward >= (estimatedOptimalReward - 0.05);

                String armKey = decision.selectedProvider() + ":" + decision.selectedModel();
                engine.update(context, armKey, reward);

                result.recordRequest(reward, estimatedOptimalReward, quality.compositeScore(), latencyMs, cost, true, wasOptimal);
                return response;
            })
            .onErrorResume(err -> {
                result.recordRequest(0.0, 0.5, 0.0, System.currentTimeMillis() - start, 0.0, false, false);
                return Mono.empty();
            })
            .then();
    }
}
