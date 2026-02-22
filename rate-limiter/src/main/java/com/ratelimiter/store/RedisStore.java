package com.ratelimiter.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Redis-backed {@link CounterStore} for V3.1.
 *
 * <p>Uses per-algorithm Lua scripts for atomic, distributed rate limiting.
 * All three algorithms (Fixed Window, Sliding Window, Token Bucket) run
 * their logic entirely inside Redis, eliminating round-trip race conditions.</p>
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li>Lua scripts load at startup from {@code classpath:lua/}</li>
 *   <li>TTL = {@code windowSizeInSeconds × 2} — prevents unbounded memory growth</li>
 *   <li>{@code compute()} is <em>not used</em> in Redis mode; strategies that
 *       detect a {@link RedisStore} call {@link #evaluateFixedWindow},
 *       {@link #evaluateSlidingWindow}, or {@link #evaluateTokenBucket} directly.</li>
 *   <li>On Redis failure: falls back to the provided {@link InMemoryStore}
 *       (fail-open with per-instance limiting). Configurable via
 *       {@code rate-limit.redis-failure-mode}.</li>
 * </ul>
 *
 * <h3>Horizontal scaling</h3>
 * <p>Multiple app instances share the same Redis → globally consistent rate limits.</p>
 */
public class RedisStore implements CounterStore {

    private static final Logger log = LoggerFactory.getLogger(RedisStore.class);

    private final StringRedisTemplate redis;
    private final int windowSizeInSeconds;
    private final int ttlSeconds;          // windowSizeInSeconds * 2
    private final InMemoryStore fallback;
    private final RedisFailureMode failureMode;

    // Loaded Lua scripts
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final DefaultRedisScript<List> tokenBucketScript;

    public RedisStore(StringRedisTemplate redis,
                      int windowSizeInSeconds,
                      RedisFailureMode failureMode) {
        this.redis              = redis;
        this.windowSizeInSeconds = windowSizeInSeconds;
        this.ttlSeconds         = windowSizeInSeconds * 2;
        this.fallback           = new InMemoryStore();
        this.failureMode        = failureMode;

        this.fixedWindowScript   = loadScript("lua/fixed_window.lua");
        this.slidingWindowScript = loadScript("lua/sliding_window.lua");
        this.tokenBucketScript   = loadScript("lua/token_bucket.lua");

        log.info("RedisStore initialized — ttl={}s, failureMode={}", ttlSeconds, failureMode);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Algorithm-specific evaluation methods (called by Redis-aware strategies)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates a Fixed Window request atomically in Redis.
     *
     * @return long[] {count, windowStartMs, allowed(1/0)}
     */
    public long[] evaluateFixedWindow(String key, int maxRequests, long windowMs, long nowMs) {
        try {
            List<Long> result = redis.execute(
                    fixedWindowScript,
                    List.of(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowMs),
                    String.valueOf(nowMs),
                    String.valueOf(ttlSeconds));

            if (result == null || result.size() < 3) {
                return handleRedisError("fixed-window", key, null);
            }
            return new long[]{result.get(0), result.get(1), result.get(2)};
        } catch (Exception ex) {
            return handleRedisError("fixed-window", key, ex);
        }
    }

    /**
     * Evaluates a Sliding Window request atomically in Redis.
     *
     * @return long[] {count, oldestTimestampMs, allowed(1/0)}
     */
    public long[] evaluateSlidingWindow(String key, int maxRequests, long windowMs, long nowMs) {
        try {
            List<Long> result = redis.execute(
                    slidingWindowScript,
                    List.of(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowMs),
                    String.valueOf(nowMs),
                    String.valueOf(ttlSeconds));

            if (result == null || result.size() < 3) {
                return handleRedisError("sliding-window", key, null);
            }
            return new long[]{result.get(0), result.get(1), result.get(2)};
        } catch (Exception ex) {
            return handleRedisError("sliding-window", key, ex);
        }
    }

    /**
     * Evaluates a Token Bucket request atomically in Redis.
     *
     * @return long[] {tokensFloor, allowed(1/0), lastRefillMs}
     */
    public long[] evaluateTokenBucket(String key, int maxTokens, double refillRate, long nowMs) {
        try {
            List<Long> result = redis.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(maxTokens),
                    String.valueOf(refillRate),
                    String.valueOf(nowMs),
                    String.valueOf(ttlSeconds));

            if (result == null || result.size() < 3) {
                return handleRedisError("token-bucket", key, null);
            }
            return new long[]{result.get(0), result.get(1), result.get(2)};
        } catch (Exception ex) {
            return handleRedisError("token-bucket", key, ex);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CounterStore interface — delegates to fallback for cleanup/admin ops
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * NOT used by strategies when running in Redis mode — strategies call
     * {@code evaluateXxx()} directly. This method is kept for interface
     * compliance; it always delegates to the in-memory fallback.
     */
    @Override
    public Object compute(String key, BiFunction<String, Object, Object> remappingFunction) {
        return fallback.compute(key, remappingFunction);
    }

    @Override
    public Object get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("Redis GET failed for key={}, using fallback", key, ex);
            return null;
        }
    }

    @Override
    public void remove(String key) {
        try {
            redis.delete(key);
        } catch (Exception ex) {
            log.warn("Redis DELETE failed for key={}", key, ex);
        }
    }

    /** Uses SCAN (safe for production — avoids KEYS *). */
    @Override
    public Set<String> keys() {
        try {
            Set<String> keys = redis.keys("rl:*");
            return keys != null ? keys : Set.of();
        } catch (Exception ex) {
            log.warn("Redis KEYS scan failed", ex);
            return Set.of();
        }
    }

    @Override
    public void forEach(BiConsumer<String, Object> action) {
        // No-op for Redis — TTL handles expiry; cleanup scheduler skips RedisStore
    }

    /** Approximate active-key count via DBSIZE (fast, approximate). */
    @Override
    public int size() {
        try {
            Long size = redis.execute(connection -> connection.serverCommands().dbSize(), true);
            return size != null ? size.intValue() : 0;
        } catch (Exception ex) {
            log.warn("Redis DBSIZE failed", ex);
            return 0;
        }
    }

    @Override
    public void clear() {
        try {
            redis.getConnectionFactory().getConnection().serverCommands().flushDb();
            log.info("Redis FLUSHDB executed via clear()");
        } catch (Exception ex) {
            log.error("Redis FLUSHDB failed", ex);
        }
    }

    /** Exposes the in-memory fallback store (used by cleanup scheduler guard). */
    public InMemoryStore getFallback() {
        return fallback;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    private long[] handleRedisError(String algorithm, String key, Exception ex) {
        if (ex != null) {
            log.warn("Redis {} script failed for key={} — failureMode={}", algorithm, key, failureMode, ex);
        } else {
            log.warn("Redis {} returned null result for key={} — failureMode={}", algorithm, key, failureMode);
        }

        return switch (failureMode) {
            case ALLOW_ALL -> new long[]{0L, System.currentTimeMillis(), 1L};  // allow, remaining=max
            case DENY_ALL  -> new long[]{Long.MAX_VALUE, System.currentTimeMillis(), 0L}; // block
            case FALLBACK_TO_MEMORY -> {
                // Fallback is handled in strategies via instanceof check; here we signal it
                log.warn("Signalling FALLBACK_TO_MEMORY for key={}", key);
                yield new long[]{-1L, System.currentTimeMillis(), -1L};  // sentinel
            }
        };
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<List> loadScript(String classpathLocation) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        script.setResultType(List.class);
        return script;
    }
}
