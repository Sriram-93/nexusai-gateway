package com.llm.nexusai_gateway.Agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_logs")
public class FeedbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "selected_provider")
    private String selectedProvider;

    @Column(name = "selected_model")
    private String selectedModel;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "cost_usd")
    private double costUsd;

    @Column(name = "accuracy_score")
    private double accuracyScore;

    @Column(name = "user_rating")
    private int userRating;

    @Column(name = "failures", columnDefinition = "TEXT")
    private String failures;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    public FeedbackLog() {}

    public FeedbackLog(String prompt, String selectedProvider, String selectedModel, long latencyMs,
                       double costUsd, double accuracyScore, int userRating, String failures) {
        this.prompt = prompt;
        this.selectedProvider = selectedProvider;
        this.selectedModel = selectedModel;
        this.latencyMs = latencyMs;
        this.costUsd = costUsd;
        this.accuracyScore = accuracyScore;
        this.userRating = userRating;
        this.failures = failures;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getSelectedProvider() { return selectedProvider; }
    public void setSelectedProvider(String selectedProvider) { this.selectedProvider = selectedProvider; }

    public String getSelectedModel() { return selectedModel; }
    public void setSelectedModel(String selectedModel) { this.selectedModel = selectedModel; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public double getCostUsd() { return costUsd; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }

    public double getAccuracyScore() { return accuracyScore; }
    public void setAccuracyScore(double accuracyScore) { this.accuracyScore = accuracyScore; }

    public int getUserRating() { return userRating; }
    public void setUserRating(int userRating) { this.userRating = userRating; }

    public String getFailures() { return failures; }
    public void setFailures(String failures) { this.failures = failures; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
