package com.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer-backed metrics for the rate limiter.
 *
 * <p>Exposed via Spring Boot Actuator at {@code /actuator/metrics}:</p>
 * <ul>
 *   <li>{@code rate_limiter.requests.allowed} — total allowed requests</li>
 *   <li>{@code rate_limiter.requests.blocked} — total blocked requests (429s)</li>
 *   <li>{@code rate_limiter.active.keys} — current tracked storage keys</li>
 * </ul>
 */
@Component
public class RateLimitMetrics {

    private final Counter allowedCounter;
    private final Counter blockedCounter;
    private final AtomicInteger activeKeys;

    public RateLimitMetrics(MeterRegistry registry) {
        this.allowedCounter = Counter.builder("rate_limiter.requests.allowed")
                .description("Total allowed requests")
                .register(registry);

        this.blockedCounter = Counter.builder("rate_limiter.requests.blocked")
                .description("Total blocked requests (HTTP 429)")
                .register(registry);

        this.activeKeys = registry.gauge("rate_limiter.active.keys",
                new AtomicInteger(0));
    }

    public void recordAllowed() {
        allowedCounter.increment();
    }

    public void recordBlocked() {
        blockedCounter.increment();
    }

    public void updateActiveKeys(int count) {
        activeKeys.set(count);
    }

    /** Returns the total allowed count (for admin API). */
    public double getTotalAllowed() {
        return allowedCounter.count();
    }

    /** Returns the total blocked count (for admin API). */
    public double getTotalBlocked() {
        return blockedCounter.count();
    }

    /** Returns the current active key count (for admin API). */
    public int getActiveKeysCount() {
        return activeKeys.get();
    }
}
