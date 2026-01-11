package com.taskscheduler.service.report;

import com.taskscheduler.model.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full report generation pipeline.
 *
 * Pipeline stages:
 *   1. Fetch raw data   →  ReportService.fetchRevenueData()
 *   2. Compute metrics  →  ReportService.calculateMetrics()
 *   3. Render output    →  formatReport() (text for V1; PDF for V2)
 *
 * V2: Output to PDF via iText/Apache PDFBox and upload to S3.
 */
@Service
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    private final ReportService reportService;

    public ReportGenerator(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Execute the full report generation pipeline for a scheduled job.
     *
     * @param job the scheduled job metadata
     */
    public void generateReport(ScheduledJob job) {
        log.info("═══════════════════════════════════════════════");
        log.info("  REPORT GENERATION STARTED: {}", job.getName());
        log.info("═══════════════════════════════════════════════");

        // Stage 1: Data acquisition
        List<Map<String, Object>> rawData = reportService.fetchRevenueData();

        // Stage 2: Metric computation
        Map<String, Object> metrics = reportService.calculateMetrics(rawData);

        // Stage 3: Render report
        String report = formatReport(job, rawData, metrics);

        log.info("\n{}", report);
        log.info("═══════════════════════════════════════════════");
        log.info("  REPORT GENERATION COMPLETED: {}", job.getName());
        log.info("═══════════════════════════════════════════════");
    }

    private String formatReport(ScheduledJob job,
                                List<Map<String, Object>> data,
                                Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════╗\n");
        sb.append("║         REVENUE REPORT                       ║\n");
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Job:   %-38s ║\n", job.getName()));
        sb.append(String.format("║ Date:  %-38s ║\n", metrics.get("reportDate")));
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append("║ REGIONAL BREAKDOWN                           ║\n");
        sb.append("╠──────────────────────────────────────────────╣\n");

        for (Map<String, Object> entry : data) {
            sb.append(String.format("║ %-18s $%-12s  Txn: %-5s ║\n",
                    entry.get("region"),
                    entry.get("revenue"),
                    entry.get("transactions")));
        }

        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append("║ SUMMARY                                      ║\n");
        sb.append("╠──────────────────────────────────────────────╣\n");
        sb.append(String.format("║ Total Revenue:       $%-23s ║\n", metrics.get("totalRevenue")));
        sb.append(String.format("║ Total Transactions:  %-24s ║\n", metrics.get("totalTransactions")));
        sb.append(String.format("║ Avg/Region:          $%-23s ║\n", metrics.get("avgRevenuePerRegion")));
        sb.append("╚══════════════════════════════════════════════╝\n");

        return sb.toString();
    }
}
