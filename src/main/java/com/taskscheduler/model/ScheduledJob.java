package com.taskscheduler.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a schedulable job persisted in the database.
 *
 * V2: Added jobGroup and description fields for Quartz integration.
 * The cronExpression field is now actively used by Quartz CronTrigger.
 */
@Entity
@Table(name = "scheduled_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String type;  // EMAIL, PDF_REPORT, KAFKA_PUBLISH

    @Column(name = "job_group", nullable = false)
    @Builder.Default
    private String jobGroup = "DEFAULT";

    @Column(length = 500)
    private String description;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
