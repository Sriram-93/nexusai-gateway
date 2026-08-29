package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Provider.ModelDiscoveryService;
import com.llm.nexusai_gateway.Provider.ModelHealthCheckService;
import com.llm.nexusai_gateway.Provider.RegisteredModel;
import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models/health")
@CrossOrigin(origins = "*")
public class ModelHealthController {

    private final RegisteredModelRepository modelRepository;
    private final ModelHealthCheckService healthCheckService;
    private final ModelDiscoveryService discoveryService;

    public ModelHealthController(RegisteredModelRepository modelRepository,
                                 ModelHealthCheckService healthCheckService,
                                 ModelDiscoveryService discoveryService) {
        this.modelRepository = modelRepository;
        this.healthCheckService = healthCheckService;
        this.discoveryService = discoveryService;
    }

    /**
     * GET /api/models/health
     * Returns full real-time health metrics across all registered models.
     */
    @GetMapping
    public ResponseEntity<?> getModelHealthOverview() {
        List<RegisteredModel> models = modelRepository.findAll();
        long healthyCount = models.stream().filter(m -> "HEALTHY".equals(m.getHealthStatus())).count();
        long degradedCount = models.stream().filter(m -> "DEGRADED".equals(m.getHealthStatus())).count();
        long unreachableCount = models.stream().filter(m -> "UNREACHABLE".equals(m.getHealthStatus())).count();

        double avgLatency = models.stream()
                .filter(m -> m.getLastHealthLatencyMs() != null && m.getLastHealthLatencyMs() > 0)
                .mapToLong(RegisteredModel::getLastHealthLatencyMs)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of(
            "totalModels", models.size(),
            "healthyCount", healthyCount,
            "degradedCount", degradedCount,
            "unreachableCount", unreachableCount,
            "averageLatencyMs", Math.round(avgLatency),
            "models", models
        ));
    }

    /**
     * POST /api/models/health/run
     * Triggers an immediate manual background health scan across all active models.
     */
    @PostMapping("/run")
    public Mono<ResponseEntity<?>> triggerFullHealthCheck() {
        return Mono.fromCallable(healthCheckService::runFullSystemHealthCheck)
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/models/health/logs
     * Returns recent diagnostic health ping trace logs.
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getHealthLogs() {
        return ResponseEntity.ok(healthCheckService.getRecentHealthLogs());
    }

    /**
     * POST /api/models/health/verify-single
     * Live verifies a single model before manual activation.
     */
    @PostMapping("/verify-single")
    public Mono<ResponseEntity<?>> verifySingleModel(
            @RequestParam String providerSlug,
            @RequestParam String modelId,
            @RequestParam(required = false) String apiKey) {
        return Mono.fromCallable(() -> discoveryService.testSingleModelHealth(providerSlug, modelId, apiKey))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }
}
