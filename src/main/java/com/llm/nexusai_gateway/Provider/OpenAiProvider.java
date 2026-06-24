package com.llm.nexusai_gateway.Provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class OpenAiProvider implements LlmProvider {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:true}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String message, String modelName) {
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;

        if (apiKey == null || apiKey.isBlank() || "your_openai_api_key_here".equals(apiKey)) {
            if (mockEnabled) {
                String text = "[MOCK OpenAI " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("OpenAI API key is not configured."));
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
                .retry(2)
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
                        throw new RuntimeException("Failed to parse OpenAI response: " + e.getMessage(), e);
                    }
                });
    }

    @Override
    public boolean supports(String providerName) {
        return "openai".equalsIgnoreCase(providerName);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
