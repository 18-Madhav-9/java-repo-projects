package com.ratelimiter.scheduler;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.strategy.RateLimitStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CleanupSchedulerTest {

    private RateLimitStrategy strategy;
    private RateLimitConfig config;
    private CleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        strategy = mock(RateLimitStrategy.class);
        config = mock(RateLimitConfig.class);
        when(config.getWindowSizeInSeconds()).thenReturn(60);
        scheduler = new CleanupScheduler(strategy, config);
    }

    @Test
    @DisplayName("Scheduler calls strategy cleanup with correct window size")
    void shouldCallCleanupOnStrategy() {
        when(strategy.cleanupExpired(anyLong(), eq(60000L))).thenReturn(5);
        when(strategy.getActiveKeyCount()).thenReturn(10);

        scheduler.cleanupExpiredEntries();

        verify(strategy).cleanupExpired(anyLong(), eq(60000L));
        verify(strategy).getActiveKeyCount();
    }
}
