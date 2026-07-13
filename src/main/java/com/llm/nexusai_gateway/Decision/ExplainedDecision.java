package com.llm.nexusai_gateway.Decision;

import java.util.Map;

/**
 * An explained routing decision with full transparency.
 *
 * Doc 02 Principle 4: "Every routing decision should be understandable."
 * Doc 06 Novelty 3: "Decision-centric architecture."
 *
 * @param selectedProvider  The provider chosen for this request
 * @param selectedModel     The model to use on the selected provider
 * @param expectedReward    The estimated reward for this selection
 * @param qualityEstimate   Estimated quality based on provider reputation
 * @param latencyEstimate   Estimated latency based on provider reputation
 * @param healthScore       Current health score of the selected provider
 * @param reason            Human-readable explanation of the selection decision
 * @param armScores         Scores for all candidate providers (for explainability)
 * @param strategy          Which routing strategy produced this decision
 */
public record ExplainedDecision(
    String selectedProvider,
    String selectedModel,
    double expectedReward,
    double qualityEstimate,
    double latencyEstimate,
    double healthScore,
    String reason,
    Map<String, Double> armScores,
    RoutingStrategy strategy
) {}
