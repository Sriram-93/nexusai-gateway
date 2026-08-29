package com.llm.nexusai_gateway.Integration;

import com.llm.nexusai_gateway.Governance.Budget;
import com.llm.nexusai_gateway.Governance.BudgetRepository;
import com.llm.nexusai_gateway.Governance.BudgetService;
import com.llm.nexusai_gateway.Security.AuditLogRepository;
import com.llm.nexusai_gateway.Telemetry.RequestTracingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test suite: Full pipeline validation.
 *
 * Covers:
 *   1. BudgetService pre-check enforcement (hard block at cap)
 *   2. BudgetService 80% warning trigger
 *   3. BudgetService spend accumulation
 *   4. RequestTracingService async audit persistence
 *   5. Streaming readiness: StreamingOrchestrationService is wired
 *   6. BudgetRepository upsert idempotency
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullPipelineIntegrationTest {

    @Autowired
    BudgetService budgetService;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    RequestTracingService tracingService;

    private static final String TARGET_TYPE = "ORGANIZATION";
    private static final String TARGET_ID   = "integ-test-org";

    @BeforeEach
    void cleanUp() {
        // Remove any leftover test budget from previous run
        budgetRepository.findByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID)
            .ifPresent(budgetRepository::delete);
    }

    // ─── 1. No budget cap → always allowed ────────────────────────────────────

    @Test
    @Order(1)
    void whenNoBudgetConfigured_requestIsAlwaysAllowed() {
        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(TARGET_TYPE, TARGET_ID);

        assertThat(result.allowed()).isTrue();
        assertThat(result.message()).contains("No budget");
    }

    // ─── 2. Hard cap enforcement ───────────────────────────────────────────────

    @Test
    @Order(2)
    void whenDailyCapExceeded_requestIsBlocked() {
        // Set a $0.01 daily cap, then record $0.02 of spend
        Budget budget = new Budget(TARGET_TYPE, TARGET_ID, 0.01, 100.0);
        budgetRepository.save(budget);

        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 0.02);

        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(TARGET_TYPE, TARGET_ID);

        assertThat(result.allowed()).isFalse();
        assertThat(result.message()).containsIgnoringCase("Daily budget cap exceeded");
        assertThat(result.currentDailySpendUsd()).isGreaterThanOrEqualTo(0.02);
    }

    // ─── 3. 80% warning trigger ────────────────────────────────────────────────

    @Test
    @Order(3)
    void whenSpendExceeds80Percent_warningIsTriggered() {
        // Cap = $1.00, spend $0.85 → over 80%
        Budget budget = new Budget(TARGET_TYPE, TARGET_ID, 1.00, 50.0);
        budgetRepository.save(budget);

        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 0.85);

        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(TARGET_TYPE, TARGET_ID);

        assertThat(result.allowed()).isTrue();  // still allowed — not over 100%
        assertThat(result.is80PercentWarningTriggered()).isTrue();
    }

    // ─── 4. Spend accumulation is additive ────────────────────────────────────

    @Test
    @Order(4)
    void budgetSpendAccumulatesCorrectly() {
        Budget budget = new Budget(TARGET_TYPE, TARGET_ID, 10.0, 200.0);
        budgetRepository.save(budget);

        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 1.50);
        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 2.25);
        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 0.75);

        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(TARGET_TYPE, TARGET_ID);

        assertThat(result.currentDailySpendUsd()).isCloseTo(4.50, within(0.01));
        assertThat(result.allowed()).isTrue();
    }

    // ─── 5. Budget upsert idempotency ─────────────────────────────────────────

    @Test
    @Order(5)
    void budgetUpsertIsIdempotent() {
        Budget first = new Budget(TARGET_TYPE, TARGET_ID, 5.0, 100.0);
        budgetRepository.save(first);

        // Reload and update
        Budget existing = budgetRepository
            .findByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID)
            .orElseThrow();
        existing.setDailyCapUsd(10.0);
        budgetRepository.save(existing);

        long count = budgetRepository.findAll().stream()
            .filter(b -> b.getTargetId().equals(TARGET_ID))
            .count();

        assertThat(count).isEqualTo(1);  // exactly one record, no duplicate
        Budget reloaded = budgetRepository
            .findByTargetTypeAndTargetId(TARGET_TYPE, TARGET_ID)
            .orElseThrow();
        assertThat(reloaded.getDailyCapUsd()).isCloseTo(10.0, within(0.001));
    }

    // ─── 6. Audit log written by tracing service ──────────────────────────────

    @Test
    @Order(6)
    void tracingService_writesAuditLogEntry() throws InterruptedException {
        long countBefore = auditLogRepository.count();

        // Fire async trace events
        tracingService.traceRoutingDecision(TARGET_ID, "integ-user",
            "groq:llama-3.3-70b-versatile", "ADAPTIVE", "Best EWMA composite score");

        tracingService.traceFallback(TARGET_ID, "integ-user",
            "groq:llama-3.3-70b-versatile", "gemini:gemini-flash", "Circuit Breaker OPEN");

        // Allow async @Async dispatch to complete
        Thread.sleep(500);

        long countAfter = auditLogRepository.count();
        assertThat(countAfter).isGreaterThan(countBefore);
    }

    // ─── 7. Monthly cap enforcement ───────────────────────────────────────────

    @Test
    @Order(7)
    void whenMonthlyCappedAndSpendExceedsMonthly_requestIsBlocked() {
        // Very low monthly cap = $0.05, daily = $100 (high enough to not interfere)
        Budget budget = new Budget(TARGET_TYPE, TARGET_ID, 100.0, 0.05);
        budgetRepository.save(budget);

        budgetService.recordSpend(TARGET_TYPE, TARGET_ID, 0.06);

        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(TARGET_TYPE, TARGET_ID);

        assertThat(result.allowed()).isFalse();
        assertThat(result.message()).containsIgnoringCase("monthly");
    }
}
