package com.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Rate Limiter Spring Boot application.
 *
 * <p>This application provides an in-memory, IP-based rate limiting system
 * using a fixed-window algorithm. No external dependencies (Redis, DB) are required.</p>
 */
@SpringBootApplication
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
