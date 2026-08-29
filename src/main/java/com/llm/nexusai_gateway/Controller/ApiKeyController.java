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

    public ApiKeyController(ApiKeyService apiKeyService, ApiKeyRepository apiKeyRepository) {
        this.apiKeyService = apiKeyService;
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * GET /api/keys
     * Returns list of API keys in the gateway.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getKeys(
            @RequestParam(required = false) String projectId) {
        try {
            List<ApiKey> keys = (projectId != null && !projectId.isBlank())
                    ? apiKeyRepository.findByProjectId(projectId)
                    : apiKeyRepository.findAll();

            List<Map<String, Object>> response = keys.stream().map(this::toMap).toList();
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
