package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

@Component
public class AzureAuthStrategy implements ProviderAuthStrategy {

    @Override
    public boolean supports(ProviderConfig.ProviderType type) {
        return type == ProviderConfig.ProviderType.AZURE;
    }

    @Override
    public Mono<ClientRequest> applyAuth(ClientRequest request, ProviderConfig config) {
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            ClientRequest authorizedRequest = ClientRequest.from(request)
                .header("api-key", apiKey)
                .build();
            return Mono.just(authorizedRequest);
        }
        return Mono.just(request);
    }
}
