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
    Map<String, Double> providerHealthScores,
    float[] semanticEmbedding
) {

    /**
     * Converts context features into a normalized double array for the bandit algorithm.
     *
     * NEW: Replaced the manual 5D engineered features with the raw ONNX semantic embedding.
     * We take the first 16 dimensions of the 384-dimensional embedding to balance 
     * rich semantic representation with LinUCB convergence speed (d=17).
     *
     *   [0] bias term
     *   [1..16] First 16 principal/raw dimensions of the semantic embedding
     */
    public double[] toFeatureVector() {
        double[] features = new double[FEATURE_DIMENSION];
        features[0] = 1.0; // bias term
        
        if (semanticEmbedding != null && semanticEmbedding.length >= 16) {
            for (int i = 0; i < 16; i++) {
                features[i + 1] = semanticEmbedding[i];
            }
        }
        
        return features;
    }

    /** Number of features in the context vector. Must match toFeatureVector() length. */
    public static final int FEATURE_DIMENSION = 17;
}
