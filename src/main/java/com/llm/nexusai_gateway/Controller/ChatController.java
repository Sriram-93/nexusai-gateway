package com.llm.nexusai_gateway.Controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.ChatResponse;
import com.llm.nexusai_gateway.Model.RouteDecision;
import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderResponse;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import com.llm.nexusai_gateway.Service.RoutingService;
import com.llm.nexusai_gateway.Service.LoggingService;

import com.llm.nexusai_gateway.Service.ResponseCacheService;
import com.llm.nexusai_gateway.Service.RateLimitingService;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ProviderRegistry providerRegistry;
    private final RoutingService routingService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final LoggingService loggingService;
    private final ResponseCacheService responseCacheService;
    private final RateLimitingService rateLimitingService;

    // Map to collapse duplicate in-flight requests for the same prompt and model (Cache Stampede Protection)
    private final ConcurrentHashMap<String, Mono<ProviderResponse>> inFlightRequests = new ConcurrentHashMap<>();

    public ChatController(ProviderRegistry providerRegistry, 
                          RoutingService routingService, 
                          CircuitBreakerRegistry circuitBreakerRegistry,
                          LoggingService loggingService,
                          ResponseCacheService responseCacheService,
                          RateLimitingService rateLimitingService) {
        this.providerRegistry = providerRegistry;
        this.routingService = routingService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.loggingService = loggingService;
        this.responseCacheService = responseCacheService;
        this.rateLimitingService = rateLimitingService;
    }

    @PostMapping("/chat")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        long start = System.currentTimeMillis();
        
        // Resolve target provider and model dynamically
        RouteDecision decision = routingService.selectRoute(request);

        LlmProvider provider = providerRegistry.getProvider(decision.provider());

        if (provider == null) {
            long latency = System.currentTimeMillis() - start;
            String errMsg = "Error: Provider '" + decision.provider() + "' is not supported.";
            logRequest(request, errMsg, decision.provider(), decision.model(), decision.priority().name(), latency, "FAILED", 0, 0);
            return Mono.just(new ChatResponse(errMsg, "system", latency));
        }

        // Get or create the circuit breaker for this provider
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(decision.provider().toLowerCase());

        // 1. Run Rate Limiter
        return rateLimitingService.checkRateLimits(request, decision.provider())
                // 2. Check Response Cache
                .then(responseCacheService.getCachedResponse(decision.model(), request.getMessage()))
                .flatMap(cachedAnswer -> {
                    // Cache HIT
                    long latency = System.currentTimeMillis() - start;
                    logRequest(request, cachedAnswer, decision.provider() + " (Cached)", decision.model(), decision.priority().name(), latency, "CACHE_HIT", 0, 0);
                    return Mono.just(new ChatResponse(
                            cachedAnswer,
                            decision.provider() + " (" + decision.model() + " - CACHE HIT)",
                            latency
                    ));
                })
                .switchIfEmpty(
                    // Cache MISS -> Call LLM provider with Request Collapsing
                    getCollapsedChat(provider, request.getMessage(), decision.model(), cb)
                            // Save to response cache on success
                            .flatMap(response -> responseCacheService.cacheResponse(decision.model(), request.getMessage(), response.content())
                                    .thenReturn(response))
                            .map(response -> {
                                long latency = System.currentTimeMillis() - start;
                                logRequest(request, response.content(), decision.provider(), decision.model(), decision.priority().name(), latency, "SUCCESS", response.inputTokens(), response.outputTokens());
                                return new ChatResponse(
                                        response.content(),
                                        decision.provider() + " (" + decision.model() + ")",
                                        latency
                                );
                            })
                            .onErrorResume(e -> {
                                boolean isCircuitOpen = e instanceof CallNotPermittedException;
                                String failureReason = isCircuitOpen ? "Circuit Breaker OPEN" : "Failed (" + e.getMessage() + ")";
                                
                                System.err.println("Primary provider " + decision.provider() + " failed due to: " + failureReason);

                                // Fallback logic
                                String fallbackProviderName = null;
                                String fallbackModelName = null;

                                if ("gemini".equalsIgnoreCase(decision.provider())) {
                                    fallbackProviderName = "groq";
                                    fallbackModelName = "llama-3.3-70b-versatile";
                                } else if ("groq".equalsIgnoreCase(decision.provider()) && "llama-3.3-70b-versatile".equalsIgnoreCase(decision.model())) {
                                    fallbackProviderName = "groq";
                                    fallbackModelName = "llama-3.1-8b-instant";
                                }

                                if (fallbackProviderName != null) {
                                    String finalFallbackProvider = fallbackProviderName;
                                    String finalFallbackModel = fallbackModelName;
                                    
                                    LlmProvider fallbackProvider = providerRegistry.getProvider(finalFallbackProvider);

                                    if (fallbackProvider != null) {
                                        CircuitBreaker fallbackCb = circuitBreakerRegistry.circuitBreaker(finalFallbackProvider.toLowerCase());
                                        
                                        return getCollapsedChat(fallbackProvider, request.getMessage(), finalFallbackModel, fallbackCb)
                                                // Save fallback to cache as well
                                                .flatMap(response -> responseCacheService.cacheResponse(finalFallbackModel, request.getMessage(), response.content())
                                                        .thenReturn(response))
                                                .map(response -> {
                                                    long latency = System.currentTimeMillis() - start;
                                                    logRequest(request, response.content(), finalFallbackProvider, finalFallbackModel, decision.priority().name(), latency, "FALLBACK_RECOVERY", response.inputTokens(), response.outputTokens());
                                                    return new ChatResponse(
                                                            response.content(),
                                                            decision.provider().toUpperCase() + " (" + failureReason + " &rarr; Fallback to " + finalFallbackProvider.toUpperCase() + " " + finalFallbackModel + ")",
                                                            latency
                                                    );
                                                })
                                                .onErrorResume(err -> {
                                                    long latency = System.currentTimeMillis() - start;
                                                    String errMsg = "Error: Both primary and fallback providers failed. " + err.getMessage();
                                                    logRequest(request, errMsg, decision.provider(), decision.model(), decision.priority().name(), latency, "FAILED", 0, 0);
                                                    return Mono.just(new ChatResponse(
                                                            errMsg,
                                                            decision.provider() + " (Failed & Fallback Failed)",
                                                            latency
                                                    ));
                                                });
                                    }
                                }

                                long latency = System.currentTimeMillis() - start;
                                String errMsg = "Error: " + e.getMessage();
                                logRequest(request, errMsg, decision.provider(), decision.model(), decision.priority().name(), latency, "FAILED", 0, 0);
                                return Mono.just(new ChatResponse(
                                        errMsg,
                                        decision.provider() + " (Failed)",
                                        latency
                                ));
                            })
                );
    }

    @PostMapping("/cache/clear")
    public Mono<Void> clearCache() {
        return responseCacheService.clearCache();
    }

    /**
     * Executes a LLM call with Request Collapsing to avoid Cache Stampede.
     */
    private Mono<ProviderResponse> getCollapsedChat(LlmProvider provider, String message, String model, CircuitBreaker cb) {
        String collapseKey = model + ":" + message;
        return inFlightRequests.computeIfAbsent(collapseKey, key -> 
            provider.chat(message, model)
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .doFinally(signal -> inFlightRequests.remove(collapseKey))
                .share()
        );
    }

    private void logRequest(ChatRequest request, String answer, String provider, String model, String priority, long latencyMs, String status, Integer inputTokens, Integer outputTokens) {
        RequestLog log = new RequestLog(
            request.getUserId() != null ? request.getUserId() : "anonymous",
            request.getMessage(),
            answer,
            provider,
            model,
            priority,
            latencyMs,
            null, // Token usage calculated in service
            null, // Cost calculated in service
            null, // Timestamp generated in service
            status
        );

        loggingService.saveLog(log, inputTokens, outputTokens)
            .doOnError(err -> System.err.println("Failed to write request log to DB: " + err.getMessage()))
            .subscribe(); // Non-blocking fire-and-forget save
    }

    @org.springframework.web.bind.annotation.GetMapping("/logs")
    public Mono<List<RequestLog>> getAllLogs() {
        return Mono.fromCallable(() -> loggingService.getAllLogs())
                   .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}