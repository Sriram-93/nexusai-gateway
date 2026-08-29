package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ModelHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthCheckService.class);

    private final RegisteredModelRepository modelRepository;
    private final ModelDiscoveryService discoveryService;

    // Recent health ping trace logs for UI review (max 100 entries)
    private final List<Map<String, Object>> recentHealthLogs = new CopyOnWriteArrayList<>();

    public ModelHealthCheckService(RegisteredModelRepository modelRepository,
                                  ModelDiscoveryService discoveryService) {
        this.modelRepository = modelRepository;
        this.discoveryService = discoveryService;
    }

    /**
     * Automated scheduled health check running every 5 minutes.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 15000)
    public void runAutomatedHealthCheck() {
        log.info("Starting automated background health check across all enabled models...");
        runFullSystemHealthCheck();
    }

    /**
     * Executes a health scan across all active enabled models.
     */
    public Map<String, Object> runFullSystemHealthCheck() {
        List<RegisteredModel> targetModels = modelRepository.findByEnabledTrue();
        if (targetModels.isEmpty()) {
            targetModels = modelRepository.findAll();
        }
        int healthyCount = 0;
        int degradedCount = 0;
        int unreachableCount = 0;

        List<Map<String, Object>> currentResults = new ArrayList<>();

        for (RegisteredModel model : targetModels) {
            Map<String, Object> pingRes = discoveryService.testSingleModelHealth(model.getProviderSlug(), model.getModelId(), null);
            boolean healthy = Boolean.TRUE.equals(pingRes.get("healthy"));
            String status = (String) pingRes.getOrDefault("status", healthy ? "HEALTHY" : "UNREACHABLE");
            Long latency = pingRes.get("latencyMs") instanceof Number ? ((Number) pingRes.get("latencyMs")).longValue() : 0L;
            String errorMsg = (String) pingRes.get("error");

            if (healthy) {
                if (latency > 2500) {
                    status = "DEGRADED";
                    degradedCount++;
                } else {
                    healthyCount++;
                }
            } else {
                unreachableCount++;
            }

            model.setHealthStatus(status);
            model.setLastHealthCheck(Instant.now());
            model.setLastHealthLatencyMs(latency);
            model.setLastHealthError(healthy ? null : errorMsg);
            modelRepository.save(model);

            Map<String, Object> logEntry = new HashMap<>(pingRes);
            logEntry.put("armKey", model.getArmKey());
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("healthStatus", status);

            currentResults.add(logEntry);

            // Add to circular log buffer
            recentHealthLogs.add(0, logEntry);
            if (recentHealthLogs.size() > 100) {
                recentHealthLogs.remove(recentHealthLogs.size() - 1);
            }
        }

        log.info("System Health Check Complete: {} Healthy, {} Degraded, {} Unreachable",
                healthyCount, degradedCount, unreachableCount);

        return Map.of(
            "timestamp", Instant.now().toString(),
            "totalTested", targetModels.size(),
            "healthyCount", healthyCount,
            "degradedCount", degradedCount,
            "unreachableCount", unreachableCount,
            "details", currentResults
        );
    }

    public List<Map<String, Object>> getRecentHealthLogs() {
        return Collections.unmodifiableList(recentHealthLogs);
    }
}
