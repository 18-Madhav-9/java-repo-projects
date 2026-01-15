package com.taskscheduler.controller;

import com.taskscheduler.model.ScheduledJob;
import com.taskscheduler.repository.ScheduledJobRepository;
import com.taskscheduler.util.CronUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for job management.
 *
 * Provides CRUD operations for scheduled jobs, allowing admins to
 * create, list, enable/disable, and delete jobs at runtime without
 * requiring an application restart.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final ScheduledJobRepository jobRepository;

    public JobController(ScheduledJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    // ── CREATE ──────────────────────────────────────────────────────

    /**
     * Create a new scheduled job.
     *
     * POST /jobs
     * Body: { "name": "...", "type": "EMAIL|REPORT|SYNC", "cronExpression": "...", "active": true }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@Valid @RequestBody CreateJobRequest request) {
        log.info("Creating job: name={}, type={}", request.name(), request.type());

        // Validate job type
        String type = request.type().toUpperCase().trim();
        if (!isValidType(type)) {
            return errorResponse(HttpStatus.BAD_REQUEST,
                    "Invalid job type: " + type + ". Supported: EMAIL, REPORT, SYNC");
        }

        // Check for duplicate name
        if (jobRepository.findByName(request.name()).isPresent()) {
            return errorResponse(HttpStatus.CONFLICT,
                    "Job with name '" + request.name() + "' already exists");
        }

        // Validate cron expression if provided
        if (request.cronExpression() != null && !request.cronExpression().isBlank()) {
            if (!CronUtils.isValid(request.cronExpression())) {
                return errorResponse(HttpStatus.BAD_REQUEST,
                        "Invalid cron expression: " + request.cronExpression());
            }
        }

        ScheduledJob job = ScheduledJob.builder()
                .name(request.name())
                .type(type)
                .cronExpression(request.cronExpression())
                .active(request.active() != null ? request.active() : true)
                .build();

        ScheduledJob saved = jobRepository.save(job);
        log.info("Job created: id={}, name={}", saved.getId(), saved.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(jobToMap(saved));
    }

    // ── READ ────────────────────────────────────────────────────────

    /**
     * List all scheduled jobs.
     *
     * GET /jobs
     * Optional query param: ?type=EMAIL
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listJobs(
            @RequestParam(required = false) String type) {

        List<ScheduledJob> jobs;
        if (type != null && !type.isBlank()) {
            jobs = jobRepository.findByType(type.toUpperCase().trim());
        } else {
            jobs = jobRepository.findAll();
        }

        List<Map<String, Object>> result = jobs.stream()
                .map(this::jobToMap)
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Get a single job by ID.
     *
     * GET /jobs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable Long id) {
        return jobRepository.findById(id)
                .map(job -> ResponseEntity.ok(jobToMap(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── ENABLE / DISABLE ────────────────────────────────────────────

    /**
     * Enable a job.
     *
     * PUT /jobs/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public ResponseEntity<Map<String, Object>> enableJob(@PathVariable Long id) {
        return toggleJob(id, true);
    }

    /**
     * Disable a job.
     *
     * PUT /jobs/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public ResponseEntity<Map<String, Object>> disableJob(@PathVariable Long id) {
        return toggleJob(id, false);
    }

    // ── DELETE ───────────────────────────────────────────────────────

    /**
     * Delete a job permanently.
     *
     * DELETE /jobs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable Long id) {
        return jobRepository.findById(id)
                .map(job -> {
                    jobRepository.delete(job);
                    log.info("Job deleted: id={}, name={}", id, job.getName());

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("message", "Job deleted successfully");
                    response.put("deletedJob", jobToMap(job));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── HELPERS ─────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> toggleJob(Long id, boolean active) {
        return jobRepository.findById(id)
                .map(job -> {
                    job.setActive(active);
                    ScheduledJob saved = jobRepository.save(job);
                    String action = active ? "enabled" : "disabled";
                    log.info("Job {}: id={}, name={}", action, id, job.getName());

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("message", "Job " + action + " successfully");
                    response.put("job", jobToMap(saved));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> jobToMap(ScheduledJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("name", job.getName());
        map.put("type", job.getType());
        map.put("cronExpression", job.getCronExpression());
        map.put("cronDescription", CronUtils.describe(job.getCronExpression()));
        map.put("active", job.isActive());
        map.put("createdAt", job.getCreatedAt());
        map.put("updatedAt", job.getUpdatedAt());
        return map;
    }

    private boolean isValidType(String type) {
        return "EMAIL".equals(type) || "REPORT".equals(type) || "SYNC".equals(type);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }

    // ── REQUEST DTO ─────────────────────────────────────────────────

    public record CreateJobRequest(
            @NotBlank(message = "Job name is required")
            String name,
            @NotBlank(message = "Job type is required")
            String type,
            String cronExpression,
            Boolean active
    ) {}
}
