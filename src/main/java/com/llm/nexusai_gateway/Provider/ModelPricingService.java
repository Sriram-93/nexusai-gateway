package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.RegisteredModelRepository;
import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import com.llm.nexusai_gateway.Provider.ProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

/**
 * Synchronises model pricing from the community-maintained LiteLLM
 * model price catalogue — updated within hours of any provider price change.
 *
 * <p>Source: {@code https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json}</p>
 *
 * <p>This JSON file is maintained by the open-source community and covers
 * 500+ models across all major providers. It is updated rapidly when providers
 * announce pricing changes, making it far more reliable than any hardcoded values.</p>
 *
 * <h3>Pricing sync schedule:</h3>
 * <ul>
 *   <li>On startup (with a 30s delay to allow DB migration to complete)</li>
 *   <li>Every 12 hours — providers frequently change pricing</li>
 * </ul>
 *
 * <h3>Fallback strategy:</h3>
 * If the remote fetch fails, existing DB pricing is preserved unchanged.
 * Models with no pricing data are flagged with {@code pricingVerified = false}.
 */
@Service
public class ModelPricingService {

    private static final Logger log = LoggerFactory.getLogger(ModelPricingService.class);

    /**
     * The LiteLLM community pricing JSON — 500+ models, updated within hours of provider changes.
     * If your deployment is air-gapped, host a copy of this file internally and update the URL.
     */
    private static final String LITELLM_PRICING_URL =
        "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json";

    private final RegisteredModelRepository modelRepository;
    private final ProviderConfigRepository providerConfigRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ModelPricingService(RegisteredModelRepository modelRepository,
                               ProviderConfigRepository providerConfigRepository,
                               WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.modelRepository = modelRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch the LiteLLM price list and update all registered models whose
     * modelId matches an entry in the JSON.
     *
     * Runs on startup and every 12 hours. Uses a 30s initial delay to ensure
     * model discovery runs first (scheduled for 10s).
     */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT12H")
    public void syncPricingFromLiteLLMScheduled() {
        syncPricingFromLiteLLM().subscribe(
            null,
            e -> log.error("Scheduled pricing sync failed: {}", e.getMessage())
        );
    }

    public reactor.core.publisher.Mono<Void> syncPricingFromLiteLLM() {
        log.info("Starting pricing sync from LiteLLM community catalogue...");
        return webClient.get()
            .uri(LITELLM_PRICING_URL)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(json -> {
                if (json == null || json.isBlank()) {
                    log.warn("LiteLLM pricing JSON was empty. Skipping sync.");
                    return reactor.core.publisher.Mono.empty();
                }
                try {
                    JsonNode catalogue = objectMapper.readTree(json);
                    applyPricingAndDiscover(catalogue);
                    return reactor.core.publisher.Mono.empty();
                } catch (Exception e) {
                    return reactor.core.publisher.Mono.error(new RuntimeException("Failed to parse LiteLLM JSON", e));
                }
            })
            .onErrorResume(e -> {
                log.error("Pricing sync failed: {}. Existing DB pricing preserved.", e.getMessage());
                return reactor.core.publisher.Mono.empty();
            })
            .then();
    }

    /**
     * Manually update pricing for a specific model arm.
     * Use this when a provider announces pricing before the community JSON is updated.
     *
     * @param armKey            e.g. "groq:llama-3.3-70b-versatile"
     * @param inputPricePer1M   USD per 1M input tokens
     * @param outputPricePer1M  USD per 1M output tokens
     */
    public void updatePricingManually(String armKey, double inputPricePer1M, double outputPricePer1M) {
        modelRepository.findByArmKey(armKey).ifPresentOrElse(model -> {
            model.setInputPricePer1M(inputPricePer1M);
            model.setOutputPricePer1M(outputPricePer1M);
            model.setPricingVerified(true);
            model.setPricingUpdatedAt(Instant.now());
            modelRepository.save(model);
            log.info("Manually updated pricing for {}: in=${}/1M out=${}/1M",
                     armKey, inputPricePer1M, outputPricePer1M);
        }, () -> log.warn("Cannot update pricing — arm key '{}' not found in registry.", armKey));
    }

    /**
     * Returns a list of all registered models whose pricing has not been verified.
     * Useful for admin dashboards to flag models that need manual pricing entry.
     */
    public List<RegisteredModel> getUnpricedModels() {
        return modelRepository.findByPricingVerifiedFalse();
    }

    // ─── Internal ──────────────────────────────────────────────────────────────

    private void applyPricingAndDiscover(JsonNode catalogue) {
        List<String> activeProviders = providerConfigRepository.findByEnabledTrue().stream()
            .map(ProviderConfig::getSlug)
            .toList();

        int discoveredCount = 0;
        int pricingUpdatedCount = 0;

        // 1. Passive Discovery: Scan all 500+ models in LiteLLM
        var fields = catalogue.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            JsonNode data = entry.getValue();

            String provider = data.has("litellm_provider") ? data.get("litellm_provider").asText() : "";
            
            // Fallback: parse "provider/model" from key if litellm_provider is missing
            if (provider.isBlank() && key.contains("/")) {
                provider = key.split("/")[0];
            }

            String modelId = key;
            if (!provider.isBlank() && key.startsWith(provider + "/")) {
                modelId = key.substring(provider.length() + 1);
            } else if (key.contains("/")) {
                modelId = key.substring(key.indexOf("/") + 1);
            }

            String armKey = provider.isBlank() ? "unknown:" + modelId : provider + ":" + modelId;
            if (!modelRepository.existsByArmKey(armKey)) {
                RegisteredModel newModel = new RegisteredModel(provider.isBlank() ? "unknown" : provider, modelId);
                newModel.setEnabled(false); // Default to off for safety
                applyEntryToModel(newModel, data);
                modelRepository.save(newModel);
                discoveredCount++;
                log.info("LiteLLM Passive Discovery: Found new model {}", armKey);
            }
        }

        // 2. Pricing Sync for existing models
        List<RegisteredModel> allModels = modelRepository.findAll();
        for (RegisteredModel model : allModels) {
            JsonNode pricing = findPricingEntry(catalogue, model.getProviderSlug(), model.getModelId());
            if (pricing != null) {
                applyEntryToModel(model, pricing);
                modelRepository.save(model);
                pricingUpdatedCount++;
            }
        }

        log.info("Pricing sync & discovery complete. Discovered {} new models. Updated pricing for {} models.", 
                 discoveredCount, pricingUpdatedCount);
    }

