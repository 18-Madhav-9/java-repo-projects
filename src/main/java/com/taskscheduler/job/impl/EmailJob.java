package com.taskscheduler.job.impl;

import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.service.email.EmailJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for EMAIL type jobs.
 *
 * This class is a thin adapter between the scheduling infrastructure
 * and the business-level {@link EmailJobService}. It contains no email
 * logic — it simply delegates to the service layer.
 */
@Component
public class EmailJob {

    private static final Logger log = LoggerFactory.getLogger(EmailJob.class);

    private final EmailJobService emailJobService;

    public EmailJob(EmailJobService emailJobService) {
        this.emailJobService = emailJobService;
    }

    /**
     * Execute the email job by delegating to the email business service.
     *
     * @param job the scheduled job metadata
     */
    public void run(ScheduledJob job) {
        log.info("EmailJob [{}] starting execution", job.getName());
        emailJobService.processEmailJob(job);
        log.info("EmailJob [{}] completed", job.getName());
    }
}
