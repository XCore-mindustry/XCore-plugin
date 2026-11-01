package org.xcore.ui;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void singleArgShorthandBuildsMap() {
        LocalizerResolver resolver = (key, args) -> key + "=" + args.get("count");
        assertThat(Text.t("items", "count", 42).resolve(resolver)).isEqualTo("items=42");
    }

    @Test
    void joinedConcatenatesLazily() {
        LocalizerResolver resolver = (key, args) -> "<" + key + ">";
        Text joined = Text.join(Text.t("a"), Text.join(Text.t("b"), Text.raw("!")));
        assertThat(joined.resolve(resolver)).isEqualTo("<a><b>!");

        Text multiJoin = Text.join(Text.raw("1"), Text.raw("2"), Text.raw("3"));
        assertThat(multiJoin.resolve(resolver)).isEqualTo("123");
    }

    @Test
    void localizedArgsAreDefensivelyCopied() {
        Map<String, Object> args = new java.util.HashMap<>(Map.of("k", "v"));
        Text.Localized text = (Text.Localized) Text.t("key", args);
        args.put("k", "changed");
        assertThat(text.args()).containsEntry("k", "v");
    }

    @Test
    void positionalResolvesViaFormatPositional() {
        LocalizerResolver resolver = new LocalizerResolver() {
            @Override
            public String format(String key, Map<String, Object> args) {
                return key;
            }

            @Override
            public String formatPositional(String key, Object... args) {
                return key + ":" + String.join(",", java.util.Arrays.stream(args).map(String::valueOf).toList());
            }
        };

        assertThat(Text.pos("items", "A", 123).resolve(resolver)).isEqualTo("items:A,123");
        assertThat(Text.positional("items", "B").resolve(resolver)).isEqualTo("items:B");
        assertThat(Text.pos("items").resolve(null)).isEqualTo("items");
    }

    @Test
    void positionalBridgesToNamedByDefault() {
        LocalizerResolver namedResolver = (key, args) -> key + "->" + args.get("0") + "," + args.get("arg1");
        assertThat(Text.pos("test", "first", "second").resolve(namedResolver))
                .isEqualTo("test->first,second");
    }

    @Test
    void argsBuilderConstructsUnmodifiableMap() {
        Map<String, Object> args = Text.args("page", 1, "total", 10, "author", null);
        assertThat(args).hasSize(3);
        assertThat(args.get("page")).isEqualTo(1);
        assertThat(args.get("total")).isEqualTo(10);
        assertThat(args.get("author")).isNull();

        assertThatThrownBy(() -> args.put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void argsBuilderValidatesInput() {
        assertThat(Text.args()).isEmpty();
        assertThat(Text.args((Object[]) null)).isEmpty();

        assertThatThrownBy(() -> Text.args("oddKey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("even count");

        assertThatThrownBy(() -> Text.args(null, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");

        assertThatThrownBy(() -> Text.args(123, "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a String");
    }
}
