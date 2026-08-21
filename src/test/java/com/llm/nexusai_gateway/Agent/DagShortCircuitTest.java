package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Model.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DagShortCircuitTest {

    @Test
    void testAgentContextDependencyTracking() {
        ChatRequest req = new ChatRequest();
        req.setMessage("test prompt");
        req.setUserId("user123");
        AgentContext ctx = new AgentContext(req);

        assertFalse(ctx.hasDependencySkippedOrFailed(List.of("IntentAgent", "ContextAgent")));

        ctx.markSkipped("ContextAgent");

        assertTrue(ctx.isSkipped("ContextAgent"));
        assertFalse(ctx.isFailed("ContextAgent"));
        assertTrue(ctx.hasDependencySkippedOrFailed(List.of("IntentAgent", "ContextAgent")));
        assertFalse(ctx.hasDependencySkippedOrFailed(List.of("IntentAgent")));
    }

    @Test
    void testFailedDependencyTracking() {
        ChatRequest req = new ChatRequest();
        req.setMessage("test prompt");
        req.setUserId("user123");
        AgentContext ctx = new AgentContext(req);

        ctx.markFailed("PolicyAgent");

        assertTrue(ctx.isFailed("PolicyAgent"));
        assertTrue(ctx.hasDependencySkippedOrFailed(List.of("PolicyAgent")));
        assertTrue(ctx.hasDependencySkippedOrFailed(List.of("IntentAgent", "PolicyAgent")));
    }
}
