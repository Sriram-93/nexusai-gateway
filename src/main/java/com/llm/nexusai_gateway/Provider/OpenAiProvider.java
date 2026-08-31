package com.llm.nexusai_gateway.Provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OpenAiProvider implements LlmProvider, StreamingLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);


    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.key:}")
    private String defaultApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String defaultModel;

    @Value("${gateway.mock-missing-providers:false}")
    private boolean mockEnabled;

    private final WebClient webClient;

    public OpenAiProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        return chatWithKey(providerSlug, message, modelName, null);
    }

    @Override
    public Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String overrideApiKey) {
        log.info("[PROVIDER CALL] OpenAi -> model: {}", modelName);
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;
        String activeKey = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : defaultApiKey;

        if (activeKey == null || activeKey.isBlank() || "your_openai_api_key_here".equals(activeKey)) {
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
                .header("Authorization", "Bearer " + activeKey)
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
                })
                .onErrorResume(e -> {
                    if (mockEnabled) {
                        org.slf4j.LoggerFactory.getLogger(OpenAiProvider.class).warn("OpenAI real call failed (API key may be invalid or unpaid). Falling back to mock. Error: {}", e.getMessage());
                        String text = "[MOCK OpenAI " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public boolean supports(String providerName) {
        return "openai".equalsIgnoreCase(providerName);
    }

    @Override
    public Flux<String> streamChat(String providerSlug, String message, String modelName, String runtimeApiKey) {
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : defaultModel;
        String activeKey = (runtimeApiKey != null && !runtimeApiKey.isBlank()) ? runtimeApiKey : defaultApiKey;

        if (activeKey == null || activeKey.isBlank() || "your_openai_api_key_here".equals(activeKey)) {
            // Fallback: simulate streaming from mock
            String mockText = "[MOCK OpenAI " + activeModel + "] Streamed response to: " + message;
            return simulateWordStream(mockText);
        }

        // Build request body with stream: true
        String streamUrl = apiUrl.replace("/chat/completions", "") + "/chat/completions";
        Map<String, Object> body = Map.of(
            "model", activeModel,
            "stream", true,
            "messages", List.of(Map.of("role", "user", "content", message))
        );

        return webClient.post()
            .uri(streamUrl)
            .header("Authorization", "Bearer " + activeKey)
            .header("Content-Type", "application/json")
            .bodyValue(body)
            .retrieve()
            // OpenAI sends "text/event-stream" lines — read as raw Strings
            .bodyToFlux(String.class)
            .filter(line -> line.startsWith("data: ") && !line.equals("data: [DONE]"))
            .map(line -> line.substring(6).trim())  // strip "data: " prefix
            .flatMap(json -> {
                try {
                    // Parse {"choices":[{"delta":{"content":"..."},"finish_reason":null}]}
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta != null && delta.get("content") != null) {
                            return Flux.just((String) delta.get("content"));
                        }
                    }
                } catch (Exception ignored) {}
                return Flux.<String>empty();
            })
            .onErrorResume(e -> {
                log.warn("[STREAM] OpenAI streaming error: {}. Falling back to simulated stream.", e.getMessage());
                // Fallback to buffered response
                return chatWithKey(providerSlug, message, activeModel, activeKey)
                    .flatMapMany(resp -> simulateWordStream(resp.content()));
            });
    }

    private Flux<String> simulateWordStream(String text) {
        if (text == null || text.isBlank()) return Flux.empty();
        String[] parts = text.split("(?<=\\s)|(?=\\s)");
        return Flux.fromArray(parts).filter(s -> !s.isEmpty());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / 4);
    }
}
