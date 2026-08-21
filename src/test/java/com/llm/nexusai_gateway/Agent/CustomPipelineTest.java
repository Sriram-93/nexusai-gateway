package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Model.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CustomPipelineTest {

    @Autowired
    private WorkflowDefinitionFactory factory;

    @Autowired
    private AgentOrchestrationService orchestrationService;

    @Test
    void testExplicitPipelineNameSelection() {
        ChatRequest req = new ChatRequest();
        req.setMessage("Explain JVM memory architecture in depth.");
        req.setPipelineName("GREETING");

        AgentContext ctx = new AgentContext(req);
        WorkflowDefinition def = factory.select(ctx);

        assertEquals("GREETING", def.getName());
        assertFalse(def.getSteps().stream().anyMatch(s -> "ContextAgent".equals(s.getAgentName())));
    }

    @Test
    void testCustomStepsSelection() {
        ChatRequest req = new ChatRequest();
        req.setMessage("Execute security audit");
        req.setCustomSteps(List.of("IntentAgent", "PolicyAgent", "FeedbackAgent"));

        AgentContext ctx = new AgentContext(req);
        WorkflowDefinition def = factory.select(ctx);

        assertEquals("CUSTOM", def.getName());
        assertEquals(3, def.getSteps().size());
        assertEquals("IntentAgent", def.getSteps().get(0).getAgentName());
        assertEquals("PolicyAgent", def.getSteps().get(1).getAgentName());
        assertEquals("FeedbackAgent", def.getSteps().get(2).getAgentName());
    }

    @Test
    void testCustomPipelineExecution() {
        ChatRequest req = new ChatRequest();
        req.setMessage("Test custom pipeline execution");
        req.setCustomSteps(List.of("IntentAgent", "PolicyAgent", "FeedbackAgent"));

        AgentChatResponse response = orchestrationService.process(req).block();

        assertNotNull(response);
        assertNotNull(response.getIntent());
        assertNotNull(response.getPolicy());
    }
}
