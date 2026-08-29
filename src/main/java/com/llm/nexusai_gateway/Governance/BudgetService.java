package com.llm.nexusai_gateway.Governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public record BudgetCheckResult(
            boolean allowed,
            double dailyCapUsd,
            double currentDailySpendUsd,
            double monthlyCapUsd,
            double currentMonthlySpendUsd,
            boolean is80PercentWarningTriggered,
            String message
    ) {}

    @Transactional(readOnly = true)
    public BudgetCheckResult checkBudgetAllowed(String targetType, String targetId) {
        Optional<Budget> budgetOpt = budgetRepository.findByTargetTypeAndTargetId(targetType, targetId);
        if (budgetOpt.isEmpty()) {
            // Default: no cap set, allow request
            return new BudgetCheckResult(true, 1000.0, 0.0, 10000.0, 0.0, false, "No budget limit configured");
        }

        Budget b = budgetOpt.get();
        checkAndResetIfNewDayOrMonth(b);

        boolean dailyExceeded = b.getCurrentDailySpendUsd() >= b.getDailyCapUsd();
        boolean monthlyExceeded = b.getCurrentMonthlySpendUsd() >= b.getMonthlyCapUsd();

        boolean is80Pct = (b.getCurrentDailySpendUsd() >= 0.8 * b.getDailyCapUsd()) ||
                          (b.getCurrentMonthlySpendUsd() >= 0.8 * b.getMonthlyCapUsd());

        if (dailyExceeded || monthlyExceeded) {
            String msg = dailyExceeded ? "Daily budget cap exceeded ($" + b.getDailyCapUsd() + ")" : "Monthly budget cap exceeded ($" + b.getMonthlyCapUsd() + ")";
            log.warn("Budget cap breached for targetType={}, targetId={}: {}", targetType, targetId, msg);
            return new BudgetCheckResult(
                    false, b.getDailyCapUsd(), b.getCurrentDailySpendUsd(),
                    b.getMonthlyCapUsd(), b.getCurrentMonthlySpendUsd(),
                    true, msg
            );
        }

        return new BudgetCheckResult(
                true, b.getDailyCapUsd(), b.getCurrentDailySpendUsd(),
                b.getMonthlyCapUsd(), b.getCurrentMonthlySpendUsd(),
                is80Pct, "Budget within allowed thresholds"
        );
    }

    @Transactional
    public void recordSpend(String targetType, String targetId, double costUsd) {
        if (costUsd <= 0) return;
        Budget budget = budgetRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .orElseGet(() -> new Budget(targetType, targetId, 100.0, 2500.0));

        checkAndResetIfNewDayOrMonth(budget);

        budget.setCurrentDailySpendUsd(budget.getCurrentDailySpendUsd() + costUsd);
        budget.setCurrentMonthlySpendUsd(budget.getCurrentMonthlySpendUsd() + costUsd);
        budgetRepository.save(budget);

        log.debug("Recorded spend of ${} for targetType={}, targetId={}. Total daily=${}",
                String.format("%.4f", costUsd), targetType, targetId, String.format("%.2f", budget.getCurrentDailySpendUsd()));
    }

    private void checkAndResetIfNewDayOrMonth(Budget budget) {
        LocalDate lastResetDate = budget.getLastResetAt().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nowDate = LocalDate.now();

        if (nowDate.isAfter(lastResetDate)) {
            budget.setCurrentDailySpendUsd(0.0);
            if (nowDate.getMonth() != lastResetDate.getMonth()) {
                budget.setCurrentMonthlySpendUsd(0.0);
            }
            budget.setLastResetAt(Instant.now());
            budgetRepository.save(budget);
        }
    }
}
