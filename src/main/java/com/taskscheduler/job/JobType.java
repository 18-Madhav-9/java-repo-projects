package com.taskscheduler.job;

/**
 * Enumerates all supported job types in the system.
 *
 * To add a new job type in V2:
 *   1. Add the enum constant here
 *   2. Create a new JobXxx implementation in job/impl/
 *   3. Create the backing business service in service/
 *   4. Register the new case in JobExecutor's dispatch table
 */
public enum JobType {

    EMAIL,
    REPORT,
    SYNC;

    /**
     * Safe parse that returns null instead of throwing on unknown types.
     * Allows the executor to gracefully skip unsupported job types.
     */
    public static JobType fromString(String type) {
        try {
            return JobType.valueOf(type.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
