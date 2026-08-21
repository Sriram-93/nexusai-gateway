package com.llm.nexusai_gateway.Provider;

import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Resolves a provider slug to an executable {@link LlmProvider} adapter.
 *
 * <h3>Resolution strategy (in priority order):</h3>
 * <ol>
 *   <li>Dedicated Spring beans (Groq, Gemini, Anthropic, OpenAI, Ollama, Claude) —
 *       matched by their {@code supports()} method.</li>
 *   <li>Dynamically registered OPENAI_COMPATIBLE providers from the DB —
 *       handled by {@link GenericOpenAiCompatibleProvider}.</li>
 *   <li>{@code null} if no match found.</li>
 * </ol>
 *
 * <p>This means existing built-in providers continue to work with their battle-tested
 * implementations, while any new provider added via the management API is immediately
 * routable through the generic adapter — with no code changes or restarts.</p>
 */
@Component
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    /** All LlmProvider Spring beans (Groq, Gemini, Anthropic, OpenAI, Ollama, Claude, Generic). */
    private final List<LlmProvider> providers;
    private final GenericOpenAiCompatibleProvider genericProvider;
    private final ProviderConfigRepository providerConfigRepository;

    public ProviderRegistry(List<LlmProvider> providers,
                            GenericOpenAiCompatibleProvider genericProvider,
                            ProviderConfigRepository providerConfigRepository) {
        this.providers = providers;
        this.genericProvider = genericProvider;
        this.providerConfigRepository = providerConfigRepository;
    }

    /**
     * Resolve an executable LlmProvider for the given providerSlug.
     *
     * @param providerSlug The provider identifier (e.g. "groq", "gemini", "my-custom-llm")
     * @return A ready-to-call provider, or null if not found.
     */
    public LlmProvider getProvider(String providerSlug) {
        if (providerSlug == null || providerSlug.isBlank()) return null;

        // Step 1: Try dedicated Spring beans first (Groq, Gemini, etc.)
        // We explicitly exclude GenericOpenAiCompatibleProvider so it doesn't accidentally
        // intercept requests meant for dedicated beans just because they share a type.
        LlmProvider dedicated = providers.stream()
            .filter(p -> !(p instanceof GenericOpenAiCompatibleProvider))
            .filter(p -> p.supports(providerSlug))
            .findFirst()
            .orElse(null);

        if (dedicated != null) {
            return dedicated;
        }

        // Step 2: Fall through to DB-driven dynamic provider lookup.
        // If the slug matches a registered OPENAI_COMPATIBLE or OLLAMA provider,
        // wrap the generic adapter so it executes with the correct config.
        boolean isGenericProvider = providerConfigRepository.findBySlug(providerSlug)
            .map(config -> config.getType() == ProviderConfig.ProviderType.OPENAI_COMPATIBLE
                        || config.getType() == ProviderConfig.ProviderType.AZURE
                        || config.getType() == ProviderConfig.ProviderType.OLLAMA)
            .orElse(false);

        if (isGenericProvider) {
            log.debug("Routing provider '{}' via GenericOpenAiCompatibleProvider.", providerSlug);
            // Return an anonymous wrapper that binds the slug to the generic adapter.
            return new BoundGenericProvider(providerSlug, genericProvider);
        }

        log.warn("No provider adapter found for slug: '{}'", providerSlug);
        return null;
    }

    public List<LlmProvider> getAllProviders() {
        return providers;
    }

    // ─── Inner class: binds a specific slug to the generic adapter ─────────────

    /**
     * A lightweight wrapper that locks the generic adapter to a specific provider slug.
     * This allows the rest of the system to call provider.chat(message, model) uniformly
     * without knowing whether the provider is built-in or dynamically registered.
     */
    private record BoundGenericProvider(
        String slug,
        GenericOpenAiCompatibleProvider delegate
    ) implements LlmProvider {

        @Override
        public Mono<ProviderResponse> chat(String providerSlug, String message, String modelName) {
            return delegate.chat(slug, message, modelName);
        }

        @Override
        public boolean supports(String providerName) {
            return slug.equalsIgnoreCase(providerName);
        }
    }
}
