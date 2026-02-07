package com.ratelimiter.model;

/**
 * Represents a single rate limit rule resolved from configuration.
 *
 * @param endpointPattern the matched endpoint pattern (e.g. {@code GET:/api/user/**} or {@code *} for default)
 * @param maxRequests     maximum requests allowed within the window
 * @param windowSizeSeconds duration of the rate limit window in seconds
 */
public record RateLimitRule(
        String endpointPattern,
        int maxRequests,
        int windowSizeSeconds
) {
}
