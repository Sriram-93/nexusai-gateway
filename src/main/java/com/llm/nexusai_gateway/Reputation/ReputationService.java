package com.llm.nexusai_gateway.Reputation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages runtime reputation for all registered LLM providers.
 *
 * This service is the bridge between the observability layer (LoggingService)
 * and the decision layer (DecisionEngine). After every completed request,
 * the provider's reputation is updated, closing the feedback loop.
 *
 * Doc 05 Gap 2: "Introduce online learning that continuously updates routing
 * decisions based on observed outcomes."
 */
@Service
public class ReputationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationService.class);

    private final ConcurrentHashMap<String, ProviderReputation> reputations = new ConcurrentHashMap<>();

    /**
     * Update a provider's reputation after a completed request.
     *
     * @param provider  Provider name (e.g., "gemini", "groq")
     * @param quality   Quality score [0.0, 1.0] from QualityEvaluator
     * @param latencyMs Response latency in milliseconds
     * @param costUsd   Cost of this request in USD
     * @param success   Whether the request completed without errors
     */
    public void update(String provider, double quality, long latencyMs, double costUsd, boolean success) {
        String key = provider.toLowerCase();
        ProviderReputation rep = reputations.computeIfAbsent(key, ProviderReputation::new);
        rep.update(quality, latencyMs, costUsd, success);

        log.debug("Updated reputation for {}: health={:.3f}, quality={:.3f}, latency={:.0f}ms",
                  key, rep.getHealthScore(), rep.getAvgQuality(), rep.getAvgLatencyMs());
    }

    /**
     * Get the reputation for a specific provider.
     * Returns a default neutral reputation if the provider has not been observed yet.
     */
    public ProviderReputation get(String provider) {
        return reputations.computeIfAbsent(provider.toLowerCase(), ProviderReputation::new);
    }

    /**
     * Get health scores for all known providers.
     * Used by ContextExtractor to populate providerHealthScores in RequestContext.
     */
    public Map<String, Double> getAllHealthScores() {
        if (reputations.isEmpty()) {
            return Collections.emptyMap();
        }
        return reputations.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getHealthScore()
            ));
    }

    /**
     * Get all provider reputations (for monitoring/debugging).
     */
    public Map<String, ProviderReputation> getAll() {
        return Collections.unmodifiableMap(reputations);
    }
}