    private JsonNode findPricingEntry(JsonNode catalogue, String providerSlug, String modelId) {
        // Strategy 1: exact match on "providerSlug/modelId"
        String prefixedKey = providerSlug + "/" + modelId;
        if (catalogue.has(prefixedKey)) {
            return catalogue.get(prefixedKey);
        }
        // Strategy 2: exact match on bare modelId
        if (catalogue.has(modelId)) {
            return catalogue.get(modelId);
        }
        // Strategy 3: case-insensitive partial match (handles minor name variations)
        String lowerModelId = modelId.toLowerCase();
        var fields = catalogue.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (entry.getKey().toLowerCase().endsWith(lowerModelId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void applyEntryToModel(RegisteredModel model, JsonNode entry) {
        // LiteLLM uses "input_cost_per_token" (per single token)
        // We store as "per 1M tokens" for human readability
        if (entry.has("input_cost_per_token")) {
            double perToken = entry.get("input_cost_per_token").asDouble(0.0);
            model.setInputPricePer1M(perToken * 1_000_000.0);
        }
        if (entry.has("output_cost_per_token")) {
            double perToken = entry.get("output_cost_per_token").asDouble(0.0);
            model.setOutputPricePer1M(perToken * 1_000_000.0);
        }
        if (entry.has("cache_read_input_token_cost")) {
            double perToken = entry.get("cache_read_input_token_cost").asDouble(0.0);
            model.setCachedInputPricePer1M(perToken * 1_000_000.0);
        }
        if (entry.has("max_tokens")) {
            model.setContextWindowTokens(entry.get("max_tokens").asInt(8192));
        }
        model.setPricingVerified(true);
        model.setPricingUpdatedAt(Instant.now());
    }
}
