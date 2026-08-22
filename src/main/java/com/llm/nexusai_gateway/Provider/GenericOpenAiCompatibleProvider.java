package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A universal, runtime-configured LLM provider adapter for any OpenAI-compatible API.
 *
 * <p>This single class replaces the need for a dedicated Java bean per provider.
 * Any API that follows the OpenAI chat completions schema works with this adapter,
 * including: Groq, Together.ai, Perplexity, Mistral, Fireworks, Anyscale,
 * OpenRouter, LM Studio, vLLM, and custom fine-tuned model servers.</p>
 *
 * <p>Provider configuration (baseUrl, apiKey) is read at request time from
 * {@link ProviderConfigRepository}, not from hard-wired {@code @Value} annotations.
 * This means providers can be added, updated, or disabled without any restart.</p>
 */
@Service
public class GenericOpenAiCompatibleProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GenericOpenAiCompatibleProvider.class);

    private final ProviderConfigRepository providerConfigRepository;
    private final WebClient webClient;

    public GenericOpenAiCompatibleProvider(ProviderConfigRepository providerConfigRepository,
                                           WebClient.Builder webClientBuilder) {
        this.providerConfigRepository = providerConfigRepository;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Execute a chat completion against the provider identified by {@code providerSlug}.
     * The provider's baseUrl and apiKey are read live from the DB.
     */
    /**
     * Primary execution path for dynamically registered providers.
     *
     * @param providerSlug The slug of the registered ProviderConfig (e.g. "my-groq", "together-ai").
     * @param message      The user's prompt.
     * @param modelName    The exact model ID as the provider expects it.
     */
    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        return chatWithKey(providerSlug, message, modelName, null);
    }

    @Override
    public Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String overrideApiKey) {
        return providerConfigRepository.findBySlug(providerSlug) // Note: this fetches global config if tenantId is missing, but it is acceptable if the key is overridden
            .map(config -> executeChat(config, message, modelName, overrideApiKey))
            .orElseGet(() -> Mono.error(new IllegalArgumentException(
                "Provider with slug '" + providerSlug + "' not found in registry.")));
    }

    private Mono<ProviderResponse> executeChat(ProviderConfig config, String message, String modelName, String overrideApiKey) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                "Provider '" + config.getSlug() + "' has no baseUrl configured."));
        }

        String endpoint = baseUrl.stripTrailing();
        if (config.getType() == ProviderConfig.ProviderType.AZURE) {
            // Azure format: https://{resource}.openai.azure.com/openai/deployments/{model}/chat/completions?api-version={api-version}
            String apiVersion = config.getCredentials().getOrDefault("api_version", "2024-02-15-preview");
            if (!endpoint.contains("/deployments/")) {
                endpoint = endpoint + "/openai/deployments/" + modelName + "/chat/completions?api-version=" + apiVersion;
            }
        } else {
            if (!endpoint.endsWith("/chat/completions")) {
                endpoint = endpoint + "/chat/completions";
            }
        }
        
        log.info("[PROVIDER CALL] {} -> {} (model={})", config.getSlug(), endpoint, modelName);

        Map<String, Object> body = Map.of(
            "model", modelName,
            "messages", List.of(Map.of("role", "user", "content", message))
        );

        String activeKey = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : config.getApiKey();

        return webClient.post()
            .uri(endpoint)
            .attribute("providerConfig", config)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + activeKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map.class)
            .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
            .map(this::parseOpenAiResponse)
            .onErrorResume(e -> {
                log.error("Call to '{}' failed: {}", config.getSlug(), e.getMessage());
                return Mono.error(e);
            });
    }

    @SuppressWarnings("unchecked")
    private ProviderResponse parseOpenAiResponse(Map<?, ?> response) {
        try {
            var choices = (List<Map<String, Object>>) response.get("choices");
            var firstChoice = choices.get(0);
            var messageMap = (Map<String, Object>) firstChoice.get("message");
            String content = (String) messageMap.get("content");

            var usage = (Map<String, Object>) response.get("usage");
            int inputTokens = usage != null ? ((Number) usage.get("prompt_tokens")).intValue() : estimateTokens(content);
            int outputTokens = usage != null ? ((Number) usage.get("completion_tokens")).intValue() : estimateTokens(content);

            return new ProviderResponse(content, inputTokens, outputTokens);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI-compatible response: " + e.getMessage(), e);
        }
    }

    /**
     * This adapter handles any provider slug that is registered in the DB with
     * type OPENAI_COMPATIBLE or OLLAMA, and is NOT already handled by a dedicated bean
     * (Groq, Gemini, Anthropic beans take priority via {@link ProviderRegistry}).
     */
    @Override
    public boolean supports(String providerSlug) {
        // Only engage for dynamically registered providers not handled by a dedicated bean.
        // Dedicated beans (groq, gemini, anthropic, claude, openai, ollama) have higher priority
        // in the ProviderRegistry stream and will match first.
        return providerConfigRepository.findBySlug(providerSlug)
            .map(config -> config.getType() == ProviderConfig.ProviderType.OPENAI_COMPATIBLE
                        || config.getType() == ProviderConfig.ProviderType.AZURE
                        || config.getType() == ProviderConfig.ProviderType.OLLAMA)
            .orElse(false);
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }
}
