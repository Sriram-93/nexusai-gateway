package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Agent.AgentRegistry;
import com.llm.nexusai_gateway.Decision.RoutingEngineManager;
import com.llm.nexusai_gateway.Decision.RoutingStrategy;
import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import com.llm.nexusai_gateway.Reputation.ProviderReputation;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Reward.RewardCalculator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.llm.nexusai_gateway.Service.DashboardMetricsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard REST API — exposes real runtime data to the frontend.
 *
 * Every field here is sourced from a live service or database.
 * There are no hardcoded values, no mock data.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RequestLogRepository logRepository;
    private final ReputationService reputationService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RoutingEngineManager routingEngineManager;
    private final RewardCalculator rewardCalculator;
    private final AgentRegistry agentRegistry;
    private final ModelRegistry modelRegistry;
    private final DashboardMetricsService metricsService;
    private final com.llm.nexusai_gateway.Security.JwtUtil jwtUtil;
    private final com.llm.nexusai_gateway.Repository.ProviderConfigRepository providerConfigRepository;

    public DashboardController(RequestLogRepository logRepository,
                               ReputationService reputationService,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               RoutingEngineManager routingEngineManager,
                               RewardCalculator rewardCalculator,
                               AgentRegistry agentRegistry,
                               ModelRegistry modelRegistry,
                               DashboardMetricsService metricsService,
                               com.llm.nexusai_gateway.Security.JwtUtil jwtUtil,
                               com.llm.nexusai_gateway.Repository.ProviderConfigRepository providerConfigRepository) {
        this.logRepository = logRepository;
        this.reputationService = reputationService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.routingEngineManager = routingEngineManager;
        this.rewardCalculator = rewardCalculator;
        this.agentRegistry = agentRegistry;
        this.modelRegistry = modelRegistry;
        this.metricsService = metricsService;
        this.jwtUtil = jwtUtil;
        this.providerConfigRepository = providerConfigRepository;
    }

    // ─── 1. Top-level metric cards ────────────────────────────────────────────

    /**
     * GET /api/dashboard/metrics
     *
     * - totalRequests  → COUNT(*) from request_logs
     * - totalCostUsd   → SUM(cost_usd) from request_logs
     * - avgLatencyMs   → AVG(latency_ms) from request_logs
     * - activeAgents   → AgentRegistry.getOrderedAgents().size()
     * - activeStrategy → RoutingEngineManager.getStrategy().name()
     * - activeEngine   → RoutingEngineManager.getActiveEngineClass()
     * - rewardTier     → RewardCalculator.getActiveTier()
     * - enabledArmCount→ ModelRegistry.getEnabledArmKeys().size()
     */
    @GetMapping("/metrics")
    public Mono<Map<String, Object>> getMetrics(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Mono.fromCallable(() -> {
            io.jsonwebtoken.Claims claims = extractClaims(authHeader);
            if (claims == null) return metricsService.getGlobalMetrics(); // fallback if no auth

            String role = claims.get("role", String.class);
            String tenantId = claims.get("tenantId", String.class);
            String userId = claims.getSubject();

            if ("TEAM_MEMBER".equals(role)) {
                return metricsService.getUserMetrics(tenantId, userId);
            } else {
                return metricsService.getTenantMetrics(tenantId);
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // ─── 2. Provider / Model health ───────────────────────────────────────────

    /**
     * GET /api/dashboard/models
     *
     * Real EWMA reputation stats + circuit breaker state per enabled arm.
     * Returns hasData=false for arms that have not yet received any requests.
     */
    @GetMapping("/models")
    public Mono<List<Map<String, Object>>> getModels(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Mono.fromCallable(() -> {
            io.jsonwebtoken.Claims claims = extractClaims(authHeader);
            String tenantId = claims != null ? claims.get("tenantId", String.class) : null;
            
            Set<String> tenantProviderSlugs = (tenantId != null) 
                ? providerConfigRepository.findByTenantId(tenantId).stream()
                    .filter(p -> p.getApiKey() != null && !p.getApiKey().isBlank() && p.isEnabled())
                    .map(com.llm.nexusai_gateway.Provider.ProviderConfig::getSlug).collect(Collectors.toSet())
                : Collections.emptySet();

            Map<String, ProviderReputation> all = reputationService.getAll();
            List<String> enabledArms = modelRegistry.getEnabledArmKeys();

            return enabledArms.stream()
                .filter(armKey -> {
                    if (tenantId == null) return true; // global view (fallback)
                    String providerSlug = armKey.contains(":") ? armKey.split(":")[0] : armKey;
                    return tenantProviderSlugs.contains(providerSlug);
                })
                .map(armKey -> {
                String providerSlug = armKey.contains(":") ? armKey.split(":")[0] : armKey;
                String modelId      = armKey.contains(":") ? armKey.split(":")[1] : armKey;

                ProviderReputation rep = all.getOrDefault(armKey,
                                             all.getOrDefault(providerSlug, null));

                String cbState = "UNKNOWN";
                try {
                    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(providerSlug);
                    cbState = cb.getState().name();
                } catch (Exception ignored) {}

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("armKey",        armKey);
                row.put("provider",      providerSlug);
                row.put("model",         modelId);
                row.put("cbState",       cbState);
                row.put("healthScore",   rep != null ? rep.getHealthScore()   : null);
                row.put("avgQuality",    rep != null ? rep.getAvgQuality()    : null);
                row.put("avgLatencyMs",  rep != null ? rep.getAvgLatencyMs()  : null);
                row.put("availability",  rep != null ? rep.getAvailability()  : null);
                row.put("failureRate",   rep != null ? rep.getFailureRate()   : null);
                row.put("totalRequests", rep != null ? rep.getTotalRequests() : 0L);
                row.put("hasData",       rep != null && rep.getTotalRequests() > 0);
                return row;
            }).collect(Collectors.toList());

        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // ─── 3. Live activity (last 50 logs) ──────────────────────────────────────

    /**
     * GET /api/dashboard/activity
     *
     * The 50 most recent request_log rows from DB, newest first.
     */
    @GetMapping("/activity")
    public Mono<List<Map<String, Object>>> getActivity(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Mono.fromCallable(() -> {
            io.jsonwebtoken.Claims claims = extractClaims(authHeader);
            List<RequestLog> logs;
            
            if (claims == null) {
                logs = logRepository.findTop50ByOrderByIdDesc();
            } else {
                String role = claims.get("role", String.class);
                String tenantId = claims.get("tenantId", String.class);
                String userId = claims.get("userId", String.class);
                if (userId == null) userId = claims.getSubject();
                
                if ("TEAM_MEMBER".equals(role)) {
                    logs = logRepository.findTop100ByTenantIdAndUserIdOrderByIdDesc(tenantId, userId);
                    if (logs.size() > 50) logs = logs.subList(0, 50);
                } else {
                    logs = logRepository.findTop50ByTenantIdOrderByIdDesc(tenantId);
                }
                if (logs == null) logs = Collections.emptyList();
            }
            return logs.stream().map(log -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id",        log.getId());
                row.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().toString() : null);
                row.put("provider",  log.getProvider());
                row.put("model",     log.getModel());
                row.put("strategy",  log.getPriority());
                row.put("latencyMs", log.getLatencyMs());
                row.put("costUsd",   log.getCostUsd());
                row.put("tokens",    log.getTokenUsage());
                row.put("status",    log.getStatus());
                return row;
            }).collect(Collectors.toList());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }


    // ─── 4. SSE live stream (polls DB every 3 s) ──────────────────────────────

    /**
     * GET /api/dashboard/stream  (text/event-stream)
     *
     * Emits the most recent log row every 3 seconds.
     * Frontend connects via EventSource — no WebSocket needed.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> streamActivity(
            @RequestParam(value = "token", required = false) String token) {
        return Flux.interval(Duration.ofSeconds(3))
            .flatMap(tick -> Mono.fromCallable(() -> {
                io.jsonwebtoken.Claims claims = token != null ? extractClaims("Bearer " + token) : null;
                List<RequestLog> recent;
                
                if (claims == null) {
                    recent = logRepository.findTop50ByOrderByIdDesc();
                } else {
                    String role = claims.get("role", String.class);
                    String tenantId = claims.get("tenantId", String.class);
                    String userId = claims.get("userId", String.class);
                    if (userId == null) userId = claims.getSubject();
                    
                    if ("TEAM_MEMBER".equals(role)) {
                        recent = logRepository.findTop100ByTenantIdAndUserIdOrderByIdDesc(tenantId, userId);
                    } else {
                        recent = logRepository.findTop50ByTenantIdOrderByIdDesc(tenantId);
                    }
                }
                if (recent.isEmpty()) return null;

                RequestLog log = recent.get(0);
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("id",        log.getId());
                event.put("timestamp", log.getTimestamp() != null ? log.getTimestamp().toString() : null);
                event.put("provider",  log.getProvider());
                event.put("model",     log.getModel());
                event.put("latencyMs", log.getLatencyMs());
                event.put("costUsd",   log.getCostUsd());
                event.put("status",    log.getStatus());
                String prompt = log.getPrompt();
                event.put("promptSnippet", prompt != null && prompt.length() > 80
                        ? prompt.substring(0, 80) + "\u2026" : prompt);
                return event;
            }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
            .filter(Objects::nonNull);
    }

    // ─── 5. Learning / bandit state ───────────────────────────────────────────

    /**
     * GET /api/dashboard/learning
     *
     * Returns: active strategy, reward tier, and per-arm EWMA reputation state
     * that represents what the bandit has learned from real interactions.
     */
    @GetMapping("/learning")
    public Mono<Map<String, Object>> getLearningState(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return Mono.fromCallable(() -> {
            io.jsonwebtoken.Claims claims = extractClaims(authHeader);
            String tenantId = claims != null ? claims.get("tenantId", String.class) : null;
            
            Set<String> tenantProviderSlugs = (tenantId != null) 
                ? providerConfigRepository.findByTenantId(tenantId).stream()
                    .filter(p -> p.getApiKey() != null && !p.getApiKey().isBlank() && p.isEnabled())
                    .map(com.llm.nexusai_gateway.Provider.ProviderConfig::getSlug).collect(Collectors.toSet())
                : Collections.emptySet();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("activeStrategy", routingEngineManager.getStrategy().name());
            result.put("activeEngine",   routingEngineManager.getActiveEngineClass());
            result.put("rewardTier",     rewardCalculator.getActiveTier());

            Map<String, ProviderReputation> all = reputationService.getAll();
            List<Map<String, Object>> armStates = all.entrySet().stream()
                .filter(e -> {
                    if (tenantId == null) return true; // global view (fallback)
                    String armKey = e.getKey();
                    String providerSlug = armKey.contains(":") ? armKey.split(":")[0] : armKey;
                    return tenantProviderSlugs.contains(providerSlug);
                })
                .map(e -> {
                ProviderReputation rep = e.getValue();
                Map<String, Object> arm = new LinkedHashMap<>();
                arm.put("armKey",        e.getKey());
                arm.put("healthScore",   rep.getHealthScore());
                arm.put("avgQuality",    rep.getAvgQuality());
                arm.put("avgLatencyMs",  rep.getAvgLatencyMs());
                arm.put("availability",  rep.getAvailability());
                arm.put("failureRate",   rep.getFailureRate());
                
                // For tenant-specific view, overwrite the global request count with tenant's specific traffic if possible
                // (Since Reputation is global EWMA, the scores are global, but we can filter what they see).
                arm.put("totalRequests", rep.getTotalRequests());
                arm.put("successCount",  rep.getSuccessCount());
                arm.put("lastUpdatedMs", rep.getLastUpdatedMs());
                return arm;
            }).collect(Collectors.toList());

            result.put("armStates",        armStates);
            result.put("totalArmsTracked", armStates.size());
            return result;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // ─── 6. Registered agents ─────────────────────────────────────────────────

    /**
     * GET /api/dashboard/agents
     *
     * Real Spring AgentRegistry — returns registered agents with actual
     * execution order, required inputs, and produced outputs.
     */
    @GetMapping("/agents")
    public Mono<List<Map<String, Object>>> getAgents() {
        return Mono.fromCallable(() ->
            agentRegistry.getOrderedAgents().stream().map(agent -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name",            agent.getName());
                row.put("order",           agent.getOrder());
                row.put("dependencies",    agent.getDependencies());
                row.put("requiredInputs",  agent.getRequiredInputs());
                row.put("producedOutputs", agent.getProducedOutputs());
                return row;
            }).collect(Collectors.toList())
        ).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // ─── 7. Runtime strategy switching ────────────────────────────────────────

    /**
     * PATCH /api/dashboard/settings/routing
     *
     * Body: { "strategy": "FEDERATED" } or { "strategy": "WEIGHTED", "weights": { "gemini:gemini-2.5-flash": 0.7, ... } }
     *
     * Switches the active routing engine at runtime — zero restart.
     * The AtomicReference in RoutingEngineManager guarantees thread safety.
     *
     * Valid values: STATIC | RULE_BASED | WEIGHTED | ADAPTIVE | FEDERATED
     */
    @PatchMapping("/settings/routing")
    public ResponseEntity<Map<String, Object>> switchRoutingStrategy(
            @RequestBody Map<String, Object> body) {

        String strategyStr = (String) body.get("strategy");
        if (strategyStr == null || strategyStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Missing 'strategy' field",
                "valid", Arrays.stream(RoutingStrategy.values()).map(Enum::name).toList()
            ));
        }

        RoutingStrategy strategy;
        try {
            strategy = RoutingStrategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Unknown strategy: " + strategyStr,
                "valid", Arrays.stream(RoutingStrategy.values()).map(Enum::name).toList()
            ));
        }

        // Parse optional weights map (only used for WEIGHTED strategy)
        Map<String, Double> weights = null;
        Object rawWeights = body.get("weights");
        if (rawWeights instanceof Map<?, ?> rawMap) {
            weights = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                try {
                    weights.put(String.valueOf(entry.getKey()),
                                Double.parseDouble(String.valueOf(entry.getValue())));
                } catch (NumberFormatException ignored) {}
            }
        }

        String activated = routingEngineManager.switchStrategy(strategy, weights);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",          "switched");
        response.put("activeStrategy",  activated);
        response.put("activeEngine",    routingEngineManager.getActiveEngineClass());
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/dashboard/settings/bandit
     * Tune the LinUCB exploration parameter (alpha).
     */
    @PatchMapping("/settings/bandit")
    public ResponseEntity<?> updateBanditHyperparameters(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("alpha")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field 'alpha'"));
        }
        try {
            double alpha = Double.parseDouble(String.valueOf(body.get("alpha")));
            if (alpha < 0.0 || alpha > 5.0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Alpha must be between 0.0 and 5.0"));
            }
            boolean updated = routingEngineManager.updateBanditAlpha(alpha);
            return ResponseEntity.ok(Map.of(
                "updated", updated,
                "alpha", alpha,
                "activeEngine", routingEngineManager.getActiveEngineClass(),
                "message", updated ? "Bandit exploration alpha updated." : "Active engine is not a LinUCB bandit engine. Switch strategy to ADAPTIVE or FEDERATED first."
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid numeric value for alpha"));
        }
    }

    /**
     * POST /api/dashboard/circuit-breaker/{provider}/trip
     * Manually trip a provider circuit breaker to OPEN state.
     */
    @PostMapping("/circuit-breaker/{provider}/trip")
    public ResponseEntity<?> tripCircuitBreaker(@PathVariable String provider) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
            cb.transitionToOpenState();
            return ResponseEntity.ok(Map.of(
                "provider", provider,
                "cbState", cb.getState().name(),
                "message", "Circuit breaker manually tripped to OPEN."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/dashboard/circuit-breaker/{provider}/reset
     * Manually reset a provider circuit breaker back to CLOSED state.
     */
    @PostMapping("/circuit-breaker/{provider}/reset")
    public ResponseEntity<?> resetCircuitBreaker(@PathVariable String provider) {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(provider);
            cb.transitionToClosedState();
            return ResponseEntity.ok(Map.of(
                "provider", provider,
                "cbState", cb.getState().name(),
                "message", "Circuit breaker manually reset to CLOSED."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private io.jsonwebtoken.Claims extractClaims(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            return jwtUtil.extractClaim(authHeader.substring(7), c -> c);
        } catch (Exception e) {
            return null;
        }
    }
}
