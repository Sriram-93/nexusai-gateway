package com.llm.nexusai_gateway.Security;

import com.llm.nexusai_gateway.Tenant.TenantConfig;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Enterprise Gateway Security Filter.
 * Enforces Auth (API Key), Rate Limiting (Redis), and Billing (Budget constraints).
 */
@Component
@Order(-1) // Run very early
public class GatewaySecurityFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityFilter.class);
    
    public static final String TENANT_CONTEXT_KEY = "auth_tenant";

    private final TenantRegistry tenantRegistry;
    private final ApiKeyService apiKeyService;
    private final RedisRateLimiter rateLimiter;
    private final JwtUtil jwtUtil;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public GatewaySecurityFilter(TenantRegistry tenantRegistry, ApiKeyService apiKeyService, RedisRateLimiter rateLimiter, JwtUtil jwtUtil, org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.tenantRegistry = tenantRegistry;
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 1. Allow public endpoints to pass through without authentication
        if (path.startsWith("/api/auth/") || path.startsWith("/api/tenant/signup") || 
            path.startsWith("/api/health") || path.startsWith("/actuator") ||
            path.startsWith("/api/providers/") || path.startsWith("/api/models/health")) {
            return chain.filter(exchange);
        }

        // 2. Ignore non-API static resource requests (e.g., frontend assets)
        if (!path.startsWith("/api/") && !path.startsWith("/v1/chat")) {
            return chain.filter(exchange);
        }

        // 3. Extract Authentication Credentials (JWT first, then API Key)
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = exchange.getRequest().getQueryParams().getFirst("token");
        }

        Optional<TenantConfig> optTenant = Optional.empty();

        if (token != null && token.split("\\.").length == 3) {
            // It's a JWT from the frontend session
            try {
                String tenantId = jwtUtil.extractClaim(token, claims -> claims.get("tenantId", String.class));
                String extractedUserId = jwtUtil.extractClaim(token, claims -> claims.get("userId", String.class));
                if (extractedUserId == null) {
                    extractedUserId = jwtUtil.extractClaim(token, claims -> claims.getSubject());
                }
                if (tenantId != null) {
                    optTenant = tenantRegistry.get(tenantId);
                }
                if (extractedUserId != null) {
                    exchange.getAttributes().put("auth_user_id", extractedUserId);
                }
            } catch (Exception e) {
                log.warn("Invalid JWT provided: {}", e.getMessage());
            }
        }

        // If JWT wasn't provided or didn't match an active tenant, check X-API-Key
        if (optTenant.isEmpty()) {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = exchange.getRequest().getQueryParams().getFirst("apiKey");
            }
            if ((apiKey == null || apiKey.isBlank()) && token != null && token.split("\\.").length != 3) {
                apiKey = token;
            }

            if (apiKey != null && !apiKey.isBlank()) {
                Optional<ApiKey> apiKeyOpt = apiKeyService.validateApiKey(apiKey);
                if (apiKeyOpt.isPresent()) {
                    ApiKey k = apiKeyOpt.get();
                    Project proj = k.getProject();
                    if (proj != null && proj.getWorkspace() != null && proj.getWorkspace().getOrganization() != null) {
                        String orgId = proj.getWorkspace().getOrganization().getId();
                        optTenant = tenantRegistry.getByOrganizationId(orgId);
                    }
                }
                if (optTenant.isEmpty()) {
                    optTenant = tenantRegistry.getByApiKey(apiKey);
                }
                if (optTenant.isEmpty()) {
                    log.warn("Invalid or revoked API key provided for request to {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }
        }

        // 4. Determine request type
        boolean isRoutingEndpoint = path.startsWith("/api/chat") || 
                                    path.startsWith("/api/agent/chat") || 
                                    path.startsWith("/v1/chat");

        // Routing endpoints strictly require authentication (Valid JWT or Valid API Key).
        if (isRoutingEndpoint && optTenant.isEmpty()) {
            log.warn("Authentication required: No API key or Bearer token provided for routing request to {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Fallback default tenant for non-routing console operations if needed
        if (optTenant.isEmpty()) {
            optTenant = Optional.of(tenantRegistry.getOrCreate("default-tenant"));
        }

        TenantConfig tenant = optTenant.get();

        if (!isRoutingEndpoint) {
            // Dashboard request: authenticated and allowed immediately
            log.debug("Authorized dashboard API request for tenant: {}", tenant.getTenantId());
            exchange.getAttributes().put(TENANT_CONTEXT_KEY, tenant);
            return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(TENANT_CONTEXT_KEY, tenant));
        }

        // 6. Routing Endpoint Validations (Active Status, IP Whitelist, Budget, Rate Limits)
        
        // 6.1 Check Active Status
        if (!tenant.isActive()) {
            log.warn("Tenant {} is deactivated.", tenant.getTenantId());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 6.2 Check IP Whitelist
        if (tenant.getAllowedIps() != null && !tenant.getAllowedIps().isEmpty()) {
            String clientIp = exchange.getRequest().getRemoteAddress() != null 
                              ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                              : null;
            if (clientIp == null || !tenant.getAllowedIps().contains(clientIp)) {
                log.warn("IP Whitelist rejection for tenant {}. Client IP: {}", tenant.getTenantId(), clientIp);
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        // 6.3 Check Pre-paid Budget via Redis
        if (tenant.getDailyBudgetUsd() > 0) {
            String dateStr = java.time.LocalDate.now().toString();
            String redisKey = "nexus:budget:team:" + tenant.getTenantId() + ":" + dateStr;
            String currentStr = redisTemplate.opsForValue().get(redisKey);
            if (currentStr != null) {
                try {
                    double currentSpend = Double.parseDouble(currentStr);
                    if (currentSpend >= tenant.getDailyBudgetUsd()) {
                        log.warn("Tenant {} has exhausted their daily budget in Redis.", tenant.getTenantId());
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS); // Quota exceeded
                        return exchange.getResponse().setComplete();
                    }
                } catch (Exception e) {
                    // Ignore parse error
                }
            }
        }

        // 6.4 Check Redis Rate Limiter
        return rateLimiter.isAllowed(tenant.getTenantId(), tenant.getMaxRequestsPerMinute())
            .flatMap(allowed -> {
                if (!allowed) {
                    log.warn("Tenant {} exceeded rate limit of {} req/min", 
                             tenant.getTenantId(), tenant.getMaxRequestsPerMinute());
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                }

                // 7. Fully Authorized and Allowed -> Inject Tenant into context and proceed
                log.debug("Authorized routing request for tenant: {}", tenant.getTenantId());
                exchange.getAttributes().put(TENANT_CONTEXT_KEY, tenant);
                return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put(TENANT_CONTEXT_KEY, tenant));
            });
    }
}
