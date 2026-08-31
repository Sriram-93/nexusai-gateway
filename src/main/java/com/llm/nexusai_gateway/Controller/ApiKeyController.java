package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Security.ApiKey;
import com.llm.nexusai_gateway.Security.ApiKeyRepository;
import com.llm.nexusai_gateway.Security.ApiKeyService;
import com.llm.nexusai_gateway.Security.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API for Gateway API Key Management.
 *
 * Exposes:
 *   GET    /api/keys        — List active gateway API keys
 *   POST   /api/keys        — Generate a new API key (returns raw key once)
 *   DELETE /api/keys/{id}   — Revoke an existing API key
 */
@RestController
@RequestMapping("/api/keys")
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRepository apiKeyRepository;
    private final com.llm.nexusai_gateway.Security.UserRepository userRepository;

    public ApiKeyController(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository, com.llm.nexusai_gateway.Security.UserRepository userRepository) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    public ResponseEntity<List<Map<String, Object>>> getKeys(String projectId) {
        return getKeys(projectId, null, null, null);
    }

    public ResponseEntity<List<Map<String, Object>>> getKeys() {
        return getKeys(null, null, null, null);
    }

    /**
     * GET /api/keys
     * Returns list of API keys in the gateway scoped to the current tenant organization.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getKeys(
            @RequestParam(required = false) String projectId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            org.springframework.web.server.ServerWebExchange exchange) {
        try {
            List<ApiKey> allKeys = (projectId != null && !projectId.isBlank())
                    ? apiKeyRepository.findByProjectId(projectId)
                    : apiKeyRepository.findAll();

            String userEmail = exchange != null && exchange.getAttribute("auth_user_id") != null
                    ? (String) exchange.getAttribute("auth_user_id")
                    : null;

            String userOrgId = null;
            if (userEmail != null && !userEmail.isBlank()) {
                userOrgId = userRepository.findByEmail(userEmail)
                        .map(u -> u.getOrganization() != null ? u.getOrganization().getId() : null)
                        .orElse(null);
            }

            final String targetOrgId = userOrgId;
            List<ApiKey> tenantKeys = allKeys.stream().filter(k -> {
                if (targetOrgId == null) return false;
                try {
                    return k.getProject() != null &&
                           k.getProject().getWorkspace() != null &&
                           k.getProject().getWorkspace().getOrganization() != null &&
                           targetOrgId.equals(k.getProject().getWorkspace().getOrganization().getId());
                } catch (Exception e) {
                    return false;
                }
            }).toList();

            List<Map<String, Object>> response = tenantKeys.stream().map(this::toMap).toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ApiKeyController.class).error("Error fetching API keys", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * POST /api/keys
     * Body: { name, projectId, environment, actorEmail }
     * Generates a new API key and returns the raw secret ONCE.
     */
    public ResponseEntity<Map<String, Object>> createKey(Map<String, Object> body) {
        return createKey(body, null);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createKey(
            @RequestBody(required = false) Map<String, Object> body,
            org.springframework.web.server.ServerWebExchange exchange) {
        if (body == null) body = Collections.emptyMap();
        try {
            String name       = body.get("name") != null ? body.get("name").toString() : "Gateway API Key";
            String projectId  = body.get("projectId") != null ? body.get("projectId").toString() : null;
            String envStr     = body.get("environment") != null ? body.get("environment").toString() : "DEVELOPMENT";
            
            String actorEmail = body.get("actorEmail") != null ? body.get("actorEmail").toString() : null;
            if (actorEmail == null && exchange != null && exchange.getAttribute("auth_user_id") != null) {
                actorEmail = exchange.getAttribute("auth_user_id");
            }
            if (actorEmail == null) {
                actorEmail = "admin@nexusai.io";
            }

            com.llm.nexusai_gateway.Tenant.TenantConfig tenant = exchange != null ? exchange.getAttribute(com.llm.nexusai_gateway.Security.GatewaySecurityFilter.TENANT_CONTEXT_KEY) : null;
            String orgId = tenant != null ? tenant.getOrganizationId() : null;

            Environment env;
            try {
                env = Environment.valueOf(envStr.toUpperCase());
            } catch (Exception e) {
                env = Environment.DEVELOPMENT;
            }

            ApiKeyService.GeneratedKeyResult result = apiKeyService.generateApiKey(name, projectId, env, actorEmail, orgId);

            Map<String, Object> response = toMap(result.apiKey());
            response.put("rawSecretKey", result.rawSecretKey()); // Returned ONLY once upon creation!
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ApiKeyController.class).error("Error generating API Key", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Failed to generate key"));
        }
    }

    /**
     * DELETE /api/keys/{id}
     * Revokes an existing API key.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> revokeKey(
            @PathVariable String id,
            @RequestParam(defaultValue = "admin@nexusai.io") String actorEmail) {
        apiKeyService.revokeApiKey(id, actorEmail);
        Map<String, Object> res = new HashMap<>();
        res.put("id", id);
        res.put("status", "REVOKED");
        res.put("message", "API key revoked successfully.");
        return ResponseEntity.ok(res);
    }

    private Map<String, Object> toMap(ApiKey key) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", key.getId());
        map.put("name", key.getName());
        map.put("keyPrefix", key.getKeyPrefix());
        map.put("environment", key.getEnvironment() != null ? key.getEnvironment().name() : "DEVELOPMENT");
        map.put("status", key.getStatus());
        map.put("createdAt", key.getCreatedAt() != null ? key.getCreatedAt().toString() : null);
        map.put("lastUsedAt", key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : null);
        map.put("projectId", key.getProject() != null ? key.getProject().getId() : null);
        return map;
    }
}
