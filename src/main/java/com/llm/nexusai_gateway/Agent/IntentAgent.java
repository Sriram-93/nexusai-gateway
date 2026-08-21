package com.llm.nexusai_gateway.Agent;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Set;

@Component
public class IntentAgent implements Agent {
    @Override
    public String getName() { return "IntentAgent"; }

    @Override
    public int getOrder() { return 1; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("message");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("intentResult");
    }
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        return Mono.fromCallable(() -> {
            long t = System.currentTimeMillis();
            
            // Read the dynamically classified semantic context
            var reqCtx = ctx.getRequestContext();
            
            // If it's missing (shouldn't happen), fallback to basic
            if (reqCtx == null) {
                ctx.setIntentResult(new IntentResult("conversation", "low", false, false));
                return WorkflowSignal.CONTINUE;
            }

            String task = reqCtx.taskCategory().name().toLowerCase();
            boolean needsCode = reqCtx.taskCategory().name().equals("CODE");
            boolean needsReasoning = reqCtx.taskCategory().name().equals("REASONING");
            
            String complexity = "low";
            if (reqCtx.estimatedComplexity() > 0.7) {
                complexity = "high";
            } else if (reqCtx.estimatedComplexity() > 0.4) {
                complexity = "medium";
            }

            IntentResult result = new IntentResult(task, complexity, needsCode, needsReasoning);
            ctx.setIntentResult(result);
            ctx.recordAgentTiming(getName(), System.currentTimeMillis() - t);
            return WorkflowSignal.CONTINUE;
        });
    }

    public static class IntentResult {
        private String task;
        private String complexity;
        private boolean needsCode;
        private boolean needsReasoning;

        public IntentResult() {}

        public IntentResult(String task, String complexity, boolean needsCode, boolean needsReasoning) {
            this.task = task;
            this.complexity = complexity;
            this.needsCode = needsCode;
            this.needsReasoning = needsReasoning;
        }

        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }

        public String getComplexity() { return complexity; }
        public void setComplexity(String complexity) { this.complexity = complexity; }

        public boolean isNeedsCode() { return needsCode; }
        public void setNeedsCode(boolean needsCode) { this.needsCode = needsCode; }

        public boolean isNeedsReasoning() { return needsReasoning; }
        public void setNeedsReasoning(boolean needsReasoning) { this.needsReasoning = needsReasoning; }

        @Override
        public String toString() {
            return String.format("{\"task\":\"%s\", \"complexity\":\"%s\", \"needsCode\":%b, \"needsReasoning\":%b}",
                    task, complexity, needsCode, needsReasoning);
        }
    }
}
