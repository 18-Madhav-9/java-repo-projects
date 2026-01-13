package com.taskscheduler.job;

import com.taskscheduler.job.impl.EmailJob;
import com.taskscheduler.job.impl.ReportJob;
import com.taskscheduler.job.impl.SyncJob;
import com.taskscheduler.model.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatcher — routes a {@link ScheduledJob} to the correct job implementation
 * based on its type.
 *
 * This class owns NO business logic. It is a pure routing layer that maps
 * {@link JobType} to the corresponding job handler. New job types are added
 * by extending the switch statement and injecting the new handler.
 *
 * Design note (V2): This can be refactored to a Strategy Registry
 * (Map<JobType, Runnable>) for fully dynamic dispatch, eliminating the
 * switch entirely.
 */
@Component
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final EmailJob emailJob;
    private final ReportJob reportJob;
    private final SyncJob syncJob;

    public JobExecutor(EmailJob emailJob, ReportJob reportJob, SyncJob syncJob) {
        this.emailJob = emailJob;
        this.reportJob = reportJob;
        this.syncJob = syncJob;
    }

    /**
     * Dispatches the given job to the appropriate handler.
     *
     * @param job the scheduled job to execute
     * @throws IllegalArgumentException if the job type is unknown or unsupported
     */
    public void execute(ScheduledJob job) {
        JobType type = JobType.fromString(job.getType());

        if (type == null) {
            throw new IllegalArgumentException(
                "Unknown job type: " + job.getType() + " for job: " + job.getName());
        }

        log.info("Dispatching job [{}] of type [{}]", job.getName(), type);

        switch (type) {
            case EMAIL  -> emailJob.run(job);
            case REPORT -> reportJob.run(job);
            case SYNC   -> syncJob.run(job);
        }
    }
}
