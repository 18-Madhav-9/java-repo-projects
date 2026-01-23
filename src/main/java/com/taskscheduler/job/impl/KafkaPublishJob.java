package com.taskscheduler.job.impl;

import com.taskscheduler.quartz.BaseQuartzJob;
import com.taskscheduler.service.KafkaService;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Quartz job for publishing events to Kafka.
 *
 * Extends BaseQuartzJob for automatic execution logging.
 * Delegates event publishing to KafkaService (mock for V2).
 * Publishes a structured JSON payload to topic "job-events".
 */
@Component
public class KafkaPublishJob extends BaseQuartzJob {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublishJob.class);
    private static final String TOPIC = "job-events";

    @Autowired
    private KafkaService kafkaService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws Exception {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        log.info("KafkaPublishJob [{}] starting execution", jobName);

        // Build structured event payload
        Map<String, Object> payload = kafkaService.buildJobEventPayload(
                jobName, jobGroup, "TRIGGERED");

        // Publish to Kafka topic
        kafkaService.publish(TOPIC, payload);

        log.info("KafkaPublishJob [{}] completed — event published to [{}]", jobName, TOPIC);
    }
}
