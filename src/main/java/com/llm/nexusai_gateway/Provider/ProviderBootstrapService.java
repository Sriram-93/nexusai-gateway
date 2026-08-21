package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * Bootstraps the provider system on application startup.
 *
 * <h3>Responsibilities:</h3>
 * <ol>
 *   <li>Seeds the {@code registered_models} table from {@code application.properties} if empty.</li>
 *   <li>Seeds the {@code provider_configs} table with built-in provider stubs if empty,
 *       so the management API shows them even before a customer explicitly registers them.</li>
 * </ol>
 *
 * <p>This service runs exactly once on startup via {@link ApplicationRunner}.
 * After the first run, all data comes from the DB and config is ignored.</p>
 */
@Service
public class ProviderBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderBootstrapService.class);

    private final ModelRegistry modelRegistry;
    private final ProviderConfigRepository providerConfigRepository;
    private final org.springframework.core.env.Environment env;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ProviderBootstrapService(ModelRegistry modelRegistry,
                                    ProviderConfigRepository providerConfigRepository,
                                    org.springframework.core.env.Environment env,
                                    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.modelRegistry = modelRegistry;
        this.providerConfigRepository = providerConfigRepository;
        this.env = env;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running provider bootstrap...");
        seedBuiltInProviders();
        
        // Migrate existing tenants to have their own providers
        try {
            java.util.List<String> tenantIds = jdbcTemplate.queryForList("SELECT tenant_id FROM tenant_registry", String.class);
            for (String tid : tenantIds) {
                seedProvidersForTenant(tid);
            }
        } catch(Exception e) {
            log.warn("Could not migrate tenants: {}", e.getMessage());
        }

        int seeded = modelRegistry.seedFromConfig();
        if (seeded > 0) {
            log.info("Seeded {} models from application.properties into DB.", seeded);
        } else {
            log.info("DB already populated — skipping config seed.");
        }
    }

    /**
     * Ensure built-in providers (Groq, Gemini, Anthropic, etc.) exist in the
     * provider_configs table so they appear in the management API.
     * API keys are left blank — customers fill them in via environment variables
     * or update them via PATCH /api/providers/{slug}.
     */
    private void seedBuiltInProviders() {
        seedProviderIfAbsent("groq", "Groq",
            ProviderConfig.ProviderType.OPENAI_COMPATIBLE,
            "https://api.groq.com/openai/v1", null);

        seedProviderIfAbsent("gemini", "Google Gemini",
            ProviderConfig.ProviderType.GEMINI,
            null, null);

        seedProviderIfAbsent("anthropic", "Anthropic Claude",
            ProviderConfig.ProviderType.ANTHROPIC,
            null, null);

        seedProviderIfAbsent("openai", "OpenAI",
            ProviderConfig.ProviderType.OPENAI_COMPATIBLE,
            "https://api.openai.com/v1", null);

        seedProviderIfAbsent("ollama", "Ollama (Local)",
            ProviderConfig.ProviderType.OLLAMA,
            "http://localhost:11434", null);
    }

    private void seedProviderIfAbsent(String slug, String displayName,
                                       ProviderConfig.ProviderType type,
                                       String baseUrl, String apiKey) {
        ProviderConfig config = providerConfigRepository.findBySlugAndTenantId(slug, null).orElse(null);
        if (config == null) {
            config = new ProviderConfig();
            config.setDisplayName(displayName);
            config.setSlug(slug);
            config.setType(type);
            config.setBaseUrl(baseUrl);
            if (apiKey != null) config.setApiKey(apiKey);
            providerConfigRepository.save(config);
            log.info("Seeded built-in provider: {} ({})", slug, type);
        } else if (apiKey != null && !apiKey.isBlank() && (config.getApiKey() == null || config.getApiKey().isBlank())) {
            config.setApiKey(apiKey);
            providerConfigRepository.save(config);
            log.info("Updated existing provider '{}' with API key from environment", slug);
        }
    }

    /**
     * Seeds the built-in providers for a specific tenant when a new workspace is created.
     */
    public void seedProvidersForTenant(String tenantId) {
        seedTenantProviderIfAbsent(tenantId, "groq", "Groq", ProviderConfig.ProviderType.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1");
        seedTenantProviderIfAbsent(tenantId, "gemini", "Google Gemini", ProviderConfig.ProviderType.GEMINI, null);
        seedTenantProviderIfAbsent(tenantId, "anthropic", "Anthropic Claude", ProviderConfig.ProviderType.ANTHROPIC, null);
        seedTenantProviderIfAbsent(tenantId, "openai", "OpenAI", ProviderConfig.ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1");
        seedTenantProviderIfAbsent(tenantId, "ollama", "Ollama (Local)", ProviderConfig.ProviderType.OLLAMA, "http://localhost:11434");
    }

    private void seedTenantProviderIfAbsent(String tenantId, String slug, String displayName, ProviderConfig.ProviderType type, String baseUrl) {
        if (!providerConfigRepository.existsBySlugAndTenantId(slug, tenantId)) {
            ProviderConfig config = new ProviderConfig();
            config.setDisplayName(displayName);
            config.setSlug(slug);
            config.setType(type);
            config.setBaseUrl(baseUrl);
            config.setTenantId(tenantId);
            providerConfigRepository.save(config);
            log.info("Seeded provider {} for tenant {}", slug, tenantId);
        }
    }
}
