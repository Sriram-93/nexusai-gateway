package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Health.ProviderHealthMonitor;
import com.llm.nexusai_gateway.Model.*;
import com.llm.nexusai_gateway.Provider.ModelRegistry;
import com.llm.nexusai_gateway.Provider.RegisteredModel;
import com.llm.nexusai_gateway.Service.ChatOrchestrationService;
import com.llm.nexusai_gateway.Service.StreamingOrchestrationService;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
public class UniversalGatewayController {

    private static final Logger log = LoggerFactory.getLogger(UniversalGatewayController.class);

    private final ChatOrchestrationService orchestrationService;
    private final StreamingOrchestrationService streamingService;
    private final ModelRegistry modelRegistry;
    private final ProviderHealthMonitor healthMonitor;
    private final EmbeddingModel embeddingModel;

    public UniversalGatewayController(ChatOrchestrationService orchestrationService,
                                      StreamingOrchestrationService streamingService,
                                      ModelRegistry modelRegistry,
                                      ProviderHealthMonitor healthMonitor) {
        this.orchestrationService = orchestrationService;
        this.streamingService = streamingService;
        this.modelRegistry = modelRegistry;
        this.healthMonitor = healthMonitor;
        this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    /**
     * Standard OpenAI-compatible Chat Completions Endpoint
     * POST /v1/chat/completions
     *
     * When request.stream = true → returns text/event-stream SSE (Phase 7).
     * When request.stream = false (default) → returns buffered JSON.
     */
    @PostMapping(value = "/chat/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Mono<ResponseEntity<?>> chatCompletions(
            @RequestBody OpenAiChatCompletionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-NexusAI-Provider", required = false) String providerOverride,
            org.springframework.web.server.ServerWebExchange exchange) {

        boolean streamMode = Boolean.TRUE.equals(request.getStream());
        String prompt = extractPrompt(request);
        String targetModel = (request.getModel() != null && !request.getModel().isBlank()) ? request.getModel() : "auto";

        com.llm.nexusai_gateway.Tenant.TenantConfig tenant = exchange.getAttribute(com.llm.nexusai_gateway.Security.GatewaySecurityFilter.TENANT_CONTEXT_KEY);
        String tenantId = tenant != null ? tenant.getTenantId() : "default";
        String userId = exchange.getAttribute("auth_user_id") != null ? exchange.getAttribute("auth_user_id") : "anonymous";

        // ─── SSE Streaming Branch ─────────────────────────────────────────────
        if (streamMode) {
            ChatRequest internalReq = new ChatRequest();
            internalReq.setMessage(prompt);
            internalReq.setModel(targetModel);
            internalReq.setTenantId(tenantId);
            internalReq.setUserId(userId);
            if (providerOverride != null && !providerOverride.isBlank()) {
                internalReq.setProvider(providerOverride);
            }

            String respId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
            long created = System.currentTimeMillis() / 1000;

            // Build an SSE Flux that wraps each token in OpenAI wire format
            Flux<String> sseFlux = streamingService.streamTokens(internalReq)
                .map(token -> {
                    // OpenAI SSE format: "data: {json}\n\n"
                    String json = "{\"id\":\"" + respId + "\",\"object\":\"chat.completion.chunk\"," +
                        "\"created\":" + created + ",\"model\":\"" + targetModel + "\"," +
                        "\"choices\":[{\"index\":0,\"delta\":{\"content\":" +
                        escapeJsonString(token) + "},\"finish_reason\":null}]}";
                    return "data: " + json + "\n\n";
                })
                .concatWith(Flux.just("data: [DONE]\n\n"));

            return Mono.just(ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseFlux));
        }

        // ─── Buffered JSON Branch (original) ─────────────────────────────────
        ChatRequest internalReq = new ChatRequest();
        internalReq.setMessage(prompt);
        internalReq.setModel(targetModel);
        internalReq.setTenantId(tenantId);
        internalReq.setUserId(userId);
        if (providerOverride != null && !providerOverride.isBlank()) {
            internalReq.setProvider(providerOverride);
        }

        return orchestrationService.process(internalReq).map(chatResponse -> {
            String respId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
            String content = chatResponse.getAnswer() != null ? chatResponse.getAnswer() : "";

            int promptTokens = Math.max(1, prompt.length() / 4);
            int completionTokens = Math.max(1, content.length() / 4);

            OpenAiChatCompletionRequest.ChatMessage msg =
                    new OpenAiChatCompletionRequest.ChatMessage("assistant", content);
            OpenAiChatCompletionResponse.Choice choice =
                    new OpenAiChatCompletionResponse.Choice(0, msg, "stop");
            OpenAiChatCompletionResponse.Usage usage =
                    new OpenAiChatCompletionResponse.Usage(promptTokens, completionTokens);

            String selectedProv = chatResponse.getProvider() != null ? chatResponse.getProvider() : "nexusai-router";
            OpenAiChatCompletionResponse.NexusAiRoutingMetadata meta =
                    new OpenAiChatCompletionResponse.NexusAiRoutingMetadata(
                            selectedProv,
                            targetModel,
                            chatResponse.getActiveEngine() != null ? chatResponse.getActiveEngine() : "ADAPTIVE",
                            chatResponse.getRoutingReason() != null ? chatResponse.getRoutingReason() : "Optimal candidate selection",
                            chatResponse.getLatencyMs(),
                            chatResponse.getArmScores() != null ? chatResponse.getArmScores() : Map.of()
                    );

            OpenAiChatCompletionResponse openAiResp =
                    new OpenAiChatCompletionResponse(respId, targetModel, List.of(choice), usage, meta);

            return (ResponseEntity<?>) ResponseEntity.ok(openAiResp);
        });
    }

