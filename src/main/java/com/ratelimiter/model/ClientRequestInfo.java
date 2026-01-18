package com.ratelimiter.model;

/**
 * Mutable tracking state for a single client IP within the current fixed window.
 *
 * <p>Instances are stored inside a {@link java.util.concurrent.ConcurrentHashMap}
 * and mutated under the map's per-key compute lock, so no additional
 * synchronization is needed on this class itself.</p>
 */
public class ClientRequestInfo {

    private int requestCount;
    private long windowStartTimestamp;

    public ClientRequestInfo(int requestCount, long windowStartTimestamp) {
        this.requestCount = requestCount;
        this.windowStartTimestamp = windowStartTimestamp;
    }

    // ---- accessors ----

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public long getWindowStartTimestamp() {
        return windowStartTimestamp;
    }

    public void setWindowStartTimestamp(long windowStartTimestamp) {
        this.windowStartTimestamp = windowStartTimestamp;
    }
}
