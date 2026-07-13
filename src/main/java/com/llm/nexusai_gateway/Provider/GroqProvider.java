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

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:true}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String message, String modelName) {
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;
        log.info("[PROVIDER CALL] Executing Groq chat for model: {}", activeModel);

        if (apiKey == null || apiKey.isBlank() || "your_groq_api_key_here".equals(apiKey)) {
            if (mockEnabled) {
                String text = "[MOCK Groq " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("Groq API key is not configured."));
        }

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
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)))
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
                        log.warn("Groq real call failed (API key may be invalid or unpaid). Falling back to mock. Error: {}", e.getMessage());
                        String text = "[MOCK Groq " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
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
