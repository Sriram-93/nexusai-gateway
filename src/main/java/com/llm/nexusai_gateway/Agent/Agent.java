package com.llm.nexusai_gateway.Agent;

import reactor.core.publisher.Mono;

/**
 * Common contract for all pipeline agents in the NexusAI Cognitive Control Plane.
 *
 * Every agent in the system implements this interface. The WorkflowEngine
 * discovers all Agent beans via AgentRegistry and executes them in
 * getOrder() sequence, passing the shared AgentContext between steps.
 *
 * Design Pattern: Chain of Responsibility + Plugin/Registry
 * Open/Closed: Adding a new agent requires ZERO modification to the orchestrator.
 */
public interface Agent {

    /**
     * Unique name identifying this agent in logs, metrics, and workflow definitions.
     */
    String getName();

    /**
     * Execution order within the default workflow.
     * Lower number = earlier execution.
     * Parallel agents (e.g. Intent=1, Context=1) share the same order value.
     */
    int getOrder();

    /**
     * Names of other agents that must complete before this agent can execute.
     */
    default java.util.List<String> getDependencies() {
        return java.util.Collections.emptyList();
    }

    /**
     * Input fields required from AgentContext for execution.
     */
    default java.util.List<String> getRequiredInputs() {
        return java.util.Collections.emptyList();
    }

    /**
     * Output fields produced and written to AgentContext upon execution.
     */
    default java.util.List<String> getProducedOutputs() {
        return java.util.Collections.emptyList();
    }

    /**
     * Execute this agent's logic against the shared context.
     * Agents read inputs from AgentContext and write their results back into it.
     * Returning a Mono that emits WorkflowSignal.TERMINATE halts the pipeline.
     *
     * @param context shared execution state for this request
     * @return Mono emitting the signal for the workflow engine
     */
    Mono<WorkflowSignal> execute(AgentContext context);
}
