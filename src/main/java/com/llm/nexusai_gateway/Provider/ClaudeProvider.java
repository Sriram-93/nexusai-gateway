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
public class ClaudeProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);



    @Value("${claude.api.url}")
    private String apiUrl;

    @Value("${claude.api.key:}")
    private String defaultApiKey;

    @Value("${claude.model:claude-3-5-sonnet-20241022}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:false}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        return chatWithKey(providerSlug, message, modelName, null);
    }

    @Override
    public Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String overrideApiKey) {
        log.info("[PROVIDER CALL] Claude -> model: {}", modelName);
        String activeKey = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : defaultApiKey;
        if (activeKey == null || activeKey.isBlank() || "your_claude_api_key_here".equals(activeKey)) {
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
                .header("x-api-key", activeKey)
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
                })
                .onErrorResume(e -> {
                    if (mockEnabled) {
                        org.slf4j.LoggerFactory.getLogger(ClaudeProvider.class).warn("Claude real call failed (API key may be invalid or unpaid). Falling back to mock. Error: {}", e.getMessage());
                        String text = "[MOCK Claude " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
                    return Mono.error(e);
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
