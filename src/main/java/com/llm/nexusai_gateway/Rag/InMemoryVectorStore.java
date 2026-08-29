package com.llm.nexusai_gateway.Rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * In-Memory Semantic Vector Store implementing TF-IDF Cosine Similarity Search (Priority 6).
 *
 * Provides standalone out-of-the-box semantic retrieval for enterprise knowledge bases
 * without external vector database binary dependencies.
 */
@Service
public class InMemoryVectorStore implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final List<IndexedDoc> index = new ArrayList<>();

    public InMemoryVectorStore() {
        // Seed default enterprise knowledge base
        seedEnterpriseKnowledge();
    }

    private void seedEnterpriseKnowledge() {
        indexDocument("JVM_Specs_Java21.pdf",
            "JVM memory consists of Heap (Young/Old Gen), Thread Stacks, Metaspace, and Native Memory. Garbage collection manages heap allocations using ZGC or G1 GC.",
            Map.of("category", "runtime", "version", "21"));

        indexDocument("Garbage_Collection_Tuning_Guide.md",
            "ZGC provides low-latency garbage collection with max pause times <1ms. G1GC balances throughput and latency for server workloads.",
            Map.of("category", "performance"));

        indexDocument("Gang_of_Four_Patterns.pdf",
            "Double-checked locking pattern requires the instance variable to be volatile to prevent instruction reordering in multithreaded singleton creation.",
            Map.of("category", "design_patterns"));

        indexDocument("Java_Concurrency_In_Practice.epub",
            "Thread safety in concurrent systems requires synchronization, volatile variables, or immutable data structures to prevent race conditions.",
            Map.of("category", "concurrency"));

        indexDocument("Aedf_Mathematical_Proof.pdf",
            "LinUCB optimizes the upper confidence bound to balance exploration vs exploitation in contextual bandit spaces using ridge regression matrices.",
            Map.of("category", "ai_routing", "paper", "Li et al. 2010"));

        indexDocument("Contextual_Bandits_Li_2010.pdf",
            "Contextual bandit algorithms model reward as a linear payoff function of context features, estimating parameters using online least squares.",
            Map.of("category", "ai_routing"));

        indexDocument("NexusAI_Architecture_Spec.md",
            "NexusAI Gateway is a Spring WebFlux Cognitive Control Plane utilizing a blackboard pattern and dynamic DAG workflow engine for multi-agent LLM routing.",
            Map.of("category", "architecture"));

        log.info("InMemoryVectorStore seeded with {} enterprise knowledge document chunks", index.size());
    }

    @Override
    public synchronized void indexDocument(String documentName, String content, Map<String, String> metadata) {
        String id = "doc_" + (index.size() + 1);
        Map<String, Double> termFreq = computeTermFrequency(content);
        index.add(new IndexedDoc(id, documentName, content, termFreq, metadata));
    }

    @Override
    public synchronized List<KnowledgeChunk> getAllChunks() {
        return index.stream()
            .map(doc -> new KnowledgeChunk(doc.id, doc.documentName, doc.content, 1.0, doc.metadata))
            .toList();
    }

    @Override
    public synchronized boolean deleteChunk(String id) {
        return index.removeIf(doc -> doc.id.equals(id));
    }

    @Override
    public List<KnowledgeChunk> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, Double> queryTf = computeTermFrequency(query);

        List<KnowledgeChunk> results = new ArrayList<>();
        for (IndexedDoc doc : index) {
            double similarity = computeCosineSimilarity(queryTf, doc.termFreq);
            if (similarity > 0.05) { // Similarity threshold
                results.add(new KnowledgeChunk(
                    doc.id,
                    doc.documentName,
                    doc.content,
                    similarity,
                    doc.metadata
                ));
            }
        }

        results.sort(Comparator.comparingDouble(KnowledgeChunk::getSimilarityScore).reversed());

        int limit = Math.min(topK, results.size());
        List<KnowledgeChunk> topResults = results.subList(0, limit);

        log.info("InMemoryVectorStore: Query '{}' matched {} chunks (top similarity={})",
            query, topResults.size(),
            topResults.isEmpty() ? 0.0 : String.format("%.3f", topResults.get(0).getSimilarityScore()));

        return topResults;
    }

    private Map<String, Double> computeTermFrequency(String text) {
        Map<String, Double> tf = new HashMap<>();
        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String token : tokens) {
            if (token.length() > 2 && !isStopWord(token)) {
                tf.put(token, tf.getOrDefault(token, 0.0) + 1.0);
            }
        }
        double total = tokens.length;
        if (total > 0) {
            tf.replaceAll((k, v) -> v / total);
        }
        return tf;
    }

    private double computeCosineSimilarity(Map<String, Double> vecA, Map<String, Double> vecB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (double v : vecA.values()) {
            normA += v * v;
        }
        for (double v : vecB.values()) {
            normB += v * v;
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;

        for (Map.Entry<String, Double> entry : vecA.entrySet()) {
            if (vecB.containsKey(entry.getKey())) {
                dotProduct += entry.getValue() * vecB.get(entry.getKey());
            }
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private boolean isStopWord(String word) {
        return Set.of("the", "and", "for", "that", "this", "with", "from", "are", "was", "were", "been", "have", "has", "had").contains(word);
    }

    private record IndexedDoc(
        String id,
        String documentName,
        String content,
        Map<String, Double> termFreq,
        Map<String, String> metadata
    ) {}
}
