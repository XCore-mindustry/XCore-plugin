package org.xcore.plugin.localization;

import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationRenderTest {

    @Test
    @DisplayName("numbers are grouped with a space the client's font can draw")
    void numbersAreGroupedWithADrawableSpace() {
        assertThat(Localization.integers(Locale.FRENCH).format(1_204_000)).isEqualTo("1 204 000");
        assertThat(Localization.integers(Locale.ENGLISH).format(1_204_000)).isEqualTo("1,204,000");
    }

    @Test
    @DisplayName("text typed by others cannot recolor or close the colors of relayed messages")
    void untrustedTextIsEscaped() {
        Bundle bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));

        for (Locale locale : bundle.getAvailableLocales()) {
            String discord = bundle.format(locale, "discord-chat-format",
                    Map.of("author", "[red]Mallory", "message", "hi[] [scarlet]fake"));
            assertThat(discord).as("discord-chat-format in %s", locale)
                    .contains("[[red]Mallory")
                    .contains("hi[[] [[scarlet]fake");

            Map<String, Object> vote = new HashMap<>();
            for (String name : bundle.variables("votekick-vote")) {
                vote.put(name, "x");
            }
            vote.put("reason", "grief[] [gold]free admin");
            assertThat(bundle.format(locale, "votekick-vote", vote)).as("votekick-vote in %s", locale)
                    .contains("grief[[] [[gold]free admin");
        }
    }

    @Test
    @DisplayName("every translated message renders without errors the English message does not have")
    void translationsRenderWithoutNewErrors() {
        Bundle bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        Set<String> failing = new HashSet<>();
        bundle.setFormatErrorHandler(context -> failing.add(context.locale() + "/" + context.entryName()));

        Set<String> englishKeys = bundle.keys(Locale.ENGLISH);
        for (String key : englishKeys) {
            bundle.format(Locale.ENGLISH, key, sampleArgs(bundle, key));
        }
        Set<String> englishFailures = new HashSet<>(failing);

        List<String> problems = new ArrayList<>();
        for (Locale locale : bundle.getAvailableLocales()) {
            if (locale.equals(Locale.ENGLISH)) {
                continue;
            }
            for (String key : bundle.keys(locale)) {
                if (!englishKeys.contains(key)) {
                    continue;
                }
                String text = bundle.format(locale, key, sampleArgs(bundle, key));
                String id = locale + "/" + key;
                if (failing.contains(id) && !englishFailures.contains(Locale.ENGLISH + "/" + key)) {
                    problems.add(id + " -> " + text);
                }
            }
        }

        assertThat(problems)
                .withFailMessage("Translations fail to render:%n%s", String.join(System.lineSeparator(), problems))
                .isEmpty();
    }

    private static Map<String, Object> sampleArgs(Bundle bundle, String key) {
        Map<String, Object> args = new HashMap<>();
        for (String variable : bundle.variables(key)) {
            args.put(variable, 2);
        }
        return args;
    }
}
