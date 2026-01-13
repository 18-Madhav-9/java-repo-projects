package com.taskscheduler.job.impl;

import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.service.sync.ExternalApiSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for SYNC type jobs.
 *
 * Delegates external API synchronization to {@link ExternalApiSyncService}.
 */
@Component
public class SyncJob {

    private static final Logger log = LoggerFactory.getLogger(SyncJob.class);

    private final ExternalApiSyncService syncService;

    public SyncJob(ExternalApiSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Execute the sync job.
     *
     * @param job the scheduled job metadata
     */
    public void run(ScheduledJob job) {
        log.info("SyncJob [{}] starting execution", job.getName());
        syncService.syncFromExternalApi(job);
        log.info("SyncJob [{}] completed", job.getName());
    }
}
