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
 * Selects the active routing strategy based on application properties.
 *
 * Doc 07: "Only the routing strategy will change between experiments."
 */
@Configuration
public class DecisionEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(DecisionEngineConfig.class);

    @Value("${nexusai.routing.strategy:ADAPTIVE}")
    private String strategyName;

    @Value("${nexusai.routing.static.provider:gemini}")
    private String staticProvider;

    @Value("${nexusai.routing.static.model:gemini-2.5-flash}")
    private String staticModel;

    @Value("${nexusai.routing.linucb.alpha:1.0}")
    private double linucbAlpha;

    @Bean
    public DecisionEngine decisionEngine(ReputationService reputationService) {
        RoutingStrategy strategy;
        try {
            strategy = RoutingStrategy.valueOf(strategyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown routing strategy '{}', falling back to ADAPTIVE", strategyName);
            strategy = RoutingStrategy.ADAPTIVE;
        }

        DecisionEngine engine = switch (strategy) {
            case STATIC -> new StaticDecisionEngine(staticProvider, staticModel, reputationService);
            case RULE_BASED -> new RuleBasedDecisionEngine(reputationService);
            case WEIGHTED -> new WeightedDecisionEngine(
                Map.of(
                    "gemini:gemini-2.5-flash", 0.4,
                    "groq:llama-3.3-70b-versatile", 0.4,
                    "groq:llama-3.1-8b-instant", 0.2
                ), reputationService);
            case ADAPTIVE -> new LinUcbDecisionEngine(linucbAlpha, reputationService);
        };

        log.info("Decision Engine initialized: strategy={}, implementation={}",
                 strategy, engine.getClass().getSimpleName());
        return engine;
    }
}
