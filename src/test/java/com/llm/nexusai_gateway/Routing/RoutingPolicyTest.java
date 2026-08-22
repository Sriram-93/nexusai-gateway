package com.llm.nexusai_gateway.Routing;

import com.llm.nexusai_gateway.Agent.PolicyAgent;
import com.llm.nexusai_gateway.Agent.RoutingAgent;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Decision.ExplainedDecision;
import com.llm.nexusai_gateway.Provider.ModelCatalog;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Health.ProviderHealthMonitor;
import com.llm.nexusai_gateway.Policy.PolicyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingPolicyTest {

    private DecisionEngine mockDecisionEngine;
    private ModelRegistry mockModelRegistry;
    private PolicyFilter mockPolicyFilter;
    private RoutingAgent routingAgent;
    private PolicyAgent.PolicyResult cleanPolicyResult;

    @BeforeEach
    void setUp() {
        mockDecisionEngine = mock(DecisionEngine.class);
        mockModelRegistry = mock(ModelRegistry.class);
        mockPolicyFilter = mock(PolicyFilter.class);
        routingAgent = new RoutingAgent(mockDecisionEngine, new ProviderHealthMonitor(), mockModelRegistry, mockPolicyFilter);
        cleanPolicyResult = new PolicyAgent.PolicyResult(List.of(), 0.0, true, false, false, "Clean");
    }

    @Test
    void testLowestCostPolicy() {
        List<String> providers = List.of(
            "gemini:gemini-3.5-flash",
            "groq:llama-3.3-70b-versatile",
            "groq:llama-3.1-8b-instant"
        );

        when(mockModelRegistry.findByArmKey("gemini:gemini-3.5-flash"))
            .thenReturn(Optional.of(new ModelCatalog("gemini:gemini-3.5-flash", "gemini", "gemini-3.5-flash", 0.5, 1.5, 1000, 8000, true)));
        when(mockModelRegistry.findByArmKey("groq:llama-3.3-70b-versatile"))
            .thenReturn(Optional.of(new ModelCatalog("groq:llama-3.3-70b-versatile", "groq", "llama-3.3-70b-versatile", 0.7, 0.9, 800, 8000, true)));
        when(mockModelRegistry.findByArmKey("groq:llama-3.1-8b-instant"))
            .thenReturn(Optional.of(new ModelCatalog("groq:llama-3.1-8b-instant", "groq", "llama-3.1-8b-instant", 0.05, 0.08, 400, 8000, true)));

        RoutingAgent.RoutingResult result = routingAgent.route(
            null,
            cleanPolicyResult,
            providers,
            RoutingPolicy.LOWEST_COST
        );

        assertEquals("groq", result.getProvider());
        assertEquals("llama-3.1-8b-instant", result.getModel());
        assertEquals("LOWEST_COST", result.getStrategy());
    }

    @Test
    void testLowestLatencyPolicy() {
        List<String> providers = List.of(
            "gemini:gemini-3.5-flash",
            "gemini:gemini-2.5-flash"
        );

        when(mockModelRegistry.findByArmKey("gemini:gemini-3.5-flash"))
            .thenReturn(Optional.of(new ModelCatalog("gemini:gemini-3.5-flash", "gemini", "gemini-3.5-flash", 0.5, 1.5, 1200, 8000, true)));
        when(mockModelRegistry.findByArmKey("gemini:gemini-2.5-flash"))
            .thenReturn(Optional.of(new ModelCatalog("gemini:gemini-2.5-flash", "gemini", "gemini-2.5-flash", 0.1, 0.2, 300, 8000, true)));

        RoutingAgent.RoutingResult result = routingAgent.route(
            null,
            cleanPolicyResult,
            providers,
            RoutingPolicy.LOWEST_LATENCY
        );

        assertEquals("gemini", result.getProvider());
        assertEquals("gemini-2.5-flash", result.getModel());
        assertEquals("LOWEST_LATENCY", result.getStrategy());
    }

    @Test
    void testFallbackChainPolicy() {
        List<String> providers = List.of(
            "gemini:gemini-2.5-flash",
            "groq:llama-3.1-8b-instant"
        );

        RoutingAgent.RoutingResult result = routingAgent.route(
            null,
            cleanPolicyResult,
            providers,
            RoutingPolicy.FALLBACK_CHAIN
        );

        assertEquals("gemini", result.getProvider());
        assertEquals("gemini-2.5-flash", result.getModel());
        assertEquals("FALLBACK_CHAIN", result.getStrategy());
    }

    @Test
    void testAdaptiveBanditDelegation() {
        when(mockDecisionEngine.select(any(), anyList()))
            .thenReturn(new ExplainedDecision(
                "gemini", "gemini-2.5-flash",
                0.85, 0.9, 250.0, 1.0,
                "LinUCB UCB score",
                java.util.Map.of("gemini:gemini-2.5-flash", 0.85),
                com.llm.nexusai_gateway.Decision.RoutingStrategy.ADAPTIVE
            ));

        RoutingAgent.RoutingResult result = routingAgent.route(
            null,
            cleanPolicyResult,
            List.of("gemini:gemini-2.5-flash"),
            RoutingPolicy.FEDERATED_ADAPTIVE_BANDIT
        );

        assertEquals("gemini", result.getProvider());
        assertEquals("gemini-2.5-flash", result.getModel());
        assertEquals("ADAPTIVE", result.getStrategy());
    }
}
