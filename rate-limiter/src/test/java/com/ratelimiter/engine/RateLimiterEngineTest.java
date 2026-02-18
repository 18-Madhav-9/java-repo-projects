package com.ratelimiter.engine;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.metrics.RateLimitMetrics;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.InMemoryStore;
import com.ratelimiter.strategy.SlidingWindowStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterEngineTest {

    private RateLimiterEngine engine;

    @BeforeEach
    void setUp() {
        RateLimitConfig config = new RateLimitConfig();
        config.setAlgorithm("SLIDING_WINDOW");
        config.setIdentity("IP");
        config.setDefaultMaxRequests(20);
        config.setWindowSizeInSeconds(60);
        config.setEndpointLimits(Map.of(
                "GET:/api/test", 5,
                "POST:/api/order", 10,
                "DELETE:/api/user", 5
        ));

        SlidingWindowStrategy strategy = new SlidingWindowStrategy(new InMemoryStore());
        RateLimitMetrics metrics = new RateLimitMetrics(new SimpleMeterRegistry());
        engine = new RateLimiterEngine(strategy, config, metrics);
    }

    @Test
    @DisplayName("Enforces per-endpoint limit via engine")
    void shouldEnforcePerEndpointLimit() {
        for (int i = 0; i < 5; i++) {
            RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis());
            assertTrue(engine.evaluate(ctx).allowed());
        }
        RequestContext ctx = RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis());
        assertFalse(engine.evaluate(ctx).allowed());
    }

    @Test
    @DisplayName("Different endpoints are isolated")
    void shouldIsolateEndpoints() {
        for (int i = 0; i < 5; i++) {
            engine.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis()));
        }
        assertFalse(engine.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/test", System.currentTimeMillis())).allowed());
        assertTrue(engine.evaluate(RequestContext.of("10.0.0.1", "POST", "/api/order", System.currentTimeMillis())).allowed());
    }

    @Test
    @DisplayName("Different IPs are isolated")
    void shouldIsolateIps() {
        for (int i = 0; i < 5; i++) {
            engine.evaluate(RequestContext.of("10.0.0.1", "DELETE", "/api/user", System.currentTimeMillis()));
        }
        assertFalse(engine.evaluate(RequestContext.of("10.0.0.1", "DELETE", "/api/user", System.currentTimeMillis())).allowed());
        assertTrue(engine.evaluate(RequestContext.of("10.0.0.2", "DELETE", "/api/user", System.currentTimeMillis())).allowed());
    }

    @Test
    @DisplayName("Unconfigured endpoint uses default limit")
    void shouldFallbackToDefault() {
        for (int i = 0; i < 20; i++) {
            assertTrue(engine.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/unknown", System.currentTimeMillis())).allowed());
        }
        assertFalse(engine.evaluate(RequestContext.of("10.0.0.1", "GET", "/api/unknown", System.currentTimeMillis())).allowed());
    }

    @Test
    @DisplayName("Result includes response header data")
    void shouldReturnHeaderData() {
        RequestContext ctx = RequestContext.of("10.0.0.1", "POST", "/api/order", System.currentTimeMillis());
        RateLimitResult r = engine.evaluate(ctx);
        assertTrue(r.allowed());
        assertEquals(10, r.limit());
        assertEquals(9, r.remaining());
        assertTrue(r.resetEpoch() > 0);
    }
}
