package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
public class BearerAuthStrategy implements ProviderAuthStrategy {

    @Override
    public boolean supports(ProviderConfig.ProviderType type) {
        return type == ProviderConfig.ProviderType.OPENAI_COMPATIBLE || 
               type == ProviderConfig.ProviderType.OLLAMA;
    }

    @Override
    public Mono<ClientRequest> applyAuth(ClientRequest request, ProviderConfig config) {
        String apiKey = config.getApiKey(); // Retrieves "api_key" from the JSON map
        if (apiKey != null && !apiKey.isBlank()) {
            ClientRequest authorizedRequest = ClientRequest.from(request)
                .header("Authorization", "Bearer " + apiKey)
                .build();
            return Mono.just(authorizedRequest);
        }
        return Mono.just(request);
    }
}
