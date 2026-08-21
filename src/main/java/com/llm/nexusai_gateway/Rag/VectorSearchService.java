package com.llm.nexusai_gateway.Rag;

import java.util.List;
import java.util.Map;

/**
 * Common contract for Semantic Vector Search and Retrieval Augmented Generation (RAG).
 */
public interface VectorSearchService {

    /**
     * Search the vector index for chunks semantically related to the query string.
     *
     * @param query natural language prompt/query
     * @param topK maximum number of chunks to return
     * @return List of matching KnowledgeChunk sorted by similarity score descending
     */
    List<KnowledgeChunk> search(String query, int topK);

    /**
     * Add a document chunk to the vector store index.
     */
    void indexDocument(String documentName, String content, Map<String, String> metadata);
}
