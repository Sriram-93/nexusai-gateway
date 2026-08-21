package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Strategy pattern for injecting authentication into WebClient requests based on Provider Type.
 * This completely decouples API keys, OAuth tokens, and AWS Sigs from the core routing engine.
 */
public interface ProviderAuthStrategy {
    
    boolean supports(ProviderConfig.ProviderType type);
    
    /**
     * Mutates the incoming HTTP request to inject provider-specific authentication.
     */
    reactor.core.publisher.Mono<org.springframework.web.reactive.function.client.ClientRequest> applyAuth(
        org.springframework.web.reactive.function.client.ClientRequest request, 
        ProviderConfig config
    );
}
