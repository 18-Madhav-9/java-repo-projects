package com.ratelimiter.strategy;

import com.ratelimiter.model.ClientRequestInfo;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.RedisStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-window rate limiting strategy.
 *
 * <p>Divides time into fixed intervals and counts requests per window.
 * Simple, O(1), but allows burst at window boundaries (up to 2× limit
 * across a boundary).</p>
 *
 * <p>This is the V2 algorithm, ported into the V3 strategy interface.</p>
 *
 * <h3>Redis mode (V3.1)</h3>
 * <p>When the store is a {@link RedisStore}, the algorithm runs atomically
 * inside Redis via a Lua script — {@link RedisStore#evaluateFixedWindow}.</p>
 */
public class FixedWindowStrategy implements RateLimitStrategy {

    private final CounterStore store;

    public FixedWindowStrategy(CounterStore store) {
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

        long[] result = redisStore.evaluateFixedWindow(context.storageKey(), limit, windowMs, now);

        // Sentinel -1 → Redis is down, fall back to per-instance InMemory
        if (result[0] == -1L) {
            return evaluateInMemoryFallback(context, rule);
        }

        long count       = result[0];
        long windowStart = result[1];
        boolean allowed  = result[2] == 1L;

        long resetEpoch = (windowStart + windowMs) / 1000;

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

        final int[]  count       = {0};
        final long[] windowStart = {now};

        store.compute(context.storageKey(), (key, existing) -> {
            ClientRequestInfo info;
            if (existing == null || now - ((ClientRequestInfo) existing).getWindowStartTimestamp() >= windowMs) {
                info = new ClientRequestInfo(1, now);
            } else {
                info = (ClientRequestInfo) existing;
                info.setRequestCount(info.getRequestCount() + 1);
            }
            count[0]       = info.getRequestCount();
            windowStart[0] = info.getWindowStartTimestamp();
            return info;
        });

        long resetEpoch = (windowStart[0] + windowMs) / 1000;

        if (count[0] > limit) {
            long retryAfter = Math.max(1, resetEpoch - (now / 1000));
            return RateLimitResult.blocked(limit, resetEpoch, retryAfter);
        }

        int remaining = limit - count[0];
        return RateLimitResult.allowed(limit, remaining, resetEpoch);
    }

    /** Used when Redis is down and failureMode=FALLBACK_TO_MEMORY. */
    private RateLimitResult evaluateInMemoryFallback(RequestContext context, RateLimitRule rule) {
        if (store instanceof RedisStore redisStore) {
            // Temporarily delegate to the embedded InMemoryStore
            FixedWindowStrategy fallbackStrategy = new FixedWindowStrategy(redisStore.getFallback());
            return fallbackStrategy.evaluate(context, rule);
        }
        return evaluateInMemory(context, rule);
    }

    @Override
    public int cleanupExpired(long now, long windowSizeMs) {
        if (store instanceof RedisStore) {
            return 0; // Redis TTL handles expiry automatically
        }
        List<String> expired = new ArrayList<>();
        store.forEach((key, value) -> {
            ClientRequestInfo info = (ClientRequestInfo) value;
            if (now - info.getWindowStartTimestamp() >= windowSizeMs) {
                expired.add(key);
            }
        });
        expired.forEach(store::remove);
        return expired.size();
    }

    @Override
    public int getActiveKeyCount() {
        return store.size();
    }
}
