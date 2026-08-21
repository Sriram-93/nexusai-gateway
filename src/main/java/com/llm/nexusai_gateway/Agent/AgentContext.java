package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Model.ChatRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, mutable execution context passed between all agents in the pipeline.
 *
 * AgentContext replaces the tightly-coupled parameter threading that existed
 * in the old AgentOrchestrationService. Every agent reads its required inputs
 * from this context and writes its results back into it.
 *
 * This is the shared "blackboard" in the Blackboard architectural pattern.
 * It provides a single source of truth for the entire workflow execution.
 */
public class AgentContext {

    // --- Request metadata ---
    private final ChatRequest originalRequest;
    private final String message;
    private final String userId;
    private final long startTimeMs;
    private RequestContext requestContext;

    // --- Agent results (populated as pipeline executes) ---
    private IntentAgent.IntentResult intentResult;
    private ContextAgent.ContextResult contextResult;
    private PolicyAgent.PolicyResult policyResult;
    private RoutingAgent.RoutingResult routingResult;
    private QualityAgent.QualityResult qualityResult;

    // --- Execution state ---
    private volatile String finalResponse;
    private volatile boolean terminated = false;
    private volatile String terminationReason;

    // --- Timing data: agent name → duration in ms ---
    private final ConcurrentHashMap<String, Long> agentTimings = new ConcurrentHashMap<>();

    // --- Collected warnings/messages from agents ---
    private final List<String> executionNotes = new java.util.concurrent.CopyOnWriteArrayList<>();

    // --- DAG Short-circuit tracking ---
    private final java.util.Set<String> skippedAgents = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final java.util.Set<String> failedAgents  = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public AgentContext(ChatRequest request) {
        this.originalRequest = request;
        this.message = request.getMessage() != null ? request.getMessage() : "";
        this.userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        this.startTimeMs = System.currentTimeMillis();
    }

    // --- Execution helpers ---

    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    public void recordAgentTiming(String agentName, long durationMs) {
        agentTimings.put(agentName, durationMs);
    }

    public void addNote(String note) {
        executionNotes.add(note);
    }

    public void terminate(String reason) {
        this.terminated = true;
        this.terminationReason = reason;
    }

    public void markSkipped(String agentName) {
        if (agentName != null) this.skippedAgents.add(agentName);
    }

    public void markFailed(String agentName) {
        if (agentName != null) this.failedAgents.add(agentName);
    }

    public boolean isSkipped(String agentName) {
        return this.skippedAgents.contains(agentName);
    }

    public boolean isFailed(String agentName) {
        return this.failedAgents.contains(agentName);
    }

    public boolean hasDependencySkippedOrFailed(List<String> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) return false;
        for (String dep : dependencies) {
            if (skippedAgents.contains(dep) || failedAgents.contains(dep)) {
                return true;
            }
        }
        return false;
    }

    // --- Getters ---

    public ChatRequest getOriginalRequest()                     { return originalRequest; }
    public String getMessage()                                  { return message; }
    public String getUserId()                                   { return userId; }
    public long getStartTimeMs()                                { return startTimeMs; }
    public RequestContext getRequestContext()                    { return requestContext; }
    public IntentAgent.IntentResult getIntentResult()           { return intentResult; }
    public ContextAgent.ContextResult getContextResult()        { return contextResult; }
    public PolicyAgent.PolicyResult getPolicyResult()           { return policyResult; }
    public RoutingAgent.RoutingResult getRoutingResult()        { return routingResult; }
    public QualityAgent.QualityResult getQualityResult()        { return qualityResult; }
    public String getFinalResponse()                            { return finalResponse; }
    public boolean isTerminated()                               { return terminated; }
    public String getTerminationReason()                        { return terminationReason; }
    public ConcurrentHashMap<String, Long> getAgentTimings()    { return agentTimings; }
    public List<String> getExecutionNotes()                     { return executionNotes; }

    // --- Setters ---

    public void setRequestContext(RequestContext requestContext)               { this.requestContext = requestContext; }
    public void setIntentResult(IntentAgent.IntentResult intentResult)         { this.intentResult = intentResult; }
    public void setContextResult(ContextAgent.ContextResult contextResult)     { this.contextResult = contextResult; }
    public void setPolicyResult(PolicyAgent.PolicyResult policyResult)         { this.policyResult = policyResult; }
    public void setRoutingResult(RoutingAgent.RoutingResult routingResult)     { this.routingResult = routingResult; }
    public void setQualityResult(QualityAgent.QualityResult qualityResult)    { this.qualityResult = qualityResult; }
    public void setFinalResponse(String finalResponse)                         { this.finalResponse = finalResponse; }
}
