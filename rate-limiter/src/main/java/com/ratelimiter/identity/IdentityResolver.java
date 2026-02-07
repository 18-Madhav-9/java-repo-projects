package com.ratelimiter.identity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving the client identity from an HTTP request.
 *
 * <p>The resolved identity becomes part of the composite storage key
 * ({@code identity:METHOD:path}), determining <em>who</em> is rate-limited.</p>
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link IpIdentityResolver} — V3.0 (IP address, X-Forwarded-For aware)</li>
 *   <li>{@link ApiKeyIdentityResolver} — V3.1 stub (X-API-Key header)</li>
 *   <li>{@link JwtIdentityResolver} — V3.2 stub (Authorization Bearer token)</li>
 * </ul>
 */
public interface IdentityResolver {

    /**
     * Resolves the client identity from the incoming request.
     *
     * @param request the HTTP servlet request
     * @return a non-null string identifying the client
     */
    String resolve(HttpServletRequest request);
}
