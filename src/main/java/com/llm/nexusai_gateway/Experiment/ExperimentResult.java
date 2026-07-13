package com.llm.nexusai_gateway.Experiment;

/**
 * Results of a routing experiment run.
 * Tracks metrics across all requests for a specific routing strategy.
 *
 * Doc 07: These are the primary and secondary evaluation metrics.
 */
public class ExperimentResult {

    private final String strategy;
    private final int totalRequests;

    // Primary metrics
    private double totalReward = 0.0;
    private double cumulativeRegret = 0.0;
    private int correctSelections = 0; // Selections that matched the optimal provider

    // Secondary metrics
    private double totalQuality = 0.0;
    private long totalLatencyMs = 0;
    private double totalCostUsd = 0.0;
    private int successfulRequests = 0;
    private int failedRequests = 0;

    public ExperimentResult(String strategy, int totalRequests) {
        this.strategy = strategy;
        this.totalRequests = totalRequests;
    }

    public void recordRequest(double reward, double optimalReward, double quality,
                              long latencyMs, double costUsd, boolean success, boolean wasOptimal) {
        this.totalReward += reward;
        this.cumulativeRegret += (optimalReward - reward);
        if (wasOptimal) this.correctSelections++;
        this.totalQuality += quality;
        this.totalLatencyMs += latencyMs;
        this.totalCostUsd += costUsd;
        if (success) this.successfulRequests++;
        else this.failedRequests++;
    }

    // --- Getters for computed averages ---

    public String getStrategy() { return strategy; }
    public int getTotalRequests() { return totalRequests; }

    public double getAverageReward() { return totalRequests == 0 ? 0 : totalReward / totalRequests; }
    public double getCumulativeRegret() { return cumulativeRegret; }
    public double getSelectionAccuracy() { return totalRequests == 0 ? 0 : (double) correctSelections / totalRequests; }

    public double getAverageQuality() { return successfulRequests == 0 ? 0 : totalQuality / successfulRequests; }
    public double getAverageLatencyMs() { return successfulRequests == 0 ? 0 : (double) totalLatencyMs / successfulRequests; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public double getAvailability() { return totalRequests == 0 ? 0 : (double) successfulRequests / totalRequests; }
}
