package com.taskscheduler.job.impl;

import com.taskscheduler.quartz.BaseQuartzJob;
import com.taskscheduler.service.email.EmailService;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Quartz job for sending scheduled emails.
 *
 * Extends BaseQuartzJob for automatic execution logging.
 * Delegates all email business logic to EmailService, which
 * has @Retryable with 3 attempts and 2-second backoff.
 *
 * Spring beans are injected via QuartzJobFactory — this is NOT
 * a standard Spring bean but a Quartz-managed instance with
 * autowired dependencies.
 */
@Component
public class EmailJob extends BaseQuartzJob {

    private static final Logger log = LoggerFactory.getLogger(EmailJob.class);

    @Autowired
    private EmailService emailService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws Exception {
        String jobName = context.getJobDetail().getKey().getName();
        log.info("EmailJob [{}] starting execution", jobName);

        // In production: fetch recipients from DB/config
        List<String> recipients = List.of(
                "admin@company.com",
                "ops-team@company.com",
                "manager@company.com"
        );

        String subject = "Scheduled Notification — " + jobName;
        String body = buildEmailBody(jobName);

        int sent = emailService.sendBulkEmails(recipients, subject, body);
        log.info("EmailJob [{}] finished: {} emails delivered", jobName, sent);
    }

    private String buildEmailBody(String jobName) {
        return String.format("""
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Automated Notification
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            Job Name: %s
            Status:   Executed Successfully

            This is an automated message from the
            Task Scheduler System V2.
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """, jobName);
    }
}
