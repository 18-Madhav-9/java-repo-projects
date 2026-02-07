package com.ratelimiter.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Resolves client identity from a JWT {@code Authorization: Bearer} token.
 *
 * <p><b>V3.2 stub</b> — extracts the {@code sub} claim from the JWT payload
 * without full signature verification. Falls back to IP if no valid token
 * is present.</p>
 *
 * <p>For production use, integrate with Spring Security's
 * {@code JwtDecoder} for proper validation.</p>
 */
public class JwtIdentityResolver implements IdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtIdentityResolver.class);
    private final IpIdentityResolver ipFallback = new IpIdentityResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    // Naive extraction — production should use a proper JWT library
                    int subIdx = payload.indexOf("\"sub\"");
                    if (subIdx >= 0) {
                        int valueStart = payload.indexOf('"', subIdx + 5) + 1;
                        int valueEnd = payload.indexOf('"', valueStart);
                        if (valueStart > 0 && valueEnd > valueStart) {
                            String subject = payload.substring(valueStart, valueEnd);
                            return "jwt:" + subject;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract JWT subject — falling back to IP", e);
            }
        }
        return ipFallback.resolve(request);
    }
}
