package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
public class GeminiAuthStrategy implements ProviderAuthStrategy {

    @Override
    public boolean supports(ProviderConfig.ProviderType type) {
        return type == ProviderConfig.ProviderType.GEMINI;
    }

    @Override
    public Mono<ClientRequest> applyAuth(ClientRequest request, ProviderConfig config) {
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            ClientRequest authorizedRequest = ClientRequest.from(request)
                .header("x-goog-api-key", apiKey)
                .build();
            return Mono.just(authorizedRequest);
        }
        return Mono.just(request);
    }
}
