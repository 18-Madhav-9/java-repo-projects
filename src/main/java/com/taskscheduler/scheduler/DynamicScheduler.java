package com.taskscheduler.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduler engine — a thin, "dumb" timer that triggers job execution.
 *
 * This class has a single responsibility: fire on a fixed interval and
 * delegate all work to {@link JobRunner}. It contains NO knowledge of
 * job types, business logic, or execution mechanics.
 *
 * Design rationale:
 *   Separating the timer from the runner allows independent testing,
 *   easy replacement of the scheduling mechanism (e.g. swap to Quartz
 *   in V2), and clean adherence to SRP.
 *
 * Configuration:
 *   - Polling interval: controlled via {@code scheduler.polling-interval-ms}
 *     in application.yml (default: 60000ms = 1 minute)
 */
@Component
public class DynamicScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicScheduler.class);

    private final JobRunner jobRunner;

    public DynamicScheduler(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    /**
     * Scheduler tick — fires every N milliseconds as configured.
     * Delegates all work to the JobRunner.
     */
    @Scheduled(fixedRateString = "${scheduler.polling-interval-ms:60000}")
    public void runJobs() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  SCHEDULER TICK — Checking for pending jobs...");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            jobRunner.runPendingJobs();
        } catch (Exception e) {
            log.error("Scheduler cycle encountered an unexpected error", e);
        }
    }
}
