package com.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized rate limit configuration, bound from {@code application.properties}.
 *
 * <p>Defaults: 5 requests per 10-second fixed window.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    /** Maximum number of requests allowed within a single window. */
    private int maxRequests = 5;

    /** Duration of the fixed window in seconds. */
    private int windowSizeInSeconds = 10;

    // ---- accessors ----

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public int getWindowSizeInSeconds() {
        return windowSizeInSeconds;
    }

    public void setWindowSizeInSeconds(int windowSizeInSeconds) {
        this.windowSizeInSeconds = windowSizeInSeconds;
    }
}
