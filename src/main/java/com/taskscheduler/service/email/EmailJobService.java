package com.taskscheduler.service.email;

import com.taskscheduler.model.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates email job execution.
 *
 * This service bridges the scheduling system and the low-level
 * {@link EmailService}. It knows WHAT emails to send (recipients,
 * content) but delegates the HOW to the email service.
 *
 * In production the recipient list and template would come from
 * a database or configuration. For V1, sample data is used.
 */
@Service
public class EmailJobService {

    private static final Logger log = LoggerFactory.getLogger(EmailJobService.class);

    private final EmailService emailService;

    public EmailJobService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Process an email job — determine recipients and content,
     * then delegate to the email service.
     *
     * @param job the scheduled job metadata
     */
    public void processEmailJob(ScheduledJob job) {
        log.info("Processing email job: {}", job.getName());

        // In production: fetch from user_notification_preferences table
        List<String> recipients = List.of(
            "admin@company.com",
            "ops-team@company.com",
            "manager@company.com"
        );

        String subject = "Scheduled Notification — " + job.getName();
        String body = buildEmailBody(job);

        int sent = emailService.sendBulkEmails(recipients, subject, body);
        log.info("Email job [{}] finished: {} emails delivered", job.getName(), sent);
    }

    private String buildEmailBody(ScheduledJob job) {
        return String.format("""
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Automated Notification
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            Job Name: %s
            Job Type: %s
            Status:   Executed Successfully

            This is an automated message from the
            Task Scheduler System.
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """, job.getName(), job.getType());
    }
}
