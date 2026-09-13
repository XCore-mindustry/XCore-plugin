package org.xcore.ui;

import arc.struct.ObjectMap;
import arc.util.I18NBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizerResolverTest {

    @AfterEach
    void resetDefault() {
        LocalizerResolver.setDefault(LocalizerResolver.IDENTITY);
    }

    @Test
    void identityReturnsKey() {
        assertThat(LocalizerResolver.IDENTITY.format("any.key")).isEqualTo("any.key");
        assertThat(LocalizerResolver.IDENTITY.format("any.key", Map.of("a", 1))).isEqualTo("any.key");
        assertThat(LocalizerResolver.IDENTITY.formatPositional("any.key", "foo", "bar")).isEqualTo("any.key");
        assertThat(LocalizerResolver.IDENTITY.has("any.key")).isFalse();
    }

    @Test
    void fromNamedFunctionResolvesNamedArgs() {
        LocalizerResolver resolver = LocalizerResolver.from((key, args) -> key + "=" + args.get("val"));
        assertThat(resolver.format("greeting", Map.of("val", "hello"))).isEqualTo("greeting=hello");
        assertThat(resolver.format("simple")).isEqualTo("simple=null");
        // Positional call bridges to numeric and arg0 keys
        assertThat(resolver.formatPositional("pos", "first")).isEqualTo("pos=null"); // uses "val" which isn't there
        LocalizerResolver bridgingTester = LocalizerResolver.from((key, args) -> key + ":" + args.get("0") + "/" + args.get("arg0"));
        assertThat(bridgingTester.formatPositional("pos", "val0")).isEqualTo("pos:val0/val0");
    }

    @Test
    void fromLookupFunctionResolvesKeyOnly() {
        Map<String, String> dict = Map.of("app.title", "Mindustry", "app.version", "v160");
        LocalizerResolver resolver = LocalizerResolver.from(dict::get);

        assertThat(resolver.has("app.title")).isTrue();
        assertThat(resolver.has("missing")).isFalse();

        assertThat(resolver.format("app.title")).isEqualTo("Mindustry");
        assertThat(resolver.format("app.title", Map.of("unused", 123))).isEqualTo("Mindustry");
        assertThat(resolver.formatPositional("app.version", "unused")).isEqualTo("v160");
        assertThat(resolver.format("missing")).isEqualTo("missing");
    }

    @Test
    void fromPositionalFunctionResolvesPositionalAndBridgesNamed() {
        LocalizerResolver resolver = LocalizerResolver.positional((key, args) -> {
            return key + "[" + String.join(",", java.util.Arrays.stream(args).map(String::valueOf).toList()) + "]";
        });

        assertThat(resolver.formatPositional("coords", 10, 20)).isEqualTo("coords[10,20]");
        assertThat(resolver.format("single", "value")).isEqualTo("single[value]");

        // Named map with sequential numeric keys is unpacked to array
        Map<String, Object> numericMap = Map.of("0", "first", "1", "second");
        assertThat(resolver.format("test", numericMap)).isEqualTo("test[first,second]");
    }

    @Test
    void fromI18NBundleFormatsPositionalAndNamed() {
        I18NBundle bundle = I18NBundle.createEmptyBundle();
        ObjectMap<String, String> props = new ObjectMap<>();
        props.put("player.online", "{0} is currently online");
        props.put("map.info", "Map: {name} by {author}");
        bundle.setProperties(props);

        LocalizerResolver resolver = LocalizerResolver.from(bundle);

        assertThat(resolver.has("player.online")).isTrue();
        assertThat(resolver.has("unknown.key")).isFalse();

        // Positional formatting
        assertThat(resolver.formatPositional("player.online", "Ospx")).isEqualTo("Ospx is currently online");

        // Named map placeholder replacement: "{name}" -> "Archipelago"
        Map<String, Object> namedArgs = Map.of("name", "Archipelago", "author", "Anuke");
        assertThat(resolver.format("map.info", namedArgs)).isEqualTo("Map: Archipelago by Anuke");

        // Missing key echoes the key
        assertThat(resolver.format("unknown.key")).isEqualTo("unknown.key");
    }

    @Test
    void compositeChainsResolversInPriorityOrder() {
        Map<String, String> primary = Map.of("color.red", "Red");
        Map<String, String> fallback = Map.of("color.red", "FallbackRed", "color.blue", "Blue");

        LocalizerResolver resolver = LocalizerResolver.composite(
                LocalizerResolver.from(primary::get),
                LocalizerResolver.from(fallback::get)
        );

        assertThat(resolver.has("color.red")).isTrue();
        assertThat(resolver.has("color.blue")).isTrue();
        assertThat(resolver.has("color.green")).isFalse();

        assertThat(resolver.format("color.red")).isEqualTo("Red");
        assertThat(resolver.format("color.blue")).isEqualTo("Blue");
        assertThat(resolver.format("color.green")).isEqualTo("color.green");
    }

    @Test
    void orElseChainsFallback() {
        LocalizerResolver primary = LocalizerResolver.from(Map.of("hello", "Hi")::get);
        LocalizerResolver secondary = LocalizerResolver.from(Map.of("world", "Earth")::get);

        LocalizerResolver chained = primary.orElse(secondary);
        assertThat(chained.format("hello")).isEqualTo("Hi");
        assertThat(chained.format("world")).isEqualTo("Earth");
        assertThat(chained.format("unknown")).isEqualTo("unknown");
    }

    @Test
    void globalDefaultManagement() {
        assertThat(LocalizerResolver.getDefault()).isSameAs(LocalizerResolver.IDENTITY);

        LocalizerResolver custom = (key, args) -> "custom:" + key;
        LocalizerResolver.setDefault(custom);
        assertThat(LocalizerResolver.getDefault()).isSameAs(custom);

        VNodeCompiler compiler = new VNodeCompiler();
        assertThat(compiler.resolver()).isSameAs(custom);

        LocalizerResolver.setDefault(null);
        assertThat(LocalizerResolver.getDefault()).isSameAs(LocalizerResolver.IDENTITY);
    }
}
