package org.xcore.plugin.cases;

public record ReportDraft(String reporterUuid, ReportTarget target, ReportReason reason, String comment) {
}
