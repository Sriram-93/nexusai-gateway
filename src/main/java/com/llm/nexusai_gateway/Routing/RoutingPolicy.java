package com.llm.nexusai_gateway.Routing;

/**
 * Advanced routing strategies supported by the NexusAI Gateway.
 * Defines the decision-making strategies used by the Federated Adaptive Decision Engine (FADE).
 */
public enum RoutingPolicy {

    /**
     * Core LinUCB Contextual Bandit: balances exploration vs exploitation based on user features, 
     * utilizing federated multi-tenant learning and drift-aware covariance decay.
     */
    FEDERATED_ADAPTIVE_BANDIT(true, true),

    /**
     * Drift-Aware Thompson Sampling: Bayesian alternative for exploration-exploitation 
     * specifically optimized for highly non-stationary model performance environments.
     */
    DRIFT_AWARE_THOMPSON_SAMPLING(true, false),

    /**
     * Multi-Objective SLA Routing: Uses reward decomposition to jointly optimize 
     * across cost constraints, latency thresholds, and throughput requirements.
     */
    MULTI_OBJECTIVE_SLA_OPTIMIZED(true, true),
    
    /**
     * Quality-Weighted Ensemble: Routes to multiple providers simultaneously and aggregates 
     * results based on historical context-specific performance weights.
     */
    QUALITY_WEIGHTED_ENSEMBLE(false, false),

    /**
     * Semantic Cache-First Routing: Leverages embedding similarity to route to a high-speed 
     * semantic cache, falling back to a contextual bandit if confidence is low.
     */
    SEMANTIC_CACHE_FIRST(true, false),

    /**
     * Cost-optimized deterministic policy: selects the provider/model with lowest token cost per 1K tokens.
     */
    LOWEST_COST(false, false),

    /**
     * Latency-optimized deterministic policy: selects the provider/model with lowest historical latency.
     */
    LOWEST_LATENCY(false, false),

    /**
     * Deterministic fallback chain: routes to preferred provider first, failing over sequentially if unavailable.
     */
    FALLBACK_CHAIN(false, false);

    private final boolean isAdaptive;
    private final boolean supportsFederation;

    RoutingPolicy(boolean isAdaptive, boolean supportsFederation) {
        this.isAdaptive = isAdaptive;
        this.supportsFederation = supportsFederation;
    }

    /**
     * @return true if the policy uses machine learning or dynamic adaptation to route queries.
     */
    public boolean isAdaptive() {
        return isAdaptive;
    }

    /**
     * @return true if the policy supports cross-tenant federated learning and state sharing.
     */
    public boolean supportsFederation() {
        return supportsFederation;
    }
}
