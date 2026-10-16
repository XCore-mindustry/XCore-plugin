package org.xcore.plugin.cases;

import java.util.Locale;

public enum ReportReason {
    GRIEFING, TOXICITY, CHEATING, NSFW, EVASION, OTHER;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String bundleKey() {
        return "report-reason-" + code();
    }
}
