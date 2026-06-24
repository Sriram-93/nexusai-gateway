package com.llm.nexusai_gateway.Model;

public class ChatResponse {
    private String answer;
    private String provider;
    private long latencyMs;

    public ChatResponse() {}

    public ChatResponse(String answer, String provider, long latencyMs) {
        this.answer = answer;
        this.provider = provider;
        this.latencyMs = latencyMs;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
