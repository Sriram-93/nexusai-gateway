package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Agent.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for querying registered agent workflow definitions and agent contracts (Priority 5).
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineConfigController {

    private final AgentRegistry agentRegistry;
    private final WorkflowDefinitionFactory definitionFactory;

    public PipelineConfigController(AgentRegistry agentRegistry,
                                    WorkflowDefinitionFactory definitionFactory) {
        this.agentRegistry     = agentRegistry;
        this.definitionFactory = definitionFactory;
    }

    /**
     * Get all registered agents along with their order, dependencies, required inputs, and produced outputs.
     */
    @GetMapping("/agents")
    public Mono<List<Map<String, Object>>> getRegisteredAgents() {
        List<Map<String, Object>> agentsMap = agentRegistry.getOrderedAgents().stream()
            .map(agent -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", agent.getName());
                map.put("order", agent.getOrder());
                map.put("dependencies", agent.getDependencies());
                map.put("requiredInputs", agent.getRequiredInputs());
                map.put("producedOutputs", agent.getProducedOutputs());
                return map;
            })
            .toList();

        return Mono.just(agentsMap);
    }

    /**
     * Get all predefined workflow pipeline definitions and their steps.
     */
    @GetMapping("/definitions")
    public Mono<Map<String, List<String>>> getPipelineDefinitions() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        result.put("DEFAULT", getStepNames(WorkflowDefinitionFactory.DEFAULT));
        result.put("GREETING", getStepNames(WorkflowDefinitionFactory.GREETING));
        result.put("SECURITY_FAST_PATH", getStepNames(WorkflowDefinitionFactory.SECURITY_FAST_PATH));
        result.put("CODING", getStepNames(WorkflowDefinitionFactory.CODING));

        return Mono.just(result);
    }

    private List<String> getStepNames(WorkflowDefinition definition) {
        return definition.getSteps().stream()
            .map(WorkflowStep::getAgentName)
            .toList();
    }
}
