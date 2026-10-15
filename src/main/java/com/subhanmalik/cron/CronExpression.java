package com.subhanmalik.cron;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;

/**
 * A parsed 5-field cron expression:
 * {@code minute hour day-of-month month day-of-week}.
 *
 * <p>Instances are immutable and thread-safe. Schedules resolve at minute
 * precision. Day-of-month and day-of-week follow standard cron semantics:
 * if both are restricted, a day matches when <em>either</em> matches;
 * if one is a wildcard, the other decides.
 */
public final class CronExpression {

    private static final Map<String, Integer> MONTH_NAMES = CronField.nameMap(
            "JAN=1", "FEB=2", "MAR=3", "APR=4", "MAY=5", "JUN=6",
            "JUL=7", "AUG=8", "SEP=9", "OCT=10", "NOV=11", "DEC=12");
    private static final Map<String, Integer> DOW_NAMES = CronField.nameMap(
            "SUN=0", "MON=1", "TUE=2", "WED=3", "THU=4", "FRI=5", "SAT=6");

    /** Special aliases such as {@code @daily} and {@code @hourly}. */
    private static final Map<String, String> ALIASES = Map.of(
            "YEARLY", "0 0 1 1 *",
            "ANNUALLY", "0 0 1 1 *",
            "MONTHLY", "0 0 1 * *",
            "WEEKLY", "0 0 * * 0",
            "DAILY", "0 0 * * *",
            "MIDNIGHT", "0 0 * * *",
            "HOURLY", "0 * * * *");

    /** How far ahead {@link #nextRun} looks before giving up (years). */
    private static final int SEARCH_YEARS = 5;

    private final CronField minute;
    private final CronField hour;
    private final CronField dayOfMonth;
    private final CronField month;
    private final CronField dayOfWeek;
    private final String source;

    private CronExpression(String source, CronField minute, CronField hour,
                           CronField dayOfMonth, CronField month, CronField dayOfWeek) {
        this.source = source;
        this.minute = minute;
        this.hour = hour;
        this.dayOfMonth = dayOfMonth;
        this.month = month;
        this.dayOfWeek = dayOfWeek;
    }

    /**
     * Parses a 5-field cron expression, e.g. {@code "0/15 9-17 * * MON-FRI"}.
     * Also accepts {@code @daily}, {@code @hourly}, {@code @weekly},
     * {@code @monthly}, {@code @yearly}/{@code @annually} and {@code @midnight}.
     *
     * @throws CronParserException if the expression is malformed
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new CronParserException("(empty)", "expression must not be blank");
        }
        String normalized = expression.trim();
        if (normalized.startsWith("@")) {
            String alias = normalized.substring(1).toUpperCase(Locale.ROOT);
            if (alias.equals("REBOOT")) {
                throw new CronParserException(expression,
                        "'@reboot' runs once at startup and is not a schedule");
            }
            String expansion = ALIASES.get(alias);
            if (expansion == null) {
                throw new CronParserException(expression,
                        "unsupported alias '" + normalized + "' (did you mean @daily, @hourly, …?)");
            }
            normalized = expansion;
        }
        String[] fields = normalized.split("\\s+");
        if (fields.length != 5) {
            throw new CronParserException(expression,
                    "expected 5 fields (minute hour day-of-month month day-of-week), got "
                            + fields.length);
        }
        try {
            CronField minute = CronField.parse(fields[0], 0, 59, null);
            CronField hour = CronField.parse(fields[1], 0, 23, null);
            CronField dayOfMonth = CronField.parse(fields[2], 1, 31, null);
            CronField month = CronField.parse(fields[3], 1, 12, MONTH_NAMES);
            CronField dayOfWeek = CronField.parse(fields[4], 0, 7, DOW_NAMES, true);
            return new CronExpression(expression.trim(), minute, hour, dayOfMonth, month, dayOfWeek);
        } catch (CronParserException e) {
            throw new CronParserException(expression, e.getMessage());
        }
    }

    /** Whether {@code time} (truncated to the minute) matches this schedule. */
    public boolean matches(ZonedDateTime time) {
        ZonedDateTime t = time.withSecond(0).withNano(0);
        return minute.matches(t.getMinute())
                && hour.matches(t.getHour())
                && month.matches(t.getMonthValue())
                && dayMatches(t.toLocalDate());
    }

    /**
     * The first run strictly after {@code from}.
     *
     * @throws CronComputationException if no run occurs within
     *         {@value #SEARCH_YEARS} years (e.g. {@code "0 0 30 2 *"})
     */
    public ZonedDateTime nextRun(ZonedDateTime from) {
        ZonedDateTime cursor = from.plusMinutes(1).withSecond(0).withNano(0);
        ZonedDateTime limit = from.plusYears(SEARCH_YEARS);
        while (cursor.isBefore(limit)) {
            if (month.matches(cursor.getMonthValue()) && dayMatches(cursor.toLocalDate())) {
                ZonedDateTime run = nextTimeOnDay(cursor);
                if (run != null) {
                    return run;
                }
            }
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay(cursor.getZone());
        }
        throw new CronComputationException(
                "No run of '" + source + "' within " + SEARCH_YEARS + " years of " + from);
    }

