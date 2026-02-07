package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.model.RateLimitRule;

/**
 * Strategy interface for rate limiting algorithms.
 *
 * <p>Each implementation encapsulates a different algorithm while sharing
 * the same evaluation contract. The active strategy is selected at startup
 * via {@code rate-limit.algorithm} in configuration.</p>
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link FixedWindowStrategy} — simple counter per time window</li>
 *   <li>{@link SlidingWindowStrategy} — timestamp log, eliminates boundary bursts</li>
 *   <li>{@link TokenBucketStrategy} — steady-rate with burst allowance</li>
 * </ul>
 */
public interface RateLimitStrategy {

    /**
     * Evaluates whether the request should be allowed under the given rule.
     *
     * @param context the request context (identity, method, path, timestamp)
     * @param rule    the resolved rate limit rule (max requests, window size)
     * @return a {@link RateLimitResult} with the decision and header data
     */
    RateLimitResult evaluate(RequestContext context, RateLimitRule rule);

    /**
     * Removes stale entries from the store to prevent unbounded memory growth.
     *
     * @param now            current epoch milliseconds
     * @param windowSizeMs   the global window duration in milliseconds
     * @return the number of entries removed
     */
    int cleanupExpired(long now, long windowSizeMs);

    /**
     * Returns the number of active keys currently tracked.
     */
    int getActiveKeyCount();
}
