package com.ratelimiter.engine;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.metrics.RateLimitMetrics;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.model.RequestContext;
import com.ratelimiter.strategy.RateLimitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central orchestrator that replaces the V2 {@code RateLimiterService}.
 *
 * <p>Wires together the active {@link RateLimitStrategy}, the
 * {@link RateLimitConfig} for rule resolution, and {@link RateLimitMetrics}
 * for observability. The filter calls {@link #evaluate(RequestContext)}
 * for every incoming request.</p>
 */
@Service
public class RateLimiterEngine {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterEngine.class);

    private final RateLimitStrategy strategy;
    private final RateLimitConfig config;
    private final RateLimitMetrics metrics;

    public RateLimiterEngine(RateLimitStrategy strategy,
                             RateLimitConfig config,
                             RateLimitMetrics metrics) {
        this.strategy = strategy;
        this.config = config;
        this.metrics = metrics;
        log.info("RateLimiterEngine initialized — algorithm={}, identity={}",
                config.getAlgorithm(), config.getIdentity());
    }

    /**
     * Evaluates a request against the rate limit rules.
     *
     * @param context the fully-built request context
     * @return the rate limit decision with header data
     */
    public RateLimitResult evaluate(RequestContext context) {
        RateLimitRule rule = config.resolveRule(context.endpointKey());
        RateLimitResult result = strategy.evaluate(context, rule);

        // Record metrics
        if (result.allowed()) {
            metrics.recordAllowed();
        } else {
            metrics.recordBlocked();
            log.warn("Rate limit exceeded — identity={}, endpoint={}, limit={}",
                    context.clientIdentity(), context.endpointKey(), rule.maxRequests());
        }
        metrics.updateActiveKeys(strategy.getActiveKeyCount());

        return result;
    }

    /** Delegates to the active strategy. */
    public RateLimitStrategy getStrategy() {
        return strategy;
    }
}
