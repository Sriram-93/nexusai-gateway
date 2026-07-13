package com.llm.nexusai_gateway.Context;

import java.util.Map;

/**
 * Structured context extracted from an incoming request.
 * This is the feature vector provided to the Decision Engine.
 *
 * Every field here must have a technical justification
 * (Doc 01: "Every context feature must have a technical justification").
 *
 * @param taskCategory       Classified workload type — determines quality expectations
 * @param estimatedComplexity Normalized [0.0, 1.0] complexity score — influences provider capability matching
 * @param estimatedTokenCount Approximate input tokens — determines context window requirements
 * @param requiresLongContext Whether prompt exceeds standard context thresholds — filters eligible providers
 * @param userId             User identifier — for policy filtering and per-user tracking
 * @param tenantId           Tenant identifier — for enterprise policy enforcement
 * @param providerHealthScores Current health score per provider — from ReputationService
 */
public record RequestContext(
    TaskCategory taskCategory,
    double estimatedComplexity,
    int estimatedTokenCount,
    boolean requiresLongContext,
    String userId,
    String tenantId,
    Map<String, Double> providerHealthScores
) {

    /**
     * Converts context features into a normalized double array for the bandit algorithm.
     *
     * 5 dimensions (per convergence constraint — see directives Q2):
     *   [0] bias          — constant 1.0, allows θ to learn a baseline reward intercept
     *   [1] complexity    — [0,1] estimated prompt complexity (length + vocab + structure)
     *   [2] tokenNorm     — [0,1] estimated token count normalized to 4096 context window
     *   [3] longContext   — binary 0/1, whether prompt exceeds 4096 token threshold
     *   [4] domainWeight  — [0,1] task type as a quality-weight proxy
     *                       (CODE=1.0, REASONING=0.75, CREATIVE=0.5, FACTUAL=0.25, CONVERSATION=0.0)
     *
     * Rationale: One-hot encoding (4 dims) collapsed into a single ordinal domain weight.
     * This keeps d=5, ensuring LinUCB converges with realistic traffic volumes.
     */
    public double[] toFeatureVector() {
        double tokenCountNorm = Math.min(1.0, estimatedTokenCount / 4096.0);
        double longCtx = requiresLongContext ? 1.0 : 0.0;
        double domainWeight = switch (taskCategory) {
            case CODE         -> 1.00;
            case REASONING    -> 0.75;
            case CREATIVE     -> 0.50;
            case FACTUAL      -> 0.25;
            case CONVERSATION -> 0.00;
        };

        return new double[]{
            1.0,                    // [0] bias term
            estimatedComplexity,    // [1] complexity ∈ [0,1]
            tokenCountNorm,         // [2] token count ∈ [0,1]
            longCtx,                // [3] long context flag (binary)
            domainWeight            // [4] domain quality weight ∈ [0,1]
        };
    }

    /** Number of features in the context vector. Must match toFeatureVector() length. */
    public static final int FEATURE_DIMENSION = 5;
}
