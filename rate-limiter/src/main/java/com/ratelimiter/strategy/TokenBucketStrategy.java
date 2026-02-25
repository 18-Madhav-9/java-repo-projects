package com.ratelimiter.strategy;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.RedisStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-bucket rate limiting strategy with <b>lazy refill</b>.
 *
 * <p>Each storage key has a bucket that starts full (tokens = maxRequests).
 * Each request consumes one token. Tokens are refilled at a steady rate
 * proportional to elapsed time — calculated on demand, not via a
 * background thread.</p>
 *
 * <h3>Why token bucket</h3>
 * <p>Allows controlled bursts (up to the bucket capacity) while enforcing
 * a long-term average rate. Ideal for APIs that tolerate short spikes
 * but need sustained throughput control.</p>
 *
 * <h3>Refill rate</h3>
 * <pre>
 * refillRate = maxRequests / windowSizeSeconds   (tokens per second)
 * tokensToAdd = elapsed_seconds × refillRate     (capped at maxRequests)
 * </pre>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>Time: O(1) per evaluation</li>
 *   <li>Space: O(1) per key (two fields: tokens + lastRefill)</li>
 * </ul>
 *
 * <h3>Redis mode (V3.1)</h3>
 * <p>When the store is a {@link RedisStore}, uses a Redis hash with lazy refill
 * computed in a Lua script — {@link RedisStore#evaluateTokenBucket}.</p>
 */
public class TokenBucketStrategy implements RateLimitStrategy {

    private final CounterStore store;

    public TokenBucketStrategy(CounterStore store) {
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
        long now       = context.timestamp();
        int  maxTokens = rule.maxRequests();
        double refillRate = (double) maxTokens / rule.windowSizeSeconds();

        long[] result = redisStore.evaluateTokenBucket(context.storageKey(), maxTokens, refillRate, now);

        // Sentinel -1 → Redis is down, fall back to per-instance InMemory
        if (result[0] == -1L && result[1] == -1L) {
            return evaluateInMemoryFallback(context, rule);
        }

        int  remaining   = (int) result[0];
        boolean allowed  = result[1] == 1L;
        long resetEpoch;
        if (remaining > 0) {
            resetEpoch = (now / 1000) + (long) Math.ceil(1.0 / refillRate);
        } else {
            resetEpoch = (now / 1000) + (long) Math.ceil(1.0 / refillRate);
        }

        if (!allowed) {
            long retryAfter = Math.max(1, (long) Math.ceil(1.0 / refillRate));
            return RateLimitResult.blocked(maxTokens, resetEpoch, retryAfter);
        }
        return RateLimitResult.allowed(maxTokens, remaining, resetEpoch);
    }

    private RateLimitResult evaluateInMemory(RequestContext context, RateLimitRule rule) {
        long now      = context.timestamp();
        int  maxTokens = rule.maxRequests();
        double refillRate = (double) maxTokens / rule.windowSizeSeconds(); // tokens/sec

        final boolean[] allowed    = {false};
        final double[]  tokensAfter = {0};

        store.compute(context.storageKey(), (key, existing) -> {
            TokenBucketData bucket;
            if (existing == null) {
                // First request: start with a full bucket minus this request
                bucket = new TokenBucketData(maxTokens - 1.0, now);
                allowed[0]    = true;
                tokensAfter[0] = bucket.availableTokens;
                return bucket;
            }

            bucket = (TokenBucketData) existing;

            // Lazy refill: calculate how many tokens to add based on elapsed time
            double elapsedSec = (now - bucket.lastRefillTimestamp) / 1000.0;
            double refilled   = bucket.availableTokens + (elapsedSec * refillRate);
            bucket.availableTokens    = Math.min(maxTokens, refilled); // cap at max
            bucket.lastRefillTimestamp = now;

            // Try to consume one token
            if (bucket.availableTokens >= 1.0) {
                bucket.availableTokens -= 1.0;
                allowed[0] = true;
            }

            tokensAfter[0] = bucket.availableTokens;
            return bucket;
        });

        int remaining = (int) Math.floor(tokensAfter[0]);
        // Time until at least one token is available
        long resetEpoch;
        if (remaining > 0) {
            // Next token arrives in (1/refillRate) seconds
            resetEpoch = (now / 1000) + (long) Math.ceil(1.0 / refillRate);
        } else {
            // Time until one token refills
            double secsUntilToken = (1.0 - (tokensAfter[0] % 1.0)) / refillRate;
            resetEpoch = (now / 1000) + (long) Math.ceil(secsUntilToken);
        }

        if (!allowed[0]) {
            long retryAfter = Math.max(1, (long) Math.ceil(1.0 / refillRate));
            return RateLimitResult.blocked(maxTokens, resetEpoch, retryAfter);
        }

        return RateLimitResult.allowed(maxTokens, remaining, resetEpoch);
    }

    /** Used when Redis is down and failureMode=FALLBACK_TO_MEMORY. */
    private RateLimitResult evaluateInMemoryFallback(RequestContext context, RateLimitRule rule) {
        if (store instanceof RedisStore redisStore) {
            TokenBucketStrategy fallbackStrategy = new TokenBucketStrategy(redisStore.getFallback());
            return fallbackStrategy.evaluate(context, rule);
        }
        return evaluateInMemory(context, rule);
    }

    @Override
    public int cleanupExpired(long now, long windowSizeMs) {
        if (store instanceof RedisStore) {
            return 0; // Redis TTL handles expiry automatically
        }
        List<String> stale = new ArrayList<>();
        store.forEach((key, value) -> {
            TokenBucketData bucket = (TokenBucketData) value;
            // If the bucket has been idle for 2× the window, it's stale
            if (now - bucket.lastRefillTimestamp > windowSizeMs * 2) {
                stale.add(key);
            }
        });
        stale.forEach(store::remove);
        return stale.size();
    }

    @Override
    public int getActiveKeyCount() {
        return store.size();
    }

    // ---- Internal data model ----

    /**
     * Holds the bucket state for a single storage key.
     * Package-private — only used by this strategy.
     */
    static class TokenBucketData {
        double availableTokens;
        long lastRefillTimestamp;

        TokenBucketData(double tokens, long timestamp) {
            this.availableTokens    = tokens;
            this.lastRefillTimestamp = timestamp;
        }
    }
}
