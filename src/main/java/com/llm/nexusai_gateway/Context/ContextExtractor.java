package com.llm.nexusai_gateway.Context;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts a structured {@link RequestContext} from an incoming {@link ChatRequest}.
 *
 * Uses a lightning-fast local ONNX embedding model (all-MiniLM-L6-v2) to embed the 
 * user's prompt and perform cosine similarity against predefined anchor intents.
 * This provides semantic intent classification in ~10ms with zero network calls.
 */
@Service
public class ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContextExtractor.class);
    private static final int LONG_CONTEXT_THRESHOLD = 4096;

    private final ReputationService reputationService;
    private final AllMiniLmL6V2EmbeddingModel embeddingModel;
    
    // Pre-computed embeddings for our task categories (Semantic Anchors)
    private final Map<TaskCategory, Embedding> categoryAnchors = new HashMap<>();

    public ContextExtractor(ReputationService reputationService) {
        this.reputationService = reputationService;
        
        // Initialize the in-memory ONNX embedding model
        log.info("Loading local ONNX embedding model (all-MiniLM-L6-v2) for intent detection...");
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        
        // Pre-compute embeddings for our semantic routes (anchors)
        // We use descriptive sentences so the vector captures the true semantic meaning of the category.
        categoryAnchors.put(TaskCategory.CODE, embeddingModel.embed(
            "Write a python script, fix this bug, write sql query, debug java code, how to implement algorithm, software architecture."
        ).content());
        
        categoryAnchors.put(TaskCategory.REASONING, embeddingModel.embed(
            "Explain why this happens, analyze the difference between, mathematical proof, evaluate trade-offs, solve equation, logical deduction."
        ).content());
        
        categoryAnchors.put(TaskCategory.CREATIVE, embeddingModel.embed(
            "Write a story, compose a poem, brainstorm ideas, draft a blog post, creative writing, imagine a scenario."
        ).content());
        
        categoryAnchors.put(TaskCategory.FACTUAL, embeddingModel.embed(
            "What is the capital of France, who is the president, define this term, history of, list the names, factual knowledge."
        ).content());
        
        log.info("Semantic intent anchors successfully loaded.");
    }

    public record SemanticResult(TaskCategory category, float[] embedding) {}

    /**
     * Extract a full RequestContext from an incoming chat request asynchronously.
     */
    public Mono<RequestContext> extract(ChatRequest request) {
        String message = request.getMessage() != null ? request.getMessage() : "";

        double complexity = estimateComplexity(message);
        int tokenEstimate = estimateTokenCount(message);
        boolean longContext = tokenEstimate > LONG_CONTEXT_THRESHOLD;
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default";
        Map<String, Double> healthScores = reputationService.getAllHealthScores();

        return classifyTaskSemantically(message)
            .map(semanticResult -> {
                RequestContext context = new RequestContext(
                    semanticResult.category(), complexity, tokenEstimate, longContext,
                    userId, tenantId, healthScores, semanticResult.embedding()
                );

                log.debug("Extracted context dynamically: task={}, complexity={:.2f}, tokens={}, longCtx={}",
                          semanticResult.category(), complexity, tokenEstimate, longContext);
                return context;
            });
    }

    /**
     * Classify the task type dynamically using local semantic embeddings and Cosine Similarity.
     * Takes ~5-15ms locally. Does not require external LLM calls.
     */
    Mono<SemanticResult> classifyTaskSemantically(String message) {
        if (message == null || message.isBlank()) {
            return Mono.just(new SemanticResult(TaskCategory.CONVERSATION, new float[384]));
        }

        // We run the embedding inference on the boundedElastic scheduler because DJL/ONNX 
        // inference is CPU bound and we shouldn't block the reactive Netty thread.
        return Mono.fromCallable(() -> {
            Embedding promptEmbedding = embeddingModel.embed(message).content();
            
            TaskCategory bestCategory = TaskCategory.CONVERSATION;
            double highestSimilarity = 0.0;
            
            for (Map.Entry<TaskCategory, Embedding> entry : categoryAnchors.entrySet()) {
                double similarity = CosineSimilarity.between(promptEmbedding, entry.getValue());
                log.trace("Similarity to {}: {:.4f}", entry.getKey(), similarity);
                
                if (similarity > highestSimilarity) {
                    highestSimilarity = similarity;
                    bestCategory = entry.getKey();
                }
            }
            
            // If the similarity is too low, it's just a general conversation
            if (highestSimilarity < 0.3) {
                bestCategory = TaskCategory.CONVERSATION;
            }
            
            return new SemanticResult(bestCategory, promptEmbedding.vector());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Estimate prompt complexity on a [0.0, 1.0] scale using the Flesch-Kincaid Grade Level
     * and Automated Readability Index (ARI) instead of naive word counts.
     */
    double estimateComplexity(String message) {
        if (message == null || message.isBlank()) {
            return 0.0;
        }

        int characters = message.replaceAll("\\s+", "").length();
        String[] words = message.split("\\s+");
        int wordCount = words.length;
        if (wordCount == 0) return 0.0;

        int sentences = Math.max(1, message.split("[.!?]+").length);
        
        // Count syllables (rough approximation)
        int syllables = 0;
        for (String word : words) {
            syllables += countSyllables(word);
        }

        // Flesch-Kincaid Grade Level
        double fkGrade = 0.39 * ((double) wordCount / sentences) + 11.8 * ((double) syllables / wordCount) - 15.59;
        
        // Automated Readability Index (ARI)
        double ari = 4.71 * ((double) characters / wordCount) + 0.5 * ((double) wordCount / sentences) - 21.43;

        // Average the metrics and normalize to [0, 1] range (assuming max grade level ~20 for extreme complexity)
        double combinedGrade = (fkGrade + ari) / 2.0;
        double normalized = combinedGrade / 20.0;

        return Math.min(1.0, Math.max(0.0, normalized));
    }

    private int countSyllables(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.isEmpty()) return 0;
        if (word.length() <= 3) return 1;
        
        word = word.replaceAll("(?:[^laeiouy]es|ed|[^laeiouy]e)$", "");
        word = word.replaceAll("^y", "");
        
        int syllables = word.split("[aeiouy]+").length - 1;
        return Math.max(1, syllables);
    }

    /**
     * Estimate token count using the ~4 characters per token heuristic.
     */
    int estimateTokenCount(String message) {
        if (message == null || message.isEmpty()) {
            return 0;
        }
        return Math.max(1, message.length() / 4);
    }
}
