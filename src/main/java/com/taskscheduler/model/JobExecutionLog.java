package com.taskscheduler.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit log for every job execution attempt.
 *
 * Each time the scheduler dispatches a job, a log entry is created
 * capturing the outcome (SUCCESS / FAILED), wall-clock duration,
 * and any error details. This table is append-only and serves as
 * the single source of truth for execution history.
 */
@Entity
@Table(name = "job_execution_logs", indexes = {
    @Index(name = "idx_execution_job_id", columnList = "job_id"),
    @Index(name = "idx_execution_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "job_name")
    private String jobName;

    @Column(nullable = false)
    private String status;  // SUCCESS, FAILED

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;
}
