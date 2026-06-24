package com.llm.nexusai_gateway.Model;

public class ChatRequest {
    private String message;
    private String userId;
    private String tenantId;
    private Priority priority;
    private String provider;
    private String model;

    public ChatRequest() {}

    public ChatRequest(String message, String userId, String tenantId, Priority priority, String provider, String model) {
        this.message = message;
        this.userId = userId;
        this.tenantId = tenantId;
        this.priority = priority;
        this.provider = provider;
        this.model = model;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
