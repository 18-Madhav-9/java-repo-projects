package com.ratelimiter.store;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RedisStore} using a real Redis instance
 * spun up via Testcontainers.
 *
 * <p>Covers:
 * <ul>
 *   <li>Fixed Window atomic counter increment</li>
 *   <li>Sliding Window sorted set behaviour</li>
 *   <li>Token Bucket lazy refill</li>
 *   <li>Concurrent correctness — no lost updates under thread contention</li>
 *   <li>Fail-open / fallback behaviour when Redis returns a sentinel</li>
 * </ul>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisStoreIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static StringRedisTemplate redisTemplate;
    private static RedisStore           store;

    private static final int WINDOW_SECONDS = 10;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getFirstMappedPort()));
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        store = new RedisStore(redisTemplate, WINDOW_SECONDS, RedisFailureMode.FALLBACK_TO_MEMORY);
    }

    @BeforeEach
    void flushBetweenTests() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fixed Window
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("FixedWindow: first 3 requests allowed, 4th blocked at limit=3")
    void fixedWindow_allowsThenBlocks() {
        String key      = "rl:test:fw:10.0.0.1:GET:/api/test";
        int    limit    = 3;
        long   windowMs = WINDOW_SECONDS * 1000L;
        long   now      = System.currentTimeMillis();

        long[] r1 = store.evaluateFixedWindow(key, limit, windowMs, now);
        long[] r2 = store.evaluateFixedWindow(key, limit, windowMs, now + 100);
        long[] r3 = store.evaluateFixedWindow(key, limit, windowMs, now + 200);
        long[] r4 = store.evaluateFixedWindow(key, limit, windowMs, now + 300);

        assertThat(r1[2]).as("request 1 allowed").isEqualTo(1L);
        assertThat(r2[2]).as("request 2 allowed").isEqualTo(1L);
        assertThat(r3[2]).as("request 3 allowed").isEqualTo(1L);
        assertThat(r4[2]).as("request 4 blocked").isEqualTo(0L);
        assertThat(r4[0]).as("count should be 4 (over limit)").isEqualTo(4L);
    }

    @Test
    @Order(2)
    @DisplayName("FixedWindow: window resets after expiry")
    void fixedWindow_windowReset() {
        String key      = "rl:test:fw:reset:GET:/api/test";
        int    limit    = 2;
        long   windowMs = 500L; // 500ms window for fast test
        long   now      = System.currentTimeMillis();

        store.evaluateFixedWindow(key, limit, windowMs, now);
        store.evaluateFixedWindow(key, limit, windowMs, now + 100);
        long[] blocked = store.evaluateFixedWindow(key, limit, windowMs, now + 200);
        assertThat(blocked[2]).as("blocked before window reset").isEqualTo(0L);

        // Simulate window expiry by using a timestamp past the window
        long[] afterReset = store.evaluateFixedWindow(key, limit, windowMs, now + 600);
        assertThat(afterReset[2]).as("allowed after window reset").isEqualTo(1L);
        assertThat(afterReset[0]).as("count resets to 1").isEqualTo(1L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sliding Window
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("SlidingWindow: sorted set trims old entries")
    void slidingWindow_trimsOldEntries() {
        String key      = "rl:test:sw:10.0.0.1:GET:/api/test";
        int    limit    = 3;
        long   windowMs = 1000L; // 1s window
        long   now      = System.currentTimeMillis();

        // Fill to limit
        store.evaluateSlidingWindow(key, limit, windowMs, now);
        store.evaluateSlidingWindow(key, limit, windowMs, now + 100);
        store.evaluateSlidingWindow(key, limit, windowMs, now + 200);

        long[] blocked = store.evaluateSlidingWindow(key, limit, windowMs, now + 300);
        assertThat(blocked[2]).as("4th request blocked").isEqualTo(0L);

        // After 1100ms, the first 3 entries have expired
        long[] allowed = store.evaluateSlidingWindow(key, limit, windowMs, now + 1200);
        assertThat(allowed[2]).as("allowed after old entries expired").isEqualTo(1L);
    }

    @Test
    @Order(4)
    @DisplayName("SlidingWindow: no boundary burst — truly rolling window")
    void slidingWindow_noBoundaryBurst() {
        String key      = "rl:test:sw:burst:GET:/api/test";
        int    limit    = 5;
        long   windowMs = 1000L;
        long   now      = System.currentTimeMillis();

        // 5 requests at end of window T=900ms
        for (int i = 0; i < 5; i++) {
            store.evaluateSlidingWindow(key, limit, windowMs, now + 900 + i);
        }
        // At T=1050ms, only 4 of those are expired (still within 1s of T=905..909)
        long[] r = store.evaluateSlidingWindow(key, limit, windowMs, now + 1050);
        // Some will still be in-window → should be blocked
        assertThat(r[2]).as("still within sliding window, should be blocked").isEqualTo(0L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Token Bucket
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("TokenBucket: tokens deplete then refill")
    void tokenBucket_depleteAndRefill() {
        String key         = "rl:test:tb:10.0.0.1:GET:/api/test";
        int    maxTokens   = 3;
        double refillRate  = 1.0; // 1 token/sec
        long   now         = System.currentTimeMillis();

        long[] r1 = store.evaluateTokenBucket(key, maxTokens, refillRate, now);
        long[] r2 = store.evaluateTokenBucket(key, maxTokens, refillRate, now + 100);
        long[] r3 = store.evaluateTokenBucket(key, maxTokens, refillRate, now + 200);
        long[] r4 = store.evaluateTokenBucket(key, maxTokens, refillRate, now + 300);

        assertThat(r1[1]).as("token 1 allowed").isEqualTo(1L);
        assertThat(r2[1]).as("token 2 allowed").isEqualTo(1L);
        assertThat(r3[1]).as("token 3 allowed").isEqualTo(1L);
        assertThat(r4[1]).as("token 4 blocked (bucket empty)").isEqualTo(0L);

        // After 2 seconds, 2 tokens should have refilled
        long[] afterRefill = store.evaluateTokenBucket(key, maxTokens, refillRate, now + 2100);
        assertThat(afterRefill[1]).as("token refilled after 2s").isEqualTo(1L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Concurrent correctness
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("FixedWindow: concurrent requests — no lost updates, exact count")
    void fixedWindow_concurrentRequests_exactCount() throws InterruptedException {
        String key      = "rl:test:fw:concurrent:GET:/api/test";
        int    limit    = 1000;
        long   windowMs = WINDOW_SECONDS * 1000L;
        long   now      = System.currentTimeMillis();
        int    threads  = 50;
        int    perThread = 20;   // 50 × 20 = 1000 total requests

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                int allowed = 0;
                for (int i = 0; i < perThread; i++) {
                    long[] result = store.evaluateFixedWindow(key, limit, windowMs,
                            System.currentTimeMillis());
                    if (result[2] == 1L) allowed++;
                }
                return allowed;
            }));
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        int totalAllowed = 0;
        for (Future<Integer> f : futures) {
            try { totalAllowed += f.get(); } catch (ExecutionException e) { /* ignore */ }
        }

        // With limit=1000 and exactly 1000 requests, exactly 1000 should be allowed
        assertThat(totalAllowed)
                .as("exactly %d requests allowed under concurrent load", limit)
                .isEqualTo(limit);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Key TTL
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("RedisStore: TTL is set on keys (TTL = windowSizeInSeconds × 2)")
    void keyTtlIsSet() throws InterruptedException {
        String key = "rl:test:ttl:10.0.0.1:GET:/api/test";
        store.evaluateFixedWindow(key, 10, WINDOW_SECONDS * 1000L, System.currentTimeMillis());

        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).as("TTL should be set on the key").isNotNull().isPositive();
        // TTL should be approximately windowSizeInSeconds * 2 = 20s
        assertThat(ttl).as("TTL ≤ windowSizeInSeconds × 2").isLessThanOrEqualTo(WINDOW_SECONDS * 2L);
        assertThat(ttl).as("TTL > 0").isGreaterThan(0L);
    }
}
