package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Health.ProviderHealthMonitor;
import com.llm.nexusai_gateway.Health.ProviderHealthStatus;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Provider.RegisteredModel;
import com.llm.nexusai_gateway.Reputation.ProviderReputation;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RoutingSimulatorService {

    private final ModelRegistry modelRegistry;
    private final ReputationService reputationService;
    private final ProviderHealthMonitor healthMonitor;

    public RoutingSimulatorService(ModelRegistry modelRegistry,
                                  ReputationService reputationService,
                                  ProviderHealthMonitor healthMonitor) {
        this.modelRegistry = modelRegistry;
        this.reputationService = reputationService;
        this.healthMonitor = healthMonitor;
    }

    public record SimulationRequest(
            String prompt,
            String taskCategory,
            double qualityWeight,
            double costWeight,
            double latencyWeight,
            double reliabilityWeight
    ) {}

    public record CandidateEvaluation(
            String armKey,
            String providerSlug,
            String modelId,
            String displayName,
            double qualityScore,
            double costScore,
            double latencyScore,
            double reliabilityScore,
            double healthScore,
            double finalScore,
            double estimatedCostUsd,
            int estimatedLatencyMs,
            boolean isWinner,
            String statusReason
    ) {}

    public record SimulationResult(
            String selectedArmKey,
            String selectedModelDisplayName,
            String explanationReason,
            Map<String, Double> policyWeights,
            List<CandidateEvaluation> candidates
    ) {}

    public SimulationResult simulate(SimulationRequest request) {
        List<RegisteredModel> models = modelRegistry.getEnabledModels();
        if (models.isEmpty()) {
            models = modelRegistry.getAllModels();
        }

        double wQ = request.qualityWeight() > 0 ? request.qualityWeight() : 0.4;
        double wC = request.costWeight() > 0 ? request.costWeight() : 0.3;
        double wL = request.latencyWeight() > 0 ? request.latencyWeight() : 0.15;
        double wR = request.reliabilityWeight() > 0 ? request.reliabilityWeight() : 0.15;

        // Normalize weights
        double totalW = wQ + wC + wL + wR;
        if (totalW > 0) {
            wQ /= totalW;
            wC /= totalW;
            wL /= totalW;
            wR /= totalW;
        }

        List<CandidateEvaluation> evaluations = new ArrayList<>();
        CandidateEvaluation winner = null;
        double maxScore = -1.0;

        int estInputTokens = Math.max(10, (request.prompt() != null ? request.prompt().length() : 50) / 4);
        int estOutputTokens = 150;

        for (RegisteredModel model : models) {
            String armKey = model.getArmKey();
            ProviderReputation rep = reputationService.get(armKey);

            double qScore = rep != null ? rep.getAvgQuality() : 0.85;
            double estCost = model.computeCostUsd(estInputTokens, estOutputTokens);
            // Cost score: lower cost = higher score
            double cScore = Math.max(0.0, 1.0 - (estCost * 100.0));

            int estLatency = model.getEstimatedLatencyMs();
            // Latency score: lower latency = higher score
            double lScore = Math.max(0.0, 1.0 - (estLatency / 3000.0));

            ProviderHealthStatus phs = healthMonitor.getStatus(model.getProviderSlug());
            double rScore = phs != null ? (1.0 - phs.getErrorRate()) : 0.98;
            double hScore = (phs != null && phs.isAvailable(30000L)) ? 1.0 : 0.2;

            double finalScore = (wQ * qScore) + (wC * cScore) + (wL * lScore) + (wR * rScore);
            finalScore *= hScore; // Multiply by health factor

            String reason = hScore < 0.5 ? "Provider Degraded/Unhealthy" : "Eligible Candidate";

            CandidateEvaluation eval = new CandidateEvaluation(
                    armKey, model.getProviderSlug(), model.getModelId(),
                    model.getDisplayName() != null ? model.getDisplayName() : model.getModelId(),
                    qScore, cScore, lScore, rScore, hScore, finalScore, estCost, estLatency, false, reason
            );

            evaluations.add(eval);

            if (finalScore > maxScore) {
                maxScore = finalScore;
                winner = eval;
            }
        }

        // Mark winner
        List<CandidateEvaluation> finalEvaluations = new ArrayList<>();
        for (CandidateEvaluation e : evaluations) {
            boolean isW = winner != null && e.armKey().equals(winner.armKey());
            finalEvaluations.add(new CandidateEvaluation(
                    e.armKey(), e.providerSlug(), e.modelId(), e.displayName(),
                    e.qualityScore(), e.costScore(), e.latencyScore(), e.reliabilityScore(),
                    e.healthScore(), e.finalScore(), e.estimatedCostUsd(), e.estimatedLatencyMs(),
                    isW, isW ? "SELECTED WINNER" : e.statusReason()
            ));
        }

        // Sort candidates by finalScore desc
        finalEvaluations.sort(Comparator.comparingDouble(CandidateEvaluation::finalScore).reversed());

        String winnerKey = winner != null ? winner.armKey() : "none";
        String winnerName = winner != null ? winner.displayName() : "None";
        String explanation = "Selected '" + winnerName + "' (" + winnerKey + ") with highest composite score ("
                + String.format("%.3f", maxScore) + ") under current policy weights [Quality: "
                + String.format("%.0f%%", wQ * 100) + ", Cost: " + String.format("%.0f%%", wC * 100)
                + ", Latency: " + String.format("%.0f%%", wL * 100) + ", Reliability: " + String.format("%.0f%%", wR * 100) + "].";

        return new SimulationResult(
                winnerKey, winnerName, explanation,
                Map.of("quality", wQ, "cost", wC, "latency", wL, "reliability", wR),
                finalEvaluations
        );
    }
}
