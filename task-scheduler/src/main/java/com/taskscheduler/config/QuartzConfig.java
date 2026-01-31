package com.taskscheduler.config;

import com.taskscheduler.quartz.QuartzJobFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Quartz Scheduler configuration.
 */
@Configuration
public class QuartzConfig {

    private final QuartzJobFactory quartzJobFactory;

    public QuartzConfig(QuartzJobFactory quartzJobFactory) {
        this.quartzJobFactory = quartzJobFactory;
    }

    /**
     * Customizer to inject our JobFactory without overriding Spring Boot's
     * QuartzAutoConfiguration, allowing application.yml properties to work.
     */
    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer() {
        return factory -> {
            factory.setJobFactory(quartzJobFactory);
            factory.setOverwriteExistingJobs(true);
            factory.setWaitForJobsToCompleteOnShutdown(true);
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
