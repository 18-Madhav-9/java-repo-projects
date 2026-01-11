package com.taskscheduler.service.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Business service for report data access and computation.
 *
 * In production this would query actual revenue tables, user metrics,
 * and KPI data. For V1, it generates realistic simulated data to
 * demonstrate the pipeline without external dependencies.
 *
 * V2: Wire to real data sources + caching layer.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private final Random random = new Random();

    /**
     * Simulate fetching raw revenue data from the database.
     *
     * @return list of revenue entries by region
     */
    public List<Map<String, Object>> fetchRevenueData() {
        log.info("Fetching revenue data from database...");
        simulateLatency(300);

        List<Map<String, Object>> data = new ArrayList<>();
        String[] regions = {"North America", "Europe", "Asia Pacific", "Latin America"};

        for (String region : regions) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("region", region);
            entry.put("revenue", BigDecimal.valueOf(random.nextDouble() * 1_000_000)
                    .setScale(2, RoundingMode.HALF_UP));
            entry.put("transactions", random.nextInt(5000) + 1000);
            entry.put("date", LocalDate.now());
            data.add(entry);
        }

        log.info("Fetched {} revenue records", data.size());
        return data;
    }

    /**
     * Calculate summary metrics from raw data.
     *
     * @param data raw revenue data
     * @return computed summary metrics
     */
    public Map<String, Object> calculateMetrics(List<Map<String, Object>> data) {
        log.info("Calculating revenue metrics...");

        BigDecimal totalRevenue = data.stream()
                .map(entry -> (BigDecimal) entry.get("revenue"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalTransactions = data.stream()
                .mapToInt(entry -> (int) entry.get("transactions"))
                .sum();

        BigDecimal avgRevenuePerRegion = totalRevenue
                .divide(BigDecimal.valueOf(data.size()), 2, RoundingMode.HALF_UP);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalRevenue", totalRevenue);
        metrics.put("totalTransactions", totalTransactions);
        metrics.put("avgRevenuePerRegion", avgRevenuePerRegion);
        metrics.put("numberOfRegions", data.size());
        metrics.put("reportDate", LocalDate.now());

        log.info("Metrics calculated — Total Revenue: ${}", totalRevenue);
        return metrics;
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
