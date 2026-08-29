package com.llm.nexusai_gateway.Telemetry;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Service.ChatOrchestrationService;
import com.llm.nexusai_gateway.Service.ResponseCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Automated E2E System Benchmark & Synthetic Load Validation Suite.
 *
 * Generates realistic synthetic multi-model gateway traffic to evaluate:
 * 1. LinUCB Bandit candidate scoring & model selection
 * 2. Real-Time SSE Traffic Stream delivery
 * 3. Prompt Caching hit/miss efficiency
 * 4. Circuit Breaker & Fallback cascade resilience
 */
@Service
public class SyntheticTrafficGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticTrafficGenerator.class);

    private final ChatOrchestrationService chatOrchestrationService;
    private final ResponseCacheService responseCacheService;

    private static final List<String> SAMPLE_PROMPTS = List.of(
            "Explain quantum key distribution in 2 sentences.",
            "Write a Python function to sort a list using quicksort.",
            "Summarize the key differences between REST and gRPC.",
            "What are the primary performance trade-offs of B-tree vs LSM-tree?",
            "Draft a concise cold email proposing an AI gateway partnership.",
            "Explain quantum key distribution in 2 sentences.", // Duplicate for cache hit test
            "Summarize the key differences between REST and gRPC." // Duplicate for cache hit test
    );

    public SyntheticTrafficGenerator(ChatOrchestrationService chatOrchestrationService,
                                     ResponseCacheService responseCacheService) {
        this.chatOrchestrationService = chatOrchestrationService;
        this.responseCacheService = responseCacheService;
    }

    public record BenchmarkResult(
            int totalRequests,
            int successfulRequests,
            double avgLatencyMs,
            long cacheHits,
            double cacheHitRatioPct,
            Map<String, Integer> modelDistribution
    ) {}

    /**
     * Executes a synthetic traffic benchmark sequence.
     *
     * @param requestCount Number of requests to run
     * @return Mono<BenchmarkResult>
     */
    public Mono<BenchmarkResult> runBenchmark(int requestCount) {
        log.info("Starting NexusAI Gateway Synthetic Benchmark for {} requests...", requestCount);

        List<Mono<Map<String, Object>>> tasks = new ArrayList<>();
        Map<String, Integer> modelDist = new HashMap<>();

        for (int i = 0; i < requestCount; i++) {
            String prompt = SAMPLE_PROMPTS.get(i % SAMPLE_PROMPTS.size());
            long startTime = System.currentTimeMillis();

            ChatRequest req = new ChatRequest(prompt, "benchmark-user-" + (i % 3));

            Mono<Map<String, Object>> task = chatOrchestrationService.process(req)
                    .map(resp -> {
                        long duration = System.currentTimeMillis() - startTime;
                        Map<String, Object> item = new HashMap<String, Object>();
                        item.put("success", true);
                        item.put("duration", duration);
                        item.put("model", resp.getProvider() != null ? resp.getProvider() : "unknown");
                        return item;
                    })
                    .onErrorResume(err -> {
                        long duration = System.currentTimeMillis() - startTime;
                        Map<String, Object> item = new HashMap<String, Object>();
                        item.put("success", false);
                        item.put("duration", duration);
                        item.put("model", "FAILED");
                        return Mono.just(item);
                    });

            tasks.add(task);
        }

        return Flux.fromIterable(tasks)
                .flatMap(mono -> mono, 3) // Concurrency limit of 3
                .collectList()
                .map(results -> {
                    int successes = 0;
                    long totalDuration = 0;

                    for (Map<String, Object> res : results) {
                        boolean ok = (boolean) res.get("success");
                        long dur = (long) res.get("duration");
                        String model = (String) res.get("model");

                        if (ok) successes++;
                        totalDuration += dur;
                        modelDist.put(model, modelDist.getOrDefault(model, 0) + 1);
                    }

                    double avgLat = results.isEmpty() ? 0 : (double) totalDuration / results.size();
                    ResponseCacheService.CacheStats cacheStats = responseCacheService.getStats();

                    log.info("Benchmark complete: {}/{} successful. Avg latency: {} ms", successes, requestCount, avgLat);

                    return new BenchmarkResult(
                            requestCount,
                            successes,
                            avgLat,
                            cacheStats.hits(),
                            cacheStats.hitRatio(),
                            modelDist
                    );
                });
    }
}
