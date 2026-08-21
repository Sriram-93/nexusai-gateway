package com.llm.nexusai_gateway.Provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    @Value("${ollama.api.url}")
    private String apiUrl;

    @Value("${ollama.model}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:false}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        log.info("[PROVIDER CALL] Ollama -> model: {}", modelName);
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;

        Map<String, Object> body = Map.of(
                "model", activeModel,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                )
        );

        return webClient.post()
                .uri(apiUrl)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    try {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> responseMessage = (Map<String, Object>) choice.get("message");
                        String contentText = (String) responseMessage.get("content");

                        // Extract token counts from Ollama response keys
                        int inputTokens = response.get("prompt_eval_count") != null ? ((Number) response.get("prompt_eval_count")).intValue() : estimateTokens(message);
                        int outputTokens = response.get("eval_count") != null ? ((Number) response.get("eval_count")).intValue() : estimateTokens(contentText);

                        return new ProviderResponse(contentText, inputTokens, outputTokens);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Ollama response: " + e.getMessage(), e);
                    }
                })
                .onErrorResume(e -> {
                    if (mockEnabled) {
                        org.slf4j.LoggerFactory.getLogger(OllamaProvider.class).warn("Ollama real call failed (local instance might not be running). Falling back to mock. Error: {}", e.getMessage());
                        String text = "[MOCK Ollama " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public boolean supports(String providerName) {
        return "ollama".equalsIgnoreCase(providerName);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
