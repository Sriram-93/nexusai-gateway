package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Model.RequestLog;
import com.llm.nexusai_gateway.Repository.RequestLogRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ContextAgent implements Agent {

    private final RequestLogRepository requestLogRepository;
    private final com.llm.nexusai_gateway.Rag.VectorSearchService vectorSearchService;

    public ContextAgent(RequestLogRepository requestLogRepository,
                        com.llm.nexusai_gateway.Rag.VectorSearchService vectorSearchService) {
        this.requestLogRepository = requestLogRepository;
        this.vectorSearchService = vectorSearchService;
    }

    @Override
    public String getName() { return "ContextAgent"; }

    @Override
    public int getOrder() { return 1; }  // Same order as IntentAgent → executed in parallel

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("userId", "message");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("contextResult");
    }

    /**
     * Agent interface bridge: calls process() and writes ContextResult into AgentContext.
     * If the prompt is a greeting or trivially short, bypasses RAG retrieval (DAG bypass rule).
     */
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        var reqCtx = ctx.getRequestContext();
        long t = System.currentTimeMillis();
        String userId = ctx.getUserId();
        String message = ctx.getMessage();

        // 1. Fetch conversation history asynchronously
        Mono<String> historyMono = Mono.fromCallable(() -> {
            List<RequestLog> history = requestLogRepository.findTop5ByUserIdOrderByIdDesc(userId);
            if (history == null || history.isEmpty()) return "No recent history.";
            return "Previous prompts: " + history.stream()
                .map(RequestLog::getPrompt)
                .map(p -> p.length() > 30 ? p.substring(0, 30) + "..." : p)
                .collect(Collectors.joining(" -> "));
        }).subscribeOn(Schedulers.boundedElastic());

        // 2. Perform Priority 6 Semantic Vector Search (RAG) asynchronously
        Mono<ContextResult> ragMono = Mono.fromCallable(() -> {
            List<com.llm.nexusai_gateway.Rag.KnowledgeChunk> chunks = vectorSearchService.search(message, 3);
            List<String> relevantDocuments = chunks.stream()
                .map(com.llm.nexusai_gateway.Rag.KnowledgeChunk::getDocumentName)
                .distinct()
                .collect(Collectors.toList());

            String retrievedKnowledge = chunks.isEmpty() 
                ? "No high-confidence semantic knowledge chunks retrieved."
                : chunks.stream()
                    .map(c -> String.format("[%s (score: %.3f)] %s", c.getDocumentName(), c.getSimilarityScore(), c.getContent()))
                    .collect(Collectors.joining("\n"));
                    
            return new ContextResult(relevantDocuments, "", retrievedKnowledge);
        }).subscribeOn(Schedulers.boundedElastic());

        // Run both fetches concurrently and zip the results
        return Mono.zip(historyMono, ragMono).map(tuple -> {
            String historySummary = tuple.getT1();
            ContextResult result = tuple.getT2();
            result.setConversationSummary(historySummary);
            
            ctx.setContextResult(result);
            ctx.recordAgentTiming(getName(), System.currentTimeMillis() - t);
            return WorkflowSignal.CONTINUE;
        });
    }

    public static class ContextResult {
        private List<String> relevantDocuments;
        private String conversationSummary;
        private String retrievedKnowledge;

        public ContextResult() {}

        public ContextResult(List<String> relevantDocuments, String conversationSummary, String retrievedKnowledge) {
            this.relevantDocuments = relevantDocuments;
            this.conversationSummary = conversationSummary;
            this.retrievedKnowledge = retrievedKnowledge;
        }

        public List<String> getRelevantDocuments() { return relevantDocuments; }
        public void setRelevantDocuments(List<String> relevantDocuments) { this.relevantDocuments = relevantDocuments; }

        public String getConversationSummary() { return conversationSummary; }
        public void setConversationSummary(String conversationSummary) { this.conversationSummary = conversationSummary; }

        public String getRetrievedKnowledge() { return retrievedKnowledge; }
        public void setRetrievedKnowledge(String retrievedKnowledge) { this.retrievedKnowledge = retrievedKnowledge; }
    }
}
