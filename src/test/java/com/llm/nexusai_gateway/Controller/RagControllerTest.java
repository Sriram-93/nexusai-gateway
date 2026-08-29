package com.llm.nexusai_gateway.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RagControllerTest {

    @Autowired
    private RagController ragController;

    @Test
    void testGetAllChunksAndIngestion() {
        var chunks = ragController.getAllChunks().getBody();
        assertThat(chunks).isNotNull();
        assertThat(chunks.size()).isGreaterThan(0);

        var ingestResponse = ragController.ingestChunk(java.util.Map.of(
            "documentName", "UnitTestDoc.md",
            "content", "Test knowledge chunk content for unit testing."
        )).getBody();

        assertThat(ingestResponse).isNotNull();
        assertThat(ingestResponse.get("documentName")).isEqualTo("UnitTestDoc.md");
    }
}
