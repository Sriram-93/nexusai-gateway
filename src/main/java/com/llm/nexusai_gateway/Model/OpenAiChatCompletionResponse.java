package com.llm.nexusai_gateway.Model;

import java.util.List;
import java.util.Map;

public class OpenAiChatCompletionResponse {

    private String id;
    private String object = "chat.completion";
    private long created = System.currentTimeMillis() / 1000;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    private NexusAiRoutingMetadata nexusaiRouting;

    public static class Choice {
        private int index = 0;
        private OpenAiChatCompletionRequest.ChatMessage message;
        private String finish_reason = "stop";

        public Choice() {}

        public Choice(int index, OpenAiChatCompletionRequest.ChatMessage message, String finish_reason) {
            this.index = index;
            this.message = message;
            this.finish_reason = finish_reason;
        }

        public int getIndex() { return index; }
        public OpenAiChatCompletionRequest.ChatMessage getMessage() { return message; }
        public String getFinish_reason() { return finish_reason; }
    }

    public static class Usage {
        private int prompt_tokens = 0;
        private int completion_tokens = 0;
        private int total_tokens = 0;

        public Usage() {}

        public Usage(int prompt_tokens, int completion_tokens) {
            this.prompt_tokens = prompt_tokens;
            this.completion_tokens = completion_tokens;
            this.total_tokens = prompt_tokens + completion_tokens;
        }

        public int getPrompt_tokens() { return prompt_tokens; }
        public int getCompletion_tokens() { return completion_tokens; }
        public int getTotal_tokens() { return total_tokens; }
    }

    public static class NexusAiRoutingMetadata {
        private String selectedProvider;
        private String selectedModel;
        private String strategy;
        private String reason;
        private long latencyMs;
        private Map<String, Double> armScores;

        public NexusAiRoutingMetadata() {}

        public NexusAiRoutingMetadata(String selectedProvider, String selectedModel, String strategy, String reason, long latencyMs, Map<String, Double> armScores) {
            this.selectedProvider = selectedProvider;
            this.selectedModel = selectedModel;
            this.strategy = strategy;
            this.reason = reason;
            this.latencyMs = latencyMs;
            this.armScores = armScores;
        }

        public String getSelectedProvider() { return selectedProvider; }
        public String getSelectedModel() { return selectedModel; }
        public String getStrategy() { return strategy; }
        public String getReason() { return reason; }
        public long getLatencyMs() { return latencyMs; }
        public Map<String, Double> getArmScores() { return armScores; }
    }

    public OpenAiChatCompletionResponse() {}

    public OpenAiChatCompletionResponse(String id, String model, List<Choice> choices, Usage usage, NexusAiRoutingMetadata nexusaiRouting) {
        this.id = id;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
        this.nexusaiRouting = nexusaiRouting;
    }

    public String getId() { return id; }
    public String getObject() { return object; }
    public long getCreated() { return created; }
    public String getModel() { return model; }
    public List<Choice> getChoices() { return choices; }
    public Usage getUsage() { return usage; }
    public NexusAiRoutingMetadata getNexusaiRouting() { return nexusaiRouting; }
    public void setNexusaiRouting(NexusAiRoutingMetadata nexusaiRouting) { this.nexusaiRouting = nexusaiRouting; }
}
