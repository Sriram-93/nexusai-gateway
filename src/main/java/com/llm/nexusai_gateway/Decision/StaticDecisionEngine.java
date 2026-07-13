package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Baseline 1 — Static Single Provider.
 * Always routes to the first eligible provider.
 * No learning, no adaptation.
 */
public class StaticDecisionEngine implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(StaticDecisionEngine.class);

    private final String defaultProvider;
    private final String defaultModel;
    private final ReputationService reputationService;

    public StaticDecisionEngine(String defaultProvider, String defaultModel, ReputationService reputationService) {
        this.defaultProvider = defaultProvider;
        this.defaultModel = defaultModel;
        this.reputationService = reputationService;
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        String defaultComposite = defaultProvider.toLowerCase() + ":" + defaultModel.toLowerCase();
        
        String bestArm = eligibleProviders.stream()
                .filter(p -> p.startsWith(defaultComposite))
                .findFirst()
                .orElse(eligibleProviders.get(0));

        String finalProvider = bestArm.contains(":") ? bestArm.split(":")[0] : bestArm;
        String finalModel = bestArm.contains(":") ? bestArm.split(":")[1] : defaultModel;

        double health = reputationService.get(bestArm).getHealthScore();

        return new ExplainedDecision(
            finalProvider, finalModel, health,
            reputationService.get(bestArm).getAvgQuality(),
            reputationService.get(bestArm).getAvgLatencyMs(),
            health,
            "Static routing: always select " + bestArm,
            Map.of(bestArm, health),
            RoutingStrategy.STATIC
        );
    }

    @Override
    public void update(RequestContext context, String provider, double reward) {
        // Static engine does not learn
    }

    @Override
    public RoutingStrategy getStrategy() {
        return RoutingStrategy.STATIC;
    }

    @Override
    public void reset() {
        // Nothing to reset
    }
}
