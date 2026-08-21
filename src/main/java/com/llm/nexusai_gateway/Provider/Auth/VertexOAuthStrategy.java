package com.llm.nexusai_gateway.Provider.Auth;

import com.llm.nexusai_gateway.Provider.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class VertexOAuthStrategy implements ProviderAuthStrategy {

    @Override
    public boolean supports(ProviderConfig.ProviderType type) {
        return type == ProviderConfig.ProviderType.VERTEXAI;
    }

    @Override
    public Mono<ClientRequest> applyAuth(ClientRequest request, ProviderConfig config) {
        Map<String, String> creds = config.getCredentials();
        String serviceAccountJson = creds.get("service_account_json");
        
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            return Mono.error(new IllegalArgumentException("GCP Service Account JSON missing for VertexAI"));
        }
        
        try {
            java.io.InputStream stream = new java.io.ByteArrayInputStream(serviceAccountJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.google.auth.oauth2.GoogleCredentials credentials = com.google.auth.oauth2.GoogleCredentials.fromStream(stream)
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
            
            credentials.refreshIfExpired();
            String token = credentials.getAccessToken().getTokenValue();
            
            ClientRequest authorizedRequest = ClientRequest.from(request)
                .header("Authorization", "Bearer " + token)
                .build();
                
            return Mono.just(authorizedRequest);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to exchange GCP Service Account JSON for OAuth Token", e));
        }
    }
}
