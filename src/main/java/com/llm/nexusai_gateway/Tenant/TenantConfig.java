package com.llm.nexusai_gateway.Tenant;

import jakarta.persistence.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "tenant_configs")
public class TenantConfig {

    @Id
    private String tenantId;
    
    @Column(nullable = false)
    private String tenantName;
    
    @Column(name = "organization_id")
    private String organizationId;
    
    @Column(unique = true)
    private String apiKey;
    
    private double dailyBudgetUsd;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_allowed_models", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "model_id")
    private List<String> allowedModels;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_blocked_models", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "model_id")
    private List<String> blockedModels;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_allowed_pipelines", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "pipeline_name")
    private List<String> allowedPipelines;

    private int maxRequestsPerMinute;
    private boolean piiEnforcementEnabled;
    private boolean jailbreakEnforcementEnabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_reward_weights", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "weight")
    private List<Double> rewardWeights;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean isActive = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_allowed_ips", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "ip_address")
    private List<String> allowedIps;

    @Transient
    private final AtomicLong spendMicrocents = new AtomicLong(0);

    public TenantConfig() {}

    public TenantConfig(String tenantId, String tenantName, String organizationId, double dailyBudgetUsd,
                        List<String> allowedModels, List<String> blockedModels,
                        List<String> allowedPipelines, int maxRequestsPerMinute,
                        boolean piiEnforcementEnabled, boolean jailbreakEnforcementEnabled,
                        double[] rewardWeights) {
        this.tenantId                   = tenantId;
        this.tenantName                 = tenantName;
        this.organizationId             = organizationId;
        this.dailyBudgetUsd             = dailyBudgetUsd;
        this.allowedModels              = allowedModels  != null ? allowedModels  : List.of();
        this.blockedModels              = blockedModels  != null ? blockedModels  : List.of();
        this.allowedPipelines           = allowedPipelines != null ? allowedPipelines : List.of();
        this.maxRequestsPerMinute       = maxRequestsPerMinute;
        this.piiEnforcementEnabled      = piiEnforcementEnabled;
        this.jailbreakEnforcementEnabled = jailbreakEnforcementEnabled;
        
        if (rewardWeights != null) {
            this.rewardWeights = java.util.Arrays.stream(rewardWeights).boxed().toList();
        } else {
            this.rewardWeights = List.of(0.25, 0.25, 0.25, 0.25);
        }
        this.isActive = true;
        this.allowedIps = List.of();
    }

    /**
     * Attempt to deduct a cost from this tenant's daily budget.
     * @return true if budget was available and deducted, false if budget exceeded
     */
    public boolean tryDeductBudget(double costUsd) {
        long microcents = Math.round(costUsd * 1_000_000);
        long budget     = Math.round(dailyBudgetUsd * 1_000_000);
        long after      = spendMicrocents.addAndGet(microcents);
        return after <= budget;
    }

    /** Reset daily spend (called by a scheduled job in production) */
    public void resetDailySpend() {
        spendMicrocents.set(0);
    }

    public double getRemainingBudget() {
        long spent = spendMicrocents.get();
        return Math.max(0.0, dailyBudgetUsd - (spent / 1_000_000.0));
    }

    public String getTenantId()                  { return tenantId; }
    public String getTenantName()                { return tenantName; }
    public String getOrganizationId()            { return organizationId; }
    public String getApiKey()                    { return apiKey; }
    public void setApiKey(String apiKey)         { this.apiKey = apiKey; }
    public double getDailyBudgetUsd()            { return dailyBudgetUsd; }
    public void setDailyBudgetUsd(double dailyBudgetUsd) { this.dailyBudgetUsd = dailyBudgetUsd; }
    public List<String> getAllowedModels()        { return allowedModels; }
    public List<String> getBlockedModels()       { return blockedModels; }
    public List<String> getAllowedPipelines()     { return allowedPipelines; }
    public int getMaxRequestsPerMinute()         { return maxRequestsPerMinute; }
    public boolean isPiiEnforcementEnabled()     { return piiEnforcementEnabled; }
    public boolean isJailbreakEnforcementEnabled(){ return jailbreakEnforcementEnabled; }
    public double[] getRewardWeights()           { 
        if (rewardWeights == null) return new double[]{0.25, 0.25, 0.25, 0.25};
        return rewardWeights.stream().mapToDouble(Double::doubleValue).toArray(); 
    }

    public void setRewardWeights(double[] weights) {
        this.rewardWeights = java.util.Arrays.stream(weights).boxed().toList();
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public List<String> getAllowedIps() { return allowedIps != null ? allowedIps : List.of(); }
    public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps; }

    public static String hashApiKey(String rawKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
