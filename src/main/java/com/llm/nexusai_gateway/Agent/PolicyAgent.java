package com.llm.nexusai_gateway.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PolicyAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PolicyAgent.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{1,4}[-.\\s]?\\(?\\d{1,3}\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}");

    private static final List<String> SECURITY_JAILBREAK_KEYWORDS = List.of(
        "ignore previous instructions",
        "ignore all previous",
        "system prompt",
        "reveal your system",
        "bypass restriction"
    );

    @Value("${nexusai.policy.blocked-models:openai:gpt-4}")
    private String blockedModelsCsv;

    private final com.llm.nexusai_gateway.Tenant.TenantRegistry tenantRegistry;

    public PolicyAgent(com.llm.nexusai_gateway.Tenant.TenantRegistry tenantRegistry) {
        this.tenantRegistry = tenantRegistry;
    }

    // Simulated global budget manager (used when no tenantId is provided)
    private double remainingBudget = 0.50;

    @Override
    public String getName() { return "PolicyAgent"; }

    @Override
    public int getOrder() { return 2; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.List.of("IntentAgent", "ContextAgent");
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("message", "requestContext");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("policyResult");
    }

    /**
     * Agent interface bridge: evaluates policy and writes PolicyResult into AgentContext.
     * Returns TERMINATE if security check fails, causing immediate pipeline halt.
     */
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        return Mono.fromCallable(() -> {
            long t = System.currentTimeMillis();
            int tokenCount = ctx.getRequestContext() != null ? ctx.getRequestContext().estimatedTokenCount() : 50;
            double estimatedCost = tokenCount * 0.00001;

            // Priority 10: resolve tenantId from request
            String tenantId = (ctx.getOriginalRequest() != null)
                ? ctx.getOriginalRequest().getTenantId() : null;

            PolicyResult result = evaluate(ctx.getMessage(), estimatedCost, tenantId);
            ctx.setPolicyResult(result);
            ctx.recordAgentTiming(getName(), System.currentTimeMillis() - t);
            if (!result.isSecurityPassed() || result.isPiiDetected()) {
                ctx.terminate(result.getReason());
                return WorkflowSignal.TERMINATE;
            }
            return WorkflowSignal.CONTINUE;
        });
    }

    public PolicyResult evaluate(String message, double estimatedCost) {
        return evaluate(message, estimatedCost, null);
    }

    public PolicyResult evaluate(String message, double estimatedCost, String tenantId) {
        List<String> blockedModels = new ArrayList<>();
        if (blockedModelsCsv != null && !blockedModelsCsv.isBlank()) {
            blockedModels.addAll(Arrays.asList(blockedModelsCsv.split(",")));
        }

        // Priority 10: tenant-scoped policy evaluation
        boolean tenantPiiEnforcement      = true;
        boolean tenantJailbreakEnforcement = true;
        if (tenantId != null) {
            var tenantOpt = tenantRegistry.get(tenantId);
            if (tenantOpt.isPresent()) {
                var tenant = tenantOpt.get();
                // Merge tenant blocked models
                blockedModels.addAll(tenant.getBlockedModels());
                // Budget from tenant
                boolean deducted = tenant.tryDeductBudget(estimatedCost);
                if (!deducted) {
                    log.warn("[Tenant '{}'] Budget exceeded! Remaining: ${}", tenantId, tenant.getRemainingBudget());
                    return new PolicyResult(blockedModels, tenant.getRemainingBudget(),
                                           true, false, true,
                                           "[Tenant " + tenantId + "] Budget limit reached.");
                }
                tenantPiiEnforcement      = tenant.isPiiEnforcementEnabled();
                tenantJailbreakEnforcement = tenant.isJailbreakEnforcementEnabled();
                log.info("[Tenant '{}'] Budget deducted ${}, remaining: ${}",
                         tenantId, estimatedCost, tenant.getRemainingBudget());
            } else {
                log.warn("PolicyAgent: Unknown tenantId '{}' — applying global defaults", tenantId);
            }
        }

        // 1. Global budget enforcement (when no tenant)
        boolean budgetExceeded = false;
        if (tenantId == null) {
            if (remainingBudget - estimatedCost < 0) {
                budgetExceeded = true;
                log.warn("Budget exceeded! Remaining: ${}, Estimated Cost: ${}", remainingBudget, estimatedCost);
            } else {
                remainingBudget -= estimatedCost;
            }
        }

        // 2. PII scan
        boolean piiDetected = false;
        if (tenantPiiEnforcement && message != null) {
            piiDetected = EMAIL_PATTERN.matcher(message).find() || PHONE_PATTERN.matcher(message).find();
        }

        // 3. Security (Jailbreak) scan
        boolean securityPassed = true;
        if (tenantJailbreakEnforcement && message != null) {
            String lower = message.toLowerCase();
            for (String keyword : SECURITY_JAILBREAK_KEYWORDS) {
                if (lower.contains(keyword)) {
                    securityPassed = false;
                    log.warn("Jailbreak attempt blocked: prompt matches security keyword '{}'", keyword);
                    break;
                }
            }
        }

        String reason = "Policy Check Passed.";
        if (!securityPassed) {
            reason = "Security Threat: Prompt Injection / Jailbreak attempt detected.";
        } else if (piiDetected) {
            reason = "Compliance Risk: PII (email/phone) detected in prompt.";
        } else if (budgetExceeded) {
            reason = "Resource Exhaustion: Budget limit reached.";
        }

        return new PolicyResult(blockedModels, remainingBudget, securityPassed, piiDetected, budgetExceeded, reason);
    }

    public static class PolicyResult {
        private List<String> blockedModels;
        private double remainingBudget;
        private boolean securityPassed;
        private boolean piiDetected;
        private boolean budgetExceeded;
        private String reason;

        public PolicyResult() {}

        public PolicyResult(List<String> blockedModels, double remainingBudget, boolean securityPassed,
                            boolean piiDetected, boolean budgetExceeded, String reason) {
            this.blockedModels = blockedModels;
            this.remainingBudget = remainingBudget;
            this.securityPassed = securityPassed;
            this.piiDetected = piiDetected;
            this.budgetExceeded = budgetExceeded;
            this.reason = reason;
        }

        public List<String> getBlockedModels() { return blockedModels; }
        public void setBlockedModels(List<String> blockedModels) { this.blockedModels = blockedModels; }

        public double getRemainingBudget() { return remainingBudget; }
        public void setRemainingBudget(double remainingBudget) { this.remainingBudget = remainingBudget; }

        public boolean isSecurityPassed() { return securityPassed; }
        public void setSecurityPassed(boolean securityPassed) { this.securityPassed = securityPassed; }

        public boolean isPiiDetected() { return piiDetected; }
        public void setPiiDetected(boolean piiDetected) { this.piiDetected = piiDetected; }

        public boolean isBudgetExceeded() { return budgetExceeded; }
        public void setBudgetExceeded(boolean budgetExceeded) { this.budgetExceeded = budgetExceeded; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
