package com.llm.nexusai_gateway.Experiment;

import com.llm.nexusai_gateway.Context.ContextExtractor;
import com.llm.nexusai_gateway.Model.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated Test Suite for the AEDF Experiment Harness.
 *
 * Verifies that the experimental framework can successfully run a dataset
 * through all 4 routing baselines and generate comparative metrics.
 */
@SpringBootTest
public class AedfExperimentTest {

    @Autowired
    private ExperimentRunner experimentRunner;

    @Autowired
    private ContextExtractor contextExtractor;

    @Test
    public void testFullExperimentPipeline() {
        // 1. Create a representative test dataset (mimicking the JSON dataset)
        List<ChatRequest> testDataset = List.of(
            new ChatRequest("Write a python script to parse a CSV file.", "user1", "tenant1", null, null, null),
            new ChatRequest("What is the capital of France?", "user1", "tenant1", null, null, null),
            new ChatRequest("Explain quantum entanglement to a 5 year old.", "user1", "tenant1", null, null, null)
        );

        // 2. Execute the experiment runner
        List<ExperimentResult> results = experimentRunner.runFullExperiment(testDataset).block();

        // 3. Assertions to prove the pipeline executes successfully
        assertNotNull(results, "Experiment results should not be null");
        
        // We should have 4 results (Static, Rule-Based, Weighted, Adaptive)
        assertTrue(results.size() >= 4, "Should run across all routing strategies");

        for (ExperimentResult result : results) {
            System.out.println("Validating strategy: " + result.getStrategy());
            
            // Validate all requests were processed
            assertTrue(result.getTotalRequests() == testDataset.size(), 
                "Strategy " + result.getStrategy() + " did not process all requests");
            
            // Validate metrics were collected
            assertTrue(result.getAverageReward() >= 0.0 && result.getAverageReward() <= 1.0, 
                "Reward should be normalized between 0 and 1");
            
            // Ensure no negative regret
            assertTrue(result.getCumulativeRegret() >= 0.0, 
                "Cumulative regret must be non-negative");
        }
    }
}
