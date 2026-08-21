package com.llm.nexusai_gateway.Rag;

import java.util.Map;

/**
 * Represents a semantic knowledge chunk retrieved from the RAG Vector Search engine.
 */
public class KnowledgeChunk {
    private final String id;
    private final String documentName;
    private final String content;
    private final double similarityScore;
    private final Map<String, String> metadata;

    public KnowledgeChunk(String id, String documentName, String content, double similarityScore, Map<String, String> metadata) {
        this.id = id;
        this.documentName = documentName;
        this.content = content;
        this.similarityScore = similarityScore;
        this.metadata = metadata != null ? metadata : Map.of();
    }

    public String getId() { return id; }
    public String getDocumentName() { return documentName; }
    public String getContent() { return content; }
    public double getSimilarityScore() { return similarityScore; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return String.format("[%s (score: %.3f)] %s", documentName, similarityScore, content);
    }
}
