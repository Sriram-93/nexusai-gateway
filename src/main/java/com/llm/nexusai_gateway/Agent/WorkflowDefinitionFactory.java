package com.llm.nexusai_gateway.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WorkflowDefinitionFactory — produces the correct WorkflowDefinition for each request.
 *
 * This factory replaces the hardcoded if-blocks that previously lived inside
 * AgentOrchestrationService and WorkflowEngine. Instead of:
 *   "if isGreeting → bypass context"
 * we now have:
 *   "greetingDefinition has no ContextAgent step"
 *
 * Selection logic (two-phase):
 *   Phase 1 — IntentAgent runs first (fast, in-process, no I/O).
 *   Phase 2 — Factory reads IntentResult from AgentContext and selects a definition.
 *
 * Available pipeline definitions:
 *
 *  DEFAULT        Full 6-agent pipeline. Intent + Context (parallel) → Policy → Routing
 *                 → LLM → Quality → Feedback.
 *
 *  GREETING       Skips ContextAgent (no RAG needed). Intent → Policy → Routing
 *                 → LLM → Quality → Feedback.
 *
 *  SECURITY_SCAN  Policy-first fast path. Terminates immediately on security failure
 *                 before any routing or LLM cost is incurred.
 *
 *  FACTUAL        Full pipeline, but ContextAgent uses skipForGreetings guard to
 *                 bypass trivial retrieval. Suitable for quick factual questions.
 *
 * Design Pattern: Factory Method + Strategy
 */
