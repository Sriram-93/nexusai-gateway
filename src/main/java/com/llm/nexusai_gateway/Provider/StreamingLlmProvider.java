package com.llm.nexusai_gateway.Provider;

import reactor.core.publisher.Flux;

/**
 * Streaming extension of LlmProvider.
 *
 * Providers that support SSE (Server-Sent Events) token streaming implement
 * this interface in addition to LlmProvider.
 *
 * Each emitted String is a raw delta token (word / sub-word) to forward
 * directly to the client without buffering.
 *
 * Default: non-streaming providers are wrapped with a fallback that buffers
 * the full response and emits it as a single token chunk, so the contract
 * is always satisfied even if a provider doesn't natively support streaming.
 */
public interface StreamingLlmProvider {

    /**
     * Return a Flux of raw token strings.
     * Each item is an incremental content delta — equivalent to
     * choices[0].delta.content in the OpenAI streaming wire format.
     */
    Flux<String> streamChat(String providerSlug, String message, String modelName, String runtimeApiKey);
}
