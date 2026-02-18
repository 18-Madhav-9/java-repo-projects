package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketStrategyTest {

    private TokenBucketStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TokenBucketStrategy(new InMemoryStore());
    }

    @Test
    @DisplayName("First request starts with full bucket minus one")
    void shouldAllowFirstRequest() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 10, 60);
        RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis());
        RateLimitResult r = strategy.evaluate(ctx, rule);
        assertTrue(r.allowed());
        assertEquals(10, r.limit());
        assertEquals(9, r.remaining());
    }

    @Test
    @DisplayName("Burst up to bucket capacity is allowed")
    void shouldAllowBurst() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 60);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            assertTrue(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule).allowed(),
                    "Burst request #" + (i + 1));
        }
    }

    @Test
    @DisplayName("Request after bucket depleted is blocked")
    void shouldBlockWhenDepleted() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 3, 60);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        }
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        assertFalse(r.allowed());
        assertTrue(r.retryAfterSec() > 0);
    }

    @Test
    @DisplayName("Tokens refill over time")
    void shouldRefillTokens() {
        // 10 tokens, 10-second window → refill rate = 1 token/sec
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 10, 10);
        long t0 = System.currentTimeMillis();

        // Deplete all tokens at t=0
        for (int i = 0; i < 10; i++) {
            strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0), rule);
        }
        assertFalse(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0), rule).allowed());

        // At t=2s, 2 tokens should have refilled (rate = 1 token/sec)
        long t2000 = t0 + 2000;
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t2000), rule);
        assertTrue(r.allowed(), "Should have refilled at least 1 token after 2 seconds");
    }

    @Test
    @DisplayName("Cleanup removes stale buckets")
    void shouldCleanupStale() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 1);
        long t0 = System.currentTimeMillis();
        strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0), rule);
        assertEquals(1, strategy.getActiveKeyCount());

        // After 2× window, bucket is considered stale
        int removed = strategy.cleanupExpired(t0 + 3000, 1000);
        assertEquals(1, removed);
        assertEquals(0, strategy.getActiveKeyCount());
    }
}
