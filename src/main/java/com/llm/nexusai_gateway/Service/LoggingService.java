package com.llm.nexusai_gateway.Service;

import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import com.llm.nexusai_gateway.Team.TeamRepository;
import com.llm.nexusai_gateway.Team.Team;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class LoggingService {

    private final RequestLogRepository repository;
    private final MetricsService metricsService;
    private final ModelRegistry modelRegistry;
    private final StringRedisTemplate redisTemplate;
    private final NotificationService notificationService;
    private final TeamRepository teamRepository;

    public LoggingService(RequestLogRepository repository,
                          MetricsService metricsService,
                          ModelRegistry modelRegistry,
                          StringRedisTemplate redisTemplate,
                          NotificationService notificationService,
                          TeamRepository teamRepository) {
        this.repository = repository;
        this.metricsService = metricsService;
        this.modelRegistry = modelRegistry;
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
        this.teamRepository = teamRepository;
    }

    /**
     * Asynchronously saves a request log to the database using a reactive-friendly thread pool.
     */
    public Mono<RequestLog> saveLog(RequestLog log, Integer inputTokens, Integer outputTokens) {
        return Mono.fromCallable(() -> {
            int inTok = (inputTokens != null && inputTokens > 0) ? inputTokens : estimateTokens(log.getPrompt());
            int outTok = (outputTokens != null && outputTokens > 0) ? outputTokens : estimateTokens(log.getResponse());
            log.setTokenUsage(inTok + outTok);
            log.setCostUsd(calculateCost(log.getProvider(), log.getModel(), inTok, outTok));
            if (log.getTimestamp() == null) {
                log.setTimestamp(LocalDateTime.now());
            }
            RequestLog saved = repository.save(log);

            metricsService.recordRequest(saved.getProvider(), saved.getModel(), saved.getPriority(), saved.getStatus());
            metricsService.recordLatency(saved.getProvider(), saved.getLatencyMs());
            metricsService.recordTokens(saved.getProvider(), saved.getTokenUsage());
            metricsService.recordCost(saved.getProvider(), saved.getCostUsd());

            if ("FALLBACK_RECOVERY".equalsIgnoreCase(saved.getStatus())) {
                recordFallbackMetrics(saved);
            }

            // --- REDIS COST ACCUMULATOR ---
            if (saved.getTenantId() != null && saved.getCostUsd() > 0) {
                try {
                    String dateStr = LocalDate.now().toString();
                    String redisKey = "nexus:budget:team:" + saved.getTenantId() + ":" + dateStr;
                    Double currentSpend = redisTemplate.opsForValue().increment(redisKey, saved.getCostUsd());
                    
                    // Check for 80% threshold to alert
                    if (currentSpend != null) {
                        checkAndTriggerBudgetAlert(saved.getTenantId(), currentSpend);
                    }
                } catch (Exception e) {
                    // Ignore Redis failures to not break request logging
                }
            }

            return saved;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void checkAndTriggerBudgetAlert(String tenantId, double currentSpend) {
        teamRepository.findByTenantId(tenantId).ifPresent(team -> {
            if (team.getDailyBudgetUsd() == null) return;
            
            double threshold = team.getDailyBudgetUsd() * 0.8;
            if (currentSpend >= threshold) {
                LocalDate today = LocalDate.now();
                if (team.getBudgetAlertSentDate() == null || !team.getBudgetAlertSentDate().equals(today)) {
                    // Send alert via NotificationService
                    if (team.getLeadEmail() != null) {
                        notificationService.sendBudgetWarning(team.getLeadEmail(), team.getName(), currentSpend, team.getDailyBudgetUsd());
                    }
                    
                    team.setBudgetAlertSentDate(today);
                    teamRepository.save(team);
                }
            }
        });
    }

    public List<RequestLog> getAllLogs() {
        return repository.findAll();
    }

    private void recordFallbackMetrics(RequestLog log) {
        // Derive the expected primary provider dynamically from cheapest registered arm — no hardcoding.
        String expectedPrimary = modelRegistry.getEnabledArmKeysSortedByCost().stream()
            .map(arm -> arm.split(":")[0])
            .findFirst()
            .orElse("unknown");
        if (!expectedPrimary.equalsIgnoreCase(log.getProvider())) {
            metricsService.recordFallback(expectedPrimary, log.getProvider());
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / 4);
    }

    /**
     * Delegates cost calculation entirely to ModelRegistry.
     * No pricing constants live in this class — zero hardcoded rates.
     */
    private double calculateCost(String provider, String model, int inputTokens, int outputTokens) {
        if (provider == null || model == null) return 0.0;
        // Strip parenthetical cache/fallback annotations from the provider string
        String cleanProvider = provider.toLowerCase().replaceAll("\\s*\\(.*?\\)", "").trim();
        return modelRegistry.computeCostUsd(cleanProvider + ":" + model, inputTokens, outputTokens);
    }
}
