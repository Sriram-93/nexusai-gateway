package com.llm.nexusai_gateway.Provider;

public record ProviderResponse(
    String content,
    int inputTokens,
    int outputTokens
) {}
