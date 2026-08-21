package com.llm.nexusai_gateway.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.llm.nexusai_gateway.Model.ChatRequest;
import com.llm.nexusai_gateway.Model.Priority;
import com.llm.nexusai_gateway.Model.RouteDecision;

@Service
public class RoutingService {

    @Value("${routing.model.high.provider:}")
    private String highProvider;

    @Value("${routing.model.high.name:}")
    private String highModel;

    @Value("${routing.model.medium.provider:}")
    private String mediumProvider;

    @Value("${routing.model.medium.name:}")
    private String mediumModel;

    @Value("${routing.model.low.provider:}")
    private String lowProvider;

    @Value("${routing.model.low.name:}")
    private String lowModel;

    private final com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor;

    public RoutingService(com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor) {
        this.contextExtractor = contextExtractor;
    }

    public RouteDecision selectRoute(ChatRequest request) {
        // If provider is explicitly specified in request, respect it
        Priority priority = request.getPriority() != null ? request.getPriority() : determinePriority(request);
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

    private Priority determinePriority(ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Priority.LOW;
        }

        try {
            com.llm.nexusai_gateway.Context.RequestContext ctx = contextExtractor.extract(request).block();
            if (ctx == null) return Priority.LOW;
            
            // Map the semantic category to priority
            return switch (ctx.taskCategory()) {
                case CODE, REASONING -> Priority.HIGH;
                case CREATIVE, FACTUAL -> Priority.MEDIUM;
                case CONVERSATION -> Priority.LOW;
            };
        } catch (Exception e) {
            return Priority.LOW;
        }
    }
}
