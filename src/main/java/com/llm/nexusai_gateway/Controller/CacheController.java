package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Service.ResponseCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for Prompt Cache Governance & Optimization Studio.
 *
 * Exposes:
 *   GET  /api/cache/stats  — Cache hit/miss counters, ratio, and estimated savings
 *   POST /api/cache/flush  — Flush all Redis cached prompt entries and reset counters
 */
@RestController
@RequestMapping("/api/cache")
@CrossOrigin(origins = "*")
public class CacheController {

    private final ResponseCacheService responseCacheService;

    public CacheController(ResponseCacheService responseCacheService) {
        this.responseCacheService = responseCacheService;
    }

    /**
     * GET /api/cache/stats
     * Returns real-time cache performance metrics.
     */
    @GetMapping("/stats")
    public Mono<ResponseEntity<ResponseCacheService.CacheStats>> getCacheStats() {
        return responseCacheService.getStatsAsync()
                .map(ResponseEntity::ok);
    }

    /**
     * POST /api/cache/flush
     * Evicts all prompt response cache entries from Redis.
     */
    @PostMapping("/flush")
    public Mono<ResponseEntity<Map<String, Object>>> flushCache() {
        return responseCacheService.clearCache()
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("status", "SUCCESS");
                    res.put("message", "Prompt cache flushed successfully.");
                    return ResponseEntity.ok(res);
                }));
    }
}
