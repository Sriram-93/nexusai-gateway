package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import reactor.core.publisher.Mono;

/**
 * Heuristic-based quality evaluator.
 *
 * Provides fast, deterministic quality estimates without requiring
 * an additional LLM call. Suitable for high-throughput environments
 * where the cost of LLM-as-Judge would be prohibitive.
 *
 * Evaluated dimensions:
 * - Completeness: response length relative to task-appropriate expectations
 * - Relevance: token overlap between prompt and response
 * - Format Compliance: structural validation per task category
 */
@Service
public class HeuristicQualityEvaluator implements QualityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(HeuristicQualityEvaluator.class);

    @Override
    public Mono<QualityScore> evaluate(String prompt, String response, TaskCategory taskCategory) {
        if (response == null || response.isBlank()) {
            return Mono.just(QualityScore.of(0.0, 0.0, 0.0));
        }

        double completeness = evaluateCompleteness(prompt, response, taskCategory);
        double relevance = evaluateRelevance(prompt, response);
        double formatCompliance = evaluateFormatCompliance(response, taskCategory);

        QualityScore score = QualityScore.of(completeness, relevance, formatCompliance);

        log.debug("Quality evaluation: completeness={:.2f}, relevance={:.2f}, format={:.2f}, composite={:.2f}",
                  completeness, relevance, formatCompliance, score.compositeScore());

        return Mono.just(score);
    }

    /**
     * Evaluate completeness: is the response thorough enough for the task type?
     * Different task categories have different length expectations.
     */
    private double evaluateCompleteness(String prompt, String response, TaskCategory taskCategory) {
        int responseWords = response.split("\\s+").length;
        int promptWords = prompt.split("\\s+").length;

        // Expected minimum response length varies by task
        int expectedMinWords = switch (taskCategory) {
            case CODE -> Math.max(20, promptWords * 2);
            case REASONING -> Math.max(30, promptWords * 2);
            case CREATIVE -> Math.max(40, promptWords * 3);
            case FACTUAL -> Math.max(10, promptWords);
            case CONVERSATION -> Math.max(5, promptWords / 2);
        };

        // Score based on how well response meets expected length
        double ratio = (double) responseWords / expectedMinWords;
        return Math.min(1.0, ratio);  // Cap at 1.0 — longer isn't always better
    }

    /**
     * Evaluate relevance: how much does the response relate to the prompt?
     * Uses normalized keyword overlap (Jaccard-like coefficient).
     */
    private double evaluateRelevance(String prompt, String response) {
        Set<String> promptTokens = tokenize(prompt);
        Set<String> responseTokens = tokenize(response);

        if (promptTokens.isEmpty() || responseTokens.isEmpty()) {
            return 0.0;
        }

        // Count how many prompt keywords appear in the response
        long overlap = promptTokens.stream()
            .filter(responseTokens::contains)
            .count();

        // Normalize by prompt size (what fraction of the question is addressed)
        return Math.min(1.0, (double) overlap / promptTokens.size());
    }

    /**
     * Evaluate format compliance based on task category expectations.
     */
    private double evaluateFormatCompliance(String response, TaskCategory taskCategory) {
        return switch (taskCategory) {
            case CODE -> {
                // Code responses should contain code-like patterns
                boolean hasCodeBlock = response.contains("```") || response.contains("    ");
                boolean hasKeywords = response.contains("function") || response.contains("class")
                    || response.contains("def ") || response.contains("return")
                    || response.contains("public") || response.contains("import");
                yield (hasCodeBlock ? 0.5 : 0.0) + (hasKeywords ? 0.5 : 0.0);
            }
            case REASONING -> {
                // Reasoning responses should have structure
                boolean hasStructure = response.contains("\n") || response.contains(". ");
                boolean hasLength = response.split("\\s+").length > 20;
                yield (hasStructure ? 0.5 : 0.0) + (hasLength ? 0.5 : 0.0);
            }
            case FACTUAL -> {
                // Factual responses should be concise and direct
                int words = response.split("\\s+").length;
                yield (words > 3 && words < 500) ? 1.0 : 0.5;
            }
            default -> {
                // For creative/conversation, any non-empty response is acceptable
                yield response.length() > 10 ? 1.0 : 0.5;
            }
        };
    }

    /**
     * Tokenize text into lowercase words, filtering out common stop words
     * and very short tokens that don't carry semantic meaning.
     */
    private Set<String> tokenize(String text) {
        String[] words = text.toLowerCase().split("[\\s,.!?;:\"'()\\[\\]{}]+");
        Set<String> tokens = new HashSet<>();
        for (String word : words) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "can", "shall", "and", "but", "or", "not",
        "this", "that", "these", "those", "for", "with", "from", "into",
        "about", "than", "then", "also", "just", "more", "very", "too"
    );
}
