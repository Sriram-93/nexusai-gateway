package com.llm.nexusai_gateway.Agent;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphResolverTest {

    private final DependencyGraphResolver resolver = new DependencyGraphResolver();

    static class DummyAgent implements Agent {
        private final String name;
        private final int order;
        private final List<String> deps;

        DummyAgent(String name, int order, List<String> deps) {
            this.name = name;
            this.order = order;
            this.deps = deps;
        }

        @Override public String getName() { return name; }
        @Override public int getOrder() { return order; }
        @Override public List<String> getDependencies() { return deps; }
        @Override public Mono<WorkflowSignal> execute(AgentContext context) { return Mono.just(WorkflowSignal.CONTINUE); }
    }

    @Test
    void testTopologicalSortAndTierGrouping() {
        Agent intent = new DummyAgent("IntentAgent", 1, List.of());
        Agent context = new DummyAgent("ContextAgent", 1, List.of());
        Agent policy = new DummyAgent("PolicyAgent", 2, List.of("IntentAgent", "ContextAgent"));
        Agent routing = new DummyAgent("RoutingAgent", 3, List.of("PolicyAgent"));
        Agent quality = new DummyAgent("QualityAgent", 5, List.of("RoutingAgent"));

        List<List<Agent>> tiers = resolver.resolveTiers(List.of(quality, routing, policy, context, intent));

        assertEquals(4, tiers.size());
        
        // Tier 1 should contain IntentAgent and ContextAgent in parallel
        List<String> tier1Names = tiers.get(0).stream().map(Agent::getName).toList();
        assertTrue(tier1Names.contains("IntentAgent"));
        assertTrue(tier1Names.contains("ContextAgent"));

        // Tier 2 should contain PolicyAgent
        List<String> tier2Names = tiers.get(1).stream().map(Agent::getName).toList();
        assertEquals(List.of("PolicyAgent"), tier2Names);

        // Tier 3 should contain RoutingAgent
        List<String> tier3Names = tiers.get(2).stream().map(Agent::getName).toList();
        assertEquals(List.of("RoutingAgent"), tier3Names);

        // Tier 4 should contain QualityAgent
        List<String> tier4Names = tiers.get(3).stream().map(Agent::getName).toList();
        assertEquals(List.of("QualityAgent"), tier4Names);
    }

    @Test
    void testCircularDependencyDetection() {
        Agent a = new DummyAgent("AgentA", 1, List.of("AgentB"));
        Agent b = new DummyAgent("AgentB", 2, List.of("AgentA"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            resolver.resolveTiers(List.of(a, b))
        );

        assertTrue(ex.getMessage().contains("Circular dependency detected"));
    }
}
