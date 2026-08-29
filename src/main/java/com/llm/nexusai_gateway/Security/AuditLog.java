package com.llm.nexusai_gateway.Security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_org", columnList = "organization_id"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditLog {

    @Id
    private String id;

    @Column(name = "actor_email", nullable = false)
    private String actorEmail;

    @Column(nullable = false)
    private String action; // e.g. "API_KEY_CREATED", "PROVIDER_ADDED", "POLICY_CHANGED"

    @Column(nullable = false)
    private String resource; // e.g. "ApiKey:nx_live_123", "Provider:groq"

    @Column(name = "organization_id")
    private String organizationId;

    @Column(name = "metadata_json", length = 2048)
    private String metadataJson;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public AuditLog() {
        this.id = UUID.randomUUID().toString();
    }

    public AuditLog(String actorEmail, String action, String resource, String organizationId, String metadataJson) {
        this.id = UUID.randomUUID().toString();
        this.actorEmail = actorEmail;
        this.action = action;
        this.resource = resource;
        this.organizationId = organizationId;
        this.metadataJson = metadataJson;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
