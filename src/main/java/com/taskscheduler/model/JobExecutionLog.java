package com.taskscheduler.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit log for every job execution attempt.
 *
 * V2: Added retryCount field to track retry attempts via Spring Retry.
 */
@Entity
@Table(name = "job_execution_logs", indexes = {
    @Index(name = "idx_execution_job_id", columnList = "job_id"),
    @Index(name = "idx_execution_status", columnList = "status"),
    @Index(name = "idx_execution_start_time", columnList = "start_time")
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

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_group")
    private String jobGroup;

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

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
}
