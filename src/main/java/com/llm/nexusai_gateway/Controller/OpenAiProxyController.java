package com.llm.nexusai_gateway.Controller;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.Priority;
import com.llm.nexusai_gateway.Security.GatewaySecurityFilter;
import com.llm.nexusai_gateway.Service.ChatOrchestrationService;
import com.llm.nexusai_gateway.Tenant.TenantConfig;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.*;

@RestController
@RequestMapping("/v1/chat")
public class OpenAiProxyController {

    private final ChatOrchestrationService orchestrationService;

    public OpenAiProxyController(ChatOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/completions")
    public Mono<Map<String, Object>> chatCompletions(
            @RequestBody Map<String, Object> openAiRequest,
            @RequestAttribute(GatewaySecurityFilter.TENANT_CONTEXT_KEY) TenantConfig tenant) {
        
        // 1. Parse OpenAI request
        List<Map<String, String>> messages = (List<Map<String, String>>) openAiRequest.get("messages");
        
        // Combine messages into a single prompt for now, or just take the last user message
        String prompt = messages.get(messages.size() - 1).get("content");
        
        // 2. Build internal ChatRequest
        ChatRequest request = new ChatRequest();
        request.setMessage(prompt);
        request.setTenantId(tenant.getTenantId());
        request.setUserId("openai-proxy-client");
        request.setPriority(Priority.HIGH); // default for API
        
        // 3. Execute request via Gateway
        return orchestrationService.process(request).map(response -> {
            // 4. Translate back to OpenAI format
            Map<String, Object> openaiResponse = new HashMap<>();
            openaiResponse.put("id", "chatcmpl-" + UUID.randomUUID().toString());
            openaiResponse.put("object", "chat.completion");
            openaiResponse.put("created", System.currentTimeMillis() / 1000);
            
            // OpenAI clients expect a model string. Let's return what LinUCB chose.
            openaiResponse.put("model", response.getProvider()); 
            
            Map<String, Object> message = new HashMap<>();
            message.put("role", "assistant");
            message.put("content", response.getAnswer());
            
            Map<String, Object> choice = new HashMap<>();
            choice.put("index", 0);
            choice.put("message", message);
            choice.put("finish_reason", "stop");
            
            openaiResponse.put("choices", List.of(choice));
            
            // Mock usage for now. In a real system, you'd pull this from the response if tracked
            Map<String, Object> usage = new HashMap<>();
            usage.put("prompt_tokens", prompt.length() / 4);
            usage.put("completion_tokens", response.getAnswer().length() / 4);
            usage.put("total_tokens", (prompt.length() + response.getAnswer().length()) / 4);
            openaiResponse.put("usage", usage);
            
            return openaiResponse;
        });
    }
}
