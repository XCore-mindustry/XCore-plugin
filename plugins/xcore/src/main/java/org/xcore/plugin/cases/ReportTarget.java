package org.xcore.plugin.cases;

/** UUID is a server-side selection reference; render only name, pid and online/leftAt. */
public record ReportTarget(String uuid, Integer pid, String name, boolean online, long leftAt) {
}
