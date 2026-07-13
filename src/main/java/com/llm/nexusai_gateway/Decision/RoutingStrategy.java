package com.llm.nexusai_gateway.Decision;

/**
 * Routing strategy enum for experimental comparison.
 * Doc 07: Compare 4 strategies with controlled variables.
 */
public enum RoutingStrategy {
    /** Baseline 1: Always routes to a single configured provider */
    STATIC,

    /** Baseline 2: Current keyword-based routing logic (preserved for comparison) */
    RULE_BASED,

    /** Baseline 3: Distributes requests using static weights across providers */
    WEIGHTED,

    /** Proposed method: Contextual bandit with online learning (LinUCB) */
    ADAPTIVE
}
