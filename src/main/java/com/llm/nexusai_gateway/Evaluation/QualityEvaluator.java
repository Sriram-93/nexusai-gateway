package com.llm.nexusai_gateway.Evaluation;

import com.llm.nexusai_gateway.Context.TaskCategory;

/**
 * Evaluates the quality of an LLM response using task-aware heuristics.
 *
 * Doc 01: "Acknowledge that LLM judges are imperfect."
 *
 * This implementation uses lightweight heuristics (no LLM-as-Judge dependency)
 * to provide a fast, deterministic quality signal for the reward function.
 * Limitations should be discussed in the paper.
 */
public interface QualityEvaluator {

    /**
     * Evaluate the quality of a response given the original prompt and task category.
     *
     * @param prompt       The original user prompt
     * @param response     The LLM-generated response
     * @param taskCategory The classified task type
     * @return Quality score with per-dimension breakdown
     */
    QualityScore evaluate(String prompt, String response, TaskCategory taskCategory);
}
