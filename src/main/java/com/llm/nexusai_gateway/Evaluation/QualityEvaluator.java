package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;
import reactor.core.publisher.Mono;

/**
 * Evaluates the quality of an LLM response using task-aware evaluation.
 *
 * This interface can be implemented by heuristic approaches (fast/free)
 * or by LLM-as-a-Judge approaches (slow/costly/accurate).
 */
public interface QualityEvaluator {

    /**
     * Evaluate the quality of a response given the original prompt and task category.
     *
     * @param prompt       The original user prompt
     * @param response     The LLM-generated response
     * @param taskCategory The classified task type
     * @return Quality score with per-dimension breakdown asynchronously
     */
    Mono<QualityScore> evaluate(String prompt, String response, TaskCategory taskCategory);
}
