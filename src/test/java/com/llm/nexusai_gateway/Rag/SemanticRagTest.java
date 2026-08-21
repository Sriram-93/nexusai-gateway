package com.llm.nexusai_gateway.Rag;

import com.llm.nexusai_gateway.Agent.ContextAgent;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticRagTest {

    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();

    @Test
    void testJvmMemoryVectorSearch() {
        List<KnowledgeChunk> chunks = vectorStore.search("Explain JVM memory heap and garbage collection", 3);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getSimilarityScore() > 0.0);
        assertTrue(chunks.stream().anyMatch(c -> c.getDocumentName().contains("JVM_Specs") || c.getDocumentName().contains("Garbage_Collection")));
    }

    @Test
    void testLinUcbBanditVectorSearch() {
        List<KnowledgeChunk> chunks = vectorStore.search("LinUCB contextual bandit algorithm optimization", 3);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getSimilarityScore() > 0.0);
        assertTrue(chunks.stream().anyMatch(c -> c.getDocumentName().contains("Aedf_Mathematical_Proof") || c.getDocumentName().contains("Contextual_Bandits")));
    }

    @Test
    void testContextAgentIntegrationWithVectorStore() {
        RequestLogRepository mockRepo = mock(RequestLogRepository.class);
        when(mockRepo.findTop5ByUserIdOrderByIdDesc(anyString())).thenReturn(List.of());

        ContextAgent agent = new ContextAgent(mockRepo, vectorStore);

        com.llm.nexusai_gateway.Agent.AgentContext ctx = new com.llm.nexusai_gateway.Agent.AgentContext(
            new com.llm.nexusai_gateway.Model.ChatRequest("Tell me about double-checked locking singleton thread-safe pattern", "user123", null, null, null, null)
        );
        agent.execute(ctx).block();
        ContextAgent.ContextResult result = ctx.getContextResult();

        assertNotNull(result);
        assertFalse(result.getRelevantDocuments().isEmpty());
        assertTrue(result.getRelevantDocuments().contains("Gang_of_Four_Patterns.pdf"));
        assertTrue(result.getRetrievedKnowledge().contains("Double-checked locking pattern"));
    }
}
