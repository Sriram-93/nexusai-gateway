package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Context.TaskCategory;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DecisionEngineTest {

    private ReputationService reputationService;
    private RequestContext context;

    @BeforeEach
    public void setUp() {
        reputationService = new ReputationService();
        // Initialize reputation for tested providers/models
        reputationService.update("gemini:gemini-2.5-flash", 0.9, 200, 0.0001, true);
        reputationService.update("groq:llama-3.3-70b-versatile", 0.8, 150, 0.0002, true);
        reputationService.update("groq:llama-3.1-8b-instant", 0.7, 80, 0.00005, true);

        context = new RequestContext(
            TaskCategory.CODE,
            0.8,
            200,
            false,
            "user123",
            "tenant456",
            Map.of(),
            new float[384]
        );
    }

    @Test
    public void testRuleBasedDecisionEngine() {
        RuleBasedDecisionEngine engine = new RuleBasedDecisionEngine(reputationService);
        List<String> eligible = List.of(
            "gemini:gemini-2.5-flash",
            "groq:llama-3.3-70b-versatile",
            "groq:llama-3.1-8b-instant"
        );

        ExplainedDecision decision = engine.select(context, eligible);
        assertNotNull(decision);
        assertEquals(RoutingStrategy.RULE_BASED, decision.strategy());
        assertEquals("gemini", decision.selectedProvider());
        assertEquals("gemini-2.5-flash", decision.selectedModel());

        // Test creative workload mapping
        RequestContext creativeContext = new RequestContext(
            TaskCategory.CREATIVE,
            0.5,
            100,
            false,
            "user123",
            "tenant456",
            Map.of(),
            new float[384]
        );
        ExplainedDecision creativeDecision = engine.select(creativeContext, eligible);
        assertEquals("groq", creativeDecision.selectedProvider());
        assertEquals("llama-3.3-70b-versatile", creativeDecision.selectedModel());
    }

    @Test
    public void testWeightedDecisionEngine() {
        // Test deterministic selection by setting weight of one provider to 1.0 and another to 0.0
        Map<String, Double> weights = Map.of(
            "gemini", 1.0,
            "groq", 0.0
        );

        WeightedDecisionEngine engine = new WeightedDecisionEngine(weights, reputationService);
        List<String> eligible = List.of(
            "gemini:gemini-2.5-flash",
            "groq:llama-3.3-70b-versatile"
        );

        for (int i = 0; i < 20; i++) {
            ExplainedDecision decision = engine.select(context, eligible);
            assertEquals("gemini", decision.selectedProvider(), "Should always select gemini as groq weight is 0.0");
        }

        // Test the opposite
        Map<String, Double> weightsGroq = Map.of(
            "gemini", 0.0,
            "groq", 1.0
        );
        WeightedDecisionEngine engineGroq = new WeightedDecisionEngine(weightsGroq, reputationService);
        for (int i = 0; i < 20; i++) {
            ExplainedDecision decision = engineGroq.select(context, eligible);
            assertEquals("groq", decision.selectedProvider(), "Should always select groq as gemini weight is 0.0");
        }
    }

    @Test
    public void testLinUcbDecisionEngine() {
        LinUcbDecisionEngine engine = new LinUcbDecisionEngine(1.0, reputationService, null, null);
        List<String> eligible = List.of(
            "gemini:gemini-2.5-flash",
            "groq:llama-3.3-70b-versatile"
        );

        ExplainedDecision decision = engine.select(context, eligible);
        assertNotNull(decision);
        assertEquals(RoutingStrategy.ADAPTIVE, decision.strategy());

        // Update with positive reward for gemini
        engine.update(context, "gemini:gemini-2.5-flash", 1.0);

        // Update with poor reward for groq
        engine.update(context, "groq:llama-3.3-70b-versatile", 0.0);

        // Under pure exploitation (low alpha), gemini should score higher now
        LinUcbDecisionEngine exploitativeEngine = new LinUcbDecisionEngine(0.01, reputationService, null, null);
        exploitativeEngine.update(context, "gemini:gemini-2.5-flash", 1.0);
        exploitativeEngine.update(context, "groq:llama-3.3-70b-versatile", 0.0);

        ExplainedDecision nextDecision = exploitativeEngine.select(context, eligible);
        assertEquals("gemini", nextDecision.selectedProvider());

        // Reset
        engine.reset();
    }
}
