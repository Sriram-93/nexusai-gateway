package com.llm.nexusai_gateway.Provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class GeminiProvider implements LlmProvider {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Value("${gemini.model}")
    private String model;

    @Value("${gateway.mock-missing-providers:true}")
    private boolean mockEnabled;

    private final WebClient webClient = WebClient.create();

    @Override
    public Mono<ProviderResponse> chat(String message, String modelName) {
        String activeModel = (modelName != null && !modelName.isBlank()) ? modelName : model;
        System.out.println("[PROVIDER CALL] Executing actual Gemini chat call for model: " + activeModel);
        if (apiKey == null || apiKey.isBlank() || "your_gemini_api_key_here".equals(apiKey)) {
            if (mockEnabled) {
                String text = "[MOCK Gemini " + activeModel + "] Here is a simulated response to: \"" + message + "\"";
                return Mono.just(new ProviderResponse(text, estimateTokens(message), estimateTokens(text)));
            }
            return Mono.error(new IllegalArgumentException("Gemini API key is not configured."));
        }

        // Construct the payload for the Gemini API:
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", message)
                        ))
                )
        );

        // Dynamically resolve URL if a specific model was requested
        String resolvedUrl = apiUrl;
        if (modelName != null && !modelName.isBlank() && !modelName.equalsIgnoreCase(this.model)) {
            resolvedUrl = apiUrl.replace(this.model, modelName);
        }

        // Google Generative AI REST API requires the API key to be passed as a query parameter
        String fullUrl = resolvedUrl + "?key=" + apiKey;

        return webClient.post()
                .uri(fullUrl)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .retry(2)
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
