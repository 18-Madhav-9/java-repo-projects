package com.taskscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock Kafka producer service.
 *
 * Simulates publishing JSON payloads to a Kafka topic.
 * In production, replace the mock implementation with a real
 * KafkaTemplate injection — the interface stays the same.
 *
 * Usage:
 *   kafkaService.publish("job-events", payload);
 *
 * To enable real Kafka:
 *   1. Start a Kafka broker
 *   2. Uncomment the KafkaTemplate injection
 *   3. Replace the log-based publish with template.send()
 */
@Service
public class KafkaService {

    private static final Logger log = LoggerFactory.getLogger(KafkaService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Uncomment for real Kafka:
    // private final KafkaTemplate<String, String> kafkaTemplate;
    //
    // public KafkaService(KafkaTemplate<String, String> kafkaTemplate) {
    //     this.kafkaTemplate = kafkaTemplate;
    // }

    /**
     * Publish a JSON payload to a Kafka topic.
     *
     * @param topic   the Kafka topic name
     * @param payload the data to publish
     */
    public void publish(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            log.info("╔═══════════════════════════════════════════════");
            log.info("║ KAFKA PUBLISH (mock)");
            log.info("║ Topic:     {}", topic);
            log.info("║ Payload:   {}", truncate(json, 200));
            log.info("║ Size:      {} bytes", json.length());
            log.info("╚═══════════════════════════════════════════════");

            // Real Kafka:
            // kafkaTemplate.send(topic, UUID.randomUUID().toString(), json);

            // Simulate publish latency
            simulateLatency(100);

            log.info("✓ Message published to topic [{}]", topic);

        } catch (Exception e) {
            log.error("✗ Failed to publish to Kafka topic [{}]: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Kafka publish failed for topic: " + topic, e);
        }
    }

    /**
     * Build a standard job event payload.
     *
     * @param jobName  name of the executed job
     * @param jobGroup group of the executed job
     * @param status   execution status
     * @return structured event payload
     */
    public Map<String, Object> buildJobEventPayload(String jobName, String jobGroup, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "JOB_EXECUTION");
        payload.put("jobName", jobName);
        payload.put("jobGroup", jobGroup);
        payload.put("status", status);
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("source", "task-scheduler-v2");
        return payload;
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
