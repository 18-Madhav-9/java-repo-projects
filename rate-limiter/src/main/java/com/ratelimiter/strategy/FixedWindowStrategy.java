package com.ratelimiter.strategy;

import com.ratelimiter.model.ClientRequestInfo;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.CounterStore;

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
 */
public class FixedWindowStrategy implements RateLimitStrategy {

    private final CounterStore store;

    public FixedWindowStrategy(CounterStore store) {
        this.store = store;
    }

    @Override
    public RateLimitResult evaluate(RequestContext context, RateLimitRule rule) {
        long now = context.timestamp();
        long windowMs = rule.windowSizeSeconds() * 1000L;
        int limit = rule.maxRequests();

        final int[] count = {0};
        final long[] windowStart = {now};

        store.compute(context.storageKey(), (key, existing) -> {
            ClientRequestInfo info;
            if (existing == null || now - ((ClientRequestInfo) existing).getWindowStartTimestamp() >= windowMs) {
                info = new ClientRequestInfo(1, now);
            } else {
                info = (ClientRequestInfo) existing;
                info.setRequestCount(info.getRequestCount() + 1);
            }
            count[0] = info.getRequestCount();
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

    @Override
    public int cleanupExpired(long now, long windowSizeMs) {
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
