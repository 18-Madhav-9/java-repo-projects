package com.ratelimiter.admin;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.metrics.RateLimitMetrics;
import com.ratelimiter.strategy.RateLimitStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private RateLimitMetrics metrics;
    private RateLimitStrategy strategy;
    private RateLimitConfig config;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        metrics = mock(RateLimitMetrics.class);
        strategy = mock(RateLimitStrategy.class);
        config = mock(RateLimitConfig.class);
        controller = new AdminController(metrics, strategy, config);
    }

    @Test
    @DisplayName("Stats endpoint returns live counts")
    void shouldReturnStats() {
        when(config.getAlgorithm()).thenReturn("TOKEN_BUCKET");
        when(config.getIdentity()).thenReturn("IP");
        when(metrics.getActiveKeysCount()).thenReturn(42);
        when(metrics.getTotalAllowed()).thenReturn(100.0);
        when(metrics.getTotalBlocked()).thenReturn(5.0);

        ResponseEntity<Map<String, Object>> response = controller.getStats();
        Map<String, Object> body = response.getBody();

        assertEquals("TOKEN_BUCKET", body.get("algorithm"));
        assertEquals("IP", body.get("identity"));
        assertEquals(42, body.get("activeClients"));
        assertEquals(100L, body.get("totalAllowed"));
        assertEquals(5L, body.get("totalBlocked"));
        assertTrue(body.containsKey("uptimeSeconds"));
    }

    @Test
    @DisplayName("Active keys endpoint returns current count")
    void shouldReturnActiveKeys() {
        when(strategy.getActiveKeyCount()).thenReturn(15);
        ResponseEntity<Map<String, Object>> response = controller.getActiveKeys();
        assertEquals(15, response.getBody().get("activeKeyCount"));
    }
}
