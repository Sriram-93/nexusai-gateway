package com.llm.nexusai_gateway.Telemetry;

import com.llm.nexusai_gateway.Security.AuditLog;
import com.llm.nexusai_gateway.Security.AuditLogRepository;
import com.llm.nexusai_gateway.Security.PiiRedactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async Tracing Pipeline for NexusAI Gateway.
 *
 * Writes PII-redacted audit trail entries into the AuditLog table
 * asynchronously to not block the hot request path.
 *
 * Events tracked:
 *   - GATEWAY_REQUEST           : incoming /v1/ request
 *   - ROUTING_DECISION          : selected arm + strategy
 *   - PROVIDER_FALLBACK         : fallback cascade triggered
 *   - BUDGET_ENFORCEMENT        : spend cap triggered
 *   - PROVIDER_CIRCUIT_OPEN     : circuit breaker tripped
 *   - PII_REDACTION_APPLIED     : PII found and stripped
 */
@Service
public class RequestTracingService {

    private static final Logger log = LoggerFactory.getLogger(RequestTracingService.class);

    private final AuditLogRepository auditLogRepository;
    private final PiiRedactionService piiRedactionService;
    private final TrafficBroadcaster trafficBroadcaster;

    public RequestTracingService(AuditLogRepository auditLogRepository,
                                 PiiRedactionService piiRedactionService,
                                 TrafficBroadcaster trafficBroadcaster) {
        this.auditLogRepository = auditLogRepository;
        this.piiRedactionService = piiRedactionService;
        this.trafficBroadcaster = trafficBroadcaster;
    }

    // ─── Core Trace Events ────────────────────────────────────────────────────

    @Async
    public void traceGatewayRequest(String tenantId, String userId, String prompt, String model) {
        PiiRedactionService.RedactionResult redacted = piiRedactionService.redact(prompt);
        String snippet = truncate(redacted.redactedText(), 256);
        String piiNote = redacted.piiDetected()
            ? " [PII_REDACTED:" + redacted.redactionCount() + " items]" : "";

        String meta = "{\"userId\":\"" + safe(userId) + "\",\"model\":\"" + safe(model) + "\",\"promptSnippet\":\""
                + escapeJson(snippet) + "\"" + (redacted.piiDetected()
                ? ",\"piiRedacted\":true,\"piiCount\":" + redacted.redactionCount() : "") + "}";

        persist(safe(userId), "GATEWAY_REQUEST", "Tenant:" + safe(tenantId) + piiNote, tenantId, meta);
    }

    @Async
    public void traceRoutingDecision(String tenantId, String userId, String armKey, String strategy, String reason) {
        String meta = "{\"arm\":\"" + safe(armKey) + "\",\"strategy\":\"" + safe(strategy)
                + "\",\"reason\":\"" + escapeJson(truncate(reason, 200)) + "\"}";
        persist(safe(userId), "ROUTING_DECISION", "Arm:" + safe(armKey), tenantId, meta);
    }

    @Async
    public void traceFallback(String tenantId, String userId, String failedArm, String fallbackArm, String reason) {
        String meta = "{\"failedArm\":\"" + safe(failedArm) + "\",\"fallbackArm\":\"" + safe(fallbackArm)
                + "\",\"reason\":\"" + escapeJson(truncate(reason, 200)) + "\"}";
        persist(safe(userId), "PROVIDER_FALLBACK", "Arm:" + safe(failedArm) + "→" + safe(fallbackArm), tenantId, meta);
    }

    @Async
    public void traceBudgetEnforcement(String tenantId, String userId, double spendUsd, double capUsd) {
        String meta = "{\"spendUsd\":" + String.format("%.4f", spendUsd)
                + ",\"capUsd\":" + String.format("%.2f", capUsd) + "}";
        persist(safe(userId), "BUDGET_ENFORCEMENT", "Tenant:" + safe(tenantId), tenantId, meta);
    }

    @Async
    public void traceCircuitBreakerOpen(String tenantId, String armKey) {
        String meta = "{\"arm\":\"" + safe(armKey) + "\",\"state\":\"OPEN\"}";
        persist("system", "PROVIDER_CIRCUIT_OPEN", "Arm:" + safe(armKey), tenantId, meta);
    }

    @Async
    public void tracePiiRedaction(String tenantId, String userId, int redactionCount) {
        String meta = "{\"redactedItems\":" + redactionCount + ",\"userId\":\"" + safe(userId) + "\"}";
        persist(safe(userId), "PII_REDACTION_APPLIED", "Tenant:" + safe(tenantId), tenantId, meta);
    }

    @Async
    public void traceQualityEvaluation(String tenantId, String armKey, String evaluatorType, double compositeScore, double completeness, double relevance, double format) {
        String meta = "{\"arm\":\"" + safe(armKey) + "\",\"type\":\"" + safe(evaluatorType)
                + "\",\"composite\":" + String.format("%.3f", compositeScore)
                + ",\"completeness\":" + String.format("%.3f", completeness)
                + ",\"relevance\":" + String.format("%.3f", relevance)
                + ",\"format\":" + String.format("%.3f", format) + "}";
        persist("system", "QUALITY_EVALUATION", "Arm:" + safe(armKey), tenantId, meta);
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void persist(String actor, String action, String resource, String orgId, String meta) {
        try {
            AuditLog entry = new AuditLog(actor, action, resource, orgId, meta);
            AuditLog saved = auditLogRepository.save(entry);
            trafficBroadcaster.broadcast(saved);
        } catch (Exception e) {
            log.warn("RequestTracingService: Failed to persist audit log [action={}]: {}", action, e.getMessage());
        }
    }

    private static String safe(String s) {
        return s != null ? s : "unknown";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
