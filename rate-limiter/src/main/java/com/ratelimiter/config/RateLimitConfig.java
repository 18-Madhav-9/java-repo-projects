package com.ratelimiter.config;

import com.ratelimiter.identity.*;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.InMemoryStore;
import com.ratelimiter.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * V3 rate limit configuration — config-driven algorithm and identity selection
 * with wildcard-aware per-endpoint rule resolution.
 *
 * <h3>Key properties</h3>
 * <pre>
 * rate-limit.algorithm=SLIDING_WINDOW
 * rate-limit.identity=IP
 * rate-limit.default-max-requests=20
 * rate-limit.window-size-in-seconds=60
 * rate-limit.endpoint-limits.GET\:/api/user=100
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ---- configurable properties ----

    private String algorithm = "SLIDING_WINDOW";
    private String identity = "IP";
    private int defaultMaxRequests = 20;
    private int windowSizeInSeconds = 60;
    private long cleanupIntervalMs = 300000;
    private Map<String, Integer> endpointLimits = new HashMap<>();

    // ---- lifecycle ----

    @PostConstruct
    void logConfig() {
        log.info("Rate Limit V3 Config — algorithm={}, identity={}, defaultMax={}, window={}s, endpoints={}",
                algorithm, identity, defaultMaxRequests, windowSizeInSeconds, endpointLimits);
    }

    // ---- bean factories ----

    @Bean
    public CounterStore counterStore() {
        return new InMemoryStore();
    }

    @Bean
    public RateLimitStrategy rateLimitStrategy(CounterStore store) {
        return switch (algorithm.toUpperCase()) {
            case "FIXED_WINDOW" -> {
                log.info("Using FixedWindowStrategy");
                yield new FixedWindowStrategy(store);
            }
            case "TOKEN_BUCKET" -> {
                log.info("Using TokenBucketStrategy");
                yield new TokenBucketStrategy(store);
            }
            default -> {
                log.info("Using SlidingWindowStrategy (default)");
                yield new SlidingWindowStrategy(store);
            }
        };
    }

    @Bean
    public IdentityResolver identityResolver() {
        return switch (identity.toUpperCase()) {
            case "API_KEY" -> {
                log.info("Using ApiKeyIdentityResolver");
                yield new ApiKeyIdentityResolver();
            }
            case "JWT" -> {
                log.info("Using JwtIdentityResolver");
                yield new JwtIdentityResolver();
            }
            default -> {
                log.info("Using IpIdentityResolver (default)");
                yield new IpIdentityResolver();
            }
        };
    }

    // ---- business methods ----

    /**
     * Resolves the rate limit rule for the given endpoint key.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match (e.g. {@code GET:/api/user})</li>
     *   <li>Wildcard match using Ant-style patterns (e.g. {@code GET:/api/user/**})</li>
     *   <li>Default fallback</li>
     * </ol>
     *
     * @param endpointKey the endpoint key in {@code METHOD:path} format
     * @return the resolved rule
     */
    public RateLimitRule resolveRule(String endpointKey) {
        // 1. Exact match (fast path)
        Integer exactLimit = endpointLimits.get(endpointKey);
        if (exactLimit != null) {
            return new RateLimitRule(endpointKey, exactLimit, windowSizeInSeconds);
        }

        // 2. Wildcard match
        for (Map.Entry<String, Integer> entry : endpointLimits.entrySet()) {
            if (pathMatcher.match(entry.getKey(), endpointKey)) {
                return new RateLimitRule(entry.getKey(), entry.getValue(), windowSizeInSeconds);
            }
        }

        // 3. Default fallback
        return new RateLimitRule("*", defaultMaxRequests, windowSizeInSeconds);
    }

    // ---- accessors ----

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public String getIdentity() { return identity; }
    public void setIdentity(String identity) { this.identity = identity; }

    public int getDefaultMaxRequests() { return defaultMaxRequests; }
    public void setDefaultMaxRequests(int defaultMaxRequests) { this.defaultMaxRequests = defaultMaxRequests; }

    public int getWindowSizeInSeconds() { return windowSizeInSeconds; }
    public void setWindowSizeInSeconds(int windowSizeInSeconds) { this.windowSizeInSeconds = windowSizeInSeconds; }

    public long getCleanupIntervalMs() { return cleanupIntervalMs; }
    public void setCleanupIntervalMs(long cleanupIntervalMs) { this.cleanupIntervalMs = cleanupIntervalMs; }

    public Map<String, Integer> getEndpointLimits() { return endpointLimits; }
    public void setEndpointLimits(Map<String, Integer> endpointLimits) { this.endpointLimits = endpointLimits; }
}
