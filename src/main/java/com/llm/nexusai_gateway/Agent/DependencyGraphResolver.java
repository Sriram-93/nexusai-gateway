package com.llm.nexusai_gateway.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DependencyGraphResolver (Priority 3 — Dependency Graph & Topological Execution).
 *
 * Automatically computes execution order and parallel tiers for any collection
 * of Agent implementations based on their declared dependencies (getDependencies()).
 *
 * Algorithm: Kahn's Topological Sort with Tier Grouping
 *  1. Compute in-degree (# of unsatisfied dependencies) for each agent.
 *  2. Find all agents with in-degree = 0 → Tier 1 (can run in parallel).
 *  3. Remove Tier 1 agents from graph and update remaining in-degrees.
 *  4. Repeat until all agents are placed into ordered tiers.
 *  5. If remaining nodes exist after iteration, throw exception (cycle detected).
 *
 * Benefits:
 *  - Fully automated execution order based on inputs/outputs & dependencies.
 *  - Automatically identifies parallelization opportunities without manual order IDs.
 *  - Reverts gracefully to declared order if dependencies are empty.
 */
@Component
public class DependencyGraphResolver {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphResolver.class);

    /**
     * Resolve agents into ordered parallel execution tiers.
     *
     * @param agents input list of agents
     * @return List of agent tiers, where agents in each inner list can execute concurrently
     */
    public List<List<Agent>> resolveTiers(List<Agent> agents) {
        if (agents == null || agents.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Agent> nameToAgent = agents.stream()
            .collect(Collectors.toMap(Agent::getName, a -> a, (a1, a2) -> a1));

        // Compute in-degrees and build adjacency lists
        Map<String, Set<String>> graph = new HashMap<>(); // agent -> dependents that wait on agent
        Map<String, Integer> inDegree = new HashMap<>();

        for (Agent agent : agents) {
            String name = agent.getName();
            inDegree.putIfAbsent(name, 0);
            graph.putIfAbsent(name, new HashSet<>());

            for (String dep : agent.getDependencies()) {
                if (nameToAgent.containsKey(dep)) {
                    graph.putIfAbsent(dep, new HashSet<>());
                    graph.get(dep).add(name);
                    inDegree.put(name, inDegree.getOrDefault(name, 0) + 1);
                } else {
                    log.warn("Agent '{}' declared dependency on '{}' which is not present in registry", name, dep);
                }
            }
        }

        List<List<Agent>> resolvedTiers = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        while (processed.size() < agents.size()) {
            // Find all agents with 0 unsatisfied dependencies in this iteration
            List<Agent> currentTier = agents.stream()
                .filter(a -> !processed.contains(a.getName()))
                .filter(a -> inDegree.getOrDefault(a.getName(), 0) == 0)
                .collect(Collectors.toList());

            if (currentTier.isEmpty()) {
                Set<String> cycleNodes = agents.stream()
                    .map(Agent::getName)
                    .filter(n -> !processed.contains(n))
                    .collect(Collectors.toSet());
                throw new IllegalStateException("Circular dependency detected among agents: " + cycleNodes);
            }

            resolvedTiers.add(currentTier);

            // Mark current tier processed & reduce in-degree of dependents
            for (Agent agent : currentTier) {
                String name = agent.getName();
                processed.add(name);

                for (String dependent : graph.getOrDefault(name, Collections.emptySet())) {
                    inDegree.put(dependent, inDegree.get(dependent) - 1);
                }
            }
        }

        log.info("DependencyGraphResolver resolved {} agents into {} parallel tiers:", agents.size(), resolvedTiers.size());
        for (int i = 0; i < resolvedTiers.size(); i++) {
            List<String> names = resolvedTiers.get(i).stream().map(Agent::getName).toList();
            log.info("  Tier {}: {}", i + 1, names);
        }

        return resolvedTiers;
    }
}
