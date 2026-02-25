package com.ratelimiter.scheduler;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.RedisStore;
import com.ratelimiter.strategy.RateLimitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic cleanup of expired rate limit entries to prevent unbounded
 * memory growth in the {@link com.ratelimiter.store.CounterStore}.
 *
 * <p>Runs at a configurable interval (default: every 5 minutes) and
 * delegates to the active strategy's {@code cleanupExpired()} method.</p>
 *
 * <h3>Redis mode (V3.1)</h3>
 * <p>When the active store is a {@link RedisStore}, cleanup is a <strong>no-op</strong>
 * — Redis TTL (set to {@code windowSizeInSeconds × 2}) and the Lua scripts'
 * {@code ZREMRANGEBYSCORE} handle expiry automatically. No scheduler overhead needed.</p>
 */
@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final RateLimitStrategy strategy;
    private final RateLimitConfig   config;
    private final CounterStore      store;

    public CleanupScheduler(RateLimitStrategy strategy,
                            RateLimitConfig config,
                            CounterStore store) {
        this.strategy = strategy;
        this.config   = config;
        this.store    = store;
    }

    /**
     * Evicts stale entries from the store.
     * Interval is configured via {@code rate-limit.cleanup-interval-ms}.
     */
    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:300000}")
    public void cleanupExpiredEntries() {
        // Redis TTL + Lua scripts handle expiry — skip expensive Java-side cleanup
        if (store instanceof RedisStore) {
            log.debug("Cleanup skipped — RedisStore uses TTL-based expiry");
            return;
        }

        long now      = System.currentTimeMillis();
        long windowMs = config.getWindowSizeInSeconds() * 1000L;

        int removed   = strategy.cleanupExpired(now, windowMs);
        int remaining = strategy.getActiveKeyCount();

        if (removed > 0) {
            log.info("Cleanup: evicted {} expired entries, {} active keys remaining", removed, remaining);
        } else {
            log.debug("Cleanup: no expired entries found, {} active keys", remaining);
        }
    }
}
