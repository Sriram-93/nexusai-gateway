package com.llm.nexusai_gateway.Agent;

/**
 * Functional interface representing a runtime condition on AgentContext.
 *
 * Used inside WorkflowStep to decide if a step should execute.
 * If the condition returns false, the step is SKIPPED.
 *
 * Examples:
 *   ctx -> !ctx.getMessage().matches("^(hi|hello|hey).*")   // skip for greetings
 *   ctx -> ctx.getIntentResult() != null                     // skip if intent unavailable
 *   ctx -> "coding".equals(ctx.getIntentResult().getTask())  // only run for coding tasks
 */
@FunctionalInterface
public interface WorkflowCondition {

    /** @return true if this step should execute, false if it should be skipped */
    boolean shouldExecute(AgentContext ctx);

    /** Convenience constant: always execute */
    static WorkflowCondition always() {
        return ctx -> true;
    }

    /** Skip if prompt is a greeting or trivially short */
    static WorkflowCondition skipForGreetings() {
        return ctx -> {
            String msg = ctx.getMessage();
            boolean isGreeting = msg.trim().toLowerCase().matches("^(hi|hello|hey|greetings|hola|yo)[.!]*$");
            return !isGreeting && msg.length() >= 10;
        };
    }

    /** Only run if the pipeline has not been terminated */
    static WorkflowCondition onlyIfActive() {
        return ctx -> !ctx.isTerminated();
    }

    /** Only run if intent task matches the given type */
    static WorkflowCondition onlyForTask(String task) {
        return ctx -> ctx.getIntentResult() != null
                   && task.equalsIgnoreCase(ctx.getIntentResult().getTask());
    }

    /** Only run if intent task is any of the given types */
    static WorkflowCondition onlyForTasks(String... tasks) {
        return ctx -> {
            if (ctx.getIntentResult() == null) return false;
            String actual = ctx.getIntentResult().getTask();
            for (String t : tasks) {
                if (t.equalsIgnoreCase(actual)) return true;
            }
            return false;
        };
    }
}
