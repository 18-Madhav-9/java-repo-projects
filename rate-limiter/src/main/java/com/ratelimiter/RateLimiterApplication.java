package com.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Rate Limiter V3 Spring Boot application.
 *
 * <p>Features:
 * <ul>
 *   <li>Pluggable rate limiting algorithms (Fixed Window, Sliding Window, Token Bucket)</li>
 *   <li>Per-endpoint configurable limits with wildcard support</li>
 *   <li>Identity resolution (IP, API Key, JWT)</li>
 *   <li>Standard rate limit response headers</li>
 *   <li>Micrometer metrics via Actuator</li>
 *   <li>Scheduled stale-entry cleanup</li>
 *   <li>Admin REST APIs</li>
 *   <li>OpenAPI 3 / Swagger UI with dark theme</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
