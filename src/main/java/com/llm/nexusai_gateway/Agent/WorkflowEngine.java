package com.llm.nexusai_gateway.Agent;

import com.llm.nexusai_gateway.Context.ContextExtractor;
import com.llm.nexusai_gateway.Decision.DecisionEngine;
import com.llm.nexusai_gateway.Provider.LlmProvider;
import com.llm.nexusai_gateway.Provider.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WorkflowEngine (Priority 2 — Configurable Workflow Engine).
 *
 * BEFORE (Priority 1):
 *   Derived execution order implicitly from Agent.getOrder(). All pipeline
 *   branching logic (greeting bypass, security short-circuit) was hardcoded
 *   inside this class using if-blocks.
 *
 * AFTER (Priority 2):
 *   Reads a WorkflowDefinition produced by WorkflowDefinitionFactory.
 *   The definition declares exactly which agents run, in what order, with what
 *   conditions and failure actions. The engine executes it with zero knowledge
 *   of business rules.
 *
 * Execution protocol:
 *   1. Run IntentAgent first (fast, always — needed for definition selection).
 *   2. Ask WorkflowDefinitionFactory to select the right definition.
 *   3. Execute remaining steps in definition order, grouping consecutive
 *      parallel=true steps into concurrent tiers via Mono.zip.
 *   4. After RoutingAgent step, automatically inject the LLM execution step.
 *   5. alwaysRun=true steps (FeedbackAgent) execute even on TERMINATE.
 *   6. onFailure=LOG_AND_SKIP steps degrade gracefully on error or TERMINATE.
 *
 * Design Pattern: Strategy (definition-driven) + Template Method (LLM injection)
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final String LLM_STEP_MARKER = "__LLM_EXECUTION__";

    private final AgentRegistry registry;
    private final ProviderRegistry providerRegistry;
    private final ContextExtractor contextExtractor;
    private final WorkflowDefinitionFactory definitionFactory;
    private final com.llm.nexusai_gateway.Health.ProviderHealthMonitor healthMonitor;

    public WorkflowEngine(AgentRegistry registry,
                          ProviderRegistry providerRegistry,
                          ContextExtractor contextExtractor,
                          WorkflowDefinitionFactory definitionFactory,
                          com.llm.nexusai_gateway.Health.ProviderHealthMonitor healthMonitor) {
        this.registry           = registry;
        this.providerRegistry   = providerRegistry;
        this.contextExtractor   = contextExtractor;
        this.definitionFactory  = definitionFactory;
        this.healthMonitor      = healthMonitor;
    }

    /**
     * Execute the pipeline for the given AgentContext.
     *
     * Two-phase execution:
     *  Phase 1 — IntentAgent runs synchronously to determine request characteristics.
     *  Phase 2 — WorkflowDefinitionFactory selects a definition; engine executes it.
     */
    public Mono<AgentContext> execute(AgentContext ctx) {
        return contextExtractor.extract(ctx.getOriginalRequest())
            .flatMap(requestContext -> {
                ctx.setRequestContext(requestContext);

                // Phase 1: Run IntentAgent first so the factory can inspect intent
                Agent intentAgent = registry.getAgent("IntentAgent");
                return intentAgent.execute(ctx)
                    .flatMap(signal -> {
                        // Phase 2: Select the definition based on detected intent
                        WorkflowDefinition definition = definitionFactory.select(ctx);
                        log.info("WorkflowEngine: Executing pipeline '{}' ({} steps)",
                                 definition.getName(), definition.getSteps().size());

                        // Execute remaining steps (IntentAgent already ran)
                        return executeDefinition(ctx, definition);
                    });
            });
    }

    /**
     * Execute all steps in the WorkflowDefinition.
     * Groups consecutive parallel steps into concurrent tiers.
     * Injects LLM step automatically after the RoutingAgent step.
     */
    private Mono<AgentContext> executeDefinition(AgentContext ctx, WorkflowDefinition definition) {
        List<WorkflowStep> steps = definition.getSteps();

        // Build the reactive chain from the definition
        Mono<AgentContext> chain = Mono.just(ctx);

        int i = 0;
        while (i < steps.size()) {
            WorkflowStep step = steps.get(i);

            // Skip IntentAgent — already executed in Phase 1
            if ("IntentAgent".equals(step.getAgentName())) {
                i++;
                continue;
            }

            final int stepIndex = i;

            if (step.isParallel()) {
                // Collect consecutive parallel steps into one tier
                List<WorkflowStep> parallelGroup = new ArrayList<>();
                while (stepIndex + parallelGroup.size() < steps.size()
                       && steps.get(stepIndex + parallelGroup.size()).isParallel()) {
                    WorkflowStep s = steps.get(stepIndex + parallelGroup.size());
                    if (!"IntentAgent".equals(s.getAgentName())) {
                        parallelGroup.add(s);
                    } else {
                        parallelGroup.add(s); // still include to count correctly
                    }
                    if (!steps.get(stepIndex + parallelGroup.size() - 1).isParallel()) break;
                }

                final List<WorkflowStep> tier = parallelGroup;
                chain = chain.flatMap(c -> executeParallelTier(c, tier));
                i += parallelGroup.size();
            } else {
                final WorkflowStep currentStep = step;
                chain = chain.flatMap(c -> executeStep(c, currentStep).thenReturn(c));

                // After RoutingAgent completes, inject LLM execution
                if ("RoutingAgent".equals(step.getAgentName())) {
                    chain = chain.flatMap(c -> {
                        if (c.isTerminated()) return Mono.just(c);
                        return executeLlmStep(c);
                    });
                }
                i++;
            }
        }

        return chain;
    }

    /**
     * Execute a group of parallel steps concurrently using Mono.zip.
     * Filters out steps whose conditions are not met.
     * alwaysRun steps execute even when context is terminated.
     */
    private Mono<AgentContext> executeParallelTier(AgentContext ctx, List<WorkflowStep> steps) {
        List<WorkflowStep> eligible = steps.stream()
            .filter(s -> !"IntentAgent".equals(s.getAgentName())) // already ran
            .filter(s -> s.isAlwaysRun() || !ctx.isTerminated())
            .filter(s -> s.getCondition().shouldExecute(ctx))
            .collect(Collectors.toList());

        if (eligible.isEmpty()) return Mono.just(ctx);

        if (eligible.size() == 1) {
            return executeStep(ctx, eligible.get(0)).thenReturn(ctx);
        }

        log.info("WorkflowEngine: Running {} agents in parallel: {}",
                 eligible.size(), eligible.stream().map(WorkflowStep::getAgentName).collect(Collectors.joining(", ")));

        List<Mono<WorkflowSignal>> monos = eligible.stream()
            .map(s -> executeStep(ctx, s))
            .collect(Collectors.toList());

        return Mono.zip(monos, results -> ctx);
    }

    /**
     * Execute a single WorkflowStep: check condition, evaluate DAG dependencies, run agent, handle signal.
     */
    private Mono<WorkflowSignal> executeStep(AgentContext ctx, WorkflowStep step) {
        // Respect alwaysRun vs terminated state
        if (ctx.isTerminated() && !step.isAlwaysRun()) {
            log.debug("WorkflowEngine: Skipping {} (pipeline terminated)", step.getAgentName());
            ctx.markSkipped(step.getAgentName());
            return Mono.just(WorkflowSignal.SKIP);
        }

        // Evaluate runtime condition
        if (!step.getCondition().shouldExecute(ctx)) {
            log.info("WorkflowEngine: Skipping {} (condition not met)", step.getAgentName());
            ctx.addNote(step.getAgentName() + ": skipped by condition");
            ctx.markSkipped(step.getAgentName());
            return Mono.just(WorkflowSignal.SKIP);
        }

        Agent agent;
        try {
            agent = registry.getAgent(step.getAgentName());
        } catch (IllegalArgumentException e) {
            log.warn("WorkflowEngine: Agent '{}' not found in registry, skipping", step.getAgentName());
            ctx.markSkipped(step.getAgentName());
            return Mono.just(WorkflowSignal.SKIP);
        }

        // Priority 4 DAG Short-circuit: check if any required dependencies failed or were skipped
        if (!step.isAlwaysRun() && ctx.hasDependencySkippedOrFailed(agent.getDependencies())) {
            log.info("WorkflowEngine: DAG Short-circuit! Skipping {} because dependencies {} were skipped/failed",
                     step.getAgentName(), agent.getDependencies());
            ctx.addNote(step.getAgentName() + ": skipped due to unsatisfied dependencies " + agent.getDependencies());
            ctx.markSkipped(step.getAgentName());
            return Mono.just(WorkflowSignal.SKIP);
        }

        log.info("WorkflowEngine → executing: {}", step.getAgentName());

        return agent.execute(ctx)
            .doOnNext(signal -> {
                log.info("  {} → {}", step.getAgentName(), signal);
                if (signal == WorkflowSignal.SKIP) {
                    ctx.markSkipped(step.getAgentName());
                } else if (signal == WorkflowSignal.TERMINATE) {
                    ctx.markFailed(step.getAgentName());
                    if (step.getOnFailure() == OnFailureAction.HALT) {
                        log.warn("  {} triggered HALT — pipeline will terminate after this step", step.getAgentName());
                    } else if (step.getOnFailure() == OnFailureAction.LOG_AND_SKIP) {
                        log.warn("  {} returned TERMINATE but onFailure=LOG_AND_SKIP — continuing pipeline", step.getAgentName());
                        ctx.addNote(step.getAgentName() + " returned TERMINATE but was set to LOG_AND_SKIP");
                    }
                }
            })
            .onErrorResume(err -> {
                log.error("  {} threw exception: {} — applying onFailure={}", step.getAgentName(), err.getMessage(), step.getOnFailure());
                ctx.addNote(step.getAgentName() + " error: " + err.getMessage());
                ctx.markFailed(step.getAgentName());
                if (step.getOnFailure() == OnFailureAction.HALT) {
                    ctx.terminate("Agent " + step.getAgentName() + " failed: " + err.getMessage());
                    return Mono.just(WorkflowSignal.TERMINATE);
                }
                return Mono.just(WorkflowSignal.SKIP); // LOG_AND_SKIP
            });
    }

    /**
     * LLM Execution Step — injected automatically after RoutingAgent.
     * Records success/failure back into ProviderHealthMonitor for circuit breaker tracking (Priority 8).
     */
    private Mono<AgentContext> executeLlmStep(AgentContext ctx) {
        RoutingAgent.RoutingResult routing = ctx.getRoutingResult();
        if (routing == null || "none".equals(routing.getProvider())) {
            log.warn("WorkflowEngine [LLM]: No routing result, skipping LLM execution.");
            ctx.setFinalResponse("No provider selected.");
            return Mono.just(ctx);
        }

        LlmProvider provider = providerRegistry.getProvider(routing.getProvider());
        if (provider == null) {
            log.error("WorkflowEngine [LLM]: Provider '{}' not registered.", routing.getProvider());
            ctx.setFinalResponse("Service Unavailable: provider not registered.");
            return Mono.just(ctx);
        }

        String providerArm = routing.getProvider() + ":" + routing.getModel();
        long llmStart = System.currentTimeMillis();
        log.info("WorkflowEngine [LLM] → {}/{}", routing.getProvider(), routing.getModel());

        return provider.chat(routing.getProvider(), ctx.getMessage(), routing.getModel())
            .doOnNext(response -> {
                long latencyMs = System.currentTimeMillis() - llmStart;
                ctx.setFinalResponse(response.content());
                ctx.recordAgentTiming("LLMExecution", latencyMs);
                log.info("WorkflowEngine [LLM]: {} output tokens received", response.outputTokens());
                // Priority 8: record success for circuit breaker
                healthMonitor.recordSuccess(providerArm, latencyMs);
            })
            .thenReturn(ctx)
            .onErrorResume(err -> {
                long latencyMs = System.currentTimeMillis() - llmStart;
                log.error("WorkflowEngine [LLM]: Call failed: {}", err.getMessage());
                ctx.setFinalResponse("Service Unavailable: " + err.getMessage());
                ctx.addNote("LLM failure: " + err.getMessage());
                ctx.recordAgentTiming("LLMExecution", latencyMs);
                // Priority 8: record failure for circuit breaker
                healthMonitor.recordFailure(providerArm);
                return Mono.just(ctx);
            });
    }
}
