package com.llm.nexusai_gateway.Context;

import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extracts a structured {@link RequestContext} from an incoming {@link ChatRequest}.
 *
 * Replaces the old keyword-based determinePriority() in RoutingService.
 * Uses vocabulary analysis and structural heuristics instead of simple keyword matching.
 */
@Service
public class ContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContextExtractor.class);

    private static final int LONG_CONTEXT_THRESHOLD = 4096;

    // Keyword sets for task classification
    private static final Set<String> CODE_KEYWORDS = Set.of(
        "code", "program", "function", "class", "method", "implement", "debug",
        "compile", "syntax", "algorithm", "api", "database", "sql", "html",
        "python", "java", "javascript", "typescript", "refactor", "deploy"
    );

    private static final Set<String> REASONING_KEYWORDS = Set.of(
        "explain", "compare", "analyze", "why", "how does", "difference between",
        "evaluate", "calculate", "solve", "prove", "derive", "optimize",
        "trade-off", "pros and cons", "reasoning", "logic"
    );

    private static final Set<String> CREATIVE_KEYWORDS = Set.of(
        "write", "story", "poem", "creative", "brainstorm", "imagine",
        "generate", "compose", "draft", "essay", "blog", "article"
    );

    private static final Set<String> FACTUAL_KEYWORDS = Set.of(
        "what is", "define", "who is", "when did", "where is",
        "list", "name", "facts about", "history of", "capital of"
    );

    private final ReputationService reputationService;

    public ContextExtractor(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    /**
     * Extract a full RequestContext from an incoming chat request.
     */
    public RequestContext extract(ChatRequest request) {
        String message = request.getMessage() != null ? request.getMessage() : "";

        TaskCategory taskCategory = classifyTask(message);
        double complexity = estimateComplexity(message);
        int tokenEstimate = estimateTokenCount(message);
        boolean longContext = tokenEstimate > LONG_CONTEXT_THRESHOLD;
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default";
        Map<String, Double> healthScores = reputationService.getAllHealthScores();

        RequestContext context = new RequestContext(
            taskCategory, complexity, tokenEstimate, longContext,
            userId, tenantId, healthScores
        );

        log.debug("Extracted context: task={}, complexity={:.2f}, tokens={}, longCtx={}",
                  taskCategory, complexity, tokenEstimate, longContext);

        return context;
    }

    /**
     * Classify the task type based on keyword analysis.
     * Uses weighted keyword matching across multiple categories.
     */
    TaskCategory classifyTask(String message) {
        String lower = message.toLowerCase().trim();

        int codeScore = countKeywordHits(lower, CODE_KEYWORDS);
        int reasoningScore = countKeywordHits(lower, REASONING_KEYWORDS);
        int creativeScore = countKeywordHits(lower, CREATIVE_KEYWORDS);
        int factualScore = countKeywordHits(lower, FACTUAL_KEYWORDS);

        int maxScore = Math.max(Math.max(codeScore, reasoningScore),
                               Math.max(creativeScore, factualScore));

        if (maxScore == 0) {
            return TaskCategory.CONVERSATION;
        }

        if (codeScore == maxScore) return TaskCategory.CODE;
        if (reasoningScore == maxScore) return TaskCategory.REASONING;
        if (creativeScore == maxScore) return TaskCategory.CREATIVE;
        if (factualScore == maxScore) return TaskCategory.FACTUAL;

        return TaskCategory.CONVERSATION;
    }

    /**
     * Estimate prompt complexity on a [0.0, 1.0] scale.
     * Considers: word count, vocabulary richness, average word length, sentence count.
     */
    double estimateComplexity(String message) {
        if (message == null || message.isBlank()) {
            return 0.0;
        }

        String[] words = message.split("\\s+");
        int wordCount = words.length;

        // Factor 1: Length contribution (normalized, caps at 500 words)
        double lengthFactor = Math.min(1.0, wordCount / 500.0);

        // Factor 2: Vocabulary richness (unique words / total words)
        Set<String> uniqueWords = new HashSet<>(Arrays.asList(words));
        double vocabRichness = wordCount > 0 ? (double) uniqueWords.size() / wordCount : 0.0;

        // Factor 3: Average word length (longer words → more technical)
        double avgWordLength = Arrays.stream(words)
            .mapToInt(String::length)
            .average()
            .orElse(0.0);
        double wordLengthFactor = Math.min(1.0, avgWordLength / 10.0);

        // Factor 4: Sentence count (more sentences → more complex instructions)
        long sentenceCount = message.chars().filter(c -> c == '.' || c == '?' || c == '!').count();
        double sentenceFactor = Math.min(1.0, sentenceCount / 10.0);

        // Weighted combination
        double complexity = (0.30 * lengthFactor)
                          + (0.25 * vocabRichness)
                          + (0.25 * wordLengthFactor)
                          + (0.20 * sentenceFactor);

        return Math.min(1.0, Math.max(0.0, complexity));
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

    private int countKeywordHits(String text, Set<String> keywords) {
        int hits = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                hits++;
            }
        }
        return hits;
    }
}
