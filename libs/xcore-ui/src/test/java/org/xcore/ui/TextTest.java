package org.xcore.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TextTest {

    @Test
    void rawResolvesToItself() {
        assertThat(Text.raw("Hello").resolve(LocalizerResolver.IDENTITY)).isEqualTo("Hello");
        assertThat(Text.raw(null).resolve(LocalizerResolver.IDENTITY)).isEmpty();
    }

    @Test
    void localizedResolvesThroughResolver() {
        LocalizerResolver resolver = (key, args) -> "[" + key + "/" + args + "]";
        assertThat(Text.t("ui.title").resolve(resolver)).isEqualTo("[ui.title/{}]");
        assertThat(Text.t("ui.welcome", Map.of("name", "Ospx")).resolve(resolver))
                .isEqualTo("[ui.welcome/{name=Ospx}]");
    }

    @Test
    void localizedFallsBackToKeyWithoutResolver() {
        assertThat(Text.t("ui.title").resolve(null)).isEqualTo("ui.title");
    }

    @Test
    void joinedConcatenatesLazily() {
        LocalizerResolver resolver = (key, args) -> "<" + key + ">";
        Text joined = Text.join(Text.t("a"), Text.join(Text.t("b"), Text.raw("!")));
        assertThat(joined.resolve(resolver)).isEqualTo("<a><b>!");
    }

    @Test
    void localizedArgsAreDefensivelyCopied() {
        Map<String, Object> args = new java.util.HashMap<>(Map.of("k", "v"));
        Text.Localized text = (Text.Localized) Text.t("key", args);
        args.put("k", "changed");
        assertThat(text.args()).containsEntry("k", "v");
    }
}