@Component
public class WorkflowDefinitionFactory {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionFactory.class);

    // -----------------------------------------------------------------------
    // Pre-built definitions (singletons — immutable, thread-safe)
    // -----------------------------------------------------------------------

    /**
     * Default full pipeline.
     * IntentAgent + ContextAgent run in parallel (both marked parallel=true).
     * FeedbackAgent is marked alwaysRun=true so it executes even on TERMINATE.
     */
    public static final WorkflowDefinition DEFAULT = new WorkflowDefinition("DEFAULT", List.of(
        // Tier 1: Parallel analysis
        WorkflowStep.of("IntentAgent")
            .parallel()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("ContextAgent")
            .parallel()
            .condition(WorkflowCondition.always())
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        // Tier 2: Security & compliance gate
        WorkflowStep.of("PolicyAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        // Tier 3: Adaptive routing
        WorkflowStep.of("RoutingAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        // Tier 4: LLM execution — handled specially by WorkflowEngine
        // (not an agent bean, inserted as a special step)
        // Tier 5: Post-execution quality audit
        WorkflowStep.of("QualityAgent")
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        // Tier 6: Telemetry — always runs
        WorkflowStep.of("FeedbackAgent")
            .alwaysRun()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build()
    ));

    /**
     * Greeting pipeline: skips ContextAgent RAG retrieval entirely.
     * Suitable for: "hi", "hello", "how are you", trivially short prompts.
     */
    public static final WorkflowDefinition GREETING = new WorkflowDefinition("GREETING", List.of(
        WorkflowStep.of("IntentAgent")
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        // ContextAgent intentionally omitted
        WorkflowStep.of("PolicyAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        WorkflowStep.of("RoutingAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        WorkflowStep.of("QualityAgent")
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("FeedbackAgent")
            .alwaysRun()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build()
    ));

    /**
     * Security fast-path: runs Policy first, terminates immediately on failure.
     * No routing or LLM call happens, saving 100% of token cost.
     */
    public static final WorkflowDefinition SECURITY_FAST_PATH = new WorkflowDefinition("SECURITY_FAST_PATH", List.of(
        WorkflowStep.of("IntentAgent")
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("PolicyAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        // RoutingAgent, LLM, QualityAgent skipped — terminate is already set by PolicyAgent
        WorkflowStep.of("FeedbackAgent")
            .alwaysRun()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build()
    ));

    /**
     * Coding pipeline: full pipeline with ContextAgent (code docs relevant).
     * Intent + Context run in parallel. Same as DEFAULT but named for clarity.
     */
    public static final WorkflowDefinition CODING = new WorkflowDefinition("CODING", List.of(
        WorkflowStep.of("IntentAgent")
            .parallel()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("ContextAgent")
            .parallel()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("PolicyAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        WorkflowStep.of("RoutingAgent")
            .onFailure(OnFailureAction.HALT)
            .build(),
        WorkflowStep.of("QualityAgent")
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build(),
        WorkflowStep.of("FeedbackAgent")
            .alwaysRun()
            .onFailure(OnFailureAction.LOG_AND_SKIP)
            .build()
    ));

    // -----------------------------------------------------------------------
    // Factory method — selects definition based on AgentContext
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Factory methods & Lookup
    // -----------------------------------------------------------------------

    public WorkflowDefinition getByName(String name) {
        if (name == null) return DEFAULT;
        return switch (name.toUpperCase()) {
            case "GREETING"           -> GREETING;
            case "SECURITY_FAST_PATH" -> SECURITY_FAST_PATH;
            case "CODING"             -> CODING;
            case "DEFAULT"            -> DEFAULT;
            default                   -> DEFAULT;
        };
    }

    public WorkflowDefinition createFromCustomSteps(List<String> stepNames) {
        if (stepNames == null || stepNames.isEmpty()) return DEFAULT;
        List<WorkflowStep> steps = stepNames.stream()
            .map(name -> {
                if ("FeedbackAgent".equalsIgnoreCase(name)) {
                    return WorkflowStep.of("FeedbackAgent").alwaysRun().onFailure(OnFailureAction.LOG_AND_SKIP).build();
                } else if ("PolicyAgent".equalsIgnoreCase(name) || "RoutingAgent".equalsIgnoreCase(name)) {
                    return WorkflowStep.of(name).onFailure(OnFailureAction.HALT).build();
                } else {
                    return WorkflowStep.of(name).onFailure(OnFailureAction.LOG_AND_SKIP).build();
                }
            })
            .toList();
        return new WorkflowDefinition("CUSTOM", steps);
    }

    /**
     * Select the appropriate WorkflowDefinition for a given request.
     *
     * Checks explicit user request overrides first:
     *   1. If customSteps is present in ChatRequest → build dynamic CUSTOM pipeline.
     *   2. If pipelineName is present in ChatRequest → lookup predefined pipeline.
     *   3. Otherwise → fall back to intent-based automatic selection.
     */
    public WorkflowDefinition select(AgentContext ctx) {
        if (ctx.getOriginalRequest() != null) {
            if (ctx.getOriginalRequest().getCustomSteps() != null && !ctx.getOriginalRequest().getCustomSteps().isEmpty()) {
                log.info("WorkflowDefinitionFactory: Selected CUSTOM pipeline with steps {}", ctx.getOriginalRequest().getCustomSteps());
                return createFromCustomSteps(ctx.getOriginalRequest().getCustomSteps());
            }
            if (ctx.getOriginalRequest().getPipelineName() != null && !ctx.getOriginalRequest().getPipelineName().isBlank()) {
                log.info("WorkflowDefinitionFactory: Selected explicit pipelineName='{}'", ctx.getOriginalRequest().getPipelineName());
                return getByName(ctx.getOriginalRequest().getPipelineName());
            }
        }

        String msg = ctx.getMessage();

        // Fast-path: trivially short or greeting → no RAG needed
        boolean isGreeting = msg.trim().toLowerCase()
            .matches("^(hi|hello|hey|greetings|hola|yo|thanks|bye|ok|sure)[.!?]*$");
        if (isGreeting || msg.length() < 10) {
            log.info("WorkflowDefinitionFactory: Selected GREETING pipeline");
            return GREETING;
        }

        // If IntentResult is not yet available, use DEFAULT
        IntentAgent.IntentResult intent = ctx.getIntentResult();
        if (intent == null) {
            log.info("WorkflowDefinitionFactory: No IntentResult yet, using DEFAULT pipeline");
            return DEFAULT;
        }

        String task = intent.getTask();
        log.info("WorkflowDefinitionFactory: Selecting pipeline for task='{}' complexity='{}'",
                 task, intent.getComplexity());

        return switch (task.toLowerCase()) {
            case "coding"   -> CODING;
            case "creative" -> DEFAULT;    // Full context for creative tasks
            case "factual"  -> DEFAULT;    // RAG is useful for factual accuracy
            case "education"-> DEFAULT;    // Full pipeline for educational depth
            default         -> DEFAULT;
        };
    }
}
