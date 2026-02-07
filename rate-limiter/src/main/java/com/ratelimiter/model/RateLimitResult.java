package com.ratelimiter.model;

/**
 * Immutable result of a rate limit evaluation, carrying all data
 * needed to set standard response headers.
 *
 * @param allowed       {@code true} if the request should proceed
 * @param limit         configured max requests for this endpoint (X-RateLimit-Limit)
 * @param remaining     requests remaining in the current window (X-RateLimit-Remaining)
 * @param resetEpoch    Unix epoch seconds when the window resets (X-RateLimit-Reset)
 * @param retryAfterSec seconds the client should wait before retrying (Retry-After, 0 if allowed)
 */
public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpoch,
        long retryAfterSec
) {

    /** Convenience factory for an allowed result. */
    public static RateLimitResult allowed(int limit, int remaining, long resetEpoch) {
        return new RateLimitResult(true, limit, remaining, resetEpoch, 0);
    }

    /** Convenience factory for a blocked result. */
    public static RateLimitResult blocked(int limit, long resetEpoch, long retryAfterSec) {
        return new RateLimitResult(false, limit, 0, resetEpoch, retryAfterSec);
    }
}
