package com.llm.nexusai_gateway.Exception;

public class RateLimitException extends RuntimeException {
    private final String limitType;
    private final long retryAfterSeconds;

    public RateLimitException(String limitType, long retryAfterSeconds) {
        super("Rate limit exceeded for level: " + limitType + ". Please retry after " + retryAfterSeconds + " seconds.");
        this.limitType = limitType;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getLimitType() {
        return limitType;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
