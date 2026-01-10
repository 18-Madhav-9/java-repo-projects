package com.taskscheduler.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core email sending service.
 *
 * In production this would integrate with SMTP (JavaMail), SendGrid, or SES.
 * For V1 it simulates the send operation and logs outcomes. The interface
 * is designed so swapping in a real provider requires zero changes to callers.
 *
 * V2: Replace with a queue-backed async email system (SQS / Kafka).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Send an email to a single recipient.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    email body (plain text or HTML)
     */
    public void sendEmail(String to, String subject, String body) {
        log.info("┌─────────────────────────────────────────────────");
        log.info("│ SENDING EMAIL");
        log.info("│ To:      {}", to);
        log.info("│ Subject: {}", subject);
        log.info("│ Body:    {} chars", body.length());
        log.info("└─────────────────────────────────────────────────");

        // Simulate network latency
        simulateLatency(200);

        log.info("Email delivered successfully to {}", to);
    }

    /**
     * Send emails to multiple recipients.
     *
     * @param recipients list of email addresses
     * @param subject    email subject
     * @param body       email body
     * @return number of emails sent successfully
     */
    public int sendBulkEmails(List<String> recipients, String subject, String body) {
        log.info("Starting bulk email send to {} recipients", recipients.size());
        int successCount = 0;

        for (String recipient : recipients) {
            try {
                sendEmail(recipient, subject, body);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", recipient, e.getMessage());
            }
        }

        log.info("Bulk email complete: {}/{} delivered", successCount, recipients.size());
        return successCount;
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
