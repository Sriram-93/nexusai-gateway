package com.llm.nexusai_gateway.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiChatCompletionRequest {

    private String model = "auto";
    private List<ChatMessage> messages;
    private Boolean stream = false;
    private Double temperature;
    private Integer max_tokens;
    private String user;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatMessage {
        private String role; // "system", "user", "assistant"
        private String content;

        public ChatMessage() {}

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public OpenAiChatCompletionRequest() {}

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMax_tokens() { return max_tokens; }
    public void setMax_tokens(Integer max_tokens) { this.max_tokens = max_tokens; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
}
