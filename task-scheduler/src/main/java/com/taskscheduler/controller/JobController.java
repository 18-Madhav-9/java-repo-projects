package com.taskscheduler.controller;

import com.taskscheduler.job.impl.EmailJob;
import com.taskscheduler.job.impl.KafkaPublishJob;
import com.taskscheduler.job.impl.PdfReportJob;
import com.taskscheduler.model.JobExecutionLog;
import com.taskscheduler.repository.JobExecutionLogRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Unified REST API for Quartz job management and execution monitoring.
 *
 * All scheduling operations go through the Quartz Scheduler API —
 * NO custom DB polling or loop logic. This controller is the single
 * entry point for the React admin dashboard.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final Scheduler scheduler;
    private final JobExecutionLogRepository logRepository;

    public JobController(Scheduler scheduler, JobExecutionLogRepository logRepository) {
        this.scheduler = scheduler;
        this.logRepository = logRepository;
    }

    // ── LIST JOBS ───────────────────────────────────────────────────

    /**
     * List all scheduled Quartz jobs with metadata.
     *
     * GET /api/jobs
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listJobs() throws SchedulerException {
        List<Map<String, Object>> jobs = new ArrayList<>();

        for (String groupName : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

                Map<String, Object> jobInfo = new LinkedHashMap<>();
                jobInfo.put("name", jobKey.getName());
                jobInfo.put("group", jobKey.getGroup());
                jobInfo.put("description", jobDetail.getDescription());
                jobInfo.put("jobClass", jobDetail.getJobClass().getSimpleName());

                if (!triggers.isEmpty()) {
                    Trigger trigger = triggers.get(0);
                    jobInfo.put("state", scheduler.getTriggerState(trigger.getKey()).name());
                    jobInfo.put("nextFireTime", toLocalDateTime(trigger.getNextFireTime()));
                    jobInfo.put("previousFireTime", toLocalDateTime(trigger.getPreviousFireTime()));

                    if (trigger instanceof CronTrigger cronTrigger) {
                        jobInfo.put("cronExpression", cronTrigger.getCronExpression());
                    }
                } else {
                    jobInfo.put("state", "NONE");
                    jobInfo.put("nextFireTime", null);
                    jobInfo.put("previousFireTime", null);
                }

                jobs.add(jobInfo);
            }
        }

        return ResponseEntity.ok(jobs);
    }

    // ── CREATE JOB ──────────────────────────────────────────────────

    /**
     * Schedule a new Quartz job.
     *
     * POST /api/jobs
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@Valid @RequestBody CreateJobRequest request) {
        try {
            Class<? extends Job> jobClass = resolveJobClass(request.type());
            if (jobClass == null) {
                return errorResponse(HttpStatus.BAD_REQUEST,
                        "Unknown job type: " + request.type() + ". Supported: EMAIL, PDF_REPORT, KAFKA_PUBLISH");
            }

            String group = request.group() != null ? request.group() : "DEFAULT";
            JobKey jobKey = JobKey.jobKey(request.name(), group);

            // Check if job already exists
            if (scheduler.checkExists(jobKey)) {
                return errorResponse(HttpStatus.CONFLICT,
                        "Job '" + request.name() + "' already exists in group '" + group + "'");
            }

            // Build job detail
            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey)
                    .withDescription(request.description())
                    .storeDurably(true)
                    .build();

            // Build trigger
            String cron = request.cronExpression() != null ? request.cronExpression() : "0 0/5 * * * ?";
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(request.name() + "-trigger", group)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Job scheduled: {} [{}] cron={}", request.name(), request.type(), cron);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Job scheduled successfully");
            response.put("name", request.name());
            response.put("group", group);
            response.put("type", request.type());
            response.put("cronExpression", cron);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (SchedulerException e) {
            log.error("Failed to schedule job: {}", e.getMessage(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to schedule job: " + e.getMessage());
        }
    }

    // ── TRIGGER NOW ─────────────────────────────────────────────────

    /**
     * Trigger a job to run immediately.
     *
     * POST /api/jobs/{group}/{name}/trigger
     */
    @PostMapping("/{group}/{name}/trigger")
    public ResponseEntity<Map<String, Object>> triggerJob(
            @PathVariable String group, @PathVariable String name) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);

            if (!scheduler.checkExists(jobKey)) {
                return ResponseEntity.notFound().build();
            }

            scheduler.triggerJob(jobKey);
            log.info("Job triggered manually: {}.{}", group, name);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Job triggered successfully");
            response.put("name", name);
            response.put("group", group);
            return ResponseEntity.ok(response);

        } catch (SchedulerException e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to trigger job: " + e.getMessage());
        }
    }

    // ── PAUSE ───────────────────────────────────────────────────────

    /**
     * Pause a scheduled job.
     *
     * POST /api/jobs/{group}/{name}/pause
     */
    @PostMapping("/{group}/{name}/pause")
    public ResponseEntity<Map<String, Object>> pauseJob(
            @PathVariable String group, @PathVariable String name) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);

            if (!scheduler.checkExists(jobKey)) {
                return ResponseEntity.notFound().build();
            }

            scheduler.pauseJob(jobKey);
            log.info("Job paused: {}.{}", group, name);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Job paused successfully");
            response.put("name", name);
            response.put("group", group);
            response.put("state", "PAUSED");
            return ResponseEntity.ok(response);

        } catch (SchedulerException e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to pause job: " + e.getMessage());
        }
    }

    // ── RESUME ──────────────────────────────────────────────────────

    /**
     * Resume a paused job.
     *
     * POST /api/jobs/{group}/{name}/resume
     */
    @PostMapping("/{group}/{name}/resume")
    public ResponseEntity<Map<String, Object>> resumeJob(
            @PathVariable String group, @PathVariable String name) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);

            if (!scheduler.checkExists(jobKey)) {
                return ResponseEntity.notFound().build();
            }

            scheduler.resumeJob(jobKey);
            log.info("Job resumed: {}.{}", group, name);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Job resumed successfully");
            response.put("name", name);
            response.put("group", group);
            response.put("state", "NORMAL");
            return ResponseEntity.ok(response);

        } catch (SchedulerException e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resume job: " + e.getMessage());
        }
    }

    // ── DELETE ──────────────────────────────────────────────────────

    /**
     * Delete a scheduled job permanently.
     *
     * DELETE /api/jobs/{group}/{name}
     */
    @DeleteMapping("/{group}/{name}")
    public ResponseEntity<Map<String, Object>> deleteJob(
            @PathVariable String group, @PathVariable String name) {
        try {
            JobKey jobKey = JobKey.jobKey(name, group);

            if (!scheduler.checkExists(jobKey)) {
                return ResponseEntity.notFound().build();
            }

            scheduler.deleteJob(jobKey);
            log.info("Job deleted: {}.{}", group, name);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Job deleted successfully");
            response.put("name", name);
            response.put("group", group);
            return ResponseEntity.ok(response);

        } catch (SchedulerException e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete job: " + e.getMessage());
        }
    }

    // ── EXECUTION LOGS ──────────────────────────────────────────────

    /**
     * Get paginated execution logs.
     *
     * GET /api/jobs/logs?page=0&size=20
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<JobExecutionLog> logPage = logRepository
                .findAllByOrderByStartTimeDesc(PageRequest.of(page, size));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("logs", logPage.getContent().stream()
                .map(this::logToMap)
                .toList());
        response.put("currentPage", logPage.getNumber());
        response.put("totalPages", logPage.getTotalPages());
        response.put("totalElements", logPage.getTotalElements());
        response.put("pageSize", logPage.getSize());

        return ResponseEntity.ok(response);
    }

    /**
     * Get execution summary statistics.
     *
     * GET /api/jobs/logs/summary
     */
    @GetMapping("/logs/summary")
    public ResponseEntity<Map<String, Object>> getExecutionSummary() {
        List<JobExecutionLog> allLogs = logRepository.findAll();

        long total = allLogs.size();
        long success = allLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long failed = allLogs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();
        double avgDurationMs = allLogs.stream()
                .filter(l -> l.getExecutionDurationMs() != null)
                .mapToLong(JobExecutionLog::getExecutionDurationMs)
                .average()
                .orElse(0.0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalExecutions", total);
        summary.put("successCount", success);
        summary.put("failedCount", failed);
        summary.put("successRate", total > 0 ? String.format("%.1f%%", (success * 100.0) / total) : "N/A");
        summary.put("averageDurationMs", String.format("%.0f", avgDurationMs));

        return ResponseEntity.ok(summary);
    }

    // ── HELPERS ─────────────────────────────────────────────────────

    private Class<? extends Job> resolveJobClass(String type) {
        return switch (type.toUpperCase().trim()) {
            case "EMAIL"         -> EmailJob.class;
            case "PDF_REPORT"    -> PdfReportJob.class;
            case "KAFKA_PUBLISH" -> KafkaPublishJob.class;
            default              -> null;
        };
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Map<String, Object> logToMap(JobExecutionLog executionLog) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", executionLog.getId());
        map.put("jobName", executionLog.getJobName());
        map.put("jobGroup", executionLog.getJobGroup());
        map.put("status", executionLog.getStatus());
        map.put("startTime", executionLog.getStartTime());
        map.put("endTime", executionLog.getEndTime());
        map.put("executionDurationMs", executionLog.getExecutionDurationMs());
        map.put("retryCount", executionLog.getRetryCount());
        map.put("errorMessage", executionLog.getErrorMessage());
        return map;
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
            @NotBlank(message = "Job type is required (EMAIL, PDF_REPORT, KAFKA_PUBLISH)")
            String type,
            String group,
            String description,
            String cronExpression
    ) {}
}
