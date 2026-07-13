package com.llm.nexusai_gateway.Context;

/**
 * Task categories used for context-aware routing decisions.
 * Each category represents a distinct workload type with different
 * quality requirements and provider affinities.
 */
public enum TaskCategory {
    CODE,           // Programming, algorithms, debugging
    REASONING,      // Logic, math, analysis, comparison
    CREATIVE,       // Writing, stories, brainstorming
    FACTUAL,        // Facts, definitions, knowledge retrieval
    CONVERSATION    // Greetings, small talk, simple QA
}
