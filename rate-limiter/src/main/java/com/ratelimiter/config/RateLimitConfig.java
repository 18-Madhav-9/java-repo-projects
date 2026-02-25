package com.ratelimiter.config;

import com.ratelimiter.identity.*;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.InMemoryStore;
import com.ratelimiter.store.RedisFailureMode;
import com.ratelimiter.store.RedisStore;
import com.ratelimiter.strategy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.AntPathMatcher;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * V3.1 rate limit configuration — config-driven store, algorithm, and identity
 * selection with wildcard-aware per-endpoint rule resolution.
 *
 * <h3>Key properties</h3>
 * <pre>
 * rate-limit.store=MEMORY             # MEMORY (default) | REDIS
 * rate-limit.redis-failure-mode=FALLBACK_TO_MEMORY  # ALLOW_ALL | DENY_ALL | FALLBACK_TO_MEMORY
 * rate-limit.algorithm=SLIDING_WINDOW
 * rate-limit.identity=IP
 * rate-limit.default-max-requests=20
 * rate-limit.window-size-in-seconds=60
 * rate-limit.endpoint-limits.GET\:/api/user=100
 * </pre>
 *
 * <h3>Intelligent store defaulting</h3>
 * <p>If {@code rate-limit.store} is not set and Redis connection properties are
 * detected (i.e. {@code spring.data.redis.host} is configured), the store
 * automatically defaults to {@code REDIS}. Otherwise, it defaults to {@code MEMORY}.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ---- configurable properties ----

    private String algorithm        = "SLIDING_WINDOW";
    private String identity         = "IP";
    private String store            = "AUTO";   // AUTO | MEMORY | REDIS
    private String redisFailureMode = "FALLBACK_TO_MEMORY";
    private int    defaultMaxRequests    = 20;
    private int    windowSizeInSeconds   = 60;
    private long   cleanupIntervalMs     = 300000;
    private Map<String, Integer> endpointLimits = new HashMap<>();

    /** Injected only when rate-limit.store=REDIS (via @ConditionalOnProperty in RedisConfig). */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // ---- lifecycle ----

    @PostConstruct
    void logConfig() {
        log.info("Rate Limit V3.1 Config — store={}, algorithm={}, identity={}, "
                + "defaultMax={}, window={}s, redisFailureMode={}, endpoints={}",
                resolveStoreType(), algorithm, identity,
                defaultMaxRequests, windowSizeInSeconds, redisFailureMode, endpointLimits);
    }

    // ---- bean factories ----

    /**
     * Creates the active {@link CounterStore} based on {@code rate-limit.store}.
     *
     * <p><strong>Intelligent defaulting:</strong> If {@code store=AUTO} (default),
     * selects Redis when a {@link StringRedisTemplate} is available on the context,
     * otherwise falls back to InMemory.</p>
     */
    @Bean
    public CounterStore counterStore() {
        String storeType = resolveStoreType();
        log.info("CounterStore selection: {}", storeType);

        if ("REDIS".equals(storeType)) {
            if (redisTemplate == null) {
                log.warn("store=REDIS requested but StringRedisTemplate is not available. "
                        + "Ensure rate-limit.store=REDIS and spring.data.redis.* are configured. "
                        + "Falling back to InMemoryStore.");
                return new InMemoryStore();
            }
            RedisFailureMode failureMode = parseFailureMode();
            log.info("Using RedisStore — ttl={}s, failureMode={}", windowSizeInSeconds * 2, failureMode);
            return new RedisStore(redisTemplate, windowSizeInSeconds, failureMode);
        }

        log.info("Using InMemoryStore");
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

    // ---- internal helpers ----

    /**
     * Resolves the effective store type.
     *
     * <ul>
     *   <li>AUTO → REDIS if StringRedisTemplate is available, else MEMORY</li>
     *   <li>REDIS → REDIS (explicit)</li>
     *   <li>MEMORY → MEMORY (explicit)</li>
     * </ul>
     */
    public String resolveStoreType() {
        if ("AUTO".equalsIgnoreCase(store)) {
            return (redisTemplate != null) ? "REDIS" : "MEMORY";
        }
        return store.toUpperCase();
    }

    private RedisFailureMode parseFailureMode() {
        try {
            return RedisFailureMode.valueOf(redisFailureMode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown redis-failure-mode='{}', defaulting to FALLBACK_TO_MEMORY", redisFailureMode);
            return RedisFailureMode.FALLBACK_TO_MEMORY;
        }
    }

    // ---- accessors ----

    public String getAlgorithm()                      { return algorithm; }
    public void   setAlgorithm(String algorithm)       { this.algorithm = algorithm; }

    public String getIdentity()                       { return identity; }
    public void   setIdentity(String identity)         { this.identity = identity; }

    public String getStore()                          { return store; }
    public void   setStore(String store)               { this.store = store; }

    public String getRedisFailureMode()               { return redisFailureMode; }
    public void   setRedisFailureMode(String mode)    { this.redisFailureMode = mode; }

    public int  getDefaultMaxRequests()               { return defaultMaxRequests; }
    public void setDefaultMaxRequests(int v)          { this.defaultMaxRequests = v; }

    public int  getWindowSizeInSeconds()              { return windowSizeInSeconds; }
    public void setWindowSizeInSeconds(int v)         { this.windowSizeInSeconds = v; }

    public long getCleanupIntervalMs()                { return cleanupIntervalMs; }
    public void setCleanupIntervalMs(long v)          { this.cleanupIntervalMs = v; }

    public Map<String, Integer> getEndpointLimits()  { return endpointLimits; }
    public void setEndpointLimits(Map<String, Integer> v) { this.endpointLimits = v; }
}
