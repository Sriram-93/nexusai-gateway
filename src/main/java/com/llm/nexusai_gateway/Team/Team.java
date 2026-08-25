package com.llm.nexusai_gateway.Team;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A Team belongs to an Organization and has:
 *  - a designated Team Lead (userId FK into users table)
 *  - its own TenantConfig (API key + rate limits + budget)
 *  - an active/suspended flag controllable by ORG_ADMIN only
 */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** FK → organizations.id */
    @Column(nullable = false, name = "organization_id")
    private String organizationId;

    /** FK → users.id — the designated Team Lead. Nullable until one is assigned. */
    @Column(name = "lead_user_id")
    private String leadUserId;

    /** Email of the lead — cached here for notification delivery without a join. */
    @Column(name = "lead_email")
    private String leadEmail;

    /** FK → tenant_registry.tenant_id — the gateway key/rate-limit pool for this team. */
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @Column(name = "daily_budget_usd")
    private Double dailyBudgetUsd;

    @Column(name = "budget_alert_sent_date")
    private LocalDate budgetAlertSentDate;

    public Team() {}

    public Team(String name, String description, String organizationId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.organizationId = organizationId;
        this.createdAt = Instant.now();
        this.active = true;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                        { return id; }
    public void setId(String id)                 { this.id = id; }

    public String getName()                      { return name; }
    public void setName(String name)             { this.name = name; }

    public String getDescription()               { return description; }
    public void setDescription(String d)         { this.description = d; }

    public String getOrganizationId()            { return organizationId; }
    public void setOrganizationId(String orgId)  { this.organizationId = orgId; }

    public String getLeadUserId()                { return leadUserId; }
    public void setLeadUserId(String uid)        { this.leadUserId = uid; }

    public String getLeadEmail()                 { return leadEmail; }
    public void setLeadEmail(String email)       { this.leadEmail = email; }

    public String getTenantId()                  { return tenantId; }
    public void setTenantId(String tid)          { this.tenantId = tid; }

    public boolean isActive()                    { return active; }
    public void setActive(boolean active)        { this.active = active; }

    public Instant getCreatedAt()                { return createdAt; }
    public void setCreatedAt(Instant t)          { this.createdAt = t; }

    public Double getDailyBudgetUsd()            { return dailyBudgetUsd; }
    public void setDailyBudgetUsd(Double b)      { this.dailyBudgetUsd = b; }

    public LocalDate getBudgetAlertSentDate()    { return budgetAlertSentDate; }
    public void setBudgetAlertSentDate(LocalDate d) { this.budgetAlertSentDate = d; }
}
