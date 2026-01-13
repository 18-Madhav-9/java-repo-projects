package com.taskscheduler.job.impl;

import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.service.report.ReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for REPORT type jobs.
 *
 * Delegates all report generation logic to {@link ReportGenerator}.
 */
@Component
public class ReportJob {

    private static final Logger log = LoggerFactory.getLogger(ReportJob.class);

    private final ReportGenerator reportGenerator;

    public ReportJob(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    /**
     * Execute the report job.
     *
     * @param job the scheduled job metadata
     */
    public void run(ScheduledJob job) {
        log.info("ReportJob [{}] starting execution", job.getName());
        reportGenerator.generateReport(job);
        log.info("ReportJob [{}] completed", job.getName());
    }
}
