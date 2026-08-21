package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Baseline 3 — Static Weighted Router.
 * Distributes requests across providers using fixed probability weights.
 * No learning — weights remain constant throughout the experiment.
 */
public class WeightedDecisionEngine implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(WeightedDecisionEngine.class);

    private final Map<String, Double> weights;
    private final ReputationService reputationService;
    private final Random random = new Random();

    public WeightedDecisionEngine(Map<String, Double> weights, ReputationService reputationService) {
        this.weights = weights;
        this.reputationService = reputationService;
    }

    private double getWeight(String arm) {
        if (weights.containsKey(arm)) {
            return weights.get(arm);
        }
        String base = arm.contains(":") ? arm.split(":")[0] : arm;
        return weights.getOrDefault(base, 0.5);
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        // Normalize weights for eligible providers only
        double totalWeight = eligibleProviders.stream()
            .mapToDouble(this::getWeight)
            .sum();

        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        String selected = eligibleProviders.get(0);

        Map<String, Double> scores = new LinkedHashMap<>();
        for (String provider : eligibleProviders) {
            double w = getWeight(provider);
            scores.put(provider, w / totalWeight);
            cumulative += w;
            if (roll <= cumulative) {
                selected = provider;
                roll = Double.MAX_VALUE; // prevent re-selection
            }
        }

        String finalProvider = selected.contains(":") ? selected.split(":")[0] : selected;
        String finalModel = selected.contains(":") ? selected.split(":")[1] : "default";
        double health = reputationService.get(selected).getHealthScore();

        return new ExplainedDecision(
            finalProvider, finalModel, health,
            reputationService.get(selected).getAvgQuality(),
            reputationService.get(selected).getAvgLatencyMs(),
            health,
            String.format("Weighted random: selected %s (weight=%.2f)", selected, getWeight(selected)),
            scores,
            RoutingStrategy.WEIGHTED
        );
    }

    @Override
    public void update(RequestContext context, String provider, double reward) {
        // Weighted engine does not learn — weights are static
    }

    @Override
    public RoutingStrategy getStrategy() {
        return RoutingStrategy.WEIGHTED;
    }

    @Override
    public void reset() {
        // Nothing to reset
    }
}
