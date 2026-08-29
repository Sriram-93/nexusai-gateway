package com.llm.nexusai_gateway.Security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_apikey_hash", columnList = "key_hash")
})
public class ApiKey {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment = Environment.DEVELOPMENT;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, REVOKED, DISABLED

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    public ApiKey() {
        this.id = UUID.randomUUID().toString();
    }

    public ApiKey(String name, String keyHash, String keyPrefix, Project project, Environment environment) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.project = project;
        this.environment = environment != null ? environment : Environment.DEVELOPMENT;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
