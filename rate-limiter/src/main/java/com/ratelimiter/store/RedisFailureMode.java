package com.ratelimiter.store;

/**
 * Defines how the {@link RedisStore} behaves when Redis is unreachable
 * or returns an unexpected result.
 *
 * <p>Configured via {@code rate-limit.redis-failure-mode} in
 * {@code application.properties}.</p>
 *
 * <ul>
 *   <li>{@link #ALLOW_ALL} — fail-open: let all requests through (no rate limiting).
 *       Safe for availability-sensitive APIs.</li>
 *   <li>{@link #DENY_ALL} — fail-closed: block all requests (HTTP 429).
 *       Protects downstream systems at the cost of availability.</li>
 *   <li>{@link #FALLBACK_TO_MEMORY} — per-instance in-memory limiting continues
 *       using {@link InMemoryStore}. Rate limits are enforced locally (not globally).
 *       <em>This is the recommended default.</em></li>
 * </ul>
 */
public enum RedisFailureMode {

    /** Allow all requests when Redis is down (fail-open). */
    ALLOW_ALL,

    /** Block all requests when Redis is down (fail-closed). */
    DENY_ALL,

    /** Fall back to per-instance in-memory rate limiting when Redis is down. */
    FALLBACK_TO_MEMORY
}
