package com.llm.nexusai_gateway.Agent;

/**
 * Signal returned by each agent to the WorkflowEngine after execution.
 *
 * CONTINUE  — agent succeeded, proceed to next step
 * SKIP      — agent was not applicable, skip gracefully
 * TERMINATE — pipeline must halt (e.g. security violation, budget exceeded)
 */
public enum WorkflowSignal {
    CONTINUE,
    SKIP,
    TERMINATE
}
