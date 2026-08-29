package com.llm.nexusai_gateway.Governance;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budgets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"target_type", "target_id"})
})
public class Budget {

    @Id
    private String id;

    /** "ORGANIZATION", "WORKSPACE", or "PROJECT" */
    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(nullable = false)
    private double dailyCapUsd = 100.0;

    @Column(nullable = false)
    private double monthlyCapUsd = 2500.0;

    @Column(nullable = false)
    private double currentDailySpendUsd = 0.0;

    @Column(nullable = false)
    private double currentMonthlySpendUsd = 0.0;

    @Column(nullable = false)
    private String actionOnExceeded = "BLOCK"; // "BLOCK", "ALERT_ONLY", "DOWNGRADE_MODEL"

    @Column(nullable = false)
    private Instant lastResetAt = Instant.now();

    public Budget() {
        this.id = UUID.randomUUID().toString();
    }

    public Budget(String targetType, String targetId, double dailyCapUsd, double monthlyCapUsd) {
        this.id = UUID.randomUUID().toString();
        this.targetType = targetType;
        this.targetId = targetId;
        this.dailyCapUsd = dailyCapUsd;
        this.monthlyCapUsd = monthlyCapUsd;
        this.currentDailySpendUsd = 0.0;
        this.currentMonthlySpendUsd = 0.0;
        this.actionOnExceeded = "BLOCK";
        this.lastResetAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public double getDailyCapUsd() { return dailyCapUsd; }
    public void setDailyCapUsd(double dailyCapUsd) { this.dailyCapUsd = dailyCapUsd; }

    public double getMonthlyCapUsd() { return monthlyCapUsd; }
    public void setMonthlyCapUsd(double monthlyCapUsd) { this.monthlyCapUsd = monthlyCapUsd; }

    public double getCurrentDailySpendUsd() { return currentDailySpendUsd; }
    public void setCurrentDailySpendUsd(double currentDailySpendUsd) { this.currentDailySpendUsd = currentDailySpendUsd; }

    public double getCurrentMonthlySpendUsd() { return currentMonthlySpendUsd; }
    public void setCurrentMonthlySpendUsd(double currentMonthlySpendUsd) { this.currentMonthlySpendUsd = currentMonthlySpendUsd; }

    public String getActionOnExceeded() { return actionOnExceeded; }
    public void setActionOnExceeded(String actionOnExceeded) { this.actionOnExceeded = actionOnExceeded; }

    public Instant getLastResetAt() { return lastResetAt; }
    public void setLastResetAt(Instant lastResetAt) { this.lastResetAt = lastResetAt; }
}
