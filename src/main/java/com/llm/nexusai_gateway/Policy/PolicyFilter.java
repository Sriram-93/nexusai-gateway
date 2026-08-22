package com.llm.nexusai_gateway.Policy;

import com.llm.nexusai_gateway.Context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.llm.nexusai_gateway.Repository.ProviderConfigRepository;

/**
 * Enterprise Policy Filter — filters eligible providers before routing.
 *
 * Doc 01: "Keep this lightweight. Do not build a large governance subsystem."
 *
 * Policies are defined via application.properties and act as constraints
 * that reduce the set of candidate providers before the Decision Engine runs.
 */
@Service
public class PolicyFilter {

    private static final Logger log = LoggerFactory.getLogger(PolicyFilter.class);

    @Value("${nexusai.policy.approved-providers:}")
    private String approvedProvidersStr;

    @Value("${nexusai.policy.blocked-providers:}")
    private String blockedProvidersStr;

    private final ProviderConfigRepository providerConfigRepository;

    public PolicyFilter(ProviderConfigRepository providerConfigRepository) {
        this.providerConfigRepository = providerConfigRepository;
    }

    /**
     * Filter the list of all available providers down to those permitted
     * by enterprise policy for the given request context.
     *
     * @param allProviders All registered provider names
     * @param context      The extracted request context
     * @return Filtered list of eligible provider names
     */
    public List<String> filter(List<String> allProviders, RequestContext context) {
        List<String> approved = parseList(approvedProvidersStr);
        List<String> blocked = parseList(blockedProvidersStr);

        List<String> eligible = allProviders.stream()
            .map(String::toLowerCase)
            .filter(p -> {
                String base = p.contains(":") ? p.split(":")[0] : p;
                return approved.isEmpty() || approved.contains(base);
            })
            .filter(p -> {
                String base = p.contains(":") ? p.split(":")[0] : p;
                return !blocked.contains(base);
            })
            .filter(p -> {
                if (context == null || context.tenantId() == null) return true;
                String base = p.contains(":") ? p.split(":")[0] : p;
                
                List<com.llm.nexusai_gateway.Provider.ProviderConfig> tenantConfigs = 
                    providerConfigRepository.findByTenantId(context.tenantId());
                    
                if (!tenantConfigs.isEmpty()) {
                    // Tenant has explicitly configured their providers in DB (BYOK).
                    // Only allow providers that the tenant has added and enabled.
                    return tenantConfigs.stream()
                        .anyMatch(c -> c.getSlug().equalsIgnoreCase(base) && c.isEnabled());
                }
                
                // If tenant has no DB configs yet, check per-slug config or default
                return providerConfigRepository.findBySlugAndTenantId(base, context.tenantId())
                    .map(config -> config.isEnabled())
                    .orElse(true);
            })
            .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            log.warn("Policy filter removed all providers for tenant {}.", context != null ? context.tenantId() : "global");
        }

        log.debug("Policy filter: {} providers eligible out of {} total", eligible.size(), allProviders.size());
        return eligible;
    }

    private List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
