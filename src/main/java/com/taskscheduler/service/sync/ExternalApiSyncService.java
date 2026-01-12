package com.taskscheduler.service.sync;

import com.taskscheduler.model.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * External API synchronization service.
 *
 * Calls a public REST API, processes the response, and logs the result.
 * In production this would sync data between internal systems and
 * third-party services (CRM, inventory, payment providers, etc.).
 *
 * Uses JSONPlaceholder as a safe, always-available external API for V1.
 *
 * V2: Add retry with exponential backoff, circuit breaker (Resilience4j),
 *     and Kafka event publishing for downstream consumers.
 */
@Service
public class ExternalApiSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiSyncService.class);
    private static final String EXTERNAL_API_URL = "https://jsonplaceholder.typicode.com/posts/1";

    private final RestTemplate restTemplate;

    public ExternalApiSyncService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Synchronize data from an external API.
     *
     * @param job the scheduled job metadata
     */
    @SuppressWarnings("unchecked")
    public void syncFromExternalApi(ScheduledJob job) {
        log.info("┌─ EXTERNAL API SYNC ─────────────────────────");
        log.info("│ Job:       {}", job.getName());
        log.info("│ Endpoint:  {}", EXTERNAL_API_URL);
        log.info("│ Timestamp: {}", LocalDateTime.now());

        try {
            // Call external API
            Map<String, Object> response = restTemplate.getForObject(
                    EXTERNAL_API_URL, Map.class);

            if (response != null) {
                log.info("│ ✓ Response received:");
                log.info("│   userId:  {}", response.get("userId"));
                log.info("│   id:      {}", response.get("id"));
                log.info("│   title:   {}", truncate(String.valueOf(response.get("title")), 50));
                log.info("│   body:    {} chars", String.valueOf(response.get("body")).length());

                processAndStore(response);

                log.info("│ ✓ Data synced and stored successfully");
            } else {
                log.warn("│ ⚠ Empty response from external API");
            }
        } catch (Exception e) {
            log.error("│ ✗ Sync failed: {}", e.getMessage());
            throw new RuntimeException("External API sync failed for job: " + job.getName(), e);
        }

        log.info("└──────────────────────────────────────────────");
    }

    /**
     * Process and persist the synced data.
     * In production this would map to domain entities and save to DB.
     */
    private void processAndStore(Map<String, Object> data) {
        log.debug("Processing synced data: {} fields", data.size());
        // V1: Log-only persistence simulation
        // V2: Map to domain entity and repository.save()
        simulateLatency(100);
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
