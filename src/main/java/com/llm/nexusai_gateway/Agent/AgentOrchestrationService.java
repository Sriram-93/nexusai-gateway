package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Model.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * AgentOrchestrationService — entry point for the cognitive agent pipeline.
 *
 * BEFORE (tightly coupled):
 *   This class directly injected all 6 agents, contained the entire pipeline logic,
 *   and had to be modified every time a new agent was added or the pipeline changed.
 *
 * AFTER (registry-driven):
 *   This class only holds a reference to the WorkflowEngine. All pipeline logic
 *   lives in WorkflowEngine, all agents are discovered by AgentRegistry.
 *   Adding a 7th agent requires ZERO modifications to this class.
 *
 * Backward compatibility: The public process() method signature is unchanged.
 * The ChatController continues to call this service without any modification.
 *
 * Design Pattern: Façade (delegates to WorkflowEngine + post-processing)
 */
@Service
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    private final WorkflowEngine workflowEngine;
    private final DecisionEngine decisionEngine;
    private final com.llm.nexusai_gateway.Telemetry.TelemetryService telemetryService;
    private final com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor;

    public AgentOrchestrationService(WorkflowEngine workflowEngine,
                                     DecisionEngine decisionEngine,
                                     com.llm.nexusai_gateway.Telemetry.TelemetryService telemetryService,
                                     com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor) {
        this.workflowEngine   = workflowEngine;
        this.decisionEngine   = decisionEngine;
        this.telemetryService = telemetryService;
        this.contextExtractor = contextExtractor;
    }

    /**
     * Process a chat request through the full 6-agent cognitive pipeline.
     *
     * @param request the incoming chat request
     * @return a reactive Mono containing the full AgentChatResponse
     */
    public Mono<AgentChatResponse> process(ChatRequest request) {
        AgentContext ctx = new AgentContext(request);

        return contextExtractor.extract(request)
            .flatMap(reqCtx -> {
                ctx.setRequestContext(reqCtx);
                return workflowEngine.execute(ctx);
            })
            .flatMap(finishedCtx -> {
                // Post-workflow: trigger online LinUCB bandit update with the quality reward
                String pipelineName = "DEFAULT";
                if (!finishedCtx.isTerminated()
                        && finishedCtx.getQualityResult() != null
                        && finishedCtx.getRoutingResult() != null
                        && finishedCtx.getRequestContext() != null) {

                    double reward = finishedCtx.getQualityResult().getCompositeScore() / 100.0;
                    String armKey = finishedCtx.getRoutingResult().getProvider() + ":"
                                  + finishedCtx.getRoutingResult().getModel();
                    RequestContext rc = finishedCtx.getRequestContext();

                    decisionEngine.update(rc, armKey, reward);
                    log.info("LinUCB bandit updated: arm={} reward={:.3f}", armKey, reward);
                }

                // Priority 9: record telemetry metrics
                telemetryService.recordPipelineCompletion(finishedCtx, pipelineName);

                return Mono.just(buildResponse(finishedCtx));
            });
    }

    /**
     * Converts the completed AgentContext into the API response object.
     * Preserves the existing AgentChatResponse structure for backward compatibility.
     */
    private AgentChatResponse buildResponse(AgentContext ctx) {
        String responseText = ctx.getFinalResponse();
        if (responseText == null || responseText.isBlank()) {
            responseText = ctx.isTerminated()
                ? "Request Blocked: " + ctx.getTerminationReason()
                : "No response generated.";
        }

        IntentAgent.IntentResult intent = ctx.getIntentResult() != null
            ? ctx.getIntentResult()
            : new IntentAgent.IntentResult("conversation", "low", false, false);

        ContextAgent.ContextResult context = ctx.getContextResult() != null
            ? ctx.getContextResult()
            : new ContextAgent.ContextResult(java.util.Collections.emptyList(), "N/A", "N/A");

        PolicyAgent.PolicyResult policy = ctx.getPolicyResult() != null
            ? ctx.getPolicyResult()
            : new PolicyAgent.PolicyResult(java.util.Collections.emptyList(), 0.5, true, false, false, "N/A");

        RoutingAgent.RoutingResult routing = ctx.getRoutingResult() != null
            ? ctx.getRoutingResult()
            : new RoutingAgent.RoutingResult("none", "none", "Not routed", "NONE");

        QualityAgent.QualityResult quality = ctx.getQualityResult() != null
            ? ctx.getQualityResult()
            : new QualityAgent.QualityResult(0.0, 0.0, false, false, 0.0, 0.0);

        log.info("Pipeline complete in {}ms | agent timings: {}", ctx.elapsedMs(), ctx.getAgentTimings());

        return new AgentChatResponse(responseText, ctx.elapsedMs(), intent, context, policy, routing, quality);
    }
}
