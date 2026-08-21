package com.llm.nexusai_gateway.Config;

import com.llm.nexusai_gateway.Provider.Auth.ProviderAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(ProviderAuthInterceptor authInterceptor) {
        return WebClient.builder()
                .filter(authInterceptor.getFilter());
    }
}
