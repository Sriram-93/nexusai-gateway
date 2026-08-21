package com.llm.nexusai_gateway.Decision;

import com.llm.nexusai_gateway.Context.RequestContext;
import com.llm.nexusai_gateway.Reputation.ReputationService;
import com.llm.nexusai_gateway.Tenant.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime-swappable wrapper around the active DecisionEngine.
 *
 * Wraps the active engine in an AtomicReference so it can be replaced
 * at runtime via the settings API — without restarting the JVM.
 *
 * Instantiated as a @Bean in DecisionEngineConfig (not @Component) to avoid
 * circular dependency — it implements DecisionEngine but also takes one as arg.
 *
 * Thread-safe: AtomicReference guarantees visibility across reactive threads.
 */
public class RoutingEngineManager implements DecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngineManager.class);

    private final AtomicReference<DecisionEngine> activeEngine;

    // Config values passed in by DecisionEngineConfig
    private final double linucbAlpha;
    private final String staticProvider;
    private final String staticModel;

    // Dependencies for constructing new engines on-the-fly
    private final ReputationService reputationService;
    private final TenantRegistry tenantRegistry;
    private final LinUcbStateRepository stateRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.llm.nexusai_gateway.Agent.FeedbackLogRepository feedbackLogRepository;
    private final com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor;

    public RoutingEngineManager(DecisionEngine initialEngine,
                                double linucbAlpha,
                                String staticProvider,
                                String staticModel,
                                ReputationService reputationService,
                                TenantRegistry tenantRegistry,
                                LinUcbStateRepository stateRepository,
                                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                com.llm.nexusai_gateway.Agent.FeedbackLogRepository feedbackLogRepository,
                                com.llm.nexusai_gateway.Context.ContextExtractor contextExtractor) {
        this.activeEngine = new AtomicReference<>(initialEngine);
        this.linucbAlpha = linucbAlpha;
        this.staticProvider = staticProvider;
        this.staticModel = staticModel;
        this.reputationService = reputationService;
        this.tenantRegistry = tenantRegistry;
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
        this.feedbackLogRepository = feedbackLogRepository;
        this.contextExtractor = contextExtractor;
    }

    // ─── DecisionEngine delegation ─────────────────────────────────────────────

    @Override
    public ExplainedDecision select(RequestContext context, List<String> eligibleProviders) {
        return activeEngine.get().select(context, eligibleProviders);
    }

    @Override
    public void update(RequestContext context, String provider, double reward) {
        activeEngine.get().update(context, provider, reward);
    }

    @Override
    public void updateWithComponents(RequestContext context, String provider,
                                     double scalarReward, double[] rewardComponents) {
        activeEngine.get().updateWithComponents(context, provider, scalarReward, rewardComponents);
    }

    @Override
    public RoutingStrategy getStrategy() {
        return activeEngine.get().getStrategy();
    }

    @Override
    public void reset() {
        activeEngine.get().reset();
    }

    // ─── Runtime switching ─────────────────────────────────────────────────────

    /**
     * Switch the active routing strategy at runtime.
     * Constructs a fresh engine instance for the requested strategy.
     * The old engine's learned state is discarded (intentional — this is an experiment boundary).
     *
     * @param strategy Target strategy: STATIC | RULE_BASED | WEIGHTED | ADAPTIVE | FEDERATED
     * @param weights  Optional arm weights for WEIGHTED strategy (arm key → weight).
     *                 If null, uses default equal weights across eligible arms.
     * @return The strategy name of the newly active engine.
     */
    public String switchStrategy(RoutingStrategy strategy, Map<String, Double> weights) {
        DecisionEngine newEngine = switch (strategy) {
            case STATIC   -> new StaticDecisionEngine(staticProvider, staticModel, reputationService);
            case RULE_BASED -> new RuleBasedDecisionEngine(reputationService);
            case WEIGHTED -> {
                Map<String, Double> w = (weights != null && !weights.isEmpty())
                    ? weights
                    : java.util.Collections.emptyMap();
                yield new WeightedDecisionEngine(w, reputationService);
            }
            case ADAPTIVE  -> new LinUcbDecisionEngine(linucbAlpha, reputationService,
                                                        feedbackLogRepository, contextExtractor);
            case FEDERATED -> new FederatedLinUcbEngine(linucbAlpha, reputationService,
                                                         tenantRegistry, stateRepository, objectMapper);
        };

        DecisionEngine old = activeEngine.getAndSet(newEngine);
        log.info("RoutingEngineManager: switched {} → {}", old.getStrategy(), strategy);
        return strategy.name();
    }

    /** Return the current active engine's class name for observability. */
    public String getActiveEngineClass() {
        return activeEngine.get().getClass().getSimpleName();
    }
}
