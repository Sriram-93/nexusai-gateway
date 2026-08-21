package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Context.TaskCategory;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline 2 — Rule-Based Router.
 * Preserves the original keyword-based routing logic for experimental comparison.
 * Routes based on task category → provider mapping.
 */
public class RuleBasedDecisionEngine implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedDecisionEngine.class);

    private final ReputationService reputationService;

    public RuleBasedDecisionEngine(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        if (eligibleProviders == null || eligibleProviders.isEmpty()) {
            throw new IllegalStateException("No eligible providers available for routing.");
        }
        // Dynamically select the best available arm from eligibleProviders
        String bestArm = eligibleProviders.get(0);
        for (String arm : eligibleProviders) {
            String lower = arm.toLowerCase();
            if (context.taskCategory() == TaskCategory.CODE || context.taskCategory() == TaskCategory.REASONING) {
                if (lower.contains("pro") || lower.contains("70b") || lower.contains("gpt-4")) {
                    bestArm = arm;
                    break;
                }
            } else if (context.taskCategory() == TaskCategory.CONVERSATION) {
                if (lower.contains("flash") || lower.contains("8b") || lower.contains("mini")) {
                    bestArm = arm;
                    break;
                }
            }
        }

        String finalProvider = bestArm.contains(":") ? bestArm.split(":")[0] : bestArm;
        String finalModel = bestArm.contains(":") ? bestArm.split(":")[1] : "default";

        double health = reputationService.get(bestArm).getHealthScore();

        Map<String, Double> scores = new HashMap<>();
        for (String p : eligibleProviders) {
            scores.put(p, p.equals(bestArm) ? 1.0 : 0.0);
        }

        return new ExplainedDecision(
            finalProvider, finalModel, health,
            reputationService.get(bestArm).getAvgQuality(),
            reputationService.get(bestArm).getAvgLatencyMs(),
            health,
            "Rule-based: " + context.taskCategory() + " → " + bestArm,
            scores,
            RoutingStrategy.RULE_BASED
        );
    }

    @Override
    public void update(RequestContext context, String provider, double reward) {
        // Rule-based engine does not learn
    }

    @Override
    public RoutingStrategy getStrategy() {
        return RoutingStrategy.RULE_BASED;
    }

    @Override
    public void reset() {
        // Nothing to reset
    }
}
