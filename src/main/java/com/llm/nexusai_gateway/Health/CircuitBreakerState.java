package com.llm.nexusai_gateway.Health;

/**
 * Circuit breaker state machine for provider health monitoring (Priority 8).
 *
 * State transitions:
 *   CLOSED  → OPEN      : when consecutive failure threshold is breached
 *   OPEN    → HALF_OPEN : after cooldown window expires
 *   HALF_OPEN → CLOSED  : on first successful probe
 *   HALF_OPEN → OPEN    : if probe also fails
 */
public enum CircuitBreakerState {
    /** Normal operation. Requests flow through. */
    CLOSED,
    /** Provider is failing. All requests fast-fail immediately. */
    OPEN,
    /** Cooldown expired. A single probe request is allowed to test recovery. */
    HALF_OPEN
}
