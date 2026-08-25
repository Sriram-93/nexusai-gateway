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
    private final RedisRateLimiter rateLimiter;
    private final JwtUtil jwtUtil;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public GatewaySecurityFilter(TenantRegistry tenantRegistry, RedisRateLimiter rateLimiter, JwtUtil jwtUtil, org.springframework.data.redis.core.StringRedisTemplate redisTemplate) {
        this.tenantRegistry = tenantRegistry;
        this.rateLimiter = rateLimiter;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Protect the native /api/chat endpoint, /api/agent/chat, and the OpenAI proxy endpoint
        if (!path.startsWith("/api/chat") && !path.startsWith("/api/agent/chat") && !path.startsWith("/v1/chat")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        Optional<TenantConfig> optTenant = Optional.empty();

        if (apiKey == null || apiKey.isBlank()) {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (token.split("\\.").length == 3) {
                    // It's a JWT from the frontend
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
                } else {
                    apiKey = token;
                }
            }
        }

        if (optTenant.isEmpty()) {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Missing authentication for request to {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            optTenant = tenantRegistry.getByApiKey(apiKey);
            if (optTenant.isEmpty()) {
                log.warn("Invalid API Key provided: {}", apiKey);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        }

        TenantConfig tenant = optTenant.get();

        // 0.1 Check Active Status
        if (!tenant.isActive()) {
            log.warn("Tenant {} is deactivated.", tenant.getTenantId());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // 0.2 Check IP Whitelist
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

        // 1. Check Pre-paid Budget via Redis
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

        // 2. Check Redis Rate Limiter
        return rateLimiter.isAllowed(tenant.getTenantId(), tenant.getMaxRequestsPerMinute())
            .flatMap(allowed -> {
                if (!allowed) {
                    log.warn("Tenant {} exceeded rate limit of {} req/min", 
                             tenant.getTenantId(), tenant.getMaxRequestsPerMinute());
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                }

                // 3. Authenticated and Allowed -> Inject Tenant into context and proceed
                log.debug("Authorized request for tenant: {}", tenant.getTenantId());
                exchange.getAttributes().put(TENANT_CONTEXT_KEY, tenant);
                return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put(TENANT_CONTEXT_KEY, tenant));
            });
    }
}
