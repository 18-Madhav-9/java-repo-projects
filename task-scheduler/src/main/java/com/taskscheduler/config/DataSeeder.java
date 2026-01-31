package com.taskscheduler.config;

import com.taskscheduler.job.impl.EmailJob;
import com.taskscheduler.job.impl.KafkaPublishJob;
import com.taskscheduler.job.impl.PdfReportJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the Quartz scheduler with sample jobs on first startup.
 *
 * Uses Quartz API (not custom DB inserts) to register jobs,
 * ensuring they are properly tracked in the QRTZ_* tables.
 * Jobs are only seeded if they don't already exist (idempotent).
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    public CommandLineRunner seedQuartzJobs(Scheduler scheduler) {
        return args -> {
            seedJob(scheduler,
                    "Daily-Notification-Emails", "DEFAULT",
                    "Sends scheduled notification emails to team members",
                    EmailJob.class, "0 0/2 * * * ?");   // Every 2 minutes for demo

            seedJob(scheduler,
                    "Revenue-Report-Generator", "DEFAULT",
                    "Generates PDF revenue reports with regional breakdown",
                    PdfReportJob.class, "0 0/3 * * * ?"); // Every 3 minutes for demo

            seedJob(scheduler,
                    "Kafka-Event-Publisher", "DEFAULT",
                    "Publishes job execution events to Kafka topic",
                    KafkaPublishJob.class, "0 0/5 * * * ?"); // Every 5 minutes for demo

            log.info("✓ Quartz job seeding complete");
        };
    }

    private void seedJob(Scheduler scheduler, String name, String group,
                          String description, Class<? extends Job> jobClass,
                          String cronExpression) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);

            if (scheduler.checkExists(jobKey)) {
                log.info("Job [{}] already exists. Skipping seed.", name);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .withDescription(description)
                    .storeDurably(true)
                    .build();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(name + "-trigger", group)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("✓ Seeded job: [{}] cron={}", name, cronExpression);

        } catch (SchedulerException e) {
            log.error("Failed to seed job [{}]: {}", name, e.getMessage(), e);
        }
    }
}
