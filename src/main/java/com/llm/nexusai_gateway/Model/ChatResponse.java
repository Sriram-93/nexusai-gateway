package com.llm.nexusai_gateway.Model;

import java.util.Map;

/**
 * Gateway response model — includes full explainability fields.
 *
 * Improvement 1 + 2: Every response carries the active decision engine,
 * routing reason, reward score, and per-arm UCB score breakdown.
 *
 * This enables the dashboard to show "LinUCB made this decision because..." in real time
 * and provides the qualitative evidence needed for the paper's Section 4.
 *
 * Example JSON:
 * {
 *   "answer": "...",
 *   "provider": "gemini (gemini-2.5-flash)",
 *   "latencyMs": 1234,
 *   "activeEngine": "LINUCB",
 *   "routingReason": "LinUCB: gemini:gemini-2.5-flash scored 0.8471 (expected=0.8041 + exploration=0.0430)",
 *   "rewardScore": 0.834,
 *   "armScores": { "gemini:gemini-2.5-flash": 0.8471, "groq:llama-3.3-70b-versatile": 0.7234 }
 * }
 */
public class ChatResponse {
    private String answer;
    private String provider;
    private long latencyMs;

    // --- Explainability fields (Improvement 1 + 2) ---
    /** The decision engine that made this routing decision: LINUCB, RULE_BASED, WEIGHTED, or STATIC */
    private String activeEngine;

    /** Human-readable explanation of why this provider/model was selected */
    private String routingReason;

    /** The reward score computed after this request completed (0.0–1.0) */
    private double rewardScore;

    /** Per-arm UCB scores from the last LinUCB selection. Null for non-adaptive engines. */
    private Map<String, Double> armScores;

    public ChatResponse() {}

    public ChatResponse(String answer, String provider, long latencyMs) {
        this.answer = answer;
        this.provider = provider;
        this.latencyMs = latencyMs;
    }

    // --- Getters / Setters ---

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String getActiveEngine() { return activeEngine; }
    public void setActiveEngine(String activeEngine) { this.activeEngine = activeEngine; }

    public String getRoutingReason() { return routingReason; }
    public void setRoutingReason(String routingReason) { this.routingReason = routingReason; }

    public double getRewardScore() { return rewardScore; }
    public void setRewardScore(double rewardScore) { this.rewardScore = rewardScore; }

    public Map<String, Double> getArmScores() { return armScores; }
    public void setArmScores(Map<String, Double> armScores) { this.armScores = armScores; }
}
