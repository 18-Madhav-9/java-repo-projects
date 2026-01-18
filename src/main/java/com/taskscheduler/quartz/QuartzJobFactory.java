package com.taskscheduler.quartz;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

/**
 * Custom Quartz JobFactory that enables Spring dependency injection
 * inside Quartz Job instances.
 *
 * By default, Quartz creates Job instances via reflection — they are NOT
 * Spring beans and @Autowired fields remain null. This factory uses
 * Spring's AutowireCapableBeanFactory to inject dependencies after
 * Quartz creates the job instance.
 *
 * This is the critical bridge between Quartz's lifecycle and Spring's DI.
 */
@Component
public class QuartzJobFactory extends SpringBeanJobFactory {

    private AutowireCapableBeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.beanFactory = context.getAutowireCapableBeanFactory();
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        // Inject Spring beans (@Autowired, @Value, etc.) into the Quartz job
        beanFactory.autowireBean(job);
        return job;
    }
}
