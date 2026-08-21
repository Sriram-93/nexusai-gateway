package com.llm.nexusai_gateway.Agent;

/**
 * A single step in a WorkflowDefinition.
 *
 * Each step declares:
 *  - agentName       : the Agent bean to execute (looked up from AgentRegistry)
 *  - condition       : runtime predicate — if false, step is SKIPPED
 *  - onFailure       : what to do when the agent returns TERMINATE
 *  - parallel        : if true, this step may run concurrently with other
 *                      steps that share the same tier in the WorkflowDefinition
 *  - alwaysRun       : if true, this step executes even when pipeline is TERMINATED
 *                      (used by FeedbackAgent to always capture failure telemetry)
 *
 * Design: Immutable value object. Use the builder() factory method to construct.
 */
public final class WorkflowStep {

    private final String agentName;
    private final WorkflowCondition condition;
    private final OnFailureAction onFailure;
    private final boolean parallel;
    private final boolean alwaysRun;

    private WorkflowStep(Builder b) {
        this.agentName  = b.agentName;
        this.condition  = b.condition;
        this.onFailure  = b.onFailure;
        this.parallel   = b.parallel;
        this.alwaysRun  = b.alwaysRun;
    }

    public String getAgentName()            { return agentName; }
    public WorkflowCondition getCondition() { return condition; }
    public OnFailureAction getOnFailure()   { return onFailure; }
    public boolean isParallel()             { return parallel; }
    public boolean isAlwaysRun()            { return alwaysRun; }

    @Override
    public String toString() {
        return String.format("WorkflowStep[%s | parallel=%s | alwaysRun=%s | onFailure=%s]",
                agentName, parallel, alwaysRun, onFailure);
    }

    // --- Builder ---

    public static Builder of(String agentName) {
        return new Builder(agentName);
    }

    public static final class Builder {
        private final String agentName;
        private WorkflowCondition condition = WorkflowCondition.always();
        private OnFailureAction onFailure   = OnFailureAction.HALT;
        private boolean parallel            = false;
        private boolean alwaysRun           = false;

        private Builder(String agentName) {
            this.agentName = agentName;
        }

        public Builder condition(WorkflowCondition c) { this.condition = c; return this; }
        public Builder onFailure(OnFailureAction a)   { this.onFailure = a; return this; }
        public Builder parallel()                     { this.parallel = true; return this; }
        public Builder alwaysRun()                    { this.alwaysRun = true; return this; }

        public WorkflowStep build() {
            return new WorkflowStep(this);
        }
    }
}
