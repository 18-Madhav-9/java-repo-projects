package com.ratelimiter.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves client identity from the {@code X-API-Key} request header.
 *
 * <p><b>V3.1 stub</b> — falls back to IP if no API key is present.</p>
 */
public class ApiKeyIdentityResolver implements IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyIdentityResolver.class);
    private final IpIdentityResolver ipFallback = new IpIdentityResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey.trim();
        }
        log.debug("No X-API-Key header — falling back to IP identity");
        return ipFallback.resolve(request);
    }
}
