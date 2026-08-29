package com.llm.nexusai_gateway.Tenant;

import com.llm.nexusai_gateway.Repository.TenantConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);

    private final TenantConfigRepository tenantConfigRepository;

    public TenantRegistry(TenantConfigRepository tenantConfigRepository) {
        this.tenantConfigRepository = tenantConfigRepository;
    }

    @PostConstruct
    private void seedDemoTenants() {
        if (tenantConfigRepository.count() > 0) {
            log.info("TenantRegistry: Database already contains tenants. Skipping seed.");
            return;
        }

        // Tenant: "enterprise-a" — full access, high budget, all models
        TenantConfig entA = new TenantConfig(
            "enterprise-a",
            "Enterprise Client A",
            "org-ent-a",
            10.00,          // $10/day budget
            List.of(),      // all models allowed
            List.of("openai:gpt-4-turbo"),  // blocked expensive model
            List.of(),      // all pipelines allowed
            200,            // 200 req/min
            true,           // PII enforcement on
            true,           // jailbreak enforcement on
            new double[]{0.60, 0.10, 0.10, 0.20} // [Quality, Latency, Cost, Availability]
        );
        entA.setApiKey(TenantConfig.hashApiKey("nx_live_ent_a_123456789"));
        register(entA);

        // Tenant: "startup-b" — limited budget, fast models only
        TenantConfig startB = new TenantConfig(
            "startup-b",
            "Startup Client B",
            "org-start-b",
            1.00,           // $1/day budget
            List.of(), // Dynamic
            List.of(), // Dynamic
            List.of("DEFAULT", "GREETING"),
            50,             // 50 req/min
            true,
            true,
            new double[]{0.15, 0.30, 0.50, 0.05}
        );
        startB.setApiKey(TenantConfig.hashApiKey("nx_live_start_b_123456789"));
        register(startB);

        // Tenant: "research-c" — no budget cap, but jailbreak must still be enforced
        TenantConfig resC = new TenantConfig(
            "research-c",
            "Research Institution C",
            "org-res-c",
            100.00,         // $100/day
            List.of(),
            List.of(),
            List.of(),
            500,
            false,          // PII enforcement off (internal research data)
            true,
            new double[]{0.70, 0.05, 0.05, 0.20}
        );
        resC.setApiKey(TenantConfig.hashApiKey("nx_live_res_c_123456789"));
        register(resC);

        log.info("TenantRegistry: Seeded demo tenants into PostgreSQL.");
    }

    public void register(TenantConfig config) {
        tenantConfigRepository.save(config);
        log.info("TenantRegistry: Registered tenant '{}' budget=${}/day",
                 config.getTenantId(), config.getDailyBudgetUsd());
    }

    public Optional<TenantConfig> get(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return Optional.empty();
        return tenantConfigRepository.findById(tenantId);
    }

    public Optional<TenantConfig> getByApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) return Optional.empty();
        String hashedKey = TenantConfig.hashApiKey(rawApiKey);
        return tenantConfigRepository.findByApiKey(hashedKey);
    }

    public Optional<TenantConfig> getByOrganizationId(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) return Optional.empty();
        return tenantConfigRepository.findByOrganizationId(organizationId);
    }

    public Collection<TenantConfig> getAll() {
        return tenantConfigRepository.findAll();
    }

    public TenantConfig getOrCreate(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) tenantId = "default-tenant";
        String finalTenantId = tenantId;
        return tenantConfigRepository.findById(tenantId).orElseGet(() -> {
            TenantConfig cfg = new TenantConfig(
                finalTenantId,
                "Default Workspace",
                "org-default",
                100.00,
                List.of(),
                List.of(),
                List.of(),
                500,
                true,
                true,
                new double[]{0.25, 0.25, 0.25, 0.25}
            );
            return tenantConfigRepository.save(cfg);
        });
    }

    public void remove(String tenantId) {
        tenantConfigRepository.deleteById(tenantId);
    }
}