    /**
     * Standard OpenAI-compatible Embeddings Endpoint
     * POST /v1/embeddings
     */
    @PostMapping("/embeddings")
    public ResponseEntity<OpenAiEmbeddingResponse> embeddings(@RequestBody OpenAiEmbeddingRequest request) {
        String textToEmbed = request.getInput() != null ? request.getInput() : "";
        Embedding embedding = embeddingModel.embed(textToEmbed).content();
        float[] vector = embedding.vector();

        OpenAiEmbeddingResponse.EmbeddingData data = new OpenAiEmbeddingResponse.EmbeddingData(0, vector);
        int tokenCount = Math.max(1, textToEmbed.length() / 4);
        OpenAiChatCompletionResponse.Usage usage = new OpenAiChatCompletionResponse.Usage(tokenCount, 0);

        OpenAiEmbeddingResponse response = new OpenAiEmbeddingResponse(
                List.of(data),
                request.getModel() != null ? request.getModel() : "all-minilm-l6-v2",
                usage
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Standard OpenAI-compatible Model List Endpoint
     * GET /v1/models
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels() {
        List<RegisteredModel> registeredModels = modelRegistry.getAllModels();
        List<Map<String, Object>> modelList = new ArrayList<>();

        for (RegisteredModel rm : registeredModels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rm.getArmKey()); // e.g. "groq:llama-3.3-70b-versatile"
            m.put("object", "model");
            m.put("created", rm.getDiscoveredAt() != null ? rm.getDiscoveredAt().getEpochSecond() : 1700000000L);
            m.put("owned_by", rm.getProviderSlug());
            m.put("display_name", rm.getDisplayName());
            m.put("input_price_per_1m", rm.getInputPricePer1M());
            m.put("output_price_per_1m", rm.getOutputPricePer1M());
            m.put("context_window", rm.getContextWindowTokens());
            m.put("enabled", rm.isEnabled());
            modelList.add(m);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("object", "list");
        response.put("data", modelList);
        return ResponseEntity.ok(response);
    }

    /**
     * Gateway Operational Health Endpoint
     * GET /v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("gateway", "NexusAI Intelligent AI Control Plane v1.0.0");
        health.put("timestamp", System.currentTimeMillis());
        health.put("providers", healthMonitor.getAllStatuses());
        return ResponseEntity.ok(health);
    }

    /**
     * Readiness Probe Endpoint
     * GET /v1/ready
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readinessCheck() {
        return ResponseEntity.ok(Map.of("status", "READY", "mode", "LIVE"));
    }

    private String extractPrompt(OpenAiChatCompletionRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        // Return content of the last user message
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            OpenAiChatCompletionRequest.ChatMessage msg = request.getMessages().get(i);
            if ("user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return request.getMessages().get(request.getMessages().size() - 1).getContent();
    }

    /**
     * Escapes a token string for safe JSON string embedding in SSE payloads.
     * Handles quotes, backslashes, and newlines.
     */
    private String escapeJsonString(String token) {
        if (token == null) return "\"\"";
        String escaped = token
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
