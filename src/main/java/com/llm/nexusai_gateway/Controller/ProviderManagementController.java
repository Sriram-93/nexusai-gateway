package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Provider.ModelDiscoveryService;
import com.llm.nexusai_gateway.Provider.ModelPricingService;
import com.llm.nexusai_gateway.Provider.ProviderConfig;
import com.llm.nexusai_gateway.Provider.RegisteredModel;
import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer-facing REST API for managing AI providers and models.
 *
 * <p>This is the <strong>only interface</strong> a customer needs to interact with
 * to plug in new AI providers. Zero Java code changes or deployments required.</p>
 *
 * <h3>Typical customer workflow:</h3>
 * <ol>
 *   <li>{@code POST /api/providers} — Register a new provider with API key</li>
 *   <li>{@code POST /api/providers/{slug}/discover} — Fetch available models</li>
 *   <li>{@code GET  /api/providers/{slug}/models} — Review discovered models</li>
 *   <li>{@code PATCH /api/providers/{slug}/models/{modelId}/enable} — Enable for routing</li>
 *   <li>Done. The gateway now routes to the new model automatically.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderManagementController {

    private static final Logger log = LoggerFactory.getLogger(ProviderManagementController.class);

    private final ProviderConfigRepository providerConfigRepository;
    private final RegisteredModelRepository modelRepository;
    private final ModelDiscoveryService discoveryService;
    private final ModelPricingService pricingService;
    private final com.llm.nexusai_gateway.Security.JwtUtil jwtUtil;

    public ProviderManagementController(ProviderConfigRepository providerConfigRepository,
                                        RegisteredModelRepository modelRepository,
                                        ModelDiscoveryService discoveryService,
                                        ModelPricingService pricingService,
                                        com.llm.nexusai_gateway.Security.JwtUtil jwtUtil) {
        this.providerConfigRepository = providerConfigRepository;
        this.modelRepository = modelRepository;
        this.discoveryService = discoveryService;
        this.pricingService = pricingService;
        this.jwtUtil = jwtUtil;
    }

    // ─── Provider Management ────────────────────────────────────────────────────

    /**
     * Register a new AI provider.
     *
     * <pre>
     * POST /api/providers
     * {
     *   "displayName": "My Groq Account",
     *   "slug": "groq",
     *   "type": "OPENAI_COMPATIBLE",
     *   "baseUrl": "https://api.groq.com/openai/v1",
     *   "apiKey": "gsk_xxxx"
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<?> registerProvider(
            @RequestBody ProviderRegistrationRequest request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        java.util.Map<String, String> creds = request.credentials() != null ? request.credentials() : new java.util.HashMap<>();
        if (request.apiKey() != null && !creds.containsKey("api_key")) {
            creds.put("api_key", request.apiKey());
        }

        // Find existing provider scoped to this tenant
        ProviderConfig config = providerConfigRepository.findBySlugAndTenantId(request.slug(), tenantId)
            .orElse(new ProviderConfig(request.displayName(), request.slug(), request.type(), request.baseUrl(), creds));

        config.setCredentials(creds);
        config.setDisplayName(request.displayName());
        config.setTenantId(tenantId);
        if (request.region() != null) config.setRegion(request.region());
        providerConfigRepository.save(config);
        log.info("Registered/Updated provider: slug='{}', type='{}', tenant='{}'", config.getSlug(), config.getType(), tenantId);

        int newModels = discoveryService.discoverModels(config);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "providerId", config.getId(),
            "slug", config.getSlug(),
            "status", "CONNECTED",
            "modelsDiscovered", newModels,
            "message", "Provider registered. " + newModels + " new models discovered."
        ));
    }

    /** List all registered providers for the calling tenant only. */
    @GetMapping
    public ResponseEntity<?> listProviders(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        List<ProviderSummary> summaries = providerConfigRepository.findByTenantId(tenantId).stream()
            .map(p -> {
                boolean hasKey = p.getApiKey() != null && !p.getApiKey().isBlank();
                int enabledCount = (hasKey && p.isEnabled())
                    ? modelRepository.findByProviderSlugAndEnabledTrue(p.getSlug()).size()
                    : 0;
                return new ProviderSummary(
                    p.getId(), p.getDisplayName(), p.getSlug(), p.getType().name(),
                    p.isEnabled() && hasKey,
                    p.getLastDiscoveredAt() != null ? p.getLastDiscoveredAt().toString() : null,
                    enabledCount,
                    hasKey
                );
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    /**
     * GET /api/providers/status
     * Pre-flight check used by the Sandbox page before allowing a test request.
     * Returns: hasProviders, readyToChat, connectedCount.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getProviderStatus(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        // Allow read even for non-admin roles (Sandbox needs this)
        if (tenantId == null) {
            // Try extracting tenantId from JWT regardless of role for status check
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    tenantId = jwtUtil.extractClaim(token, claims -> claims.get("tenantId", String.class));
                } catch (Exception ignored) {}
            }
        }
        if (tenantId == null) {
            return ResponseEntity.ok(Map.of("hasProviders", false, "readyToChat", false, "connectedCount", 0));
        }

        List<ProviderConfig> providers = providerConfigRepository.findByTenantId(tenantId);
        long connectedCount = providers.stream()
            .filter(p -> p.getApiKey() != null && !p.getApiKey().isBlank())
            .count();
        boolean hasModels = !modelRepository.findByProviderSlugAndEnabledTrue(
            providers.stream().map(ProviderConfig::getSlug).findFirst().orElse("")).isEmpty();
        boolean readyToChat = connectedCount > 0;

        return ResponseEntity.ok(Map.of(
            "hasProviders", !providers.isEmpty(),
            "readyToChat", readyToChat,
            "connectedCount", connectedCount
        ));
    }

    /** Trigger an immediate model re-discovery for a provider (tenant-scoped). */
    @PostMapping("/{slug}/discover")
    public ResponseEntity<?> triggerDiscovery(
            @PathVariable String slug,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        return providerConfigRepository.findBySlugAndTenantId(slug, tenantId)
            .map(provider -> {
                int newModels = discoveryService.discoverModels(provider);
                return ResponseEntity.ok(Map.of(
                    "slug", slug,
                    "newModelsFound", newModels,
                    "message", "Discovery complete. " + newModels + " new models added to registry."
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Trigger a global model re-discovery for all active providers. */
    @PostMapping("/discover-all")
    public ResponseEntity<?> discoverAll() {
        discoveryService.discoverAllProviders();
        return ResponseEntity.ok(Map.of("message", "Global provider discovery initiated for all active providers."));
    }

    /**
     * POST /api/providers/gemini/test-and-load-reasoning
     * Live tests all candidate Gemini models against Google API, filters out 404/deprecated models,
     * and enables ONLY verified working reasoning models for routing.
     */
    @PostMapping("/gemini/test-and-load-reasoning")
    public reactor.core.publisher.Mono<ResponseEntity<?>> testAndLoadGeminiReasoningModels(
            @RequestParam(required = false) String apiKey) {
        return reactor.core.publisher.Mono.fromCallable(() -> discoveryService.testAndLoadWorkingGeminiModels(apiKey))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .map(ResponseEntity::ok);
    }

    /**
     * POST /api/providers/{slug}/test-and-load
     * Live tests candidate models for ANY specified provider against their API,
     * disables unauthorized/failing endpoints, and enables ONLY working models.
     */
    @PostMapping("/{slug}/test-and-load")
    public reactor.core.publisher.Mono<ResponseEntity<?>> testAndLoadProviderModels(
            @PathVariable String slug,
            @RequestParam(required = false) String apiKey) {
        return reactor.core.publisher.Mono.fromCallable(() -> discoveryService.testAndLoadWorkingModelsForProvider(slug, apiKey))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .map(ResponseEntity::ok);
    }

    /**
     * POST /api/providers/test-and-load-all
     * Live tests candidate models across all configured providers.
     */
    @PostMapping("/test-and-load-all")
    public reactor.core.publisher.Mono<ResponseEntity<?>> testAndLoadAllProviders() {
        return reactor.core.publisher.Mono.fromCallable(() -> discoveryService.testAndLoadAllProviders())
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .map(ResponseEntity::ok);
    }

    /** Enable or disable a provider (tenant-scoped). */
    @PatchMapping("/{slug}/enabled")
    public ResponseEntity<?> setProviderEnabled(
            @PathVariable String slug,
            @RequestBody Map<String, Boolean> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        return providerConfigRepository.findBySlugAndTenantId(slug, tenantId)
            .map(provider -> {
                provider.setEnabled(body.getOrDefault("enabled", true));
                providerConfigRepository.save(provider);
                return ResponseEntity.ok(Map.of("slug", slug, "enabled", provider.isEnabled()));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Update provider credentials / API key (tenant-scoped). If key is cleared, disables all models. */
    @PatchMapping("/{slug}/credentials")
    public ResponseEntity<?> updateCredentials(
            @PathVariable String slug,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        return providerConfigRepository.findBySlugAndTenantId(slug, tenantId)
            .map(provider -> {
                String newKey = body.get("apiKey");
                provider.setApiKey(newKey);
                providerConfigRepository.save(provider);

                // If key was cleared, disable all discovered models for this provider
                if (newKey == null || newKey.isBlank()) {
                    List<com.llm.nexusai_gateway.Provider.RegisteredModel> models =
                        modelRepository.findByProviderSlug(slug);
                    models.forEach(m -> m.setEnabled(false));
                    modelRepository.saveAll(models);
                    log.info("API key removed for provider '{}' (tenant {}). Disabled {} models.",
                        slug, tenantId, models.size());
                    return ResponseEntity.ok(Map.of(
                        "message", "Key removed. All models for this provider have been disabled.",
                        "modelsDisabled", models.size()
                    ));
                }
                return ResponseEntity.ok(Map.of("message", "Credentials updated."));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/providers/{slug}
     * Fully removes the provider record and disables all its models.
     */
    @DeleteMapping("/{slug}")
    public ResponseEntity<?> removeProvider(
            @PathVariable String slug,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tenantId = resolveTenantId(tenantIdHeader, authHeader);
        if (tenantId == null) return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));

        return providerConfigRepository.findBySlugAndTenantId(slug, tenantId)
            .map(provider -> {
                // Disable all models for this provider
                List<com.llm.nexusai_gateway.Provider.RegisteredModel> models =
                    modelRepository.findByProviderSlug(slug);
                models.forEach(m -> m.setEnabled(false));
                modelRepository.saveAll(models);

                // Remove the provider config
                providerConfigRepository.delete(provider);
                log.info("Provider '{}' removed for tenant {}. Disabled {} models.", slug, tenantId, models.size());

                return ResponseEntity.ok(Map.<String, Object>of(
                    "message", "Provider removed and all models disabled.",
                    "modelsDisabled", models.size()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Tenant ID Resolution ───────────────────────────────────────────────────

    /**
     * Resolve tenantId from either the X-Tenant-Id header (set by the GatewaySecurityFilter)
     * or directly from a Bearer JWT in the Authorization header.
     */
    private String resolveTenantId(String tenantIdHeader, String authHeader) {
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            return tenantIdHeader;
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String role = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));
                if (role == null || (!role.equals("ORG_ADMIN") && !role.equals("SOLO") && !role.equals("OWNER"))) {
                    log.warn("Access denied. Role {} is not permitted to manage providers.", role);
                    return null; // Deny access by returning null tenantId
                }
                return jwtUtil.extractClaim(token, claims -> claims.get("tenantId", String.class));
            } catch (Exception e) {
                log.warn("Could not extract claims from JWT: {}", e.getMessage());
            }
        }
        return null;
    }

    // ─── Model Management ───────────────────────────────────────────────────────

    /** List all models for a provider (discovered and manually registered). */
    @GetMapping("/{slug}/models")
    public ResponseEntity<?> listModels(@PathVariable String slug) {
        if (!providerConfigRepository.existsBySlug(slug)) {
            return ResponseEntity.notFound().build();
        }
        List<RegisteredModel> models = modelRepository.findByProviderSlug(slug);
        List<ModelSummary> summaries = models.stream()
            .map(m -> new ModelSummary(
                m.getModelId(), m.getArmKey(), m.getDisplayName(),
                m.isEnabled(), m.getInputPricePer1M(), m.getOutputPricePer1M(),
                m.getContextWindowTokens(), m.getEstimatedLatencyMs(), m.isPricingVerified()
            ))
            .collect(Collectors.toList());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Enable a specific model for routing.
     * The gateway will start routing to this model immediately.
     */
    @PatchMapping("/{slug}/models/{modelId}/enable")
    public ResponseEntity<?> enableModel(@PathVariable String slug,
                                         @PathVariable String modelId) {
        return modelRepository.findByProviderSlugAndModelId(slug, modelId)
            .map(model -> {
                model.setEnabled(true);
                modelRepository.save(model);
                log.info("Model enabled for routing: {}", model.getArmKey());
                return ResponseEntity.ok(Map.of(
                    "armKey", model.getArmKey(),
                    "enabled", true,
                    "message", "Model is now active and will be considered by the routing engine."
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /** Disable a model from routing without removing it from the registry. */
    @PatchMapping("/{slug}/models/{modelId}/disable")
    public ResponseEntity<?> disableModel(@PathVariable String slug,
                                           @PathVariable String modelId) {
        return modelRepository.findByProviderSlugAndModelId(slug, modelId)
            .map(model -> {
                model.setEnabled(false);
                modelRepository.save(model);
                return ResponseEntity.ok(Map.of("armKey", model.getArmKey(), "enabled", false));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Manually register a model that wasn't auto-discovered (e.g., Bedrock, VertexAI, or fine-tuned models).
     *
     * <pre>
     * POST /api/providers/bedrock/models
     * {
     *   "modelId": "anthropic.claude-3-5-sonnet-20241022-v2:0",
     *   "displayName": "Claude 3.5 Sonnet (Bedrock)",
     *   "inputPricePer1M": 3.0,
     *   "outputPricePer1M": 15.0,
     *   "contextWindowTokens": 200000,
     *   "estimatedLatencyMs": 1200,
     *   "enabled": true
     * }
     * </pre>
     */
    @PostMapping("/{slug}/models")
    public ResponseEntity<?> registerModel(@PathVariable String slug,
                                            @RequestBody ModelRegistrationRequest request) {
        if (!providerConfigRepository.existsBySlug(slug)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Provider '" + slug + "' not found."));
        }
        String armKey = slug + ":" + request.modelId();
        if (modelRepository.existsByArmKey(armKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Model '" + armKey + "' already registered."));
        }

        RegisteredModel model = new RegisteredModel(slug, request.modelId());
        if (request.displayName() != null) model.setDisplayName(request.displayName());
        if (request.inputPricePer1M() != null) { model.setInputPricePer1M(request.inputPricePer1M()); model.setPricingVerified(true); }
        if (request.outputPricePer1M() != null) model.setOutputPricePer1M(request.outputPricePer1M());
        if (request.contextWindowTokens() != null) model.setContextWindowTokens(request.contextWindowTokens());
        if (request.estimatedLatencyMs() != null) model.setEstimatedLatencyMs(request.estimatedLatencyMs());
        model.setEnabled(request.enabled() != null ? request.enabled() : true);

        modelRepository.save(model);
        log.info("Manually registered model: {}", armKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "armKey", armKey, "enabled", model.isEnabled()
        ));
    }

    /**
     * Update pricing for a specific model.
     * Useful when a provider announces pricing changes before the community sync picks it up.
     */
    @PatchMapping("/{slug}/models/{modelId}/pricing")
    public ResponseEntity<?> updatePricing(@PathVariable String slug,
                                            @PathVariable String modelId,
                                            @RequestBody PricingUpdateRequest request) {
        String armKey = slug + ":" + modelId;
        pricingService.updatePricingManually(armKey, request.inputPricePer1M(), request.outputPricePer1M());
        return ResponseEntity.ok(Map.of(
            "armKey", armKey,
            "inputPricePer1M", request.inputPricePer1M(),
            "outputPricePer1M", request.outputPricePer1M()
        ));
    }

    /** Trigger an immediate pricing sync from LiteLLM community catalogue. */
    @PostMapping("/pricing/sync")
    public reactor.core.publisher.Mono<ResponseEntity<?>> triggerPricingSync() {
        return pricingService.syncPricingFromLiteLLM()
            .then(reactor.core.publisher.Mono.fromCallable(() -> {
                List<RegisteredModel> unpriced = pricingService.getUnpricedModels();
                return ResponseEntity.ok(Map.of(
                    "message", "Pricing sync complete.",
                    "unpricedModels", unpriced.stream().map(RegisteredModel::getArmKey).collect(Collectors.toList())
                ));
            }));
    }

    /** List all models with unverified pricing that need manual attention. */
    @GetMapping("/pricing/unverified")
    public List<String> listUnpricedModels() {
        return pricingService.getUnpricedModels().stream()
            .map(RegisteredModel::getArmKey)
            .collect(Collectors.toList());
    }

    // ─── Request / Response DTOs ────────────────────────────────────────────────

    public record ProviderRegistrationRequest(
        String displayName,
        String slug,
        ProviderConfig.ProviderType type,
        String baseUrl,
        String apiKey,
        String region,
        java.util.Map<String, String> credentials
    ) {}

    public record ModelRegistrationRequest(
        String modelId,
        String displayName,
        Double inputPricePer1M,
        Double outputPricePer1M,
        Integer contextWindowTokens,
        Integer estimatedLatencyMs,
        Boolean enabled
    ) {}

    public record PricingUpdateRequest(double inputPricePer1M, double outputPricePer1M) {}

    public record ProviderSummary(
        Long id, String displayName, String slug, String type,
        boolean enabled, String lastDiscoveredAt, int enabledModelCount, boolean hasKey
    ) {}

    public record ModelSummary(
        String modelId, String armKey, String displayName, boolean enabled,
        double inputPricePer1M, double outputPricePer1M,
        int contextWindowTokens, int estimatedLatencyMs, boolean pricingVerified
    ) {}
}
