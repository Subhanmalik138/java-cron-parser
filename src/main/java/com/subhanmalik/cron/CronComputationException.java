package com.subhanmalik.cron;

/**
 * Thrown when no scheduled run can be found for an expression —
 * e.g. {@code "0 0 30 2 *"} never matches a real calendar date,
 * or no run occurs inside the search window.
 */
public class CronComputationException extends RuntimeException {

    public CronComputationException(String message) {
        super(message);
    }
}
