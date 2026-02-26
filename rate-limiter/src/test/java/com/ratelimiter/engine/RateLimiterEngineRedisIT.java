package com.ratelimiter.engine;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.RedisFailureMode;
import com.ratelimiter.store.RedisStore;
import com.ratelimiter.strategy.SlidingWindowStrategy;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full engine-level integration test simulating <strong>horizontal scaling</strong>.
 *
 * <p>Two {@link RateLimiterEngine} instances share the same Redis → rate limits
 * are globally enforced, not per-instance. This is the core V3.1 correctness
 * guarantee.</p>
 *
 * <p>Uses Testcontainers to spin up a real Redis container.</p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterEngineRedisIT {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static RateLimiterEngine engine1;
    private static RateLimiterEngine engine2;

    private static final int WINDOW_SECONDS = 60;
    private static final int LIMIT          = 10; // tight limit for fast testing

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
        factory.afterPropertiesSet();

        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        // Both engines share the same Redis store
        RedisStore sharedRedis = new RedisStore(template, WINDOW_SECONDS, RedisFailureMode.FALLBACK_TO_MEMORY);

        RateLimitConfig config = buildConfig();
        com.ratelimiter.metrics.RateLimitMetrics metrics1 = buildNoOpMetrics();
        com.ratelimiter.metrics.RateLimitMetrics metrics2 = buildNoOpMetrics();

        engine1 = new RateLimiterEngine(new SlidingWindowStrategy(sharedRedis), config, metrics1);
        engine2 = new RateLimiterEngine(new SlidingWindowStrategy(sharedRedis), config, metrics2);
    }

    @BeforeEach
    void flushRedis() {
        // Reset between tests
        engine1.getStrategy().cleanupExpired(0, 0); // no-op for Redis
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core horizontal scaling test
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Horizontal scaling: shared Redis enforces global limit across 2 engine instances")
    void horizontalScaling_globalLimitEnforced() throws InterruptedException {
        int totalRequests   = LIMIT + 5;  // 5 should be blocked globally
        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);
        CountDownLatch latch  = new CountDownLatch(totalRequests);

        // Alternate requests between engine1 and engine2 (simulates 2 app instances)
        ExecutorService exec = Executors.newFixedThreadPool(4);
        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            exec.submit(() -> {
                RequestContext ctx = buildContext("10.0.0.1");
                RateLimitResult result = (idx % 2 == 0) ? engine1.evaluate(ctx) : engine2.evaluate(ctx);
                if (result.allowed()) allowed.incrementAndGet();
                else                  blocked.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        exec.shutdown();

        assertThat(allowed.get())
                .as("Exactly %d requests allowed globally (not per instance)", LIMIT)
                .isEqualTo(LIMIT);
        assertThat(blocked.get())
                .as("Exactly 5 requests blocked (above global limit)")
                .isEqualTo(5);
    }

    @Test
    @Order(2)
    @DisplayName("Per-identity isolation: two IPs have independent counters in shared Redis")
    void perIdentity_isolation() {
        long now = System.currentTimeMillis();

        // Exhaust limit for 10.0.0.1
        for (int i = 0; i < LIMIT; i++) {
            engine1.evaluate(buildContext("10.0.0.1"));
        }
        RateLimitResult blockedForIp1 = engine1.evaluate(buildContext("10.0.0.1"));
        assertThat(blockedForIp1.allowed()).as("10.0.0.1 should be blocked").isFalse();

        // 10.0.0.2 has its own counter — should still be allowed
        RateLimitResult allowedForIp2 = engine2.evaluate(buildContext("10.0.0.2"));
        assertThat(allowedForIp2.allowed()).as("10.0.0.2 should still be allowed").isTrue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static RequestContext buildContext(String ip) {
        return RequestContext.of(ip, "GET", "/api/test", System.currentTimeMillis());
    }

    private static RateLimitConfig buildConfig() {
        RateLimitConfig config = new RateLimitConfig();
        config.setDefaultMaxRequests(LIMIT);
        config.setWindowSizeInSeconds(WINDOW_SECONDS);
        config.setAlgorithm("SLIDING_WINDOW");
        config.setIdentity("IP");
        return config;
    }

    /** Minimal no-op metrics for engine construction (avoids MeterRegistry dependency). */
    private static com.ratelimiter.metrics.RateLimitMetrics buildNoOpMetrics() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        return new com.ratelimiter.metrics.RateLimitMetrics(registry);
    }
}
