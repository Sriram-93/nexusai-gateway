package com.llm.nexusai_gateway.Agent;

public class AgentChatResponse {
    private String answer;
    private long latencyMs;
    private IntentAgent.IntentResult intent;
    private ContextAgent.ContextResult context;
    private PolicyAgent.PolicyResult policy;
    private RoutingAgent.RoutingResult routing;
    private QualityAgent.QualityResult quality;

    public AgentChatResponse() {}

    public AgentChatResponse(String answer, long latencyMs,
                             IntentAgent.IntentResult intent,
                             ContextAgent.ContextResult context,
                             PolicyAgent.PolicyResult policy,
                             RoutingAgent.RoutingResult routing,
                             QualityAgent.QualityResult quality) {
        this.answer = answer;
        this.latencyMs = latencyMs;
        this.intent = intent;
        this.context = context;
        this.policy = policy;
        this.routing = routing;
        this.quality = quality;
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public IntentAgent.IntentResult getIntent() { return intent; }
    public void setIntent(IntentAgent.IntentResult intent) { this.intent = intent; }

    public ContextAgent.ContextResult getContext() { return context; }
    public void setContext(ContextAgent.ContextResult context) { this.context = context; }

    public PolicyAgent.PolicyResult getPolicy() { return policy; }
    public void setPolicy(PolicyAgent.PolicyResult policy) { this.policy = policy; }

    public RoutingAgent.RoutingResult getRouting() { return routing; }
    public void setRouting(RoutingAgent.RoutingResult routing) { this.routing = routing; }

    public QualityAgent.QualityResult getQuality() { return quality; }
    public void setQuality(QualityAgent.QualityResult quality) { this.quality = quality; }
}
