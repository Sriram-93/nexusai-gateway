package com.llm.nexusai_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

/**
 * NexusAI Gateway — Adaptive Enterprise Decision Framework.
 *
 * Key capabilities:
 * - Dynamic provider registration (no code changes needed to add new providers)
 * - Auto-discovery of models from provider APIs (daily refresh)
 * - Community-sourced pricing sync from LiteLLM catalogue (12h refresh)
 * - LinUCB / Federated bandit adaptive routing with decomposed reward learning
 * - Full WebFlux non-blocking reactive pipeline
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableConfigurationProperties
public class NexusaiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusaiGatewayApplication.class, args);
    }
}
