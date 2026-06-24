package com.llm.nexusai_gateway.Provider;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProviderRegistry {

    private final List<LlmProvider> providers;

    public ProviderRegistry(List<LlmProvider> providers) {
        this.providers = providers;
    }

    /**
     * Resolves the supporting LlmProvider instance for the given providerName.
     */
    public LlmProvider getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return null;
        }
        return providers.stream()
                .filter(p -> p.supports(providerName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves all registered providers.
     */
    public List<LlmProvider> getAllProviders() {
        return providers;
    }
}
