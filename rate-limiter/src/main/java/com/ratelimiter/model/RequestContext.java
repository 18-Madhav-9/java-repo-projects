package com.ratelimiter.model;

/**
 * Immutable context for a single rate-limited request.
 *
 * <p>Built by the filter from the incoming {@code HttpServletRequest}
 * and the resolved client identity. Passed through the engine to the
 * active strategy.</p>
 *
 * @param clientIdentity resolved identity (IP, API key, JWT subject)
 * @param method         HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param path           request URI (e.g. {@code /api/user})
 * @param endpointKey    composite key for config lookup: {@code METHOD:path}
 * @param storageKey     composite key for storage: {@code identity:METHOD:path}
 * @param timestamp      epoch milliseconds when the request arrived
 */
public record RequestContext(
        String clientIdentity,
        String method,
        String path,
        String endpointKey,
        String storageKey,
        long timestamp
) {

    /**
     * Factory method that builds a fully-keyed context.
     */
    public static RequestContext of(String identity, String method, String path, long timestamp) {
        String endpointKey = method + ":" + path;
        String storageKey = identity + ":" + endpointKey;
        return new RequestContext(identity, method, path, endpointKey, storageKey, timestamp);
    }
}
