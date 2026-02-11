package com.ratelimiter.admin;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.metrics.RateLimitMetrics;
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

    public AdminController(RateLimitMetrics metrics,
                           RateLimitStrategy strategy,
                           RateLimitConfig config) {
        this.metrics = metrics;
        this.strategy = strategy;
        this.config = config;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get live stats", description = "Returns allowed/blocked counts, active clients, algorithm, and uptime")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("algorithm", config.getAlgorithm());
        stats.put("identity", config.getIdentity());
        stats.put("activeClients", metrics.getActiveKeysCount());
        stats.put("totalAllowed", (long) metrics.getTotalAllowed());
        stats.put("totalBlocked", (long) metrics.getTotalBlocked());
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
        cfg.put("algorithm", config.getAlgorithm());
        cfg.put("identity", config.getIdentity());
        cfg.put("defaultMaxRequests", config.getDefaultMaxRequests());
        cfg.put("windowSizeInSeconds", config.getWindowSizeInSeconds());
        cfg.put("cleanupIntervalMs", config.getCleanupIntervalMs());
        cfg.put("endpointLimits", config.getEndpointLimits());
        return ResponseEntity.ok(cfg);
    }
}
