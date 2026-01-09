package com.taskscheduler.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a schedulable job persisted in the database.
 *
 * The scheduler engine reads active jobs from this table on every tick
 * and dispatches them to the appropriate executor. Admins can enable/disable
 * jobs at runtime without requiring a restart.
 *
 * The {@code cronExpression} field is a V2 placeholder — currently unused
 * but reserved for Quartz-based cron scheduling.
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
    private String type;  // EMAIL, REPORT, SYNC

    @Column(name = "cron_expression")
    private String cronExpression;  // V2 placeholder for Quartz integration

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
