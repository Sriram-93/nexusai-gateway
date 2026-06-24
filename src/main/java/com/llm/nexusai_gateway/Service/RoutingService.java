package com.llm.nexusai_gateway.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.Priority;
import com.llm.nexusai_gateway.Model.RouteDecision;

@Service
public class RoutingService {

    @Value("${routing.model.high.provider:gemini}")
    private String highProvider;

    @Value("${routing.model.high.name:gemini-2.5-flash}")
    private String highModel;

    @Value("${routing.model.medium.provider:groq}")
    private String mediumProvider;

    @Value("${routing.model.medium.name:llama-3.3-70b-versatile}")
    private String mediumModel;

    @Value("${routing.model.low.provider:groq}")
    private String lowProvider;

    @Value("${routing.model.low.name:llama-3.1-8b-instant}")
    private String lowModel;

    public RouteDecision selectRoute(ChatRequest request) {
        // If provider is explicitly specified in request, respect it
        Priority priority = request.getPriority() != null ? request.getPriority() : determinePriority(request.getMessage());
        if (request.getProvider() != null && !request.getProvider().isBlank()) {
            return new RouteDecision(request.getProvider().toLowerCase(), request.getModel(), priority);
        }

        switch (priority) {
            case HIGH:
                return new RouteDecision(highProvider, highModel, priority);
            case MEDIUM:
                return new RouteDecision(mediumProvider, mediumModel, priority);
            case LOW:
            default:
                return new RouteDecision(lowProvider, lowModel, priority);
        }
    }

    private Priority determinePriority(String message) {
        if (message == null || message.isBlank()) {
            return Priority.LOW;
        }

        String cleanMsg = message.toLowerCase().trim();

        // 1. HIGH Complexity Keywords (Coding, Algorithms, Math, Long prompts)
        if (cleanMsg.contains("code") || cleanMsg.contains("write a") || cleanMsg.contains("program") || 
            cleanMsg.contains("function") || cleanMsg.contains("class") || cleanMsg.contains("algorithm") || 
            cleanMsg.contains("solve") || cleanMsg.contains("calculate") || cleanMsg.contains("explain the difference") || 
            cleanMsg.contains("design") || cleanMsg.contains("architect") || cleanMsg.length() > 100) {
            return Priority.HIGH;
        }

        // 2. MEDIUM Complexity Keywords (General Questions, Summarization, Explanations)
        if (cleanMsg.contains("summarize") || cleanMsg.contains("explain") || cleanMsg.contains("what is") || 
            cleanMsg.contains("how to") || cleanMsg.contains("why") || cleanMsg.contains("joke") || 
            cleanMsg.length() > 35) {
            return Priority.MEDIUM;
        }

        // 3. LOW Complexity (Greetings, Simple QA, Fast answers)
        return Priority.LOW;
    }
}
