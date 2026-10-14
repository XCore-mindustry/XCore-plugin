package org.xcore.plugin.cases;

public sealed interface ReportResult {
    record Sent() implements ReportResult {}
    record Cooldown(long seconds) implements ReportResult {}
    record SameTarget() implements ReportResult {}
    record DailyLimit() implements ReportResult {}
    record InvalidTarget() implements ReportResult {}
    record InsufficientPlaytime(long remainingMinutes) implements ReportResult {}
    record Disabled() implements ReportResult {}
    record Failed() implements ReportResult {}
}
