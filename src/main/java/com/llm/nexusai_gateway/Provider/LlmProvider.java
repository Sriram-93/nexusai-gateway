package com.llm.nexusai_gateway.Provider;

import reactor.core.publisher.Mono;

public interface LlmProvider {
    Mono<ProviderResponse> chat(String message, String modelName);
    boolean supports(String providerName);
}
