package com.llm.nexusai_gateway.Provider;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents a single discovered model arm in the database.
 *
 * Each record is the canonical source of truth for one routable model.
 * Pricing is stored here — sourced from the community-maintained LiteLLM
 * price list or updated manually by an admin via the management API.
 * No pricing constants exist anywhere in Java code.
 */
@Entity
@Table(
    name = "registered_models",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider_slug", "model_id"})
)
public class RegisteredModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The canonical routing arm key: "{providerSlug}:{modelId}".
     * Used everywhere in the decision engine (e.g., "groq:llama-3.3-70b-versatile").
     */
    @Column(nullable = false, unique = true)
    private String armKey;

    /** The slug of the parent ProviderConfig (e.g., "groq", "my-openai"). */
    @Column(name = "provider_slug", nullable = false)
    private String providerSlug;

    /** The model identifier as the provider's API expects it. */
    @Column(name = "model_id", nullable = false)
    private String modelId;

    /** Human-readable display name for the UI. */
    private String displayName;

    // ─── Pricing (USD per 1 million tokens) ──────────────────────────────────
    /** Cost per 1M prompt/input tokens in USD. 0.0 for local/free models. */
    @Column(nullable = false)
    private double inputPricePer1M = 0.0;

    /** Cost per 1M completion/output tokens in USD. */
    @Column(nullable = false)
    private double outputPricePer1M = 0.0;

    /**
     * Cached-input price (available on some providers like Anthropic/OpenAI).
     * Typically 50-90% cheaper than inputPricePer1M.
     */
    private double cachedInputPricePer1M = 0.0;

    // ─── Capabilities ─────────────────────────────────────────────────────────
    /** Maximum context window in tokens. */
    private int contextWindowTokens = 8192;

    /** Empirical median response latency in ms (used by LOWEST_LATENCY routing). */
    private int estimatedLatencyMs = 1000;

    /** Whether this model is enabled for routing by the customer. */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Whether pricing data has been populated (vs defaulting to 0). */
    @Column(nullable = false)
    private boolean pricingVerified = false;

    /** ISO timestamp of when this model was first discovered. */
    @Column(nullable = false)
    private Instant discoveredAt = Instant.now();

    /** ISO timestamp of when pricing was last updated. */
    private Instant pricingUpdatedAt;

    // ─── Business methods ─────────────────────────────────────────────────────

    /**
     * Computes actual cost in USD for a given request.
     * No magic numbers — reads from the fields above.
     */
    public double computeCostUsd(int inputTokens, int outputTokens) {
        return (inputTokens * inputPricePer1M / 1_000_000.0)
             + (outputTokens * outputPricePer1M / 1_000_000.0);
    }

    /** Rebuild the armKey from its constituent parts. Call after setting slug/modelId. */
    public void rebuildArmKey() {
        this.armKey = providerSlug + ":" + modelId;
    }

    // ─── Constructors ─────────────────────────────────────────────────────────
    public RegisteredModel() {}

    public RegisteredModel(String providerSlug, String modelId) {
        this.providerSlug = providerSlug;
        this.modelId = modelId;
        this.armKey = providerSlug + ":" + modelId;
        this.displayName = modelId;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────
    public Long getId() { return id; }
    public String getArmKey() { return armKey; }
    public void setArmKey(String armKey) { this.armKey = armKey; }
    public String getProviderSlug() { return providerSlug; }
    public void setProviderSlug(String providerSlug) { this.providerSlug = providerSlug; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public double getInputPricePer1M() { return inputPricePer1M; }
    public void setInputPricePer1M(double inputPricePer1M) { this.inputPricePer1M = inputPricePer1M; }
    public double getOutputPricePer1M() { return outputPricePer1M; }
    public void setOutputPricePer1M(double outputPricePer1M) { this.outputPricePer1M = outputPricePer1M; }
    public double getCachedInputPricePer1M() { return cachedInputPricePer1M; }
    public void setCachedInputPricePer1M(double cachedInputPricePer1M) { this.cachedInputPricePer1M = cachedInputPricePer1M; }
    public int getContextWindowTokens() { return contextWindowTokens; }
    public void setContextWindowTokens(int contextWindowTokens) { this.contextWindowTokens = contextWindowTokens; }
    public int getEstimatedLatencyMs() { return estimatedLatencyMs; }
    public void setEstimatedLatencyMs(int estimatedLatencyMs) { this.estimatedLatencyMs = estimatedLatencyMs; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isPricingVerified() { return pricingVerified; }
    public void setPricingVerified(boolean pricingVerified) { this.pricingVerified = pricingVerified; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getPricingUpdatedAt() { return pricingUpdatedAt; }
    public void setPricingUpdatedAt(Instant pricingUpdatedAt) { this.pricingUpdatedAt = pricingUpdatedAt; }
}
