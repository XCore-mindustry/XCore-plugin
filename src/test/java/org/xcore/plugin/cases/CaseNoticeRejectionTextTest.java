package org.xcore.plugin.cases;

import arc.files.Fi;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The text a reporter reads for a rejected report: arguments built by the service, rendered by the real bundles. */
class CaseNoticeRejectionTextTest {
    private static final String GENERIC_EN = "Report #57 reviewed: no action was taken. Thank you.";
    private static final String GENERIC_RU = "Жалоба #57 рассмотрена: мер не принято. Спасибо!";
    private static final String GENERIC_UK = "Скаргу #57 розглянуто: заходів не вжито. Дякуємо!";

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "en | no_violation | Report #57 reviewed: no rule violation was found. Thank you.",
            "en | no_evidence | Report #57 reviewed: there was not enough evidence. Thank you.",
            "en | already_handled | Report #57 closed: this had already been dealt with. Thank you.",
            "en | duplicate | Report #57 closed: this had already been reported. Thank you.",
            "en | abuse | Report #57 rejected: the report itself broke the server rules.",
            "ru | no_violation | Жалоба #57 рассмотрена: нарушений не обнаружено. Спасибо!",
            "ru | no_evidence | Жалоба #57 рассмотрена: доказательств недостаточно. Спасибо!",
            "ru | already_handled | Жалоба #57 закрыта: с этим случаем уже разобрались. Спасибо!",
            "ru | duplicate | Жалоба #57 закрыта: об этом уже сообщили раньше. Спасибо!",
            "ru | abuse | Жалоба #57 отклонена: она сама нарушает правила сервера.",
            "uk_UA | no_violation | Скаргу #57 розглянуто: порушень не виявлено. Дякуємо!",
            "uk_UA | no_evidence | Скаргу #57 розглянуто: доказів недостатньо. Дякуємо!",
            "uk_UA | already_handled | Скаргу #57 закрито: з цим випадком уже розібралися. Дякуємо!",
            "uk_UA | duplicate | Скаргу #57 закрито: про це вже повідомили раніше. Дякуємо!",
            "uk_UA | abuse | Скаргу #57 відхилено: вона сама порушує правила сервера."
    })
    void everyReasonCodeHasItsOwnSentence(String language, String reason, String expected) {
        assertThat(render(language, rejected(reason))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', nullValues = "<absent>", value = {
            "en | <absent>", "en | spam", "en | ABUSE", "en | other", "en | [scarlet]abuse", "en | ''", "en | never-render",
            "ru | <absent>", "ru | spam", "ru | ABUSE", "ru | other", "ru | [scarlet]abuse", "ru | ''", "ru | never-render",
            "uk_UA | <absent>", "uk_UA | spam", "uk_UA | ABUSE", "uk_UA | other", "uk_UA | [scarlet]abuse", "uk_UA | ''", "uk_UA | never-render"
    })
    void unknownOrMissingReasonKeepsTheGenericSentenceAndNeverReachesChat(String language, String reason) {
        var notice = rejected(reason);
        assertThat(CaseNoticeService.arguments(notice))
                .containsExactlyInAnyOrderEntriesOf(Map.of("number", 57L, "reason", "other"));
        assertThat(render(language, notice)).isEqualTo(switch (language) {
            case "ru" -> GENERIC_RU;
            case "uk_UA" -> GENERIC_UK;
            default -> GENERIC_EN;
        });
    }

    @ParameterizedTest
    @CsvSource({"report_received", "report_resolved", "badge_rejected", "map_rejected", "complaint_closed"})
    void reasonIsForwardedOnlyForARejectedReport(String kind) {
        var notice = new CaseNotice("57:player:" + kind, "player", kind,
                Map.of("number", "57", "reason", "abuse", "note", "never-render"), "token", Instant.now());
        assertThat(CaseNoticeService.arguments(notice)).containsOnlyKeys("number");
        assertThat(CaseNoticeService.arguments(rejected("abuse")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("number", 57L, "reason", "abuse"));
    }

    private static CaseNotice rejected(String reason) {
        var params = new HashMap<String, String>();
        params.put("number", "57");
        params.put("staff", "never-render");
        params.put("note", "never-render");
        if (reason != null) params.put("reason", reason);
        return new CaseNotice("57:player:report_rejected", "player", "report_rejected", params, "token", Instant.now());
    }

    private static String render(String language, CaseNotice notice) {
        var bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        bundle.setFormatErrorHandler(context -> {
            throw new AssertionError("Notice failed to render: " + context.locale() + "/" + context.entryName());
        });
        var locale = new Locale.Builder().setLanguageTag(language.replace('_', '-')).build();
        String text = bundle.format(locale, notice.bundleKey(), CaseNoticeService.arguments(notice));
        assertThat(text).doesNotContain("\n", "never-render", "$reason");
        return Strings.stripColors(text).replace("[accent]", "");
    }
}
