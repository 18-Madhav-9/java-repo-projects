package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.RedisStore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Sliding-window log rate limiting strategy — the <b>flagship V3 algorithm</b>.
 *
 * <p>Maintains a {@link Deque} of request timestamps per storage key.
 * On each evaluation, expired timestamps are evicted, and the remaining
 * count is checked against the limit.</p>
 *
 * <h3>Why this beats fixed window</h3>
 * <p>Fixed window allows up to 2× the limit across a window boundary
 * (e.g. 100 requests at t=59s and 100 at t=60s). Sliding window counts
 * over a <em>rolling</em> interval, eliminating the burst-at-boundary problem.</p>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>Time: O(k) per evaluation where k = expired timestamps to evict (amortized O(1))</li>
 *   <li>Space: O(n) where n = requests in the current window (~8 bytes per timestamp)</li>
 * </ul>
 *
 * <h3>Redis mode (V3.1)</h3>
 * <p>When the store is a {@link RedisStore}, uses a Redis Sorted Set via a Lua script
 * — {@link RedisStore#evaluateSlidingWindow}. Scores = timestamps, members = unique IDs.</p>
 */
public class SlidingWindowStrategy implements RateLimitStrategy {

    private final CounterStore store;

    public SlidingWindowStrategy(CounterStore store) {
        this.store = store;
    }

    @Override
    public RateLimitResult evaluate(RequestContext context, RateLimitRule rule) {
        // ── Redis path ────────────────────────────────────────────────────────
        if (store instanceof RedisStore redisStore) {
            return evaluateWithRedis(redisStore, context, rule);
        }
        // ── InMemory path ─────────────────────────────────────────────────────
        return evaluateInMemory(context, rule);
    }

    private RateLimitResult evaluateWithRedis(RedisStore redisStore,
                                               RequestContext context,
                                               RateLimitRule rule) {
        long now      = context.timestamp();
        long windowMs = rule.windowSizeSeconds() * 1000L;
        int  limit    = rule.maxRequests();

        long[] result = redisStore.evaluateSlidingWindow(context.storageKey(), limit, windowMs, now);

        // Sentinel -1 → Redis is down, fall back to per-instance InMemory
        if (result[0] == -1L) {
            return evaluateInMemoryFallback(context, rule);
        }

        long count   = result[0];
        long oldest  = result[1];   // oldest in-window timestamp ms
        boolean allowed = result[2] == 1L;

        long resetEpoch = (oldest + windowMs) / 1000;

        if (!allowed) {
            long retryAfter = Math.max(1, resetEpoch - (now / 1000));
            return RateLimitResult.blocked(limit, resetEpoch, retryAfter);
        }

        int remaining = (int) Math.max(0, limit - count);
        return RateLimitResult.allowed(limit, remaining, resetEpoch);
    }

    private RateLimitResult evaluateInMemory(RequestContext context, RateLimitRule rule) {
        long now      = context.timestamp();
        long windowMs = rule.windowSizeSeconds() * 1000L;
        int  limit    = rule.maxRequests();

        final boolean[] allowed         = {false};
        final int[]     currentCount    = {0};
        final long[]    oldestTimestamp = {now};

        store.compute(context.storageKey(), (key, existing) -> {
            SlidingWindowData data;
            if (existing == null) {
                data = new SlidingWindowData();
            } else {
                data = (SlidingWindowData) existing;
            }

            // Evict timestamps outside the sliding window
            while (!data.timestamps.isEmpty() && data.timestamps.peekFirst() <= now - windowMs) {
                data.timestamps.pollFirst();
            }

            // Check if under limit
            if (data.timestamps.size() < limit) {
                data.timestamps.addLast(now);
                allowed[0] = true;
            }

            currentCount[0] = data.timestamps.size();
            if (!data.timestamps.isEmpty()) {
                oldestTimestamp[0] = data.timestamps.peekFirst();
            }

            return data;
        });

        // The window "resets" when the oldest tracked request falls out of the window
        long resetEpoch = (oldestTimestamp[0] + windowMs) / 1000;

        if (!allowed[0]) {
            long retryAfter = Math.max(1, resetEpoch - (now / 1000));
            return RateLimitResult.blocked(limit, resetEpoch, retryAfter);
        }

        int remaining = Math.max(0, limit - currentCount[0]);
        return RateLimitResult.allowed(limit, remaining, resetEpoch);
    }

    /** Used when Redis is down and failureMode=FALLBACK_TO_MEMORY. */
    private RateLimitResult evaluateInMemoryFallback(RequestContext context, RateLimitRule rule) {
        if (store instanceof RedisStore redisStore) {
            SlidingWindowStrategy fallbackStrategy = new SlidingWindowStrategy(redisStore.getFallback());
            return fallbackStrategy.evaluate(context, rule);
        }
        return evaluateInMemory(context, rule);
    }

    @Override
    public int cleanupExpired(long now, long windowSizeMs) {
        if (store instanceof RedisStore) {
            return 0; // Redis TTL + ZREMRANGEBYSCORE in Lua script handle expiry
        }
        List<String> emptyKeys = new ArrayList<>();
        store.forEach((key, value) -> {
            SlidingWindowData data = (SlidingWindowData) value;
            // Evict old timestamps
            while (!data.timestamps.isEmpty() && data.timestamps.peekFirst() <= now - windowSizeMs) {
                data.timestamps.pollFirst();
            }
            // If nothing remains, the key is stale
            if (data.timestamps.isEmpty()) {
                emptyKeys.add(key);
            }
        });
        emptyKeys.forEach(store::remove);
        return emptyKeys.size();
    }

    @Override
    public int getActiveKeyCount() {
        return store.size();
    }

    // ---- Internal data model ----

    /**
     * Holds the deque of request timestamps for a single storage key.
     * Package-private — only used by this strategy.
     */
    static class SlidingWindowData {
        final Deque<Long> timestamps = new ArrayDeque<>();
    }
}
