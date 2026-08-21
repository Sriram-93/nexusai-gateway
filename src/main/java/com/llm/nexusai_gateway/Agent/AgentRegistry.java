package com.llm.nexusai_gateway.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Spring-managed registry that auto-discovers all Agent beans.
 *
 * Spring injects every bean that implements the Agent interface into the
 * constructor list. The registry sorts them by getOrder() and exposes
 * them to the WorkflowEngine as an ordered, immutable execution plan.
 *
 * Adding a new agent requires ZERO changes here or in the orchestrator —
 * simply implement Agent, annotate with @Component, and Spring will
 * auto-register it here.
 *
 * Design Pattern: Registry / Service Locator (with Spring DI)
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final List<Agent> orderedAgents;
    private final DependencyGraphResolver graphResolver;
    private final List<List<Agent>> resolvedTiers;

    public AgentRegistry(List<Agent> agents, DependencyGraphResolver graphResolver) {
        this.graphResolver = graphResolver;
        this.orderedAgents = agents.stream()
            .sorted(Comparator.comparingInt(Agent::getOrder))
            .toList();

        log.info("AgentRegistry initialized with {} agents:", orderedAgents.size());
        orderedAgents.forEach(a ->
            log.info("  [order={}] {} (deps={})", a.getOrder(), a.getName(), a.getDependencies())
        );

        this.resolvedTiers = graphResolver.resolveTiers(orderedAgents);
    }

    /**
     * Returns all agents sorted by their declared execution order.
     */
    public List<Agent> getOrderedAgents() {
        return orderedAgents;
    }

    /**
     * Returns automatically resolved topological execution tiers based on declared dependencies.
     */
    public List<List<Agent>> getResolvedTiers() {
        return resolvedTiers;
    }

    /**
     * Retrieve a specific agent by name (useful for testing and conditional wiring).
     */
    public Agent getAgent(String name) {
        return orderedAgents.stream()
            .filter(a -> a.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + name));
    }
}
