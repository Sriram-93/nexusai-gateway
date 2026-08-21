package com.llm.nexusai_gateway.Agent;

/**
 * Controls what the WorkflowEngine does when a step returns TERMINATE or throws.
 *
 * HALT            — stop the pipeline immediately; FeedbackAgent still runs
 * LOG_AND_SKIP    — record the failure note, continue with next step
 */
public enum OnFailureAction {
    HALT,
    LOG_AND_SKIP
}
