package com.taskscheduler.exception;

/**
 * Custom exception for job execution failures.
 *
 * Thrown by job implementations when business logic fails.
 * Caught by BaseQuartzJob to log the failure and optionally
 * trigger retry mechanisms.
 */
public class JobExecutionException extends RuntimeException {

    private final String jobName;
    private final boolean retryable;

    public JobExecutionException(String message, String jobName) {
        super(message);
        this.jobName = jobName;
        this.retryable = false;
    }

    public JobExecutionException(String message, String jobName, Throwable cause) {
        super(message, cause);
        this.jobName = jobName;
        this.retryable = false;
    }

    public JobExecutionException(String message, String jobName, boolean retryable) {
        super(message);
        this.jobName = jobName;
        this.retryable = retryable;
    }

    public JobExecutionException(String message, String jobName, Throwable cause, boolean retryable) {
        super(message, cause);
        this.jobName = jobName;
        this.retryable = retryable;
    }

    public String getJobName() {
        return jobName;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
