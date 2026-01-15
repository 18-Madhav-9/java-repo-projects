package com.taskscheduler.controller;

import com.taskscheduler.model.JobExecutionLog;
import com.taskscheduler.repository.JobExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for job execution history and monitoring.
 *
 * Provides paginated access to execution logs for admin dashboards
 * and operational monitoring. Supports filtering by job ID and status.
 */
@RestController
@RequestMapping("/jobs")
public class ExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ExecutionController.class);

    private final JobExecutionLogRepository logRepository;

    public ExecutionController(JobExecutionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * Get paginated execution history.
     *
     * GET /jobs/logs?page=0&size=20
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
     * Get execution history for a specific job.
     *
     * GET /jobs/{jobId}/logs
     */
    @GetMapping("/{jobId}/logs")
    public ResponseEntity<List<Map<String, Object>>> getJobExecutionLogs(
            @PathVariable Long jobId) {

        List<Map<String, Object>> logs = logRepository
                .findByJobIdOrderByStartTimeDesc(jobId)
                .stream()
                .map(this::logToMap)
                .toList();

        return ResponseEntity.ok(logs);
    }

    /**
     * Get execution logs filtered by status.
     *
     * GET /jobs/logs/status/{status}
     * status: SUCCESS or FAILED
     */
    @GetMapping("/logs/status/{status}")
    public ResponseEntity<List<Map<String, Object>>> getLogsByStatus(
            @PathVariable String status) {

        String normalizedStatus = status.toUpperCase().trim();
        if (!"SUCCESS".equals(normalizedStatus) && !"FAILED".equals(normalizedStatus)) {
            return ResponseEntity.badRequest().build();
        }

        List<Map<String, Object>> logs = logRepository
                .findByStatusOrderByStartTimeDesc(normalizedStatus)
                .stream()
                .map(this::logToMap)
                .toList();

        return ResponseEntity.ok(logs);
    }

    /**
     * Get execution summary statistics.
     *
     * GET /jobs/logs/summary
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

    private Map<String, Object> logToMap(JobExecutionLog executionLog) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", executionLog.getId());
        map.put("jobId", executionLog.getJobId());
        map.put("jobName", executionLog.getJobName());
        map.put("status", executionLog.getStatus());
        map.put("startTime", executionLog.getStartTime());
        map.put("endTime", executionLog.getEndTime());
        map.put("executionDurationMs", executionLog.getExecutionDurationMs());
        map.put("errorMessage", executionLog.getErrorMessage());
        return map;
    }
}