    /** The next {@code n} runs after {@code from}, ascending. */
    public List<ZonedDateTime> nextRuns(ZonedDateTime from, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1");
        }
        List<ZonedDateTime> runs = new ArrayList<>(n);
        ZonedDateTime cursor = from;
        for (int i = 0; i < n; i++) {
            cursor = nextRun(cursor);
            runs.add(cursor);
        }
        return runs;
    }

    /** Standard cron day rule: restricted fields combine with OR; a wildcard defers. */
    private boolean dayMatches(LocalDate date) {
        boolean domWild = dayOfMonth.isWildcard();
        boolean dowWild = dayOfWeek.isWildcard();
        if (domWild && dowWild) {
            return true;
        }
        if (domWild) {
            return dayOfWeek.matches(toCronDow(date.getDayOfWeek()));
        }
        if (dowWild) {
            return dayOfMonth.matches(date.getDayOfMonth());
        }
        return dayOfMonth.matches(date.getDayOfMonth())
                || dayOfWeek.matches(toCronDow(date.getDayOfWeek()));
    }

    private static int toCronDow(DayOfWeek dow) {
        return dow.getValue() % 7; // MONDAY(1)..SATURDAY(6), SUNDAY(7)->0
    }

    /** First matching minute at-or-after {@code dayCursor} on its own day. */
    private ZonedDateTime nextTimeOnDay(ZonedDateTime dayCursor) {
        LocalDate date = dayCursor.toLocalDate();
        int startHour = dayCursor.getHour();
        int startMinute = dayCursor.getMinute();
        for (int h : hour.values()) {
            if (h < startHour) {
                continue;
            }
            int minuteFloor = (h == startHour) ? startMinute : 0;
            NavigableSet<Integer> minutes = minute.values().tailSet(minuteFloor, true);
            for (int m : minutes) {
                return date.atTime(h, m).atZone(dayCursor.getZone());
            }
        }
        return null;
    }

    /**
     * A human-readable summary, e.g. {@code "At 02:30 on weekdays"}
     * or {@code "Every 15 minutes"}.
     */
    public String describe() {
        String time = describeTime();
        String days = describeDays();
        String months = describeMonths();
        StringBuilder sb = new StringBuilder(time);
        if (!days.isEmpty()) {
            sb.append(' ').append(days);
        }
        if (!months.isEmpty()) {
            sb.append(' ').append(months);
        }
        String text = sb.toString();
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private String describeTime() {
        if (hour.isWildcard() && minute.isWildcard()) {
            return "every minute";
        }
        if (hour.isWildcard()) {
            if (minute.isEvenlySpaced() && minute.values().first() == 0) {
                return "every " + minute.step() + " minutes";
            }
            if (minute.values().size() == 1) {
                return "at minute " + minute.values().first() + " past every hour";
            }
            return "at minutes " + joinInts(minute.values()) + " past every hour";
        }
        if (minute.values().size() == 1) {
            int m = minute.values().first();
            if (hour.values().size() == 1) {
                return "at " + twoDigits(hour.values().first()) + ":" + twoDigits(m);
            }
            return "at minute " + m + " past " + describeHourSet();
        }
        return "at minutes " + joinInts(minute.values()) + " of " + describeHourSet();
    }

    private String describeHourSet() {
        if (hour.isEvenlySpaced() && hour.values().size() > 2) {
            return "every " + hour.step() + " hours";
        }
        if (hour.values().size() == 1) {
            return "hour " + hour.values().first();
        }
        return "hours " + joinInts(hour.values());
    }

    private String describeDays() {
        boolean domWild = dayOfMonth.isWildcard();
        boolean dowWild = dayOfWeek.isWildcard();
        if (domWild && dowWild) {
            // sub-daily cadences ("every 15 minutes") already imply every day
            return hour.isWildcard() ? "" : "every day";
        }
        if (!domWild && dowWild) {
            return "on the " + joinOrdinals(dayOfMonth.values()) + " of every month";
        }
        if (domWild) {
            return "on " + describeDowSet(dayOfWeek.values());
        }
        return "on the " + joinOrdinals(dayOfMonth.values())
                + " or on " + describeDowSet(dayOfWeek.values());
    }

    private String describeDowSet(NavigableSet<Integer> values) {
        if (values.equals(java.util.Set.of(1, 2, 3, 4, 5))) {
            return "weekdays";
        }
        if (values.equals(java.util.Set.of(0, 6))) {
            return "weekends";
        }
        List<String> names = new ArrayList<>();
        for (int v : values) {
            DayOfWeek dow = (v == 0) ? DayOfWeek.SUNDAY : DayOfWeek.of(v);
            names.add(dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s");
        }
        return String.join(", ", names);
    }

    private String describeMonths() {
        if (month.isWildcard()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (int v : month.values()) {
            names.add(Month.of(v).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        return "in " + String.join(", ", names);
    }

    private static String joinInts(NavigableSet<Integer> values) {
        List<String> parts = new ArrayList<>();
        for (int v : values) {
            parts.add(String.valueOf(v));
        }
        return String.join(", ", parts);
    }

    private static String joinOrdinals(NavigableSet<Integer> values) {
        List<String> parts = new ArrayList<>();
        for (int v : values) {
            parts.add(ordinal(v));
        }
        return String.join(", ", parts);
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    private static String twoDigits(int n) {
        return String.format("%02d", n);
    }

    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return "CronExpression[" + source + "]";
    }
}
