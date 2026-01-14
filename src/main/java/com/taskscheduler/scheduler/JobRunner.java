package com.taskscheduler.scheduler;

import com.taskscheduler.job.JobExecutor;
import com.taskscheduler.model.JobExecutionLog;
import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.repository.JobExecutionLogRepository;
import com.taskscheduler.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Core orchestrator that fetches active jobs from the database
 * and dispatches them for execution.
 *
 * Responsibilities:
 *   1. Query DB for all active (enabled) jobs
 *   2. Iterate and dispatch each job via {@link JobExecutor}
 *   3. Record execution outcome in {@link JobExecutionLog}
 *
 * This class owns the execution lifecycle (start → execute → log)
 * but contains ZERO business logic. All real work is delegated
 * downstream through the dispatcher.
 *
 * V2 enhancements:
 *   - Job locking (SELECT FOR UPDATE / optimistic locking) to prevent
 *     duplicate execution in clustered deployments
 *   - Parallel execution via TaskExecutor thread pool
 *   - Retry logic with configurable back-off
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final ScheduledJobRepository jobRepository;
    private final JobExecutionLogRepository logRepository;
    private final JobExecutor jobExecutor;

    public JobRunner(ScheduledJobRepository jobRepository,
                     JobExecutionLogRepository logRepository,
                     JobExecutor jobExecutor) {
        this.jobRepository = jobRepository;
        this.logRepository = logRepository;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Fetch all active jobs and execute them sequentially.
     * Each execution is individually logged regardless of outcome.
     */
    public void runPendingJobs() {
        List<ScheduledJob> activeJobs = jobRepository.findActiveJobs();

        if (activeJobs.isEmpty()) {
            log.debug("No active jobs found. Skipping cycle.");
            return;
        }

        log.info("Found {} active job(s). Starting execution cycle.", activeJobs.size());

        for (ScheduledJob job : activeJobs) {
            executeAndLog(job);
        }

        log.info("Execution cycle complete. Processed {} job(s).", activeJobs.size());
    }

    /**
     * Execute a single job and record the result in the audit log.
     */
    private void executeAndLog(ScheduledJob job) {
        LocalDateTime startTime = LocalDateTime.now();
        String status;
        String errorMessage = null;

        try {
            log.info("▶ Executing job: [{}] type=[{}]", job.getName(), job.getType());
            jobExecutor.execute(job);
            status = "SUCCESS";
            log.info("✓ Job [{}] completed successfully", job.getName());

        } catch (Exception e) {
            status = "FAILED";
            errorMessage = truncateMessage(e.getMessage(), 1900);
            log.error("✗ Job [{}] failed: {}", job.getName(), e.getMessage(), e);
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        // Persist execution log
        JobExecutionLog executionLog = JobExecutionLog.builder()
                .jobId(job.getId())
                .jobName(job.getName())
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .errorMessage(errorMessage)
                .executionDurationMs(durationMs)
                .build();

        logRepository.save(executionLog);
        log.debug("Execution log saved for job [{}] — {} in {}ms",
                job.getName(), status, durationMs);
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return null;
        return message.length() <= maxLength ? message : message.substring(0, maxLength) + "…";
    }
}
