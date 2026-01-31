package com.taskscheduler.quartz;

import com.taskscheduler.model.JobExecutionLog;
import com.taskscheduler.repository.JobExecutionLogRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Abstract base class for all Quartz jobs in the system.
 *
 * Implements the Template Method pattern:
 *   1. Records start time
 *   2. Delegates to {@link #executeInternal(JobExecutionContext)}
 *   3. Logs SUCCESS or FAILED to the database
 *   4. Captures timing and error details
 *
 * All concrete jobs (EmailJob, PdfReportJob, KafkaPublishJob) extend this
 * class and implement only the business logic in executeInternal().
 *
 * This keeps execution lifecycle management (logging, error handling)
 * completely separated from business logic — a core V2 design principle.
 */
public abstract class BaseQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(BaseQuartzJob.class);

    @Autowired
    private JobExecutionLogRepository logRepository;

    @Override
    public final void execute(JobExecutionContext context) {
        JobKey jobKey = context.getJobDetail().getKey();
        String jobName = jobKey.getName();
        String jobGroup = jobKey.getGroup();

        LocalDateTime startTime = LocalDateTime.now();
        String status;
        String errorMessage = null;

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("▶ Executing job: [{}] group=[{}]", jobName, jobGroup);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            executeInternal(context);
            status = "SUCCESS";
            log.info("✓ Job [{}] completed successfully", jobName);

        } catch (Exception e) {
            status = "FAILED";
            errorMessage = truncate(e.getMessage(), 1900);
            log.error("✗ Job [{}] failed: {}", jobName, e.getMessage(), e);
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        // Persist execution log
        try {
            JobExecutionLog executionLog = JobExecutionLog.builder()
                    .jobName(jobName)
                    .jobGroup(jobGroup)
                    .status(status)
                    .startTime(startTime)
                    .endTime(endTime)
                    .errorMessage(errorMessage)
                    .executionDurationMs(durationMs)
                    .retryCount(getRetryCount(context))
                    .build();

            logRepository.save(executionLog);
            log.debug("Execution log saved for [{}] — {} in {}ms", jobName, status, durationMs);

        } catch (Exception e) {
            log.error("Failed to persist execution log for job [{}]: {}", jobName, e.getMessage());
        }
    }

    /**
     * Implement this method with the actual business logic.
     * Called by the template method after setup and before logging.
     *
     * @param context Quartz execution context
     * @throws Exception any exception will be caught and logged as FAILED
     */
    protected abstract void executeInternal(JobExecutionContext context) throws Exception;

    /**
     * Extract retry count from the job data map.
     * Defaults to 0 if not set.
     */
    private int getRetryCount(JobExecutionContext context) {
        try {
            return context.getJobDetail().getJobDataMap().getInt("retryCount");
        } catch (Exception e) {
            return 0;
        }
    }

    private String truncate(String message, int maxLength) {
        if (message == null) return null;
        return message.length() <= maxLength ? message : message.substring(0, maxLength) + "…";
    }
}
