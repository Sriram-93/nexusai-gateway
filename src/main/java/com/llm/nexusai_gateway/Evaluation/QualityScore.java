package com.llm.nexusai_gateway.Evaluation;

/**
 * Quality evaluation result for a single LLM response.
 * Each dimension is scored on [0.0, 1.0].
 *
 * Doc 01: "Quality is task dependent."
 *
 * @param completeness    How thorough is the response relative to prompt complexity
 * @param relevance       How closely the response addresses the prompt
 * @param formatCompliance Whether the response follows expected structural conventions
 * @param compositeScore  Weighted combination of all dimensions
 */
public record QualityScore(
    double completeness,
    double relevance,
    double formatCompliance,
    double compositeScore
) {
    /**
     * Create a quality score from individual dimensions using default weights.
     */
    public static QualityScore of(double completeness, double relevance, double formatCompliance) {
        double composite = (0.40 * completeness) + (0.40 * relevance) + (0.20 * formatCompliance);
        return new QualityScore(completeness, relevance, formatCompliance, composite);
    }
}
