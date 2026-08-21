package com.llm.nexusai_gateway.Agent;

import java.util.List;

/**
 * An immutable, ordered sequence of WorkflowSteps that defines a complete pipeline.
 *
 * A WorkflowDefinition is:
 *  - Named (for logging and metrics)
 *  - Composed of an ordered list of WorkflowSteps
 *  - Created by WorkflowDefinitionFactory based on request intent
 *
 * The WorkflowEngine reads the definition and executes steps in order,
 * grouping consecutive parallel steps into concurrent tiers.
 *
 * This class replaces the hardcoded getOrder()-based auto-detection in
 * the original WorkflowEngine, making the pipeline fully declarative.
 *
 * Example definitions:
 *  - DEFAULT_PIPELINE      : full 6-agent pipeline
 *  - GREETING_PIPELINE     : skips RAG Context agent
 *  - SECURITY_FAST_PATH    : terminates at Policy, no LLM call
 *  - CODING_PIPELINE       : adds specialized code-context step (future)
 */
public final class WorkflowDefinition {

    private final String name;
    private final List<WorkflowStep> steps;

    public WorkflowDefinition(String name, List<WorkflowStep> steps) {
        this.name  = name;
        this.steps = List.copyOf(steps); // immutable
    }

    public String getName()             { return name; }
    public List<WorkflowStep> getSteps(){ return steps; }

    @Override
    public String toString() {
        return String.format("WorkflowDefinition[%s, %d steps]", name, steps.size());
    }
}
