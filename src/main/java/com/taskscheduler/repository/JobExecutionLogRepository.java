package com.taskscheduler.repository;

import com.taskscheduler.model.JobExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for job execution audit logs.
 *
 * Supports paginated queries for the admin dashboard API and
 * filtered lookups by job ID or execution status.
 */
@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {

    /**
     * Paginated execution history, newest first.
     */
    Page<JobExecutionLog> findAllByOrderByStartTimeDesc(Pageable pageable);

    /**
     * All execution logs for a specific job.
     */
    List<JobExecutionLog> findByJobIdOrderByStartTimeDesc(Long jobId);

    /**
     * Filter logs by status (SUCCESS / FAILED).
     */
    List<JobExecutionLog> findByStatusOrderByStartTimeDesc(String status);
}
