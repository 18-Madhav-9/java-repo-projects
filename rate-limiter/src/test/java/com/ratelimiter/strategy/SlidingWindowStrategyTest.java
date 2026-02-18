package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowStrategyTest {

    private SlidingWindowStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SlidingWindowStrategy(new InMemoryStore());
    }

    @Test
    @DisplayName("Requests within limit are allowed")
    void shouldAllowWithinLimit() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 60);
        for (int i = 0; i < 5; i++) {
            RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis());
            assertTrue(strategy.evaluate(ctx, rule).allowed());
        }
    }

    @Test
    @DisplayName("6th request is blocked")
    void shouldBlockExceedingLimit() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 60);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        }
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", now), rule);
        assertFalse(r.allowed());
        assertEquals(0, r.remaining());
    }

    @Test
    @DisplayName("FLAGSHIP: No boundary burst — sliding window tracks individual timestamps")
    void shouldPreventBoundaryBurst() {
        // With a 2-second window and limit of 4:
        // Send 4 requests at t=0 (fills the window)
        // Send 1 request at t=1.5s — should be blocked because all 4 are still in the 2s window
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 4, 2);
        long t0 = System.currentTimeMillis();

        // Fill window at t=0
        for (int i = 0; i < 4; i++) {
            assertTrue(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0), rule).allowed());
        }

        // At t=1.5s — still within the 2s window, all 4 timestamps are still valid
        long t1500 = t0 + 1500;
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t1500), rule);
        assertFalse(r.allowed(), "Sliding window should block — all 4 requests still in window");
    }

    @Test
    @DisplayName("Oldest request slides out of window, allowing new requests")
    void shouldSlideOldRequestsOut() {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 2, 1); // 2 req per 1 sec
        long t0 = System.currentTimeMillis();

        assertTrue(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0), rule).allowed());
        assertTrue(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0 + 100), rule).allowed());
        assertFalse(strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t0 + 200), rule).allowed());

        // At t0+1100ms, the first request (t0) should have slid out of the 1s window
        long t1100 = t0 + 1100;
        RateLimitResult r = strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", t1100), rule);
        assertTrue(r.allowed(), "First request should have expired, freeing a slot");
    }

    @Test
    @DisplayName("Concurrent requests don't corrupt state")
    void shouldHandleConcurrency() throws InterruptedException {
        RateLimitRule rule = new RateLimitRule("POST:/api/order", 100, 60);
        int threadCount = 10;
        int reqPerThread = 10;
        Thread[] threads = new Thread[threadCount];
        int[] allowed = {0};
        Object lock = new Object();

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                int local = 0;
                for (int r = 0; r < reqPerThread; r++) {
                    RequestContext ctx = RequestContext.of("10.0.0.1", "POST", "/api/order", System.currentTimeMillis());
                    if (strategy.evaluate(ctx, rule).allowed()) local++;
                }
                synchronized (lock) { allowed[0] += local; }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(100, allowed[0]);
    }

    @Test
    @DisplayName("Cleanup removes entries with no active timestamps")
    void shouldCleanupEmpty() throws InterruptedException {
        RateLimitRule rule = new RateLimitRule("GET:/api/test", 5, 1);
        strategy.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis()), rule);
        assertEquals(1, strategy.getActiveKeyCount());

        Thread.sleep(1100);
        int removed = strategy.cleanupExpired(System.currentTimeMillis(), 1000);
        assertEquals(1, removed);
        assertEquals(0, strategy.getActiveKeyCount());
    }
}
