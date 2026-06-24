package com.llm.nexusai_gateway.Provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class ClaudeProvider implements LlmProvider {

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.model}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:true}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String message, String modelName) {
        if (apiKey == null || apiKey.isBlank() || "your_claude_api_key_here".equals(apiKey)) {
            if (mockEnabled) {
                String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;
                String text = "[MOCK Claude " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("Claude API key is not configured."));
        }

        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;

        Map<String, Object> body = Map.of(
                "model", activeModel,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", message
                        )
                )
        );

        return webClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retry(2)
                .map(response -> {
                    try {
                        List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
                        Map<String, Object> firstContent = contentList.get(0);
                        String contentText = (String) firstContent.get("text");

                        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                        int inputTokens = usage != null ? ((Number) usage.get("input_tokens")).intValue() : estimateTokens(message);
                        int outputTokens = usage != null ? ((Number) usage.get("output_tokens")).intValue() : estimateTokens(contentText);

                        return new ProviderResponse(contentText, inputTokens, outputTokens);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Claude response: " + e.getMessage(), e);
                    }
                });
    }

    @Override
    public boolean supports(String providerName) {
        return "claude".equalsIgnoreCase(providerName) || "anthropic".equalsIgnoreCase(providerName);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
