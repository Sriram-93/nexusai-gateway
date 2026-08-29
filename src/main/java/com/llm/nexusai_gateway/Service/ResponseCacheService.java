package com.llm.nexusai_gateway.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 100% Dynamic Vector Semantic Response Cache Service.
 *
 * Employs local ONNX embeddings (all-MiniLM-L6-v2) and Cosine Vector Similarity
 * to dynamically match prompt variations (e.g. "Who is the father of economics?" vs "father of economics")
 * purely through mathematical vector distance without ANY hardcoded word lists or fixed string values.
 */
@Service
public class ResponseCacheService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String CACHE_PREFIX = "cache:response:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.88; // Dynamic vector similarity cutoff

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    // Exact Key Cache (model + sanitized prompt hash -> response)
    private final ConcurrentHashMap<String, String> inMemoryCache = new ConcurrentHashMap<>();

    // Dynamic Vector Embedding Cache: exactKey -> VectorCacheEntry
    private record VectorCacheEntry(String model, String rawPrompt, Embedding embedding, String response) {}
    private final ConcurrentHashMap<String, VectorCacheEntry> vectorCache = new ConcurrentHashMap<>();

    private final AllMiniLmL6V2QuantizedEmbeddingModel embeddingModel;

    public ResponseCacheService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    public record CacheStats(
            long hits,
            long misses,
            double hitRatio,
            double costSavedUsd,
            long latencySavedMs,
            boolean redisActive
    ) {}

    public Mono<CacheStats> getStatsAsync() {
        long h = hitCount.get();
        long m = missCount.get();
        long total = h + m;
        double ratio = total > 0 ? ((double) h / total) * 100.0 : 0.0;
        double costSaved = h * 0.0025; // estimated $0.0025 per cached prompt
        long latencySaved = h * 450;    // estimated 450ms saved per hit

        return redisTemplate.getConnectionFactory().getReactiveConnection().ping()
                .map(res -> "PONG".equalsIgnoreCase(res))
                .onErrorReturn(false)
                .map(active -> new CacheStats(h, m, ratio, costSaved, latencySaved, active));
    }

    public CacheStats getStats() {
        return getStatsAsync().block(Duration.ofSeconds(2));
    }

    /**
     * Get a cached LLM response for the given model and prompt.
     * Uses exact hash key first, then falls back dynamically to ONNX Vector Cosine Similarity matching.
     */
    public Mono<String> getCachedResponse(String model, String prompt) {
        String exactKey = buildCacheKey(model, prompt);
        String globalKey = CACHE_PREFIX + "global:" + sha256("global:" + (prompt != null ? prompt.toLowerCase().trim() : ""));

        return redisTemplate.opsForValue().get(exactKey)
                .switchIfEmpty(Mono.justOrEmpty(inMemoryCache.get(exactKey)))
                .switchIfEmpty(redisTemplate.opsForValue().get(globalKey))
                .switchIfEmpty(Mono.justOrEmpty(inMemoryCache.get(globalKey)))
                .switchIfEmpty(Mono.defer(() -> findSemanticMatch(model, prompt)))
                .doOnNext(resp -> {
                    if (resp != null) hitCount.incrementAndGet();
                })
                .doOnSuccess(resp -> {
                    if (resp == null) missCount.incrementAndGet();
                })
                .onErrorResume(e -> {
                    org.slf4j.LoggerFactory.getLogger(ResponseCacheService.class)
                        .warn("Redis connection failed in getCachedResponse; using dynamic vector fallback: {}", e.getMessage());
                    String memResp = inMemoryCache.get(exactKey) != null ? inMemoryCache.get(exactKey) : inMemoryCache.get(globalKey);
                    if (memResp != null) {
                        hitCount.incrementAndGet();
                        return Mono.just(memResp);
                    }
                    return findSemanticMatch(model, prompt)
                            .doOnNext(r -> hitCount.incrementAndGet())
                            .switchIfEmpty(Mono.defer(() -> {
                                missCount.incrementAndGet();
                                return Mono.empty();
                            }));
                });
    }

    /**
     * Cache an LLM response for the given model and prompt.
     * Generates vector embeddings dynamically for semantic search lookup.
     */
    public Mono<Void> cacheResponse(String model, String prompt, String response) {
        String exactKey = buildCacheKey(model, prompt);
        String globalKey = CACHE_PREFIX + "global:" + sha256("global:" + (prompt != null ? prompt.toLowerCase().trim() : ""));

        inMemoryCache.put(exactKey, response);
        inMemoryCache.put(globalKey, response);

        // Store vector embedding asynchronously for dynamic neural vector similarity matching
        Mono.fromRunnable(() -> {
            try {
                Embedding emb = embeddingModel.embed(prompt).content();
                vectorCache.put(exactKey, new VectorCacheEntry(model.toLowerCase(), prompt, emb, response));
                vectorCache.put(globalKey, new VectorCacheEntry("global", prompt, emb, response));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ResponseCacheService.class)
                    .warn("Failed to generate vector embedding for cache entry: {}", e.getMessage());
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();

        return Mono.when(
            redisTemplate.opsForValue().set(exactKey, response, CACHE_TTL).onErrorResume(e -> Mono.empty()),
            redisTemplate.opsForValue().set(globalKey, response, CACHE_TTL).onErrorResume(e -> Mono.empty())
        ).then();
    }

    /**
     * Dynamically finds a cached response by calculating vector Cosine Similarity
     * between the incoming prompt embedding and all stored cache embeddings.
     */
    private Mono<String> findSemanticMatch(String model, String prompt) {
        if (prompt == null || prompt.isBlank() || vectorCache.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            Embedding queryEmbedding = embeddingModel.embed(prompt).content();
            String sanitizedModel = model.toLowerCase();

            VectorCacheEntry bestMatch = null;
            double highestSimilarity = 0.0;

            for (VectorCacheEntry entry : vectorCache.values()) {
                if (entry.model().equalsIgnoreCase(sanitizedModel)) {
                    double similarity = CosineSimilarity.between(queryEmbedding, entry.embedding());
                    if (similarity > highestSimilarity) {
                        highestSimilarity = similarity;
                        bestMatch = entry;
                    }
                }
            }

            if (highestSimilarity >= SEMANTIC_SIMILARITY_THRESHOLD && bestMatch != null) {
                org.slf4j.LoggerFactory.getLogger(ResponseCacheService.class)
                    .info("Dynamic Vector Semantic Cache Hit! Cosine Similarity={:.4f} between query='{}' and cached='{}'",
                        highestSimilarity, prompt, bestMatch.rawPrompt());
                return bestMatch.response();
            }

            return null;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
          .flatMap(Mono::justOrEmpty);
    }

    /**
     * Evict a specific cached entry.
     */
    public Mono<Boolean> evict(String model, String prompt) {
        String key = buildCacheKey(model, prompt);
        inMemoryCache.remove(key);
        vectorCache.remove(key);
        return redisTemplate.opsForValue().delete(key)
                .onErrorResume(e -> Mono.just(false));
    }

    /**
     * Flush all cached response keys.
     */
    public Mono<Void> clearCache() {
        hitCount.set(0);
        missCount.set(0);
        inMemoryCache.clear();
        vectorCache.clear();
        return redisTemplate.keys(CACHE_PREFIX + "*")
                .flatMap(redisTemplate::delete)
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private String buildCacheKey(String model, String prompt) {
        String sanitizedModel = model.toLowerCase().replaceAll("[^a-zA-Z0-9.-]", "_");
        String promptHash = sha256(model + ":" + prompt.toLowerCase().trim());
        return CACHE_PREFIX + sanitizedModel + ":" + promptHash;
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            return String.valueOf(base.hashCode());
        }
    }
}
