package com.taskscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Task Scheduler Application — Entry Point (V2)
 *
 * A production-grade, distributed job scheduling system powered by Quartz.
 * Jobs are managed via Quartz JDBC JobStore (cluster-safe) and executed
 * by Spring-managed beans with retry support.
 *
 * V2 changes:
 *   - Removed @EnableScheduling (Quartz replaces Spring scheduler)
 *   - @EnableRetry is configured in RetryConfig.java
 */
@SpringBootApplication
public class TaskSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskSchedulerApplication.class, args);
    }
}
