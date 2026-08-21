package com.llm.nexusai_gateway.Reward;

import com.llm.nexusai_gateway.Evaluation.QualityScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Multi-objective reward calculator for the AEDF feedback loop.
 *
 * Doc 01: "Never use raw judge score as the reward."
 * Doc 01: "The reward should represent enterprise utility by combining
 *          normalized measures such as quality, availability, latency, cost."
 *
 * Q5 Fix: Weights are now read from the active policy tier configured in application.properties.
 * This makes reward shaping a deliberate research contribution:
 *   ENTERPRISE        → quality 0.60, availability 0.20, latency 0.10, cost 0.10
 *   BUDGET            → cost 0.50,    latency 0.30,     quality 0.15, avail 0.05
 *   MISSION_CRITICAL  → quality 0.70, availability 0.20, latency 0.05, cost 0.05
 *
 * All inputs are normalized to [0,1] before combining.
 * Output is clamped to [0.0, 1.0].
 *
 * R = w_q * norm(quality) + w_a * norm(availability) + w_l * norm(1/latency) + w_c * norm(1/cost)
 */
@Service
public class RewardCalculator {

    private static final Logger log = LoggerFactory.getLogger(RewardCalculator.class);

    @Value("${nexusai.reward.active-tier:ENTERPRISE}")
    private String activeTier;

    // ENTERPRISE tier weights
    @Value("${nexusai.reward.tier.enterprise.quality:0.60}")
    private double enterpriseQuality;
    @Value("${nexusai.reward.tier.enterprise.availability:0.20}")
    private double enterpriseAvailability;
    @Value("${nexusai.reward.tier.enterprise.latency:0.10}")
    private double enterpriseLatency;
    @Value("${nexusai.reward.tier.enterprise.cost:0.10}")
    private double enterpriseCost;

    // BUDGET tier weights
    @Value("${nexusai.reward.tier.budget.quality:0.15}")
    private double budgetQuality;
    @Value("${nexusai.reward.tier.budget.availability:0.05}")
    private double budgetAvailability;
    @Value("${nexusai.reward.tier.budget.latency:0.30}")
    private double budgetLatency;
    @Value("${nexusai.reward.tier.budget.cost:0.50}")
    private double budgetCost;

    // MISSION_CRITICAL tier weights
    @Value("${nexusai.reward.tier.mission-critical.quality:0.70}")
    private double missionQuality;
    @Value("${nexusai.reward.tier.mission-critical.availability:0.20}")
    private double missionAvailability;
    @Value("${nexusai.reward.tier.mission-critical.latency:0.05}")
    private double missionLatency;
    @Value("${nexusai.reward.tier.mission-critical.cost:0.05}")
    private double missionCost;

    /** Maximum latency for normalization (10 seconds). Anything above yields 0 latency reward. */
    private static final double MAX_LATENCY_MS = 10_000.0;

    /** Maximum cost for normalization ($0.01 per request). */
    private static final double MAX_COST_USD = 0.01;

    /**
     * Calculate the multi-objective reward for a completed request.
     *
     * @param quality   Quality evaluation result (heuristic baseline; LLM-as-Judge is Phase 2)
     * @param latencyMs Response latency in milliseconds
     * @param costUsd   Cost of this request in USD
     * @param success   Whether the request completed without errors
     * @return Reward value in [0.0, 1.0]
     */
    public double calculate(QualityScore quality, long latencyMs, double costUsd, boolean success) {
        double[] components = calculateComponents(quality, latencyMs, costUsd, success);
        double qualityScore = components[0];
        double latencyScore = components[1];
        double costScore = components[2];
        double availabilityScore = components[3];

        // Retrieve weights for the active tier
        double wq = weightQuality();
        double wa = weightAvailability();
        double wl = weightLatency();
        double wc = weightCost();

        double reward = (wq * qualityScore)
                      + (wa * availabilityScore)
                      + (wl * latencyScore)
                      + (wc * costScore);

        // Clamp to [0.0, 1.0]
        reward = Math.min(1.0, Math.max(0.0, reward));

        log.debug("Reward [tier={}]: {:.4f} (quality={:.3f}, avail={:.1f}, latency={:.3f}, cost={:.3f})",
                  activeTier, reward, qualityScore, availabilityScore, latencyScore, costScore);

        return reward;
    }

    /**
     * Calculate individual normalized reward components for Phase 2 scalarization.
     * @return [QualityScore, LatencyScore, CostScore, AvailabilityScore]
     */
    public double[] calculateComponents(QualityScore quality, long latencyMs, double costUsd, boolean success) {
        double availabilityScore = success ? 1.0 : 0.0;
        double qualityScore      = success ? quality.compositeScore() : 0.0;
        double latencyScore      = Math.max(0.0, 1.0 - (latencyMs / MAX_LATENCY_MS));
        double costScore         = Math.max(0.0, 1.0 - (costUsd   / MAX_COST_USD));
        return new double[]{qualityScore, latencyScore, costScore, availabilityScore};
    }

    /** Returns the active tier name for logging/explainability. */
    public String getActiveTier() { return activeTier; }

    private double weightQuality()      { return resolveWeights()[0]; }
    private double weightAvailability() { return resolveWeights()[1]; }
    private double weightLatency()      { return resolveWeights()[2]; }
    private double weightCost()         { return resolveWeights()[3]; }

    private double[] resolveWeights() {
        return switch (activeTier.toUpperCase()) {
            case "BUDGET"           -> new double[]{ budgetQuality,    budgetAvailability,    budgetLatency,    budgetCost    };
            case "MISSION_CRITICAL" -> new double[]{ missionQuality,   missionAvailability,   missionLatency,   missionCost   };
            default                 -> new double[]{ enterpriseQuality, enterpriseAvailability, enterpriseLatency, enterpriseCost };
        };
    }
}
