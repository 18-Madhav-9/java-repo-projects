package com.ratelimiter.filter;

import com.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that intercepts every incoming HTTP request and applies
 * IP-based rate limiting <em>before</em> the request reaches any controller.
 *
 * <p>Extends {@link OncePerRequestFilter} to guarantee a single execution
 * per request even in async / forward scenarios.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_RESPONSE =
            "{\"error\":\"Too many requests. Please try again later.\"}";

    private final RateLimiterService rateLimiterService;

    public RateLimitFilter(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);

        if (!rateLimiterService.isAllowed(clientIp)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(RATE_LIMIT_RESPONSE);
            return; // short-circuit — do NOT continue the filter chain
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the real client IP, respecting the {@code X-Forwarded-For} header
     * that reverse proxies (nginx, ALB, Cloudflare, etc.) typically set.
     *
     * <p>If the header contains a comma-separated list, the <em>first</em> entry
     * is the original client IP.</p>
     *
     * @param request the incoming HTTP request
     * @return the resolved client IP address
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // "clientIp, proxy1, proxy2" → take the leftmost entry
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
