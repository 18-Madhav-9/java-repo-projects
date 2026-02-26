package com.ratelimiter.scheduler;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.InMemoryStore;
import com.ratelimiter.store.RedisFailureMode;
import com.ratelimiter.store.RedisStore;
import com.ratelimiter.strategy.RateLimitStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CleanupSchedulerTest {

    private RateLimitStrategy strategy;
    private RateLimitConfig   config;

    @BeforeEach
    void setUp() {
        strategy = mock(RateLimitStrategy.class);
        config   = mock(RateLimitConfig.class);
        when(config.getWindowSizeInSeconds()).thenReturn(60);
    }

    @Test
    @DisplayName("Scheduler calls strategy cleanup with correct window size (InMemoryStore)")
    void shouldCallCleanupOnStrategy_inMemory() {
        CounterStore     inMemory  = new InMemoryStore();
        CleanupScheduler scheduler = new CleanupScheduler(strategy, config, inMemory);

        when(strategy.cleanupExpired(anyLong(), eq(60000L))).thenReturn(5);
        when(strategy.getActiveKeyCount()).thenReturn(10);

        scheduler.cleanupExpiredEntries();

        verify(strategy).cleanupExpired(anyLong(), eq(60000L));
        verify(strategy).getActiveKeyCount();
    }

    @Test
    @DisplayName("Scheduler is a no-op when store is RedisStore (TTL handles expiry)")
    void shouldSkipCleanup_whenRedisStore() {
        // Use a mocked StringRedisTemplate — RedisStore constructor won't call Redis here
        StringRedisTemplate mockRedis = mock(StringRedisTemplate.class);
        RedisStore redisStore = new RedisStore(mockRedis, 60, RedisFailureMode.FALLBACK_TO_MEMORY);
        CleanupScheduler scheduler = new CleanupScheduler(strategy, config, redisStore);

        scheduler.cleanupExpiredEntries();

        // Strategy.cleanupExpired should never be called — Redis TTL handles it
        verify(strategy, never()).cleanupExpired(anyLong(), anyLong());
        verify(strategy, never()).getActiveKeyCount();
    }
}
