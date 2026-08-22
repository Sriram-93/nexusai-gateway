package com.llm.nexusai_gateway.Provider;

import reactor.core.publisher.Mono;

public interface LlmProvider {
    Mono<ProviderResponse> chat(String providerSlug, String message, String modelName);
    boolean supports(String providerName);

    /**
     * Chat with an explicit runtime API key (per-tenant BYOK).
     * Providers that override this can use the tenant's key from ProviderConfig
     * instead of the global @Value field.
     * Default implementation falls back to the global-key variant.
     */
    default Mono<ProviderResponse> chatWithKey(String providerSlug, String message, String modelName, String runtimeApiKey) {
        return chat(providerSlug, message, modelName);
    }
}
