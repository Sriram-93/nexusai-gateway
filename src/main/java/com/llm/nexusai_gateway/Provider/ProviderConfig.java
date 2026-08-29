package com.llm.nexusai_gateway.Provider;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a customer-configured AI provider stored in the database.
 *
 * A provider is any AI backend a customer adds via the management API.
 * The system supports multiple instances of the same TYPE
 * (e.g., two separate OpenAI accounts, or a custom Ollama endpoint).
 *
 * ProviderType.OPENAI_COMPATIBLE covers: Groq, Together.ai, Perplexity,
 * Mistral, Fireworks, Anyscale, OpenRouter, LM Studio, and any OpenAI-spec API.
 */
@Entity
@Table(name = "provider_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"slug", "tenant_id"})
})
public class ProviderConfig {

    public enum ProviderType {
        /** OpenAI REST schema — covers OpenAI, Groq, Together, Perplexity, Mistral, etc. */
        OPENAI_COMPATIBLE,
        /** Google Generative Language API schema */
        GEMINI,
        /** Anthropic Messages API schema */
        ANTHROPIC,
        /** AWS Bedrock InvokeModel / Converse API */
        BEDROCK,
        /** Google VertexAI generateContent API */
        VERTEXAI,
        /** Azure OpenAI API */
        AZURE,
        /** Local Ollama server (OpenAI-compatible schema, but model list via /api/tags) */
        OLLAMA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable display name set by the customer. */
    @Column(nullable = false)
    private String displayName;

    /** Internal slug used as the arm prefix (e.g. "groq", "my-openai"). */
    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderType type;

    /** Base API URL. Null for hosted providers (OpenAI, Anthropic) — use the type default. */
    private String baseUrl;

    /**
     * The tenant that owns this provider config.
     * All provider keys are isolated per tenant — one org cannot see or use another's keys.
     */
    @Column(name = "tenant_id")
    private String tenantId;

    /**
     * Flexible JSON credentials to support complex enterprise providers.
     * e.g., {"api_key": "..."} or {"access_key": "...", "secret_key": "...", "region": "..."}
     */
    @Convert(converter = EncryptedMapToJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private java.util.Map<String, String> credentials = new java.util.HashMap<>();

    /** AWS region for Bedrock. GCP project for VertexAI. */
    private String region;

    /** Whether this provider is active and eligible for routing. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Timestamp of last successful model discovery sync. */
    private Instant lastDiscoveredAt;

    /** Timestamp when this provider was registered by the customer. */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ─── Constructors ────────────────────────────────────────────────────────
    public ProviderConfig() {}

    public ProviderConfig(String displayName, String slug, ProviderType type,
                          String baseUrl, java.util.Map<String, String> credentials) {
        this.displayName = displayName;
        this.slug = slug;
        this.type = type;
        this.baseUrl = baseUrl;
        this.credentials = credentials != null ? credentials : new java.util.HashMap<>();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public ProviderType getType() { return type; }
    public void setType(ProviderType type) { this.type = type; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public java.util.Map<String, String> getCredentials() { return credentials; }
    public void setCredentials(java.util.Map<String, String> credentials) { this.credentials = credentials; }
    
    @Transient
    public String getApiKey() {
        return credentials != null ? credentials.get("api_key") : null;
    }
    
    @Transient
    public void setApiKey(String apiKey) {
        if (this.credentials == null) this.credentials = new java.util.HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            this.credentials.put("api_key", apiKey);
        } else {
            this.credentials.remove("api_key");
        }
    }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getLastDiscoveredAt() { return lastDiscoveredAt; }
    public void setLastDiscoveredAt(Instant lastDiscoveredAt) { this.lastDiscoveredAt = lastDiscoveredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
