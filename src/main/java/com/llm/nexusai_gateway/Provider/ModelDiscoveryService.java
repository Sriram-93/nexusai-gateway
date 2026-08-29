package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically discovers models from each registered provider.
 *
 * <p>On application startup and every 24 hours, this service calls each provider's
 * model-listing API and upserts the results into the {@code registered_models} table.
 * No model name ever needs to be hardcoded — new models released by providers
 * appear automatically on the next discovery cycle.</p>
 *
 * <h3>Discovery endpoints used per provider type:</h3>
 * <ul>
 *   <li>OPENAI_COMPATIBLE — {@code GET /v1/models}</li>
 *   <li>GEMINI — {@code GET /v1beta/models?key=...}</li>
 *   <li>ANTHROPIC — {@code GET /v1/models}</li>
 *   <li>OLLAMA — {@code GET /api/tags}</li>
 *   <li>BEDROCK / VERTEXAI — Requires SDK; discovery deferred to manual registration</li>
 * </ul>
 */
@Service
public class ModelDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ModelDiscoveryService.class);

    private final ProviderConfigRepository providerConfigRepository;
    private final RegisteredModelRepository modelRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final org.springframework.core.env.Environment env;

    public ModelDiscoveryService(ProviderConfigRepository providerConfigRepository,
                                 RegisteredModelRepository modelRepository,
                                 WebClient.Builder webClientBuilder,
                                 ObjectMapper objectMapper,
                                 org.springframework.core.env.Environment env) {
        this.providerConfigRepository = providerConfigRepository;
        this.modelRepository = modelRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.env = env;
    }

    /**
     * Triggered on startup (after a 10s grace period for DB init)
     * and every 24 hours to pick up newly released models automatically.
     */
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT24H")
    public void discoverAllProviders() {
        List<ProviderConfig> providers = providerConfigRepository.findByEnabledTrue();
        log.info("Starting model discovery for {} active providers.", providers.size());
        for (ProviderConfig provider : providers) {
            try {
                discoverModels(provider);
            } catch (Exception e) {
                log.error("Model discovery failed for provider '{}': {}", provider.getSlug(), e.getMessage());
            }
        }
    }

    /**
     * Discover all models available from a single provider and upsert into DB.
     * Returns the count of new models found.
     */
    public int discoverModels(ProviderConfig provider) {
        log.info("Discovering models for provider '{}' (type={})...", provider.getSlug(), provider.getType());

        List<String> discoveredModelIds = switch (provider.getType()) {
            case OPENAI_COMPATIBLE -> discoverOpenAiCompatible(provider);
            case GEMINI            -> discoverGemini(provider);
            case ANTHROPIC         -> discoverAnthropic(provider);
            case OLLAMA            -> discoverOllama(provider);
            case BEDROCK, VERTEXAI, AZURE -> {
                log.warn("Automatic discovery for {} requires SDK integration or manual mapping. Register models manually via the API.", provider.getType());
                yield List.of();
            }
        };

        int newCount = 0;
        int removedCount = 0;
        
        // 1. Add any newly discovered models that don't exist
        for (String modelId : discoveredModelIds) {
            if (!modelRepository.existsByArmKey(provider.getSlug() + ":" + modelId)) {
                RegisteredModel model = new RegisteredModel(provider.getSlug(), modelId);
                model.setEnabled(false); // Customer must explicitly enable discovered models
                modelRepository.save(model);
                newCount++;
                log.info("  Discovered new model: {}:{}", provider.getSlug(), modelId);
            }
        }

        // 2. Remove any existing models that the provider no longer returns (stale/unauthorized)
        // Only prune when discovery successfully retrieved a non-empty list of models
        if (!discoveredModelIds.isEmpty()) {
            List<RegisteredModel> existingModels = modelRepository.findByProviderSlug(provider.getSlug());
            for (RegisteredModel existing : existingModels) {
                if (!discoveredModelIds.contains(existing.getModelId())) {
                    modelRepository.delete(existing);
                    removedCount++;
                    log.info("  Removed inaccessible/stale model: {}", existing.getArmKey());
                }
            }
        }

        provider.setLastDiscoveredAt(Instant.now());
        providerConfigRepository.save(provider);
        log.info("Discovery complete for '{}': {} new models found, {} removed, {} total active.",
                 provider.getSlug(), newCount, removedCount, discoveredModelIds.size());
        return newCount;
    }

    // ─── Provider-specific discovery implementations ──────────────────────────

    private List<String> discoverOpenAiCompatible(ProviderConfig provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        String url = baseUrl.replaceAll("/+$", "") + "/models";
        try {
            String json = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + provider.getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            List<String> ids = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode model : data) {
                    ids.add(model.path("id").asText());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Failed to fetch OpenAI-compatible models from {}: {}", url, e.getMessage());
            return List.of();
        }
    }

    private List<String> discoverGemini(ProviderConfig provider) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            log.warn("Gemini API key is not set. Skipping discovery.");
            return List.of();
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + provider.getApiKey();
        try {
            String json = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode models = root.path("models");
            List<String> ids = new ArrayList<>();
            if (models.isArray()) {
                for (JsonNode model : models) {
                    // Strip "models/" prefix from Gemini model names
                    String name = model.path("name").asText().replace("models/", "");
                    // Filter for generation-capable models only
                    JsonNode methods = model.path("supportedGenerationMethods");
                    if (methods.isArray()) {
                        for (JsonNode method : methods) {
                            if ("generateContent".equals(method.asText())) {
                                ids.add(name);
                                break;
                            }
                        }
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Failed to fetch Gemini models: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> discoverAnthropic(ProviderConfig provider) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            log.warn("Anthropic API key is not set. Skipping discovery.");
            return List.of();
        }
        try {
            String json = webClient.get()
                .uri("https://api.anthropic.com/v1/models")
                .header("x-api-key", provider.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            List<String> ids = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode model : data) {
                    ids.add(model.path("id").asText());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Failed to fetch Anthropic models: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> discoverOllama(ProviderConfig provider) {
        String baseUrl = provider.getBaseUrl() != null ? provider.getBaseUrl() : "http://localhost:11434";
        String url = baseUrl.stripTrailing() + "/api/tags";
        try {
            String json = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(json);
            JsonNode models = root.path("models");
            List<String> ids = new ArrayList<>();
            if (models.isArray()) {
                for (JsonNode model : models) {
                    ids.add(model.path("name").asText());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Failed to fetch Ollama models from {}: {}", url, e.getMessage());
            return List.of();
        }
    }

    /**
     * Performs a live generation ping against candidate Gemini models,
     * verifies which models are operational (HTTP 200 OK), disables 404/deprecated models,
     * and enables ONLY working reasoning models in the registered_models repository.
     */
    public java.util.Map<String, Object> testAndLoadWorkingGeminiModels(String apiKeyOverride) {
        try {
            String apiKey = apiKeyOverride;
            List<ProviderConfig> configs = providerConfigRepository.findAllBySlug("gemini");
            for (ProviderConfig pc : configs) {
                if (pc.getApiKey() != null && !pc.getApiKey().isBlank()) {
                    apiKey = pc.getApiKey();
                    break;
                }
            }
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Test & Load for Gemini aborted: No API key configured in DB or environment.");
                return java.util.Map.of(
                    "provider", "gemini",
                    "status", "MISSING_KEY",
                    "totalActive", 0,
                    "message", "No API key configured for Gemini. Please add a valid API key first."
                );
            }

            // Save key into provider_configs so all services have access to it
            if (apiKey != null && !apiKey.isBlank()) {
                for (ProviderConfig pc : configs) {
                    if (pc.getApiKey() == null || pc.getApiKey().isBlank()) {
                        pc.setApiKey(apiKey);
                        providerConfigRepository.save(pc);
                    }
                }
            }

            List<String> candidateModelIds = discoverLiveGeminiModels(apiKey);

            List<String> verifiedWorking = new java.util.ArrayList<>();
            List<java.util.Map<String, String>> failedModels = new java.util.ArrayList<>();

            for (String modelId : candidateModelIds) {
                // Pace pings by 250ms to prevent burst rate-limiting on provider API keys
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}

                String testUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + apiKey;
                try {
                    String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"ping\"}]}],\"generationConfig\":{\"maxOutputTokens\":2}}";
                    String response = webClient.post()
                        .uri(testUrl)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(java.time.Duration.ofSeconds(4));

                    if (response != null && response.contains("candidates")) {
                        verifiedWorking.add(modelId);
                        log.info("Gemini model '{}' VERIFIED WORKING (HTTP 200 OK)", modelId);
                    } else {
                        failedModels.add(java.util.Map.of("modelId", modelId, "reason", "Invalid payload response"));
                    }
                } catch (Exception e) {
                    String err = e.getMessage() != null ? e.getMessage() : "HTTP error";
                    if (err.contains("429") || err.contains("Too Many Requests")) {
                        // 429 indicates the model ID IS VALID and OPERATIONAL, but API quota rate-limit was hit!
                        // Keep the model enabled and active!
                        verifiedWorking.add(modelId);
                        log.warn("Gemini model '{}' RATE LIMITED (429) - keeping model ACTIVE in registry.", modelId);
                    } else {
                        log.warn("Gemini model '{}' FAILED live test: {}", modelId, err);
                        failedModels.add(java.util.Map.of("modelId", modelId, "reason", err.contains("404") ? "404 Model Deprecated/Unavailable" : err));
                    }
                }
            }

            // Disable only invalid/deprecated Gemini models
            List<RegisteredModel> existingGeminiModels = modelRepository.findByProviderSlug("gemini");
            for (RegisteredModel rm : existingGeminiModels) {
                if (!verifiedWorking.contains(rm.getModelId())) {
                    rm.setEnabled(false);
                    rm.setHealthStatus("UNREACHABLE");
                    modelRepository.save(rm);
                }
            }

            // Enable and update live-verified & rate-limited working models
            for (String workingId : verifiedWorking) {
                List<RegisteredModel> existing = modelRepository.findAllByProviderSlugAndModelId("gemini", workingId);
                RegisteredModel rm;
                if (existing.isEmpty()) {
                    rm = new RegisteredModel("gemini", workingId);
                } else {
                    rm = existing.get(0);
                    if (existing.size() > 1) {
                        for (int i = 1; i < existing.size(); i++) {
                            try { modelRepository.delete(existing.get(i)); } catch (Exception ignored) {}
                        }
                    }
                }
                rm.setEnabled(true);
                rm.setDisplayName("Google Gemini " + workingId);
                rm.setContextWindowTokens(1048576);
                rm.setEstimatedLatencyMs(400);
                rm.setPricingVerified(true);
                if (rm.getHealthStatus() == null || rm.getHealthStatus().equals("UNKNOWN")) {
                    rm.setHealthStatus("HEALTHY");
                }
                modelRepository.save(rm);
            }

            log.info("Live Gemini Test & Load Complete: {} models verified active, {} disabled.", verifiedWorking.size(), failedModels.size());

            return java.util.Map.of(
                "provider", "gemini",
                "verifiedWorkingModels", verifiedWorking,
                "disabledModels", failedModels,
                "totalActive", verifiedWorking.size(),
                "status", "SUCCESS",
                "message", "Successfully tested and loaded " + verifiedWorking.size() + " working Gemini models into NexusAI Gateway."
            );
        } catch (Exception fatal) {
            log.error("Fatal error during testAndLoadWorkingGeminiModels: ", fatal);
            return java.util.Map.of(
                "status", "ERROR",
                "error", fatal.getClass().getSimpleName() + ": " + fatal.getMessage()
            );
        }
    }

    private List<String> discoverLiveGeminiModels(String apiKey) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
            String res = webClient.get().uri(url).retrieve().bodyToMono(String.class).block(java.time.Duration.ofSeconds(5));
            if (res != null && res.contains("models")) {
                List<String> discovered = new java.util.ArrayList<>();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(res);
                if (root.has("models")) {
                    for (com.fasterxml.jackson.databind.JsonNode m : root.get("models")) {
                        String name = m.path("name").asText("");
                        if (name.startsWith("models/")) name = name.substring(7);
                        // Rely strictly on API provider payload / ping success rather than hardcoded string matching
                        discovered.add(name);
                    }
                }
                if (!discovered.isEmpty()) return discovered;
            }
        } catch (Exception e) {
            log.warn("Could not dynamically list Gemini models: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Performs a live generation ping against candidate models for ANY specified provider,
     * verifies operational endpoints (HTTP 200 OK), disables failing/unauthorized models,
     * and enables ONLY verified working models in the registered_models repository.
     */
    public java.util.Map<String, Object> testAndLoadWorkingModelsForProvider(String slug, String apiKeyOverride) {
        if ("gemini".equalsIgnoreCase(slug) || "google".equalsIgnoreCase(slug)) {
            return testAndLoadWorkingGeminiModels(apiKeyOverride);
        }

        try {
            String apiKey = apiKeyOverride;
            List<ProviderConfig> configs = providerConfigRepository.findAllBySlug(slug);
            for (ProviderConfig pc : configs) {
                if (pc.getApiKey() != null && !pc.getApiKey().isBlank()) {
                    apiKey = pc.getApiKey();
                    break;
                }
            }
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = env.getProperty(slug + ".api.key");
            }
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getenv(slug.toUpperCase().replace("-", "_") + "_API_KEY");
            }

            // Check if provider requires key but none was found
            if ((apiKey == null || apiKey.isBlank()) && !"ollama".equalsIgnoreCase(slug)) {
                for (ProviderConfig pc : configs) {
                    pc.setApiKey(null);
                    providerConfigRepository.save(pc);
                }
                log.warn("Test & Load for provider '{}' aborted: No API key configured.", slug);
                return java.util.Map.of(
                    "provider", slug,
                    "status", "MISSING_KEY",
                    "totalActive", 0,
                    "message", "No API key configured for provider '" + slug + "'. Please add a valid API key first."
                );
            }



            List<String> candidateModelIds = new java.util.ArrayList<>();

            String targetBaseUrl = getBaseUrlForProvider(slug, configs);
            if (apiKey != null && !apiKey.isBlank()) {
                List<String> liveDiscovered = discoverLiveOpenAiCompatibleModels(slug, targetBaseUrl, apiKey);
                if (liveDiscovered != null && !liveDiscovered.isEmpty()) {
                    candidateModelIds.addAll(liveDiscovered);
                }
            }

            // Removed fallback / Supplementary candidates from predefined list to enforce strict dynamic discovery

            // Include models already in database for this provider
            List<String> dbModels = modelRepository.findByProviderSlug(slug).stream()
                .map(RegisteredModel::getModelId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
            for (String dbId : dbModels) {
                if (!candidateModelIds.contains(dbId)) {
                    candidateModelIds.add(dbId);
                }
            }

            List<String> verifiedWorking = new java.util.ArrayList<>();
            List<java.util.Map<String, String>> failedModels = new java.util.ArrayList<>();

            for (String modelId : candidateModelIds) {
                try { Thread.sleep(250); } catch (InterruptedException ignored) {}

                try {
                    boolean success = testProviderModelPing(slug, modelId, apiKey);
                    if (success) {
                        verifiedWorking.add(modelId);
                        log.info("Provider '{}' model '{}' VERIFIED WORKING (HTTP 200 OK)", slug, modelId);
                    } else {
                        failedModels.add(java.util.Map.of("modelId", modelId, "reason", "Invalid response from provider API"));
                    }
                } catch (Exception e) {
                    String err = e.getMessage() != null ? e.getMessage() : "HTTP error";
                    if (err.contains("429") || err.contains("Too Many Requests")) {
                        verifiedWorking.add(modelId);
                        log.warn("Provider '{}' model '{}' RATE LIMITED (429) - keeping model ACTIVE.", slug, modelId);
                    } else {
                        log.warn("Provider '{}' model '{}' FAILED live test: {}", slug, modelId, err);
                        failedModels.add(java.util.Map.of("modelId", modelId, "reason", err.contains("401") ? "401 Unauthorized / Invalid Key" : (err.contains("404") ? "404 Model Unavailable" : err)));
                    }
                }
            }

            // Disable non-working models
            List<RegisteredModel> existing = modelRepository.findByProviderSlug(slug);
            for (RegisteredModel rm : existing) {
                if (!verifiedWorking.contains(rm.getModelId())) {
                    rm.setEnabled(false);
                    rm.setHealthStatus("UNREACHABLE");
                    modelRepository.save(rm);
                }
            }

            // Save key into provider_configs ONLY if verified working models were found
            if (apiKey != null && !apiKey.isBlank() && !verifiedWorking.isEmpty()) {
                for (ProviderConfig pc : configs) {
                    pc.setApiKey(apiKey);
                    providerConfigRepository.save(pc);
                }
            } else if (verifiedWorking.isEmpty()) {
                // If 0 working models found, clear invalid/unverified key from DB
                for (ProviderConfig pc : configs) {
                    pc.setApiKey(null);
                    providerConfigRepository.save(pc);
                }
            }

            // Enable live-verified and rate-limited working models
            for (String workingId : verifiedWorking) {
                List<RegisteredModel> matches = modelRepository.findAllByProviderSlugAndModelId(slug, workingId);
                RegisteredModel rm;
                if (matches.isEmpty()) {
                    rm = new RegisteredModel(slug, workingId);
                } else {
                    rm = matches.get(0);
                    if (matches.size() > 1) {
                        for (int i = 1; i < matches.size(); i++) {
                            try { modelRepository.delete(matches.get(i)); } catch (Exception ignored) {}
                        }
                    }
                }
                rm.setEnabled(true);
                rm.setDisplayName(slug.toUpperCase() + " " + workingId);
                rm.setContextWindowTokens(128000);
                rm.setEstimatedLatencyMs(300);
                rm.setPricingVerified(true);
                if (rm.getHealthStatus() == null || rm.getHealthStatus().equals("UNKNOWN")) {
                    rm.setHealthStatus("HEALTHY");
                }
                modelRepository.save(rm);
            }

            log.info("Live Test & Load Complete for '{}': {} active models, {} failed/disabled.", slug, verifiedWorking.size(), failedModels.size());

            return java.util.Map.of(
                "provider", slug,
                "verifiedWorkingModels", verifiedWorking,
                "disabledModels", failedModels,
                "totalActive", verifiedWorking.size(),
                "status", verifiedWorking.isEmpty() && !candidateModelIds.isEmpty() ? "FAILED" : "SUCCESS",
                "message", verifiedWorking.isEmpty() 
                    ? "No working models found for provider '" + slug + "'. Check API key or endpoint configuration."
                    : "Successfully tested and loaded " + verifiedWorking.size() + " working models for " + slug + "."
            );
        } catch (Exception fatal) {
            log.error("Fatal error during testAndLoadWorkingModelsForProvider (" + slug + "): ", fatal);
            return java.util.Map.of(
                "provider", slug,
                "status", "ERROR",
                "error", fatal.getClass().getSimpleName() + ": " + fatal.getMessage()
            );
        }
    }

    private List<String> discoverLiveOpenAiCompatibleModels(String slug, String baseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return List.of();
        try {
            String url = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
            String res = webClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(5));
            if (res != null && res.contains("data")) {
                List<String> discovered = new java.util.ArrayList<>();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(res);
                if (root.has("data")) {
                    for (com.fasterxml.jackson.databind.JsonNode m : root.get("data")) {
                        String id = m.path("id").asText("");
                        if (!id.isBlank()) {
                            discovered.add(id);
                        }
                    }
                }
                if (!discovered.isEmpty()) return discovered;
            }
        } catch (Exception e) {
            log.warn("Could not dynamically fetch live models for provider '{}': {}", slug, e.getMessage());
        }
        return List.of();
    }

    private String getBaseUrlForProvider(String slug, List<ProviderConfig> configs) {
        if (configs != null) {
            for (ProviderConfig pc : configs) {
                if (pc.getBaseUrl() != null && !pc.getBaseUrl().isBlank()) {
                    return pc.getBaseUrl();
                }
            }
        }
        if ("groq".equalsIgnoreCase(slug)) return "https://api.groq.com/openai/v1";
        if ("openai".equalsIgnoreCase(slug)) return "https://api.openai.com/v1";
        if ("deepseek".equalsIgnoreCase(slug)) return "https://api.deepseek.com/v1";
        if ("together".equalsIgnoreCase(slug)) return "https://api.together.xyz/v1";
        if ("openrouter".equalsIgnoreCase(slug)) return "https://openrouter.ai/api/v1";
        if ("fireworks".equalsIgnoreCase(slug)) return "https://api.fireworks.ai/inference/v1";
        if ("perplexity".equalsIgnoreCase(slug)) return "https://api.perplexity.ai";
        if ("cerebras".equalsIgnoreCase(slug)) return "https://api.cerebras.ai/v1";
        if ("mistral".equalsIgnoreCase(slug)) return "https://api.mistral.ai/v1";
        if ("ollama".equalsIgnoreCase(slug)) return "http://localhost:11434";
        return "https://api.openai.com/v1";
    }

    private boolean testProviderModelPing(String slug, String modelId, String apiKey) throws Exception {
        if ("gemini".equalsIgnoreCase(slug) || "google".equalsIgnoreCase(slug)) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + apiKey;
            String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"ping\"}]}],\"generationConfig\":{\"maxOutputTokens\":2}}";
            String res = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(4));
            return res != null && res.contains("candidates");
        } else if ("anthropic".equalsIgnoreCase(slug)) {
            String jsonBody = "{\"model\":\"" + modelId + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":2}";
            String res = webClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(4));
            return res != null && res.contains("content");
        } else if ("ollama".equalsIgnoreCase(slug)) {
            List<ProviderConfig> configs = providerConfigRepository.findAllBySlug(slug);
            String baseUrl = getBaseUrlForProvider(slug, configs);
            String url = baseUrl.endsWith("/") ? baseUrl + "api/generate" : baseUrl + "/api/generate";
            String jsonBody = "{\"model\":\"" + modelId + "\",\"prompt\":\"ping\",\"stream\":false}";
            String res = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(4));
            return res != null && res.contains("response");
        } else {
            List<ProviderConfig> configs = providerConfigRepository.findAllBySlug(slug);
            String baseUrl = getBaseUrlForProvider(slug, configs);
            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            String jsonBody = "{\"model\":\"" + modelId + "\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":2}";
            String res = webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(4));
            return res != null && res.contains("choices");
        }
    }

    public java.util.Map<String, Object> testAndLoadAllProviders() {
        List<String> distinctSlugs = providerConfigRepository.findAll().stream()
            .map(ProviderConfig::getSlug)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(java.util.stream.Collectors.toList());

        List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (String slug : distinctSlugs) {
            try {
                results.add(testAndLoadWorkingModelsForProvider(slug, null));
            } catch (Exception e) {
                results.add(java.util.Map.of("provider", slug, "status", "ERROR", "error", e.getMessage()));
            }
        }
        return java.util.Map.of(
            "status", "SUCCESS",
            "message", "Tested and updated working models for all registered providers.",
            "results", results
        );
    }

    public java.util.Map<String, Object> testSingleModelHealth(String slug, String modelId, String apiKeyOverride) {
        String apiKey = apiKeyOverride;
        List<ProviderConfig> configs = providerConfigRepository.findAllBySlug(slug);
        for (ProviderConfig pc : configs) {
            if (pc.getApiKey() != null && !pc.getApiKey().isBlank()) {
                apiKey = pc.getApiKey();
                break;
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = env.getProperty(slug + ".api.key");
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv(slug.toUpperCase().replace("-", "_") + "_API_KEY");
        }

        if ((apiKey == null || apiKey.isBlank()) && !"ollama".equalsIgnoreCase(slug)) {
            return java.util.Map.of("modelId", modelId, "provider", slug, "status", "UNAUTHORIZED", "healthy", false, "message", "No API key configured for " + slug);
        }

        long start = System.currentTimeMillis();
        try {
            boolean success = testProviderModelPing(slug, modelId, apiKey);
            long latency = System.currentTimeMillis() - start;
            if (success) {
                return java.util.Map.of("modelId", modelId, "provider", slug, "status", "HEALTHY", "healthy", true, "latencyMs", latency, "message", "Model " + modelId + " verified 200 OK (" + latency + "ms)");
            } else {
                return java.util.Map.of("modelId", modelId, "provider", slug, "status", "UNHEALTHY", "healthy", false, "latencyMs", latency, "message", "Model ping failed response validation");
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String err = e.getMessage() != null ? e.getMessage() : "HTTP error";
            if (err.contains("429") || err.contains("Too Many Requests")) {
                return java.util.Map.of(
                    "modelId", modelId,
                    "provider", slug,
                    "status", "RATE_LIMITED",
                    "healthy", true,
                    "latencyMs", latency,
                    "message", "Model " + modelId + " rate-limited (429), kept active."
                );
            }
            return java.util.Map.of("modelId", modelId, "provider", slug, "status", "UNHEALTHY", "healthy", false, "latencyMs", latency, "error", err, "message", "Ping failed: " + err);
        }
    }
}
