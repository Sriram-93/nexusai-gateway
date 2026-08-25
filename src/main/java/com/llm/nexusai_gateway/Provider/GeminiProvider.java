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
public class GeminiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);


    @Value("${gemini.api.url}")
    private String apiBaseUrl;

    @Value("${gemini.api.key:}")
    private String defaultApiKey;

    @Value("${gateway.mock-missing-providers:false}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
        return chatWithKey(providerSlug, message, modelName, null);
    }

    @Override
    public Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String overrideApiKey) {
        if (modelName == null || modelName.isBlank()) {
            return Mono.error(new IllegalArgumentException("GeminiProvider requires a non-null modelName from ModelRegistry."));
        }
        log.info("[PROVIDER CALL] Executing Gemini chat for model: {}", modelName);

        String activeKey = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey : defaultApiKey;

        if (activeKey == null || activeKey.isBlank() || "your_gemini_api_key_here".equals(activeKey)) {
            if (mockEnabled) {
                String text = "[MOCK Gemini " + modelName + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("Gemini API key is not configured."));
        }

        // Build the per-model URL dynamically from the base URL template
        String resolvedUrl = apiBaseUrl + "/" + modelName + ":generateContent?key=" + activeKey;
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", message)
                        ))
                )
        );

        return webClient.post()
                .uri(resolvedUrl)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(e -> {
                            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                org.springframework.web.reactive.function.client.WebClientResponseException ex = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                                return ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
                            }
                            return true; // Retry on IOExceptions, connection resets, etc.
                        })
                )
                .map(response -> {
                    try {
                        List<Map> candidates = (List<Map>) response.get("candidates");
                        if (candidates == null || candidates.isEmpty()) {
                            return new ProviderResponse("Error: No candidates returned from Gemini.", 0, 0);
                        }
                        Map firstCandidate = candidates.get(0);
                        Map contentMap = (Map) firstCandidate.get("content");
                        if (contentMap == null) {
                            return new ProviderResponse("Error: No content returned from Gemini.", 0, 0);
                        }
                        List<Map> partsList = (List<Map>) contentMap.get("parts");
                        if (partsList == null || partsList.isEmpty()) {
                            return new ProviderResponse("Error: No parts returned from Gemini.", 0, 0);
                        }
                        Map firstPart = partsList.get(0);
                        String contentText = (String) firstPart.get("text");

                        // Extract token counts
                        Map<String, Object> usage = (Map<String, Object>) response.get("usageMetadata");
                        int inputTokens = usage != null ? ((Number) usage.get("promptTokenCount")).intValue() : estimateTokens(message);
                        int outputTokens = usage != null ? ((Number) usage.get("candidatesTokenCount")).intValue() : estimateTokens(contentText);

                        return new ProviderResponse(contentText, inputTokens, outputTokens);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage(), e);
                    }
                })
                .onErrorResume(e -> {
                    if (mockEnabled) {
                        log.warn("Gemini real call failed (API key may be invalid or unpaid). Falling back to mock. Error: {}", e.getMessage());
                        String text = "[MOCK Gemini " + modelName + "] Here is a simulated response to: \"" + message + "\"";
                        return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public boolean supports(String providerName) {
        return "gemini".equalsIgnoreCase(providerName);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
