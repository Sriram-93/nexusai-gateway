package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Rag.KnowledgeChunk;
import com.llm.nexusai_gateway.Rag.VectorSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Enterprise RAG Vector Knowledge Base Management API.
 */
@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagController {

    private final VectorSearchService vectorSearchService;

    public RagController(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * GET /api/rag/chunks
     * List all knowledge base chunks in the vector index.
     */
    @GetMapping("/chunks")
    public ResponseEntity<List<KnowledgeChunk>> getAllChunks() {
        return ResponseEntity.ok(vectorSearchService.getAllChunks());
    }

    /**
     * POST /api/rag/chunks
     * Ingest a new document chunk into the vector store index.
     */
    @PostMapping("/chunks")
    public ResponseEntity<Map<String, Object>> ingestChunk(@RequestBody Map<String, Object> body) {
        String documentName = (String) body.getOrDefault("documentName", "Ingested_Doc.md");
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) body.getOrDefault("metadata", Map.of());

        vectorSearchService.indexDocument(documentName, content, metadata);

        return ResponseEntity.ok(Map.of(
            "message", "Document chunk successfully indexed into vector store.",
            "documentName", documentName,
            "totalChunks", vectorSearchService.getAllChunks().size()
        ));
    }

    /**
     * POST /api/rag/search
     * Query vector index for semantic matches.
     */
    @PostMapping("/search")
    public ResponseEntity<List<KnowledgeChunk>> searchVectorIndex(
            @RequestParam(defaultValue = "5") int topK,
            @RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(vectorSearchService.search(query, topK));
    }

    /**
     * DELETE /api/rag/chunks/{id}
     * Delete a knowledge chunk by ID.
     */
    @DeleteMapping("/chunks/{id}")
    public ResponseEntity<Map<String, Object>> deleteChunk(@PathVariable String id) {
        boolean deleted = vectorSearchService.deleteChunk(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("id", id, "deleted", true));
        }
        return ResponseEntity.notFound().build();
    }
}
