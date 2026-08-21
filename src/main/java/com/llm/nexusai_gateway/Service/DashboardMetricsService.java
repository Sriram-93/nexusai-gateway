package com.llm.nexusai_gateway.Service;

import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import com.llm.nexusai_gateway.Decision.RoutingEngineManager;
import com.llm.nexusai_gateway.Agent.AgentRegistry;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Reward.RewardCalculator;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardMetricsService {
    private final RequestLogRepository logRepository;
    private final RoutingEngineManager routingEngineManager;
    private final AgentRegistry agentRegistry;
    private final ModelRegistry modelRegistry;
    private final RewardCalculator rewardCalculator;

    public DashboardMetricsService(RequestLogRepository logRepository,
                                   RoutingEngineManager routingEngineManager,
                                   AgentRegistry agentRegistry,
                                   ModelRegistry modelRegistry,
                                   RewardCalculator rewardCalculator) {
        this.logRepository = logRepository;
        this.routingEngineManager = routingEngineManager;
        this.agentRegistry = agentRegistry;
        this.modelRegistry = modelRegistry;
        this.rewardCalculator = rewardCalculator;
    }

    public Map<String, Object> getGlobalMetrics() {
        return getTenantMetrics(null);
    }

    public Map<String, Object> getTenantMetrics(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();

        long totalRequests = tenantId == null ? logRepository.count() : logRepository.countByTenantId(tenantId);
        Double rawCost    = tenantId == null ? logRepository.sumCostUsd() : logRepository.sumCostUsdByTenant(tenantId);
        Double rawLatency = tenantId == null ? logRepository.avgLatencyMs() : logRepository.avgLatencyMsByTenant(tenantId);

        m.put("totalRequests",   totalRequests);
        m.put("totalCostUsd",    rawCost    != null ? rawCost    : 0.0);
        m.put("avgLatencyMs",    rawLatency != null ? rawLatency : 0.0);
        m.put("activeAgents",    agentRegistry.getOrderedAgents().size());
        m.put("activeStrategy",  routingEngineManager.getStrategy().name());
        m.put("activeEngine",    routingEngineManager.getActiveEngineClass());
        m.put("rewardTier",      rewardCalculator.getActiveTier());
        m.put("enabledArmCount", modelRegistry.getEnabledArmKeys().size());

        return m;
    }

    public Map<String, Object> getUserMetrics(String tenantId, String userId) {
        Map<String, Object> m = new LinkedHashMap<>();

        long totalRequests = logRepository.countByTenantIdAndUserId(tenantId, userId);
        Double rawCost    = logRepository.sumCostUsdByTenantAndUser(tenantId, userId);
        Double rawLatency = logRepository.avgLatencyMsByTenantAndUser(tenantId, userId);

        m.put("totalRequests",   totalRequests);
        m.put("totalCostUsd",    rawCost    != null ? rawCost    : 0.0);
        m.put("avgLatencyMs",    rawLatency != null ? rawLatency : 0.0);
        
        // Members don't get infra stats, just their own usage
        m.put("activeAgents",    0);
        m.put("activeStrategy",  "PERSONAL_SANDBOX");
        m.put("activeEngine",    "ISOLATED_WORKSPACE");
        m.put("rewardTier",      "STANDARD");
        m.put("enabledArmCount", modelRegistry.getEnabledArmKeys().size());

        return m;
    }
}
