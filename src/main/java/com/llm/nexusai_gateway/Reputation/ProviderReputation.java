package com.llm.nexusai_gateway.Reputation;

/**
 * Runtime reputation statistics for a single LLM provider.
 * All averages use Exponentially Weighted Moving Average (EWMA) to
 * give more weight to recent observations while preserving historical trends.
 *
 * These statistics feed into the Decision Engine as context features
 * and are updated after every completed request (closed-loop learning).
 */
public class ProviderReputation {

    private final String providerName;

    /** EWMA of response quality scores [0.0, 1.0] */
    private double avgQuality;

    /** EWMA of response latency in milliseconds */
    private double avgLatencyMs;

    /** Success rate: successful requests / total requests */
    private double availability;

    /** EWMA of failure rate [0.0, 1.0] */
    private double failureRate;

    /** Quality per unit cost — higher is better */
    private double costEfficiency;

    /** Composite health score [0.0, 1.0] — weighted combination of all metrics */
    private double healthScore;

    /** Total number of requests observed */
    private long totalRequests;

    /** Total number of successful requests */
    private long successCount;

    /** Timestamp of last update */
    private long lastUpdatedMs;

    /** EWMA smoothing factor — controls how much weight recent observations receive */
    private static final double ALPHA = 0.1;

    public ProviderReputation(String providerName) {
        this.providerName = providerName;
        this.avgQuality = 0.5;       // neutral initial estimate
        this.avgLatencyMs = 1000.0;  // 1 second initial estimate
        this.availability = 1.0;     // assume available until proven otherwise
        this.failureRate = 0.0;
        this.costEfficiency = 1.0;
        this.healthScore = 0.5;      // neutral initial health
        this.totalRequests = 0;
        this.successCount = 0;
        this.lastUpdatedMs = System.currentTimeMillis();
    }

    /**
     * Update reputation after a completed request.
     *
     * @param quality  Response quality score [0.0, 1.0]
     * @param latencyMs Response latency in milliseconds
     * @param costUsd  Cost of this request in USD
     * @param success  Whether the request completed successfully
     */
    public synchronized void update(double quality, long latencyMs, double costUsd, boolean success) {
        totalRequests++;
        if (success) {
            successCount++;
        }

        // EWMA updates
        avgQuality = ewma(avgQuality, quality);
        avgLatencyMs = ewma(avgLatencyMs, latencyMs);
        failureRate = ewma(failureRate, success ? 0.0 : 1.0);

        // Availability is cumulative ratio
        availability = totalRequests > 0 ? (double) successCount / totalRequests : 1.0;

        // Cost efficiency: quality per dollar (avoid division by zero)
        if (costUsd > 0 && success) {
            double currentEfficiency = quality / costUsd;
            costEfficiency = ewma(costEfficiency, currentEfficiency);
        }

        // Composite health score
        recomputeHealthScore();

        lastUpdatedMs = System.currentTimeMillis();
    }

    /**
     * Recompute the composite health score from individual metrics.
     * Weights reflect enterprise priorities from the Constitution:
     * quality > availability > latency > cost
     */
    private void recomputeHealthScore() {
        double latencyScore = Math.max(0.0, 1.0 - (avgLatencyMs / 10000.0)); // 10s = 0 health
        healthScore = (0.35 * avgQuality)
                    + (0.30 * availability)
                    + (0.20 * latencyScore)
                    + (0.15 * Math.min(1.0, failureRate < 0.5 ? 1.0 - failureRate : 0.0));
        healthScore = Math.min(1.0, Math.max(0.0, healthScore));
    }

    private double ewma(double current, double observation) {
        return ALPHA * observation + (1.0 - ALPHA) * current;
    }

    // --- Getters ---

    public String getProviderName() { return providerName; }
    public double getAvgQuality() { return avgQuality; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public double getAvailability() { return availability; }
    public double getFailureRate() { return failureRate; }
    public double getCostEfficiency() { return costEfficiency; }
    public double getHealthScore() { return healthScore; }
    public long getTotalRequests() { return totalRequests; }
    public long getSuccessCount() { return successCount; }
    public long getLastUpdatedMs() { return lastUpdatedMs; }

    @Override
    public String toString() {
        return String.format("ProviderReputation{provider=%s, health=%.3f, quality=%.3f, " +
                "latency=%.0fms, availability=%.3f, failRate=%.3f, requests=%d}",
                providerName, healthScore, avgQuality, avgLatencyMs,
                availability, failureRate, totalRequests);
    }
}
