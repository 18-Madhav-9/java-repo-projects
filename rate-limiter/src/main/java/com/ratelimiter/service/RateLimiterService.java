package com.ratelimiter.service;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.model.ClientRequestInfo;
import com.ratelimiter.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core rate limiting logic using the <b>fixed-window</b> algorithm.
 *
 * <p>For every incoming request the service:
 * <ol>
 *   <li>Atomically increments the counter for the client IP in the current window.</li>
 *   <li>Returns {@code true} if the request is within the configured limit,
 *       or {@code false} if the limit has been exceeded.</li>
 * </ol>
 *
 * <p>Thread safety is delegated to {@link InMemoryStore#incrementAndGet}.</p>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final InMemoryStore store;
    private final RateLimitConfig config;

    public RateLimiterService(InMemoryStore store, RateLimitConfig config) {
        this.store = store;
        this.config = config;
    }

    /**
     * Determines whether the request from the given IP should be allowed.
     *
     * @param clientIp the client's IP address
     * @return {@code true} if the request is within limits; {@code false} otherwise
     */
    public boolean isAllowed(String clientIp) {
        long now = System.currentTimeMillis();
        long windowSizeMillis = config.getWindowSizeInSeconds() * 1000L;

        ClientRequestInfo info = store.incrementAndGet(clientIp, now, windowSizeMillis);

        if (info.getRequestCount() > config.getMaxRequests()) {
            log.warn("Rate limit exceeded for IP: {} — count={}, limit={}",
                    clientIp, info.getRequestCount(), config.getMaxRequests());
            return false;
        }

        return true;
    }
}
