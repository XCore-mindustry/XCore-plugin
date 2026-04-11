package org.xcore.plugin.ui.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditRecordSummary;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHistoryMenuTest {

    @Test
    @DisplayName("summarizeReason truncates long reasons")
    void summarizeReasonTruncatesLongReasons() {
        String longReason = "12345678901234567890123456789012345678901234567890-extra";

        assertThat(AuditHistoryMenu.summarizeReason(longReason))
                .isEqualTo("123456789012345678901234567890123456789012345...");
    }

    @Test
    @DisplayName("formatSummaryRow renders action actor and summarized reason")
    void formatSummaryRowRendersActionActorAndSummarizedReason() {
        Localization local = Mockito.mock(Localization.class);
        Mockito.when(local.t(Mockito.eq("audit-menu-action-ban"))).thenReturn("Ban");
        Mockito.when(local.t(Mockito.eq("audit-menu-summary-row"), Mockito.any())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            return "[accent]" + args[1].toString();
        });
        AuditRecordSummary summary = new AuditRecordSummary(
                "audit-1",
                AuditAction.BAN,
                "Target",
                "Moderator",
                "Griefing spawn repeatedly and ignoring warnings for a while",
                null,
                null,
                10L
        );

        assertThat(AuditHistoryMenu.formatSummaryRow(local, summary))
                .contains("Griefing spawn repeatedly and ignoring warnin...");
    }
}
