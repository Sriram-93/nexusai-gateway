package com.llm.nexusai_gateway.Provider;

/**
 * Immutable descriptor for a single LLM model arm.
 *
 * This is the single source of truth for all model metadata.
 * No other class should hardcode model names, pricing, or capabilities.
 *
 * @param armKey          Canonical routing key — format "provider:model"
 *                        (e.g. "groq:llama-3.3-70b-versatile").
 * @param provider        Provider identifier matching a {@link LlmProvider} bean (lower-case).
 * @param modelId         Model name as expected by the provider API.
 * @param inputPricePer1M Cost in USD per 1M input tokens.
 * @param outputPricePer1M Cost in USD per 1M output tokens.
 * @param estimatedLatencyMs Empirical median latency in milliseconds (used for ranking).
 * @param contextWindowTokens Maximum context window in tokens.
 * @param enabled         If false, the arm is invisible to all engines and policies.
 */
public record ModelCatalog(
    String armKey,
    String provider,
    String modelId,
    double inputPricePer1M,
    double outputPricePer1M,
    int estimatedLatencyMs,
    int contextWindowTokens,
    boolean enabled
) {
    /** Compute cost in USD for given token counts. */
    public double computeCostUsd(int inputTokens, int outputTokens) {
        return (inputTokens * inputPricePer1M / 1_000_000.0)
             + (outputTokens * outputPricePer1M / 1_000_000.0);
    }
}
