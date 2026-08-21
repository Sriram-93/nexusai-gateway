package com.llm.nexusai_gateway.Tenant;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoints for multi-tenant management (Priority 10).
 *
 *  GET  /api/tenants              — list all tenants and their current budget status
 *  GET  /api/tenants/{tenantId}   — get a specific tenant config
 *  POST /api/tenants              — register a new tenant
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRegistry tenantRegistry;

    public TenantController(TenantRegistry tenantRegistry) {
        this.tenantRegistry = tenantRegistry;
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> getAllTenants() {
        List<Map<String, Object>> result = tenantRegistry.getAll().stream()
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tenantId",            t.getTenantId());
                m.put("tenantName",          t.getTenantName());
                m.put("dailyBudgetUsd",      t.getDailyBudgetUsd());
                m.put("remainingBudgetUsd",  Math.round(t.getRemainingBudget() * 10000) / 10000.0);
                m.put("blockedModels",       t.getBlockedModels());
                m.put("allowedModels",       t.getAllowedModels());
                m.put("allowedPipelines",    t.getAllowedPipelines());
                m.put("maxRequestsPerMinute", t.getMaxRequestsPerMinute());
                m.put("piiEnforcement",      t.isPiiEnforcementEnabled());
                m.put("jailbreakEnforcement", t.isJailbreakEnforcementEnabled());
                m.put("hasApiKey",           t.getApiKey() != null && !t.getApiKey().isBlank());
                return m;
            })
            .collect(Collectors.toList());

        return Mono.just(result);
    }

    @GetMapping("/{tenantId}")
    public Mono<Map<String, Object>> getTenant(@PathVariable String tenantId) {
        return tenantRegistry.get(tenantId)
            .map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tenantId",            t.getTenantId());
                m.put("tenantName",          t.getTenantName());
                m.put("dailyBudgetUsd",      t.getDailyBudgetUsd());
                m.put("remainingBudgetUsd",  Math.round(t.getRemainingBudget() * 10000) / 10000.0);
                m.put("blockedModels",       t.getBlockedModels());
                m.put("allowedModels",       t.getAllowedModels());
                m.put("piiEnforcement",      t.isPiiEnforcementEnabled());
                m.put("jailbreakEnforcement", t.isJailbreakEnforcementEnabled());
                m.put("hasApiKey",           t.getApiKey() != null && !t.getApiKey().isBlank());
                return (Map<String, Object>) m;
            })
            .map(Mono::just)
            .orElse(Mono.just(Map.of("error", "Tenant not found: " + tenantId)));
    }
}
