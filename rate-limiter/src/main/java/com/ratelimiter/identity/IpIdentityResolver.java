package com.ratelimiter.identity;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves client identity by IP address, with {@code X-Forwarded-For} support
 * for reverse proxy environments (nginx, ALB, Cloudflare, etc.).
 *
 * <p>If {@code X-Forwarded-For} contains a comma-separated list, the
 * <em>leftmost</em> entry is the original client IP.</p>
 */
public class IpIdentityResolver implements IdentityResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
