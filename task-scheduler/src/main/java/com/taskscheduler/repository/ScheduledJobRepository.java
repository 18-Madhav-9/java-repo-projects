package com.taskscheduler.repository;

import com.taskscheduler.model.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for scheduled jobs.
 *
 * Provides optimised queries for the scheduler engine and
 * admin management APIs.
 */
@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {

    /**
     * Fetches all jobs that are currently enabled.
     * Called by JobRunner on every scheduler tick.
     */
    @Query("SELECT j FROM ScheduledJob j WHERE j.active = true ORDER BY j.id")
    List<ScheduledJob> findActiveJobs();

    /**
     * Lookup a job by its unique name.
     */
    Optional<ScheduledJob> findByName(String name);

    /**
     * Find all jobs of a specific type.
     */
    List<ScheduledJob> findByType(String type);
}
