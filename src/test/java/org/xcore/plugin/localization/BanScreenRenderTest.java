package org.xcore.plugin.localization;

import arc.files.Fi;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.ospx.flubundle.Bundle.args;
import static org.assertj.core.api.Assertions.assertThat;

class BanScreenRenderTest {
    @ParameterizedTest
    @CsvSource({
            "en, Ban: B-1234, Player ID: 567, Quote B-1234 in your appeal., Expires:",
            "ru, Блокировка: B-1234, ID игрока: 567, Укажите B-1234 в обращении., Истекает:",
            "uk_UA, Блокування: B-1234, ID гравця: 567, Вкажіть B-1234 у зверненні., Спливає:"
    })
    void primaryLocalesShowIdentityAndAppealHint(String language, String banLine, String pidLine, String appealHint, String expiryLabel) {
        var bundle = bundle();
        var locale = new Locale.Builder().setLanguageTag(language.replace('_', '-')).build();
        String text = renderBan(bundle, locale);

        assertThat(text).contains(banLine, pidLine, appealHint, expiryLabel, "Player", "Admin", "Griefing", "#tickets", "https://discord.example");
        assertThat(text.lines().filter(line -> line.contains("B-1234") || line.contains("567")))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(40));
        System.out.println("BAN SCREEN " + language + "\n" + text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ru", "uk_UA", "be", "cs", "de", "es", "fr", "pl"})
    void allLocalesRenderDatabaseAndVanillaBans(String language) {
        var bundle = bundle();
        var locale = new Locale.Builder().setLanguageTag(language.replace('_', '-')).build();

        assertThat(renderBan(bundle, locale)).contains("B-1234", "567");
        String permanent = bundle.format(locale, "tempban-content", args(
                "nickname", "Player", "adminName", "Admin", "reason", "Griefing",
                "banId", "B-1234", "pid", 567, "duration", 0, "permanent", "true",
                "expireDate", null, "discordUrl", "https://discord.example"));
        assertThat(permanent).contains("B-1234", "567");
        String vanilla = Strings.stripColors(bundle.format(locale, "ban-content", args(
                "nickname", "Player", "pid", 567, "discordUrl", "https://discord.example")));
        assertThat(vanilla).contains("567").doesNotContain("B-", "$pid");
        String unknown = Strings.stripColors(bundle.format(locale, "ban-content", args(
                "nickname", "Player", "pid", "-", "discordUrl", "https://discord.example")));
        assertThat(unknown).contains(": -");
    }

    private static Bundle bundle() {
        var bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        List<String> errors = new ArrayList<>();
        bundle.setFormatErrorHandler(context -> {
            errors.add(context.locale() + "/" + context.entryName());
            throw new AssertionError("Ban screen failed to render: " + errors);
        });
        return bundle;
    }

    private static String renderBan(Bundle bundle, Locale locale) {
        return Strings.stripColors(bundle.format(locale, "tempban-content", args(
                "nickname", "Player", "adminName", "Admin", "reason", "Griefing",
                "banId", "B-1234", "pid", 567, "duration", 3600, "permanent", "false",
                "expireDate", Instant.parse("2030-01-02T12:00:00Z"), "discordUrl", "https://discord.example"))).replace("[accent]", "");
    }
}
