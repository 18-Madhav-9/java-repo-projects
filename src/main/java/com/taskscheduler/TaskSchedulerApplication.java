package com.taskscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Task Scheduler Application — Entry Point
 *
 * A production-grade, database-driven job scheduling system.
 * Jobs are stored in the database and executed dynamically based on type.
 * Business logic is fully decoupled from the scheduling engine.
 *
 * Architecture:
 *   REST API → Job Management → Scheduler Engine → Dispatcher → Business Services → DB
 */
@SpringBootApplication
@EnableScheduling
public class TaskSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskSchedulerApplication.class, args);
    }
}
