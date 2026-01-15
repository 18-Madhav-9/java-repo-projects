package com.taskscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

/**
 * Central configuration for the scheduler subsystem.
 *
 * Defines infrastructure beans shared across the application:
 *   - {@link RestTemplate} for outbound HTTP calls (sync jobs)
 *   - {@link ThreadPoolTaskScheduler} reserved for V2 parallel execution
 *
 * V2: Add Quartz SchedulerFactoryBean, Resilience4j config, Kafka producer.
 */
@Configuration
public class SchedulerConfig {

    /**
     * RestTemplate bean for HTTP-based job integrations (e.g. SyncJob).
     *
     * V2: Replace with WebClient for non-blocking I/O, or add
     * interceptors for auth headers, request logging, and retry.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Thread pool for scheduled tasks.
     * Pool size is kept small for V1 (sequential execution).
     *
     * V2: Increase pool size and integrate with job locking
     * to enable safe parallel execution across multiple threads.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(t ->
            org.slf4j.LoggerFactory.getLogger("SchedulerErrorHandler")
                .error("Unhandled scheduler error", t)
        );
        scheduler.initialize();
        return scheduler;
    }
}
