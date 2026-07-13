package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;

import java.util.List;

/**
 * Decision Engine interface — the core abstraction of the AEDF.
 *
 * Each implementation represents a different routing strategy
 * (Doc 07: Static, Rule-Based, Weighted, Adaptive).
 *
 * The interface supports both selection (choosing a provider) and
 * learning (updating internal state based on observed rewards).
 */
public interface DecisionEngine {

    /**
     * Select a provider for the given request context.
     *
     * @param context           Extracted request features
     * @param eligibleProviders Providers that passed policy filtering
     * @return An explained decision with full transparency
     */
    ExplainedDecision select(RequestContext context, List<String> eligibleProviders);

    /**
     * Update internal state after observing the reward for a decision.
     * Only adaptive strategies (e.g., LinUCB) perform actual learning here.
     *
     * @param context  The context that was used for the decision
     * @param provider The provider that was selected
     * @param reward   The observed multi-objective reward [0.0, 1.0]
     */
    void update(RequestContext context, String provider, double reward);

    /**
     * Get the routing strategy this engine implements.
     */
    RoutingStrategy getStrategy();

    /**
     * Reset internal state (for experiments that need fresh runs).
     */
    void reset();
}
