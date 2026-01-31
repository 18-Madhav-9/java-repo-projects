package com.taskscheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry across the application.
 *
 * Services annotated with @Retryable (e.g. EmailService) will
 * automatically retry failed operations with configurable
 * back-off and max attempts.
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry is configured via @Retryable annotations on service methods.
    // No additional beans needed — @EnableRetry activates the AOP proxy.
}
