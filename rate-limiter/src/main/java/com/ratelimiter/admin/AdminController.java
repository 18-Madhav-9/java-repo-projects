package com.ratelimiter.admin;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.metrics.RateLimitMetrics;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.RedisStore;
import com.ratelimiter.strategy.RateLimitStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin REST API for runtime visibility into the rate limiter.
 *
 * <p>These endpoints are excluded from rate limiting by the filter.</p>
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Rate limiter admin & observability endpoints")
public class AdminController {

    private final RateLimitMetrics metrics;
    private final RateLimitStrategy strategy;
    private final RateLimitConfig config;
    private final CounterStore store;

    public AdminController(RateLimitMetrics metrics,
                           RateLimitStrategy strategy,
                           RateLimitConfig config,
                           CounterStore store) {
        this.metrics  = metrics;
        this.strategy = strategy;
        this.config   = config;
        this.store    = store;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get live stats", description = "Returns allowed/blocked counts, active clients, algorithm, and uptime")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("algorithm",     config.getAlgorithm());
        stats.put("identity",      config.getIdentity());
        stats.put("storeType",     config.resolveStoreType());
        stats.put("activeClients", metrics.getActiveKeysCount());
        stats.put("totalAllowed",  (long) metrics.getTotalAllowed());
        stats.put("totalBlocked",  (long) metrics.getTotalBlocked());
        stats.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/active-keys")
    @Operation(summary = "List active keys", description = "Returns the number of currently tracked rate limit keys")
    public ResponseEntity<Map<String, Object>> getActiveKeys() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeKeyCount", strategy.getActiveKeyCount());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    @Operation(summary = "View current config", description = "Returns the full rate limit configuration")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("algorithm",          config.getAlgorithm());
        cfg.put("identity",           config.getIdentity());
        cfg.put("store",              config.resolveStoreType());
        cfg.put("redisFailureMode",   config.getRedisFailureMode());
        cfg.put("defaultMaxRequests", config.getDefaultMaxRequests());
        cfg.put("windowSizeInSeconds", config.getWindowSizeInSeconds());
        cfg.put("cleanupIntervalMs",  config.getCleanupIntervalMs());
        cfg.put("endpointLimits",     config.getEndpointLimits());
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/store")
    @Operation(summary = "View store info",
               description = "Reports the active CounterStore type, TTL configuration, and Redis connection details (when applicable)")
    public ResponseEntity<Map<String, Object>> getStoreInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        String storeType = config.resolveStoreType();
        info.put("storeType", storeType);

        if (store instanceof RedisStore) {
            info.put("ttlSeconds",      config.getWindowSizeInSeconds() * 2);
            info.put("redisFailureMode", config.getRedisFailureMode());
            info.put("note",            "TTL = windowSizeInSeconds × 2; Lua scripts enforce atomicity");
        } else {
            info.put("note",            "InMemoryStore — ephemeral, single-instance, no TTL (cleanup via scheduler)");
            info.put("cleanupIntervalMs", config.getCleanupIntervalMs());
        }

        info.put("activeKeys", strategy.getActiveKeyCount());
        return ResponseEntity.ok(info);
    }
}
