package com.ratelimiter.store;

import com.ratelimiter.model.ClientRequestInfo;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory storage for per-IP rate limit state.
 *
 * <p>Backed by a {@link ConcurrentHashMap}; all mutations happen through
 * {@code compute()}, which guarantees atomicity per key without external locks.</p>
 *
 * <p>State is intentionally ephemeral — a restart resets all counters,
 * which is acceptable for the V1 MVP.</p>
 */
@Component
public class InMemoryStore {

    private final ConcurrentHashMap<String, ClientRequestInfo> clientMap = new ConcurrentHashMap<>();

    /**
     * Atomically increments the request count for the given client IP
     * within the current window, resetting the window if it has expired.
     *
     * @param clientIp          the client's IP address
     * @param currentTimeMillis the current epoch time in milliseconds
     * @param windowSizeMillis  the configured window duration in milliseconds
     * @return the updated {@link ClientRequestInfo} (count already incremented)
     */
    public ClientRequestInfo incrementAndGet(String clientIp, long currentTimeMillis, long windowSizeMillis) {
        return clientMap.compute(clientIp, (key, existing) -> {
            if (existing == null || currentTimeMillis - existing.getWindowStartTimestamp() >= windowSizeMillis) {
                // First request ever, or window has expired → start a new window
                return new ClientRequestInfo(1, currentTimeMillis);
            }
            // Same window — bump the counter
            existing.setRequestCount(existing.getRequestCount() + 1);
            return existing;
        });
    }

    /** Visible for testing — returns current map size. */
    public int size() {
        return clientMap.size();
    }

    /** Visible for testing — clears all entries. */
    public void clear() {
        clientMap.clear();
    }
}
