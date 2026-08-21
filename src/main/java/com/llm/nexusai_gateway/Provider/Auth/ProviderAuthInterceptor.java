package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class ProviderAuthInterceptor {

    private final List<ProviderAuthStrategy> strategies;

    public ProviderAuthInterceptor(List<ProviderAuthStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * This filter should be registered globally on the WebClient.
     * It intercepts every outgoing request, looks for a ProviderConfig attribute,
     * and delegates to the appropriate Strategy to inject credentials.
     */
    public ExchangeFilterFunction getFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            return request.attribute("providerConfig")
                .map(attr -> (ProviderConfig) attr)
                .map(config -> {
                    // Find the right strategy for this provider type
                    ProviderAuthStrategy strategy = strategies.stream()
                        .filter(s -> s.supports(config.getType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No auth strategy found for type " + config.getType()));
                    
                    // Delegate the request modification to the strategy
                    return strategy.applyAuth(request, config);
                })
                .orElse(Mono.just(request)); // Pass through if no config attached
        });
    }
}
