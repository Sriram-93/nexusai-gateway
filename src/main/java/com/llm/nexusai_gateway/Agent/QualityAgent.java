package com.llm.nexusai_gateway.Agent;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class QualityAgent implements Agent {

    private static final Pattern TOXIC_PATTERN = Pattern.compile(
        "\\b(abuse|offensive|hate|toxic|cheat|hack|illegal|exploit)\\b", 
        Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() { return "QualityAgent"; }

    @Override
    public int getOrder() { return 5; }

    @Override
    public java.util.List<String> getDependencies() {
        return java.util.List.of("RoutingAgent");
    }

    @Override
    public java.util.List<String> getRequiredInputs() {
        return java.util.List.of("message", "finalResponse", "contextResult");
    }

    @Override
    public java.util.List<String> getProducedOutputs() {
        return java.util.List.of("qualityResult");
    }

    /**
     * Agent interface bridge: runs quality evaluation on the LLM response stored in AgentContext.
     * Depends on: LLM execution (implicit, order 4) which sets finalResponse in AgentContext.
     */
    @Override
    public Mono<WorkflowSignal> execute(AgentContext ctx) {
        return Mono.fromCallable(() -> {
            long t = System.currentTimeMillis();
            String retrievedKnowledge = ctx.getContextResult() != null
                ? ctx.getContextResult().getRetrievedKnowledge() : "";
            boolean expectsCode = ctx.getIntentResult() != null && ctx.getIntentResult().isNeedsCode();
            QualityResult result = evaluate(ctx.getMessage(), ctx.getFinalResponse(), retrievedKnowledge, expectsCode);
            ctx.setQualityResult(result);
            ctx.recordAgentTiming(getName(), System.currentTimeMillis() - t);
            return WorkflowSignal.CONTINUE;
        });
    }

    public QualityResult evaluate(String prompt, String response, String retrievedKnowledge, boolean expectsCode) {
        if (response == null || response.isBlank()) {
            return new QualityResult(0.0, 0.0, false, false, 0.0, 0.0);
        }

        // 1. Hallucination check (heuristic coverage: does response align with retrieved knowledge?)
        double hallucinationScore = 1.0; // 1.0 = perfect factuality, 0.0 = high hallucination
        if (retrievedKnowledge != null && !retrievedKnowledge.isBlank()) {
            String[] knowledgeWords = retrievedKnowledge.toLowerCase().split("\\s+");
            int matches = 0;
            int totalChecked = Math.min(10, knowledgeWords.length);
            for (int i = 0; i < totalChecked; i++) {
                if (response.toLowerCase().contains(knowledgeWords[i])) {
                    matches++;
                }
            }
            hallucinationScore = totalChecked > 0 ? (double) matches / totalChecked : 1.0;
        }

        // 2. Toxicity check
        double toxicityScore = 0.0; // 0.0 = clean, 1.0 = highly toxic
        if (TOXIC_PATTERN.matcher(response).find()) {
            toxicityScore = 0.8;
        }

        // 3. Code compilation check (basic syntactical balance validation)
        boolean codeCompiles = true;
        if (expectsCode) {
            if (response.contains("```")) {
                int codeBlockStart = response.indexOf("```");
                int codeBlockEnd = response.indexOf("```", codeBlockStart + 3);
                if (codeBlockStart != -1 && codeBlockEnd != -1) {
                    String codeSnippet = response.substring(codeBlockStart + 3, codeBlockEnd);
                    // Check bracket matching balance
                    codeCompiles = checkBraceMatching(codeSnippet);
                } else {
                    codeCompiles = false; // open code block
                }
            } else {
                codeCompiles = false; // expected code but none found
            }
        }

        // 4. JSON validation check
        boolean jsonValid = true;
        String trimmed = response.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                objectMapper.readTree(trimmed);
                jsonValid = true;
            } catch (Exception e) {
                jsonValid = false;
            }
        } else {
            // Not JSON, which is only a failure if the prompt explicitly asked for JSON
            if (prompt.toLowerCase().contains("json") || prompt.toLowerCase().contains("format as object")) {
                jsonValid = false;
            }
        }

        // 5. Completeness score
        double completenessScore = 1.0;
        String[] promptWords = prompt.toLowerCase().split("\\s+");
        int keywordMatches = 0;
        int importantWords = 0;
        for (String word : promptWords) {
            if (word.length() > 4) {
                importantWords++;
                if (response.toLowerCase().contains(word)) {
                    keywordMatches++;
                }
            }
        }
        completenessScore = importantWords > 0 ? (double) keywordMatches / importantWords : 1.0;

        // Composite Quality Score out of 100
        double compositeScore = (
            (hallucinationScore * 0.3) +
            ((1.0 - toxicityScore) * 0.2) +
            ((codeCompiles ? 1.0 : 0.0) * 0.25) +
            ((jsonValid ? 1.0 : 0.0) * 0.1) +
            (completenessScore * 0.15)
        ) * 100.0;

        // Clip compositeScore
        compositeScore = Math.max(0.0, Math.min(100.0, compositeScore));

        return new QualityResult(hallucinationScore, toxicityScore, codeCompiles, jsonValid, completenessScore, compositeScore);
    }

    private boolean checkBraceMatching(String code) {
        int braces = 0;
        int parens = 0;
        for (char c : code.toCharArray()) {
            if (c == '{') braces++;
            if (c == '}') braces--;
            if (c == '(') parens++;
            if (c == ')') parens--;
            if (braces < 0 || parens < 0) return false; // closed before open
        }
        return braces == 0 && parens == 0;
    }

    public static class QualityResult {
        private double hallucinationScore;
        private double toxicityScore;
        private boolean codeCompiles;
        private boolean jsonValid;
        private double completenessScore;
        private double compositeScore;

        public QualityResult() {}

        public QualityResult(double hallucinationScore, double toxicityScore, boolean codeCompiles,
                             boolean jsonValid, double completenessScore, double compositeScore) {
            this.hallucinationScore = hallucinationScore;
            this.toxicityScore = toxicityScore;
            this.codeCompiles = codeCompiles;
            this.jsonValid = jsonValid;
            this.completenessScore = completenessScore;
            this.compositeScore = compositeScore;
        }

        public double getHallucinationScore() { return hallucinationScore; }
        public void setHallucinationScore(double hallucinationScore) { this.hallucinationScore = hallucinationScore; }

        public double getToxicityScore() { return toxicityScore; }
        public void setToxicityScore(double toxicityScore) { this.toxicityScore = toxicityScore; }

        public boolean isCodeCompiles() { return codeCompiles; }
        public void setCodeCompiles(boolean codeCompiles) { this.codeCompiles = codeCompiles; }

        public boolean isJsonValid() { return jsonValid; }
        public void setJsonValid(boolean jsonValid) { this.jsonValid = jsonValid; }

        public double getCompletenessScore() { return completenessScore; }
        public void setCompletenessScore(double completenessScore) { this.completenessScore = completenessScore; }

        public double getCompositeScore() { return compositeScore; }
        public void setCompositeScore(double compositeScore) { this.compositeScore = compositeScore; }
    }
}
