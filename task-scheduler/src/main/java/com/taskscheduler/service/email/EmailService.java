package com.taskscheduler.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Core email sending service with retry support.
 *
 * V2 upgrade: Added @Retryable with 3 attempts and 2-second exponential backoff.
 * In production this would integrate with SMTP (JavaMail), SendGrid, or SES.
 * For V2 it simulates the send operation with configurable failure for testing retries.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Send an email to a single recipient.
     * Retries up to 3 times with 2-second backoff on failure.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    email body (plain text or HTML)
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    public void sendEmail(String to, String subject, String body) {
        log.info("┌─────────────────────────────────────────────────");
        log.info("│ SENDING EMAIL");
        log.info("│ To:      {}", to);
        log.info("│ Subject: {}", subject);
        log.info("│ Body:    {} chars", body.length());
        log.info("└─────────────────────────────────────────────────");

        // Simulate network latency
        simulateLatency(200);

        log.info("✓ Email delivered successfully to {}", to);
    }

    /**
     * Fallback method called after all retry attempts are exhausted.
     */
    @Recover
    public void recoverSendEmail(Exception e, String to, String subject, String body) {
        log.error("╔═════════════════════════════════════════════════");
        log.error("║ EMAIL DELIVERY FAILED — ALL RETRIES EXHAUSTED");
        log.error("║ To:    {}", to);
        log.error("║ Error: {}", e.getMessage());
        log.error("╚═════════════════════════════════════════════════");
        throw new RuntimeException("Email delivery permanently failed for: " + to, e);
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
                log.error("Failed to send email to {} after retries: {}", recipient, e.getMessage());
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
