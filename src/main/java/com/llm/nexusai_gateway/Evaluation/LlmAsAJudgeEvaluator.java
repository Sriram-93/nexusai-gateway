package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * LLM-as-a-Judge implementation for Quality Evaluation.
 * This provides mathematically sound ground-truth labels for the LinUCB bandit
 * compared to simplistic heuristic checks.
 *
 * Uses Groq (Llama-3-8b-instant or Llama-3.3-70b-versatile) for ultra-fast, 
 * low-cost, yet highly capable assessment.
 */
@Service
@Primary
public class LlmAsAJudgeEvaluator implements QualityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmAsAJudgeEvaluator.class);
    
    // We use a fast, inexpensive model for grading to maintain high throughput
    private static final String JUDGE_PROVIDER = "groq";
    private static final String JUDGE_MODEL = "llama-3.1-8b-instant";

    private final ProviderRegistry providerRegistry;

    public LlmAsAJudgeEvaluator(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @Override
    public Mono<QualityScore> evaluate(String prompt, String response, TaskCategory taskCategory) {
        if (response == null || response.isBlank()) {
            return Mono.just(QualityScore.of(0.0, 0.0, 0.0));
        }

        LlmProvider judge = providerRegistry.getProvider(JUDGE_PROVIDER);
        if (judge == null) {
            log.warn("Judge provider '{}' not found, falling back to minimum quality score.", JUDGE_PROVIDER);
            return Mono.just(QualityScore.of(0.5, 0.5, 0.5)); // Neutral fallback
        }

        String gradingPrompt = buildGradingPrompt(prompt, response, taskCategory);

        return judge.chat(JUDGE_PROVIDER, gradingPrompt, JUDGE_MODEL)
            .map(judgeResponse -> parseScores(judgeResponse.content()))
            .onErrorResume(err -> {
                log.error("LLM-as-a-Judge failed: {}", err.getMessage());
                // Fallback to neutral score on network failure
                return Mono.just(QualityScore.of(0.5, 0.5, 0.5));
            });
    }

    private String buildGradingPrompt(String prompt, String response, TaskCategory category) {
        return """
            You are an expert AI evaluator. Grade the provided LLM response to the user's prompt based on the task category: %s.
            
            Evaluate on 3 dimensions, returning ONLY a comma-separated list of 3 decimals between 0.00 and 1.00. Do not include any other text.
            1. Completeness (Is the answer detailed enough for the task?)
            2. Relevance (Does it directly address the prompt?)
            3. Format Compliance (Does it structure the answer well for the given task?)
            
            Example Output:
            0.95,0.80,1.00
            
            User Prompt:
            %s
            
            LLM Response:
            %s
            """.formatted(category.name(), prompt, response);
    }

    private QualityScore parseScores(String responseText) {
        try {
            String[] parts = responseText.trim().split(",");
            if (parts.length >= 3) {
                double comp = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[0].trim())));
                double rel = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[1].trim())));
                double form = Math.max(0.0, Math.min(1.0, Double.parseDouble(parts[2].trim())));
                return QualityScore.of(comp, rel, form);
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM Judge response: '{}'", responseText);
        }
        return QualityScore.of(0.5, 0.5, 0.5);
    }
}
