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

    private final Map<TaskCategory, String> taskProviderMap;
    private final ReputationService reputationService;

    public RuleBasedDecisionEngine(ReputationService reputationService) {
        this.reputationService = reputationService;

        // Original rule-based mapping (equivalent to old RoutingService)
        this.taskProviderMap = Map.of(
            TaskCategory.CODE, "gemini:gemini-2.5-flash",
            TaskCategory.REASONING, "gemini:gemini-2.5-flash",
            TaskCategory.CREATIVE, "groq:llama-3.3-70b-versatile",
            TaskCategory.FACTUAL, "groq:llama-3.3-70b-versatile",
            TaskCategory.CONVERSATION, "groq:llama-3.1-8b-instant"
        );
    }

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        String mappedArm = taskProviderMap.getOrDefault(context.taskCategory(), "groq:llama-3.1-8b-instant");

        // Fallback if mapped arm is not eligible
        String bestArm = eligibleProviders.contains(mappedArm)
            ? mappedArm : eligibleProviders.get(0);

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
