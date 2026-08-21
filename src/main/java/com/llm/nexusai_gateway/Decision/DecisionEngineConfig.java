package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Reputation.ReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration for the Decision Engine.
 * Selects the active routing strategy based on application.properties,
 * then wraps it in a RoutingEngineManager to allow hot-swapping at runtime.
 */
@Configuration
public class DecisionEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineConfig.class);

    @Value("${nexusai.routing.strategy:ADAPTIVE}")
    private String strategyName;

    @Value("${nexusai.routing.static.provider:}")
    private String staticProvider;

    @Value("${nexusai.routing.static.model:}")
    private String staticModel;

    @Value("${nexusai.routing.linucb.alpha:1.0}")
    private double linucbAlpha;

    /**
     * Builds the initial engine from config, then wraps it in a RoutingEngineManager.
     * The RoutingEngineManager is what all services receive as their DecisionEngine —
     * it delegates to the active inner engine and allows hot-swapping at runtime.
     */
    @Bean
    public RoutingEngineManager decisionEngine(
            ReputationService reputationService,
            com.llm.nexusai_gateway.Agent.FeedbackLogRepository feedbackLogRepository,
            com.llm.nexusai_gateway.Tenant.TenantRegistry tenantRegistry,
            com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor,
            LinUcbStateRepository stateRepository,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {

        RoutingStrategy strategy;
        try {
            strategy = RoutingStrategy.valueOf(strategyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown routing strategy '{}', falling back to ADAPTIVE", strategyName);
            strategy = RoutingStrategy.ADAPTIVE;
        }

        // Build the initial engine from application.properties
        DecisionEngine initialEngine = switch (strategy) {
            case STATIC     -> new StaticDecisionEngine(staticProvider, staticModel, reputationService);
            case RULE_BASED -> new RuleBasedDecisionEngine(reputationService);
            case WEIGHTED   -> new WeightedDecisionEngine(java.util.Collections.emptyMap(), reputationService);
            case ADAPTIVE   -> new LinUcbDecisionEngine(linucbAlpha, reputationService,
                                                         feedbackLogRepository, contextExtractor);
            case FEDERATED  -> new FederatedLinUcbEngine(linucbAlpha, reputationService,
                                                          tenantRegistry, stateRepository, objectMapper);
        };

        log.info("DecisionEngineConfig: initial strategy={}, engine={}",
                 strategy, initialEngine.getClass().getSimpleName());

        return new RoutingEngineManager(
            initialEngine,
            linucbAlpha, staticProvider, staticModel,
            reputationService, tenantRegistry,
            stateRepository, objectMapper,
            feedbackLogRepository, contextExtractor
        );
    }
}
