package com.ratelimiter.filter;

import com.ratelimiter.engine.RateLimiterEngine;
import com.ratelimiter.identity.IdentityResolver;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * V3 servlet filter — intercepts HTTP requests, builds a {@link RequestContext},
 * delegates to the {@link RateLimiterEngine}, and sets standard rate limit
 * response headers on every response.
 *
 * <p>Swagger UI, OpenAPI spec, Actuator, Admin, and static resource paths
 * are excluded from rate limiting.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_RESPONSE =
            "{\"error\":\"Too many requests. Please try again later.\"}";

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/swagger-ui",
            "/v3/api-docs",
            "/swagger-resources",
            "/webjars",
            "/css",
            "/favicon.ico",
            "/actuator",
            "/admin"
    );

    private final RateLimiterEngine engine;
    private final IdentityResolver identityResolver;

    public RateLimitFilter(RateLimiterEngine engine, IdentityResolver identityResolver) {
        this.engine = engine;
        this.identityResolver = identityResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Skip rate limiting for docs, admin, actuator, and static resources
        if (isExcluded(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Build context
        String identity = identityResolver.resolve(request);
        String method = request.getMethod();
        RequestContext context = RequestContext.of(identity, method, uri, System.currentTimeMillis());

        // Evaluate
        RateLimitResult result = engine.evaluate(context);

        // Always set rate limit headers (both allowed and blocked)
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpoch()));

        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSec()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(RATE_LIMIT_RESPONSE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(String uri) {
        if ("/".equals(uri)) {
            return true;
        }
        for (String prefix : EXCLUDED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
