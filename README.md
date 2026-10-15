# Cron Expression Parser (Java)

A zero-dependency Java library that parses 5-field cron expressions, computes
upcoming run times, and explains schedules in plain English. Handy anywhere you
schedule jobs but don't want to drag in a full scheduler framework — CLI tools,
config validators, dashboard widgets.

```java
CronExpression cron = CronExpression.parse("*/15 9-17 * * MON-FRI");

cron.describe();                       // "Every 15 minutes on weekdays"
cron.nextRun(ZonedDateTime.now());     // next run after now
cron.nextRuns(ZonedDateTime.now(), 5); // next 5 runs
cron.matches(someInstant);             // does it run at this minute?
```

There's also a small demo CLI:

```
$ java CronCli "30 2 * * MON-FRI" 3
At 02:30 on weekdays
Next 3 run(s) from Thu 2026-10-15 14:20 PKT:
  1. Fri 2026-10-16 02:30 PKT
  2. Mon 2026-10-19 02:30 PKT
  3. Tue 2026-10-20 02:30 PKT
```

## Supported syntax

```
minute hour day-of-month month day-of-week
  0-59  0-23      1-31      1-12   0-7 (0 and 7 are both Sunday)
```

Per field: `*`, `*/n` steps, `a-b` ranges, `a-b/n`, `a,b,c` lists, and names
(`JAN`–`DEC`, `MON`–`SUN`, case-insensitive). Aliases: `@hourly`, `@daily`,
`@midnight`, `@weekly`, `@monthly`, `@yearly`/`@annually`.

Day-of-month and day-of-week follow standard cron semantics: if both are
restricted, a day matches when **either** matches; if one is a wildcard,
the other decides. So `0 0 13 * 5` runs on the 13th *or* any Friday.

## Design decisions

1. **Zero runtime dependencies** — the whole library is pure `java.time` plus
   collections. Nothing to shade, nothing to go stale; it compiles on any
   JDK 17+.
2. **Day-level search for `nextRun`** — instead of stepping minute by minute,
   the search walks one matching day at a time and then picks the first
   matching hour:minute on that day. Even a once-a-year schedule resolves in
   milliseconds, with a hard 5-year cap that turns impossible dates
   (`0 0 30 2 *`) into a clear `CronComputationException` instead of a hang.
3. **Fail-fast, specific errors** — parse problems throw `CronParserException`
   naming the offending part (`value 61 in '61' out of range [0-59]`);
   out-of-range, reversed ranges, unknown names, and wrong field counts are
   all caught at parse time.
4. **Immutability** — `CronExpression` instances are immutable and thread-safe,
   so a parsed schedule can be shared across worker threads without locking.

## Run it

Requirements: JDK 17+.

```bash
# compile
javac -d out $(find src/main/java -name '*.java')

# demo CLI
java -cp out com.subhanmalik.cron.CronCli "*/15 9-17 * * MON-FRI" 5
```

## Run the tests

JUnit 5 via Maven, or plain `javac`/`java` with the JUnit platform console:

```bash
# with Maven
mvn test

# without Maven (needs junit-platform-console-standalone on hand)
javac -cp junit-platform-console-standalone.jar -d out \
    $(find src/main/java src/test/java -name '*.java')
java -jar junit-platform-console-standalone.jar \
    --class-path out --scan-classpath
```

## Tech stack

- Java 17, no runtime dependencies
- JUnit 5 (Jupiter) for tests
- Maven (`pom.xml`) for the standard build

## License

MIT — see [LICENSE](LICENSE).
