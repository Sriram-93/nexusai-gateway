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

    @Value("${nexusai.policy.approved-providers:gemini,groq}")
    private String approvedProvidersStr;

    @Value("${nexusai.policy.blocked-providers:}")
    private String blockedProvidersStr;

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
            .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            log.warn("Policy filter removed all providers. Falling back to full list.");
            return allProviders.stream().map(String::toLowerCase).collect(Collectors.toList());
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
