package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Decision.RoutingSimulatorService;
import com.llm.nexusai_gateway.Model.OpenAiEmbeddingRequest;
import com.llm.nexusai_gateway.Model.OpenAiEmbeddingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class UniversalGatewayTest {

    @Autowired
    private UniversalGatewayController gatewayController;

    @Autowired
    private RoutingSimulatorService routingSimulatorService;

    @Test
    @DisplayName("Embeddings endpoint /v1/embeddings should return 384-dimensional dense vector")
    void testEmbeddingsGeneration() {
        OpenAiEmbeddingRequest req = new OpenAiEmbeddingRequest("Explain dependency injection in Spring Boot", "text-embedding-all-minilm-l6-v2");
        var responseEntity = gatewayController.embeddings(req);

        assertNotNull(responseEntity);
        OpenAiEmbeddingResponse body = responseEntity.getBody();
        assertNotNull(body);
        assertEquals("list", body.getObject());
        assertFalse(body.getData().isEmpty());

        float[] vector = body.getData().get(0).getEmbedding();
        assertNotNull(vector);
        assertEquals(384, vector.length, "MiniLM-L6-v2 embedding dimension must be 384");
    }

    @Test
    @DisplayName("Routing simulator should evaluate candidates and return clear winner explanation")
    void testRoutingSimulator() {
        RoutingSimulatorService.SimulationRequest req = new RoutingSimulatorService.SimulationRequest(
                "Write a Python script for fast Fourier transform",
                "coding",
                0.6, 0.2, 0.1, 0.1
        );

        RoutingSimulatorService.SimulationResult result = routingSimulatorService.simulate(req);

        assertNotNull(result);
        assertNotNull(result.selectedArmKey());
        assertNotNull(result.explanationReason());
        assertFalse(result.candidates().isEmpty());
        assertTrue(result.explanationReason().contains("highest composite score"));
    }
}
