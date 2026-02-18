package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowStrategyTest {

    private FixedWindowStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FixedWindowStrategy(new InMemoryStore());
    }

    @Test
    @DisplayName("Requests within limit are allowed")
    void shouldAllowWithinLimit() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 60);
        for (int i = 0; i < 5; i++) {
            RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis());
            RateLimitResult r = strategy.evaluate(ctx, rule);
            assertTrue(r.allowed(), "Request #" + (i + 1));
        }
    }

    @Test
    @DisplayName("6th request exceeding limit is blocked")
    void shouldBlockExceedingLimit() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 60);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        }
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        assertFalse(r.allowed());
        assertEquals(0, r.remaining());
        assertTrue(r.retryAfterSec() > 0);
    }

    @Test
    @DisplayName("Result includes correct header data")
    void shouldReturnHeaderData() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 10, 60);
        long now = System.currentTimeMillis();
        RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", now);
        RateLimitResult r = strategy.evaluate(ctx, rule);
        assertTrue(r.allowed());
        assertEquals(10, r.limit());
        assertEquals(9, r.remaining());
        assertTrue(r.resetEpoch() > now / 1000);
    }

    @Test
    @DisplayName("Window resets after expiry")
    void shouldResetAfterWindow() throws InterruptedException {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 2, 1);
        long now = System.currentTimeMillis();
        strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        assertFalse(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule).allowed());

        Thread.sleep(1100);
        assertTrue(strategy.evaluate(
                RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis()), rule).allowed());
    }

    @Test
    @DisplayName("Cleanup removes expired entries")
    void shouldCleanupExpired() throws InterruptedException {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 1);
        strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis()), rule);
        assertEquals(1, strategy.getActiveKeyCount());

        Thread.sleep(1100);
        int removed = strategy.cleanupExpired(System.currentTimeMillis(), 1000);
        assertEquals(1, removed);
        assertEquals(0, strategy.getActiveKeyCount());
    }
}
