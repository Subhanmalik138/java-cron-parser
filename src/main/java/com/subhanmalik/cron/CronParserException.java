package com.subhanmalik.cron;

/**
 * Thrown when a cron expression (or one of its fields) cannot be parsed.
 */
public class CronParserException extends IllegalArgumentException {

    public CronParserException(String message) {
        super(message);
    }

    public CronParserException(String message, Throwable cause) {
        super(message, cause);
    }

    public CronParserException(String expression, String reason) {
        super("Invalid cron expression '" + expression + "': " + reason);
    }
}
