package com.subhanmalik.cron;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Demo CLI: prints a plain-English description of a cron expression and its
 * next scheduled runs.
 *
 * <p>Usage: {@code java CronCli "<expression>" [count] [--from yyyy-MM-ddTHH:mm]}
 */
public final class CronCli {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm z");

    private CronCli() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
        }
        String expression = args[0];
        int count = 5;
        ZonedDateTime from = ZonedDateTime.now();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--from") || arg.startsWith("--from=")) {
                String value = arg.startsWith("--from=")
                        ? arg.substring("--from=".length())
                        : nextArg(args, i++, "--from");
                from = LocalDateTime.parse(value).atZone(ZoneId.systemDefault());
            } else {
                try {
                    count = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("Unexpected argument: " + arg);
                    usage();
                }
                if (count < 1 || count > 100) {
                    System.err.println("count must be between 1 and 100");
                    System.exit(2);
                }
            }
        }
        try {
            CronExpression cron = CronExpression.parse(expression);
            System.out.println(cron.describe());
            System.out.println("Next " + count + " run(s) from " + FMT.format(from) + ":");
            List<ZonedDateTime> runs = cron.nextRuns(from, count);
            for (int i = 0; i < runs.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, FMT.format(runs.get(i)));
            }
        } catch (CronParserException | CronComputationException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String nextArg(String[] args, int i, String flag) {
        if (i + 1 >= args.length) {
            System.err.println(flag + " needs a value");
            System.exit(2);
        }
        return args[i + 1];
    }

    private static void usage() {
        System.err.println("Usage: CronCli \"<minute hour dom month dow>\" [count] "
                + "[--from yyyy-MM-ddTHH:mm]");
        System.exit(2);
    }
}
