package com.subhanmalik.cron;

import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One parsed cron field: the sorted set of values it allows inside its
 * range, plus whether the field was effectively a wildcard.
 *
 * <p>Supported syntax per field:
 * <ul>
 *   <li>{@code *} — every value in the range</li>
 *   <li>{@code *&#47;n} — every n-th value</li>
 *   <li>{@code a-b} — inclusive range</li>
 *   <li>{@code a-b/n} — every n-th value inside the range</li>
 *   <li>{@code a,b,c} — comma-separated list of any of the above</li>
 *   <li>names where the field has them ({@code MON}, {@code JAN}, …)</li>
 * </ul>
 */
public final class CronField {

    private final NavigableSet<Integer> values;
    private final boolean wildcard;

    private CronField(NavigableSet<Integer> values, boolean wildcard) {
        this.values = values;
        this.wildcard = wildcard;
    }

    /** Parses a single field token. */
    public static CronField parse(String token, int min, int max, Map<String, Integer> names) {
        return parse(token, min, max, names, false);
    }

    /** Parses a single field token; {@code normalizeDow7} maps a 7 to 0 (Sunday). */
    public static CronField parse(String token, int min, int max, Map<String, Integer> names,
                                  boolean normalizeDow7) {
        if (token == null || token.isBlank()) {
            throw new CronParserException("empty field");
        }
        boolean wasWildcard = token.equals("*");
        NavigableSet<Integer> values = new TreeSet<>();
        for (String part : token.split(",")) {
            expandPart(part.trim(), min, max, names, values);
        }
        if (values.isEmpty()) {
            throw new CronParserException("field '" + token + "' matched no values");
        }
        if (normalizeDow7 && values.contains(7)) {
            values.remove(7);
            values.add(0);
        }
        boolean wildcard = wasWildcard || values.size() == (max - min + 1);
        return new CronField(values, wildcard);
    }

    private static void expandPart(String part, int min, int max,
                                   Map<String, Integer> names, NavigableSet<Integer> out) {
        if (part.isEmpty()) {
            throw new CronParserException("empty list element in field");
        }
        int slash = part.indexOf('/');
        String rangePart = (slash >= 0) ? part.substring(0, slash) : part;
        int step = 1;
        if (slash >= 0) {
            step = parsePositiveInt(part.substring(slash + 1), "step");
        }

        int start, end;
        if (rangePart.equals("*") || rangePart.isEmpty()) {
            start = min;
            end = max;
        } else {
            int dash = rangePart.indexOf('-');
            if (dash >= 0) {
                start = resolveValue(rangePart.substring(0, dash), names);
                end = resolveValue(rangePart.substring(dash + 1), names);
                if (start > end) {
                    throw new CronParserException(
                            "reversed range '" + rangePart + "' (start > end)");
                }
            } else {
                int value = resolveValue(rangePart, names);
                if (slash < 0) {
                    checkBounds(value, min, max, part);
                    out.add(value);
                    return;
                }
                start = value;
                end = max;
            }
        }
        checkBounds(start, min, max, part);
        checkBounds(end, min, max, part);
        for (int v = start; v <= end; v += step) {
            out.add(v);
        }
    }

    private static int resolveValue(String token, Map<String, Integer> names) {
        if (names != null) {
            Integer named = names.get(token.toUpperCase(Locale.ROOT));
            if (named != null) {
                return named;
            }
        }
        return parsePositiveInt(token, "value");
    }

    private static int parsePositiveInt(String token, String what) {
        try {
            int value = Integer.parseInt(token.trim());
            if (value < 0) {
                throw new CronParserException("negative " + what + " '" + token + "'");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new CronParserException("not a number: '" + token + "'", e);
        }
    }

    private static void checkBounds(int value, int min, int max, String part) {
        if (value < min || value > max) {
            throw new CronParserException(
                    "value " + value + " in '" + part + "' out of range [" + min + "-" + max + "]");
        }
    }

    /** Builds a case-insensitive name map from enum-like entries (JAN→1, …). */
    public static Map<String, Integer> nameMap(String... entries) {
        Map<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String entry : entries) {
            String[] kv = entry.split("=");
            map.put(kv[0], Integer.parseInt(kv[1]));
        }
        return Map.copyOf(map);
    }

    /** The allowed values, ascending. */
    public NavigableSet<Integer> values() {
        return new TreeSet<>(values);
    }

    /** Whether the field covers its whole range (explicitly or via {@code *}). */
    public boolean isWildcard() {
        return wildcard;
    }

    /** Whether {@code value} is in this field's set. */
    public boolean matches(int value) {
        return values.contains(value);
    }

    /** Whether the values form an evenly spaced sequence (used by descriptions). */
    public boolean isEvenlySpaced() {
        if (values.size() < 2) {
            return false;
        }
        Integer[] arr = values.toArray(Integer[]::new);
        int step = arr[1] - arr[0];
        for (int i = 2; i < arr.length; i++) {
            if (arr[i] - arr[i - 1] != step) {
                return false;
            }
        }
        return true;
    }

    /** The step of an evenly spaced field. */
    public int step() {
        Integer[] arr = values.toArray(Integer[]::new);
        return arr[1] - arr[0];
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
