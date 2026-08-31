package com.llm.nexusai_gateway.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GroqProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);


    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.api.key:}")
    private String defaultApiKey;

    @Value("${gateway.mock-missing-providers:false}")
    private boolean mockEnabled;

    private final WebClient webClient;

    public GroqProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        return chatWithKey(providerSlug, message, modelName, null);
    }

    @Override
    public Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String overrideApiKey) {
        if (modelName == null || modelName.isBlank()) {
            return Mono.error(new IllegalArgumentException("GroqProvider requires a modelName from ModelRegistry."));
        }
        log.info("[PROVIDER CALL] Executing Groq chat for model: {}", modelName);

        String activeKey = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : defaultApiKey;

        if (activeKey == null || activeKey.isBlank() || "your_groq_api_key_here".equals(activeKey)) {
            if (mockEnabled) {
                String text = "[MOCK Groq " + modelName + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("Groq API key is not configured."));
        }

        Map<String, Object> body = Map.of(
                "model", modelName,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                )
        );

        return webClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + activeKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(e -> {
                            if (e instanceof java.io.IOException || e instanceof java.nio.channels.ClosedChannelException || e instanceof io.netty.handler.ssl.SslHandshakeTimeoutException) {
                                return true;
                            }
                            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                org.springframework.web.reactive.function.client.WebClientResponseException ex = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                                return ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
                            }
                            return false;
                        })
                )
                .map(response -> {
                    try {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> responseMessage = (Map<String, Object>) choice.get("message");
                        String contentText = (String) responseMessage.get("content");

                        // Extract token counts
                        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                        int inputTokens = usage != null ? ((Number) usage.get("prompt_tokens")).intValue() : estimateTokens(message);
                        int outputTokens = usage != null ? ((Number) usage.get("completion_tokens")).intValue() : estimateTokens(contentText);

                        return new ProviderResponse(contentText, inputTokens, outputTokens);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Groq response: " + e.getMessage(), e);
                    }
                })
                .onErrorResume(e -> {
                    if (mockEnabled) {
                        log.warn("Groq call to {} failed ({}), falling back to mock response.", modelName, e.getMessage());
                        String text = "[MOCK Groq " + modelName + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
                    log.error("[GROQ ERROR] Call to model {} failed: {}", modelName, e.getMessage());
                    return Mono.error(e);
                });
    }
    @Override
    public boolean supports(String providerName) {
        return "groq".equalsIgnoreCase(providerName);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
