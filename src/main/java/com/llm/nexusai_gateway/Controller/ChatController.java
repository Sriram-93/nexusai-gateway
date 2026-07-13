package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.ChatResponse;
import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Service.ChatOrchestrationService;
import com.llm.nexusai_gateway.Service.LoggingService;
import com.llm.nexusai_gateway.Service.ResponseCacheService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * REST Controller — thin HTTP layer that delegates to ChatOrchestrationService.
 *
 * The controller's only responsibility is HTTP request/response mapping.
 * All business logic lives in the orchestration service (AEDF pipeline).
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatOrchestrationService orchestrationService;
    private final LoggingService loggingService;
    private final ResponseCacheService responseCacheService;
    private final com.llm.nexusai_gateway.Reputation.ReputationService reputationService;

    public ChatController(ChatOrchestrationService orchestrationService,
                          LoggingService loggingService,
                          ResponseCacheService responseCacheService,
                          com.llm.nexusai_gateway.Reputation.ReputationService reputationService) {
        this.orchestrationService = orchestrationService;
        this.loggingService = loggingService;
        this.responseCacheService = responseCacheService;
        this.reputationService = reputationService;
    }

    @PostMapping("/chat")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        return orchestrationService.process(request);
    }

    @GetMapping("/reputations")
    public Mono<java.util.Map<String, com.llm.nexusai_gateway.Reputation.ProviderReputation>> getAllReputationalData() {
        return Mono.fromCallable(reputationService::getAll)
                   .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @PostMapping("/cache/clear")
    public Mono<Void> clearCache() {
        return responseCacheService.clearCache();
    }

    @GetMapping("/logs")
    public Mono<List<RequestLog>> getAllLogs() {
        return Mono.fromCallable(loggingService::getAllLogs)
                   .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}