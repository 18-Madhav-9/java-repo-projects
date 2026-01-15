package com.taskscheduler.util;

import org.springframework.scheduling.support.CronExpression;

/**
 * Utility methods for cron expression handling.
 *
 * V1: Provides basic validation. The cron field on ScheduledJob is a
 * placeholder and is not yet used by the scheduler engine.
 *
 * V2: Will be used by Quartz integration to validate and compute
 * next-fire-times for jobs.
 */
public final class CronUtils {

    private CronUtils() {
        // Utility class — no instantiation
    }

    /**
     * Validate whether a cron expression is syntactically correct.
     *
     * @param expression the cron expression to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            CronExpression.parse(expression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parse a cron expression with validation.
     *
     * @param expression the cron expression
     * @return parsed CronExpression
     * @throws IllegalArgumentException if expression is invalid
     */
    public static CronExpression parse(String expression) {
        if (!isValid(expression)) {
            throw new IllegalArgumentException("Invalid cron expression: " + expression);
        }
        return CronExpression.parse(expression);
    }

    /**
     * Return a human-readable description of common cron patterns.
     * Useful for API responses and admin dashboards.
     */
    public static String describe(String expression) {
        if (expression == null) return "No schedule configured";

        return switch (expression) {
            case "0 * * * * *"     -> "Every minute";
            case "0 0 * * * *"     -> "Every hour";
            case "0 0 0 * * *"     -> "Daily at midnight";
            case "0 0 9 * * MON-FRI" -> "Weekdays at 9 AM";
            default -> "Custom: " + expression;
        };
    }
}
