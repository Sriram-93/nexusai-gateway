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

    public ModelDiscoveryService(ProviderConfigRepository providerConfigRepository,
                                 RegisteredModelRepository modelRepository,
                                 WebClient.Builder webClientBuilder,
                                 ObjectMapper objectMapper) {
        this.providerConfigRepository = providerConfigRepository;
        this.modelRepository = modelRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
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
        if (!discoveredModelIds.isEmpty() || provider.getType() == ProviderConfig.ProviderType.OLLAMA) {
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
}
