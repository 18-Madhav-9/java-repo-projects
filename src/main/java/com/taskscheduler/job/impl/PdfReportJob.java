package com.taskscheduler.job.impl;

import com.taskscheduler.quartz.BaseQuartzJob;
import com.taskscheduler.service.PdfService;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Quartz job for generating PDF reports.
 *
 * Extends BaseQuartzJob for automatic execution logging.
 * Delegates PDF generation to PdfService (OpenPDF).
 * Generated file path is logged in execution logs.
 */
@Component
public class PdfReportJob extends BaseQuartzJob {

    private static final Logger log = LoggerFactory.getLogger(PdfReportJob.class);

    @Autowired
    private PdfService pdfService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws Exception {
        String jobName = context.getJobDetail().getKey().getName();
        log.info("PdfReportJob [{}] starting execution", jobName);

        String filePath = pdfService.generateRevenueReport("Revenue Report — " + jobName);

        // Store the generated file path in the job data map
        // so it can be retrieved by the execution log
        context.setResult("PDF generated: " + filePath);

        log.info("PdfReportJob [{}] completed — output: {}", jobName, filePath);
    }
}
