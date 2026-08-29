package com.llm.nexusai_gateway.Security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_credentials", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider_slug", "organization_id"})
})
public class ProviderCredential {

    @Id
    private String id;

    @Column(name = "provider_slug", nullable = false)
    private String providerSlug; // e.g. "openai", "anthropic", "gemini", "groq", "ollama"

    @Column(name = "key_name")
    private String keyName;

    @Column(name = "encrypted_secret", nullable = false, length = 1024)
    private String encryptedSecret;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, DISABLED

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public ProviderCredential() {
        this.id = UUID.randomUUID().toString();
    }

    public ProviderCredential(String providerSlug, String keyName, String encryptedSecret, Organization organization) {
        this.id = UUID.randomUUID().toString();
        this.providerSlug = providerSlug;
        this.keyName = keyName;
        this.encryptedSecret = encryptedSecret;
        this.organization = organization;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProviderSlug() { return providerSlug; }
    public void setProviderSlug(String providerSlug) { this.providerSlug = providerSlug; }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getEncryptedSecret() { return encryptedSecret; }
    public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }

    public Organization getOrganization() { return organization; }
    public void setOrganization(Organization organization) { this.organization = organization; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
