package com.taskscheduler.config;

import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.repository.ScheduledJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the database with sample jobs on first startup.
 *
 * Only inserts if the job table is empty, so re-runs are safe.
 * This provides a working demo out of the box without requiring
 * manual API calls to create initial jobs.
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    public CommandLineRunner seedJobs(ScheduledJobRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Jobs already exist in database. Skipping seed.");
                return;
            }

            log.info("Seeding initial jobs...");

            repository.save(ScheduledJob.builder()
                    .name("Daily Notification Emails")
                    .type("EMAIL")
                    .cronExpression("0 0 9 * * MON-FRI")
                    .active(true)
                    .build());

            repository.save(ScheduledJob.builder()
                    .name("Revenue Report Generator")
                    .type("REPORT")
                    .cronExpression("0 0 0 * * *")
                    .active(true)
                    .build());

            repository.save(ScheduledJob.builder()
                    .name("External Data Sync")
                    .type("SYNC")
                    .cronExpression("0 */30 * * * *")
                    .active(true)
                    .build());

            repository.save(ScheduledJob.builder()
                    .name("Weekly Summary Report")
                    .type("REPORT")
                    .cronExpression("0 0 8 * * MON")
                    .active(false)
                    .build());

            log.info("✓ Seeded {} jobs", repository.count());
        };
    }
}
