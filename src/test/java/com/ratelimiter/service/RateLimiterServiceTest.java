package com.ratelimiter.service;

import com.ratelimiter.config.RateLimitConfig;
import com.ratelimiter.store.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RateLimiterService}.
 *
 * <p>Uses a real {@link InMemoryStore} (no mocking) because the store is a
 * lightweight in-memory structure and the tests are fast and deterministic.</p>
 */
class RateLimiterServiceTest {

    private RateLimiterService service;
    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();

        RateLimitConfig config = new RateLimitConfig();
        config.setMaxRequests(5);
        config.setWindowSizeInSeconds(10);

        service = new RateLimiterService(store, config);
    }

    @Test
    @DisplayName("Requests within the limit should be allowed")
    void shouldAllowRequestsWithinLimit() {
        String ip = "192.168.1.1";
        for (int i = 0; i < 5; i++) {
            assertTrue(service.isAllowed(ip), "Request #" + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Requests exceeding the limit should be blocked")
    void shouldBlockRequestsExceedingLimit() {
        String ip = "192.168.1.2";

        // Exhaust the quota
        for (int i = 0; i < 5; i++) {
            assertTrue(service.isAllowed(ip));
        }

        // 6th request must be rejected
        assertFalse(service.isAllowed(ip), "6th request should be blocked");
        assertFalse(service.isAllowed(ip), "7th request should also be blocked");
    }

    @Test
    @DisplayName("Different IPs should have independent counters")
    void shouldTrackIpsIndependently() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust quota for ip1
        for (int i = 0; i < 5; i++) {
            service.isAllowed(ip1);
        }
        assertFalse(service.isAllowed(ip1));

        // ip2 should still be allowed
        assertTrue(service.isAllowed(ip2), "Different IP should be unaffected");
    }

    @Test
    @DisplayName("Counter resets after window expires")
    void shouldResetAfterWindowExpires() throws InterruptedException {
        // Use a very short window for this test
        RateLimitConfig shortConfig = new RateLimitConfig();
        shortConfig.setMaxRequests(2);
        shortConfig.setWindowSizeInSeconds(1); // 1-second window

        InMemoryStore freshStore = new InMemoryStore();
        RateLimiterService shortService = new RateLimiterService(freshStore, shortConfig);

        String ip = "172.16.0.1";

        assertTrue(shortService.isAllowed(ip));
        assertTrue(shortService.isAllowed(ip));
        assertFalse(shortService.isAllowed(ip), "3rd request within window should be blocked");

        // Wait for the window to expire
        Thread.sleep(1100);

        assertTrue(shortService.isAllowed(ip), "Request after window reset should be allowed");
    }

    @Test
    @DisplayName("Concurrent requests should not corrupt state")
    void shouldHandleConcurrentRequests() throws InterruptedException {
        RateLimitConfig config = new RateLimitConfig();
        config.setMaxRequests(100);
        config.setWindowSizeInSeconds(60);

        InMemoryStore concurrentStore = new InMemoryStore();
        RateLimiterService concurrentService = new RateLimiterService(concurrentStore, config);

        String ip = "192.168.100.1";
        int threadCount = 10;
        int requestsPerThread = 10;
        Thread[] threads = new Thread[threadCount];
        int[] allowedCount = {0};
        Object lock = new Object();

        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                int localAllowed = 0;
                for (int r = 0; r < requestsPerThread; r++) {
                    if (concurrentService.isAllowed(ip)) {
                        localAllowed++;
                    }
                }
                synchronized (lock) {
                    allowedCount[0] += localAllowed;
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Exactly 100 requests should be allowed (maxRequests = 100, total sent = 100)
        assertEquals(100, allowedCount[0],
                "All 100 requests should be allowed — concurrency must not cause miscounts");
    }
}
