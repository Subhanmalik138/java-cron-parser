package com.subhanmalik.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CronExpressionTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private static ZonedDateTime at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), UTC);
    }

    // ---- parsing ---------------------------------------------------------

    @Test
    void parsesWildcardEveryMinute() {
        CronExpression cron = CronExpression.parse("* * * * *");
        assertTrue(cron.matches(at(2026, 10, 15, 13, 37)));
    }

    @Test
    void parsesStepsRangesAndLists() {
        CronExpression cron = CronExpression.parse("*/15 9-17 1,15 * *");
        assertTrue(cron.matches(at(2026, 10, 15, 10, 30))); // 30 matches */15
        assertFalse(cron.matches(at(2026, 10, 15, 10, 31)));
        assertTrue(cron.matches(at(2026, 10, 15, 9, 0)));  // range start
        assertTrue(cron.matches(at(2026, 10, 15, 17, 45))); // range end
        assertFalse(cron.matches(at(2026, 10, 15, 18, 0)));
    }

    @Test
    void parsesNamesCaseInsensitively() {
        CronExpression cron = CronExpression.parse("0 12 * JAN,FEB mon");
        assertTrue(cron.matches(at(2026, 1, 5, 12, 0)));  // Monday in January
        assertFalse(cron.matches(at(2026, 3, 2, 12, 0)));  // Monday but March
    }

    @Test
    void sundayMayBeZeroOrSeven() {
        CronExpression zero = CronExpression.parse("0 0 * * 0");
        CronExpression seven = CronExpression.parse("0 0 * * 7");
        ZonedDateTime sunday = at(2026, 10, 18, 0, 0); // a Sunday
        assertTrue(zero.matches(sunday));
        assertTrue(seven.matches(sunday));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@daily", "@midnight"})
    void dailyAliasesExpand(String alias) {
        CronExpression cron = CronExpression.parse(alias);
        assertTrue(cron.matches(at(2026, 10, 15, 0, 0)));
        assertFalse(cron.matches(at(2026, 10, 15, 0, 1)));
    }

    @Test
    void hourlyAliasExpands() {
        CronExpression cron = CronExpression.parse("@hourly");
        assertTrue(cron.matches(at(2026, 10, 15, 14, 0)));
        assertFalse(cron.matches(at(2026, 10, 15, 14, 30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "* * * *", "* * * * * *",
            "61 * * * *",          // minute out of range
            "0 24 * * *",          // hour out of range
            "0 0 0 * *",           // day-of-month out of range
            "0 0 * 13 *",          // month out of range
            "0 0 * * 8",           // day-of-week out of range
            "0 0 * * FOO",         // unknown name
            "5-2 * * * *",         // reversed range
            "@reboot", "@sometimes"
    })
    void invalidExpressionsThrow(String expression) {
        assertThrows(CronParserException.class, () -> CronExpression.parse(expression));
    }

    // ---- day-of-month / day-of-week semantics -----------------------------

    @Test
    void domAndDowCombineWithOrWhenBothRestricted() {
        // 13th of the month OR any Friday
        CronExpression cron = CronExpression.parse("0 0 13 * 5");
        assertTrue(cron.matches(at(2026, 11, 13, 0, 0))); // Friday the 13th
        assertTrue(cron.matches(at(2026, 10, 16, 0, 0))); // a Friday, not the 13th
        assertTrue(cron.matches(at(2026, 10, 13, 0, 0))); // the 13th, a Tuesday
        assertFalse(cron.matches(at(2026, 10, 14, 0, 0))); // neither
    }

    @Test
    void wildcardDomDefersToDow() {
        CronExpression cron = CronExpression.parse("0 9 * * MON-FRI");
        assertTrue(cron.matches(at(2026, 10, 15, 9, 0)));  // Thursday
        assertFalse(cron.matches(at(2026, 10, 17, 9, 0))); // Saturday
    }

    // ---- nextRun ----------------------------------------------------------

    @Test
    void nextRunIsStrictlyAfterFrom() {
        CronExpression cron = CronExpression.parse("* * * * *");
        ZonedDateTime from = at(2026, 10, 15, 13, 37);
        assertEquals(at(2026, 10, 15, 13, 38), cron.nextRun(from));
    }

    @Test
    void nextRunSkipsToNextMatchingMinute() {
        CronExpression cron = CronExpression.parse("*/20 * * * *");
        assertEquals(at(2026, 10, 15, 13, 40),
                cron.nextRun(at(2026, 10, 15, 13, 37)));
    }

    @Test
    void nextRunSkipsWeekendForWeekdaySchedule() {
        CronExpression cron = CronExpression.parse("0 9 * * MON-FRI");
        // Friday 2026-10-16 09:00 -> next is Monday 2026-10-19 09:00
        assertEquals(at(2026, 10, 19, 9, 0),
                cron.nextRun(at(2026, 10, 16, 9, 0)));
    }

    @Test
    void nextRunHandlesLeapDay() {
        CronExpression cron = CronExpression.parse("0 0 29 2 *");
        // From Oct 2026, the next Feb 29 is 2028-02-29
        assertEquals(at(2028, 2, 29, 0, 0),
                cron.nextRun(at(2026, 10, 15, 0, 0)));
    }

    @Test
    void nextRunRollsOverYearBoundary() {
        CronExpression cron = CronExpression.parse("0 0 1 1 *");
        assertEquals(at(2027, 1, 1, 0, 0),
                cron.nextRun(at(2026, 12, 31, 23, 59)));
    }

    @Test
    void impossibleDateThrowsComputationException() {
        CronExpression cron = CronExpression.parse("0 0 30 2 *"); // Feb 30 never exists
        assertThrows(CronComputationException.class,
                () -> cron.nextRun(at(2026, 10, 15, 0, 0)));
    }

    @Test
    void nextRunsReturnsAscendingSequence() {
        CronExpression cron = CronExpression.parse("0 12 * * *");
        List<ZonedDateTime> runs = cron.nextRuns(at(2026, 10, 15, 13, 0), 3);
        assertEquals(3, runs.size());
        assertEquals(at(2026, 10, 16, 12, 0), runs.get(0));
        assertEquals(at(2026, 10, 17, 12, 0), runs.get(1));
        assertEquals(at(2026, 10, 18, 12, 0), runs.get(2));
    }

    // ---- describe ---------------------------------------------------------

    @Test
    void describesCommonSchedules() {
        assertEquals("Every minute", CronExpression.parse("* * * * *").describe());
        assertEquals("Every 15 minutes", CronExpression.parse("*/15 * * * *").describe());
        assertEquals("At 02:30 on weekdays",
                CronExpression.parse("30 2 * * MON-FRI").describe());
        assertEquals("At 09:00 on the 1st of every month",
                CronExpression.parse("0 9 1 * *").describe());
        assertEquals("At 00:00 on Sundays",
                CronExpression.parse("0 0 * * 0").describe());
        assertEquals("At 09:00 every day in January, March",
                CronExpression.parse("0 9 * 1,3 *").describe());
    }

    @Test
    void describesHourlyAtFixedMinute() {
        assertEquals("At minute 30 past every hour",
                CronExpression.parse("30 * * * *").describe());
    }
}
