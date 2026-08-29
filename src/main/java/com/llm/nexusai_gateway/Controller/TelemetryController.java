package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Security.AuditLog;
import com.llm.nexusai_gateway.Security.AuditLogRepository;
import com.llm.nexusai_gateway.Governance.Budget;
import com.llm.nexusai_gateway.Governance.BudgetRepository;
import com.llm.nexusai_gateway.Governance.BudgetService;
import com.llm.nexusai_gateway.Telemetry.TrafficBroadcaster;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Telemetry & Governance Admin API.
 *
 * Exposes:
 *   GET  /api/telemetry/audit-logs          — recent PII-redacted audit trail
 *   GET  /api/telemetry/stream              — SSE real-time traffic stream
 *   GET  /api/telemetry/budget              — current spend ledger summary
 *   POST /api/telemetry/budget              — create/update a budget cap
 *   GET  /api/telemetry/budget/{targetId}   — specific target budget status
 */
import com.llm.nexusai_gateway.Security.RedisRateLimiter;
import com.llm.nexusai_gateway.Telemetry.SyntheticTrafficGenerator;

@RestController
@RequestMapping("/api/telemetry")
@CrossOrigin(origins = "*")
public class TelemetryController {

    private final AuditLogRepository auditLogRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;
    private final TrafficBroadcaster trafficBroadcaster;
    private final SyntheticTrafficGenerator syntheticTrafficGenerator;
    private final RedisRateLimiter redisRateLimiter;

    public TelemetryController(AuditLogRepository auditLogRepository,
                                BudgetRepository budgetRepository,
                                BudgetService budgetService,
                                TrafficBroadcaster trafficBroadcaster,
                                SyntheticTrafficGenerator syntheticTrafficGenerator,
                                RedisRateLimiter redisRateLimiter) {
        this.auditLogRepository = auditLogRepository;
        this.budgetRepository = budgetRepository;
        this.budgetService = budgetService;
        this.trafficBroadcaster = trafficBroadcaster;
        this.syntheticTrafficGenerator = syntheticTrafficGenerator;
        this.redisRateLimiter = redisRateLimiter;
    }

    /**
     * GET /api/telemetry/stream
     * Real-time Server-Sent Events (SSE) stream of all gateway traffic and governance events.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AuditLog> streamTraffic() {
        return trafficBroadcaster.getStream();
    }

    /**
     * GET /api/telemetry/audit-logs
     * Returns the 50 most recent audit trail entries (PII already redacted at write-time).
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getRecentAuditLogs(
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(limit, 200);
        List<AuditLog> logs = auditLogRepository.findAll(
                PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "timestamp"))
        ).getContent();
        return ResponseEntity.ok(logs);
    }

    /**
     * GET /api/telemetry/budget
     * Returns all configured budget entries for dashboard display.
     */
    @GetMapping("/budget")
    public ResponseEntity<List<Budget>> getAllBudgets() {
        return ResponseEntity.ok(budgetRepository.findAll());
    }

    /**
     * POST /api/telemetry/budget
     * Upsert a budget cap for a given target (ORGANIZATION, WORKSPACE, or PROJECT scope).
     * Body: { targetType, targetId, dailyCapUsd, monthlyCapUsd, actionOnExceeded }
     */
    @PostMapping("/budget")
    public ResponseEntity<Budget> upsertBudget(@RequestBody Map<String, Object> body) {
        String targetType = (String) body.getOrDefault("targetType", "ORGANIZATION");
        String targetId   = (String) body.getOrDefault("targetId", "global");
        double dailyCap   = ((Number) body.getOrDefault("dailyCapUsd", 100.0)).doubleValue();
        double monthlyCap = ((Number) body.getOrDefault("monthlyCapUsd", 2500.0)).doubleValue();
        String action     = (String) body.getOrDefault("actionOnExceeded", "BLOCK");

        Budget budget = budgetRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .orElseGet(() -> new Budget(targetType, targetId, dailyCap, monthlyCap));

        budget.setDailyCapUsd(dailyCap);
        budget.setMonthlyCapUsd(monthlyCap);
        budget.setActionOnExceeded(action);
        Budget saved = budgetRepository.save(budget);
        return ResponseEntity.ok(saved);
    }

    /**
     * GET /api/telemetry/budget/{targetId}
     * Returns the real-time budget status for a specific target.
     */
    @GetMapping("/budget/{targetId}")
    public ResponseEntity<Map<String, Object>> getBudgetStatus(
            @PathVariable String targetId,
            @RequestParam(defaultValue = "ORGANIZATION") String targetType) {

        BudgetService.BudgetCheckResult result = budgetService.checkBudgetAllowed(targetType, targetId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("targetType", targetType);
        response.put("targetId", targetId);
        response.put("allowed", result.allowed());
        response.put("dailyCapUsd", result.dailyCapUsd());
        response.put("currentDailySpendUsd", result.currentDailySpendUsd());
        response.put("monthlyCapUsd", result.monthlyCapUsd());
        response.put("currentMonthlySpendUsd", result.currentMonthlySpendUsd());
        response.put("is80PercentWarning", result.is80PercentWarningTriggered());
        response.put("dailyUtilizationPct",
            result.dailyCapUsd() > 0 ? (result.currentDailySpendUsd() / result.dailyCapUsd() * 100.0) : 0.0);
        response.put("message", result.message());

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/telemetry/benchmark
     * Executes synthetic load generator benchmark.
     */
    @PostMapping("/benchmark")
    public reactor.core.publisher.Mono<ResponseEntity<SyntheticTrafficGenerator.BenchmarkResult>> runBenchmark(
            @RequestParam(defaultValue = "10") int requests) {
        int safeCount = Math.min(Math.max(requests, 1), 50);
        return syntheticTrafficGenerator.runBenchmark(safeCount)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/telemetry/rate-limit
     * Obtains real-time sliding window rate limit status for a tenant.
     */
    @GetMapping("/rate-limit")
    public reactor.core.publisher.Mono<ResponseEntity<RedisRateLimiter.RateLimitStatus>> getRateLimitStatus(
            @RequestParam(defaultValue = "default-tenant") String tenantId,
            @RequestParam(defaultValue = "60") int limit) {
        return redisRateLimiter.checkRateLimit(tenantId, limit)
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/telemetry/rate-limit/reset
     * Resets active rate limit counter for a tenant.
     */
    @PostMapping("/rate-limit/reset")
    public reactor.core.publisher.Mono<ResponseEntity<Map<String, Object>>> resetRateLimit(
            @RequestParam(defaultValue = "default-tenant") String tenantId) {
        return redisRateLimiter.resetRateLimit(tenantId)
                .map(success -> ResponseEntity.ok(Map.of(
                        "tenantId", tenantId,
                        "resetSuccess", success,
                        "message", "Rate limit window counter reset."
                )));
    }
}
