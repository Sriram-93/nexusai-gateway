package com.llm.nexusai_gateway.Agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class FeedbackAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(FeedbackAgent.class);

    private final FeedbackLogRepository feedbackLogRepository;

    public FeedbackAgent(FeedbackLogRepository feedbackLogRepository) {
        this.feedbackLogRepository = feedbackLogRepository;
    }

    @Override
    public String getName() { return "FeedbackAgent"; }

    @Override
    public int getOrder() { return 6; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.List.of("QualityAgent");
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("message", "routingResult", "qualityResult");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("feedbackLog");
    }

    /**
     * Agent interface bridge: reads all accumulated metrics from AgentContext
     * and asynchronously persists a FeedbackLog record for bandit training.
     * Runs last — always executes, even after termination (to log failures).
     */
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        String provider = ctx.getRoutingResult() != null ? ctx.getRoutingResult().getProvider() : "none";
        String model    = ctx.getRoutingResult() != null ? ctx.getRoutingResult().getModel() : "none";
        long latencyMs  = ctx.elapsedMs();
        double cost     = 0.0;
        double accuracy = ctx.getQualityResult() != null ? ctx.getQualityResult().getCompositeScore() / 100.0 : 0.0;
        String failures = ctx.isTerminated() ? ctx.getTerminationReason() : null;
        return recordFeedback(ctx.getMessage(), provider, model, latencyMs, cost, accuracy, 5, failures)
            .thenReturn(WorkflowSignal.CONTINUE);
    }

    public Mono<Void> recordFeedback(String prompt, String provider, String model, long latencyMs,
                                     double costUsd, double accuracyScore, int userRating, String failures) {
        
        FeedbackLog feedback = new FeedbackLog(prompt, provider, model, latencyMs, costUsd, accuracyScore, userRating, failures);

        return Mono.fromCallable(() -> feedbackLogRepository.save(feedback))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(saved -> log.info("FeedbackAgent recorded model training data. Saved ID: {}", saved.getId()))
            .then();
    }
}
