package com.ratelimiter.scheduler;

import com.ratelimiter.config.RateLimitConfig;
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
 */
@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final RateLimitStrategy strategy;
    private final RateLimitConfig config;

    public CleanupScheduler(RateLimitStrategy strategy, RateLimitConfig config) {
        this.strategy = strategy;
        this.config = config;
    }

    /**
     * Evicts stale entries from the store.
     * Interval is configured via {@code rate-limit.cleanup-interval-ms}.
     */
    @Scheduled(fixedDelayString = "${rate-limit.cleanup-interval-ms:300000}")
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        long windowMs = config.getWindowSizeInSeconds() * 1000L;

        int removed = strategy.cleanupExpired(now, windowMs);
        int remaining = strategy.getActiveKeyCount();

        if (removed > 0) {
            log.info("Cleanup: evicted {} expired entries, {} active keys remaining", removed, remaining);
        } else {
            log.debug("Cleanup: no expired entries found, {} active keys", remaining);
        }
    }
}
