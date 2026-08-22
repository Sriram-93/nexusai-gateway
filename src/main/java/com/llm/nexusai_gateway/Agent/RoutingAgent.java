package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Decision.ExplainedDecision;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RoutingAgent implements Agent {

    private final DecisionEngine decisionEngine;
    private final com.llm.nexusai_gateway.Health.ProviderHealthMonitor healthMonitor;
    private final ModelRegistry modelRegistry;
    private final com.llm.nexusai_gateway.Policy.PolicyFilter policyFilter;

    public RoutingAgent(DecisionEngine decisionEngine,
                        com.llm.nexusai_gateway.Health.ProviderHealthMonitor healthMonitor,
                        ModelRegistry modelRegistry,
                        com.llm.nexusai_gateway.Policy.PolicyFilter policyFilter) {
        this.decisionEngine  = decisionEngine;
        this.healthMonitor   = healthMonitor;
        this.modelRegistry   = modelRegistry;
        this.policyFilter    = policyFilter;
    }

    @Override
    public String getName() { return "RoutingAgent"; }

    @Override
    public int getOrder() { return 3; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.List.of("IntentAgent", "PolicyAgent");
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("requestContext", "policyResult");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("routingResult");
    }

    /**
     * Agent interface bridge: reads RequestContext and PolicyResult from AgentContext,
     * selects a provider via the LinUCB bandit, and stores RoutingResult.
     * Depends on: IntentAgent (order 1), ContextAgent (order 1), PolicyAgent (order 2).
     */
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        return Mono.fromCallable(() -> {
            long t = System.currentTimeMillis();
            // Pull all enabled arms from the registry — no hardcoded model list.
            List<String> allProviders = modelRegistry.getEnabledArmKeys();

            // Apply PolicyFilter to enforce tenant API keys
            List<String> eligibleProviders = policyFilter.filter(allProviders, ctx.getRequestContext());

            // Priority 8: filter out providers with OPEN circuit breakers
            List<String> providers = healthMonitor.filterHealthy(eligibleProviders);

            com.llm.nexusai_gateway.Routing.RoutingPolicy policy =
                (ctx.getOriginalRequest() != null && ctx.getOriginalRequest().getRoutingPolicy() != null)
                ? ctx.getOriginalRequest().getRoutingPolicy()
                : com.llm.nexusai_gateway.Routing.RoutingPolicy.FEDERATED_ADAPTIVE_BANDIT;

            RoutingResult result = route(ctx.getRequestContext(), ctx.getPolicyResult(), providers, policy);
            ctx.setRoutingResult(result);
            ctx.recordAgentTiming(getName(), System.currentTimeMillis() - t);
            return WorkflowSignal.CONTINUE;
        });
    }

    public RoutingResult route(RequestContext context, PolicyAgent.PolicyResult policyResult, List<String> availableProviders) {
        return route(context, policyResult, availableProviders, com.llm.nexusai_gateway.Routing.RoutingPolicy.FEDERATED_ADAPTIVE_BANDIT);
    }

    public RoutingResult route(RequestContext context, PolicyAgent.PolicyResult policyResult, List<String> availableProviders, com.llm.nexusai_gateway.Routing.RoutingPolicy policy) {
        // Filter out blocked models
        List<String> eligible = availableProviders.stream()
            .filter(providerArm -> {
                String lowerArm = providerArm.toLowerCase();
                return policyResult.getBlockedModels().stream()
                    .noneMatch(blocked -> lowerArm.contains(blocked.toLowerCase()));
            })
            .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            eligible = availableProviders; // fallback
        }

        if (eligible.isEmpty()) {
            return new RoutingResult("none", "none", "No configured providers available", "NONE");
        }

        // Apply budget policy routing override if budget is exhausted
        if (policyResult.isBudgetExceeded()) {
            policy = com.llm.nexusai_gateway.Routing.RoutingPolicy.LOWEST_COST;
        }

        return switch (policy) {
            case LOWEST_COST -> selectLowestCost(eligible);
            case LOWEST_LATENCY -> selectLowestLatency(eligible);
            case FALLBACK_CHAIN -> selectFallbackChain(eligible);
            case FEDERATED_ADAPTIVE_BANDIT -> {
                ExplainedDecision decision = decisionEngine.select(context, eligible);
                yield new RoutingResult(
                    decision.selectedProvider(),
                    decision.selectedModel(),
                    decision.reason(),
                    decision.strategy().name()
                );
            }
            default -> {
                // Fallback for newly added routing policies if not fully implemented in the Agent yet
                ExplainedDecision decision = decisionEngine.select(context, eligible);
                yield new RoutingResult(
                    decision.selectedProvider(),
                    decision.selectedModel(),
                    decision.reason() + " (Fallback Policy Execution)",
                    decision.strategy().name()
                );
            }
        };
    }

    private RoutingResult selectLowestCost(List<String> eligible) {
        // Rank arms by price from ModelRegistry — no hardcoded cost numbers.
        String selected = eligible.stream()
            .min(java.util.Comparator.comparingDouble(arm ->
                modelRegistry.findByArmKey(arm)
                    .map(m -> m.inputPricePer1M())
                    .orElse(Double.MAX_VALUE)))
            .orElse(eligible.get(0));
        String[] parts = selected.split(":");
        return new RoutingResult(parts[0], parts[1], "Policy selected lowest cost arm: " + selected, "LOWEST_COST");
    }

    private RoutingResult selectLowestLatency(List<String> eligible) {
        // Rank arms by estimated latency from ModelRegistry — no hardcoded ms values.
        String selected = eligible.stream()
            .min(java.util.Comparator.comparingInt(arm ->
                modelRegistry.findByArmKey(arm)
                    .map(m -> m.estimatedLatencyMs())
                    .orElse(Integer.MAX_VALUE)))
            .orElse(eligible.get(0));
        String[] parts = selected.split(":");
        return new RoutingResult(parts[0], parts[1], "Policy selected lowest latency arm: " + selected, "LOWEST_LATENCY");
    }

    private RoutingResult selectFallbackChain(List<String> eligible) {
        String selected = eligible.get(0);
        String[] parts = selected.split(":");
        return new RoutingResult(parts[0], parts[1], "Fallback chain selected primary available arm", "FALLBACK_CHAIN");
    }

    public static class RoutingResult {
        private String provider;
        private String model;
        private String reason;
        private String strategy;

        public RoutingResult() {}

        public RoutingResult(String provider, String model, String reason, String strategy) {
            this.provider = provider;
            this.model = model;
            this.reason = reason;
            this.strategy = strategy;
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
    }
}
