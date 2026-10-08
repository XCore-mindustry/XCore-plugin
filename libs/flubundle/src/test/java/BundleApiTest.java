import arc.files.Fi;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.MissingKeyPolicy;
import com.ospx.flubundle.Text;
import fluent.bundle.FluentBundle;
import mindustry.gen.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BundleApiTest {

    private static Bundle bundleWith(String ftl, Locale locale) {
        Bundle bundle = new Bundle();
        add(bundle, ftl, locale);
        return bundle;
    }

    private static void add(Bundle bundle, String ftl, Locale locale) {
        Fi temp = Fi.tempFile("api_test.ftl");
        temp.writeString(ftl);
        bundle.addSource(temp, locale);
    }

    private static Player player(String clientLocale) {
        Player player = Player.create();
        player.locale = clientLocale;
        return player;
    }

    @Test
    void localeResolverOverridesClientLocale() {
        Bundle bundle = bundleWith("greeting = Hello\n", Locale.ENGLISH);
        add(bundle, "greeting = Привет\n", Locale.of("ru"));

        Player player = player("en");
        assertEquals(Locale.ENGLISH, bundle.locale(player));

        Map<Player, Locale> selected = new HashMap<>();
        bundle.setLocaleResolver(selected::get);

        assertEquals(Locale.ENGLISH, bundle.locale(player), "null from resolver falls back to client locale");

        selected.put(player, Locale.of("ru", "RU"));
        assertEquals(Locale.of("ru"), bundle.locale(player));
        assertEquals("Привет", bundle.localizer(player).format("greeting"));
        assertEquals("Привет", bundle.format(bundle.locale(player), Text.of("greeting")));

        bundle.setLocaleResolver(null);
        assertEquals(Locale.ENGLISH, bundle.locale(player));
    }

    @Test
    void exposesKeysAndPresence() {
        Bundle bundle = bundleWith("""
                only-en = English
                shared = Shared
                """, Locale.ENGLISH);
        add(bundle, "shared = Общий\nonly-ru = Только\n", Locale.of("ru"));

        assertTrue(bundle.has("only-ru"));
        assertFalse(bundle.has("missing"));

        assertTrue(bundle.has(Locale.of("ru"), "only-en"), "falls back to the default locale");
        assertTrue(bundle.has(Locale.ENGLISH, "shared"));
        assertFalse(bundle.has(Locale.ENGLISH, "only-ru"));

        assertEquals(Set.of("shared", "only-ru"), bundle.keys(Locale.of("ru")));
        assertEquals(Set.of(), bundle.keys(Locale.FRENCH));
    }

    @Test
    void collectsVariablesFromSelectorsFunctionsAndReferences() {
        Bundle bundle = bundleWith("""
                -brand = XCore { $ignoredTermVariable }
                inner = by { $author }
                message = { $count ->
                    [one] { $count } item from { COLOR($name) } { inner }
                   *[other] { $count } items { -brand }
                }
                    .title = Title for { $title }
                plain = No variables
                """, Locale.ENGLISH);

        assertEquals(List.of("count", "name", "author", "title"), new ArrayList<>(bundle.variables("message")));
        assertEquals(Set.of(), bundle.variables("plain"));
        assertEquals(Set.of(), bundle.variables("missing"));
    }

    @Test
    void formatsAttributesWithFallback() {
        Bundle bundle = bundleWith("""
                menu = Menu
                    .title = Main menu for { $name }
                """, Locale.ENGLISH);
        add(bundle, "menu = Меню\n", Locale.of("ru"));

        assertEquals("Main menu for Alice",
                bundle.formatAttribute(Locale.of("ru"), "menu", "title", Args.of("name", "Alice")));
        assertEquals("menu.missing", bundle.formatAttribute(Locale.ENGLISH, "menu", "missing", Args.empty()));
    }

    @Test
    void reportsFormatErrorsToHandler() {
        Bundle bundle = bundleWith("hello = Hello, { $name }!\n", Locale.ENGLISH);
        List<FluentBundle.ErrorContext> errors = new ArrayList<>();
        bundle.setFormatErrorHandler(errors::add);

        bundle.format(Locale.ENGLISH, "hello");

        assertEquals(1, errors.size());
        assertEquals("hello", errors.getFirst().entryName());
    }

    @Test
    void argsKeepOrderAndAllowNullValues() {
        Map<String, Object> args = Args.of("b", 1, "a", null);

        assertEquals(List.of("b", "a"), new ArrayList<>(args.keySet()));
        assertTrue(args.containsKey("a"));
        assertThrows(UnsupportedOperationException.class, () -> args.put("c", 2));
    }

    @Test
    void logMissingDelegatesToWrappedPolicy() {
        Bundle bundle = bundleWith("present = Here\n", Locale.ENGLISH);
        bundle.setMissingKeyPolicy(MissingKeyPolicy.logOnce((key, args, locale) -> "?" + key));

        assertEquals("?absent", bundle.format(Locale.ENGLISH, "absent"));
        assertEquals("?absent", bundle.format(Locale.ENGLISH, "absent"));
        assertEquals("Here", bundle.format(Locale.ENGLISH, "present"));
    }

    @Test
    void laterSourceOverridesEarlierKeys() {
        Bundle bundle = bundleWith("shared = First\n", Locale.ENGLISH);
        add(bundle, "shared = Second\n", Locale.ENGLISH);

        assertEquals("Second", bundle.format(Locale.ENGLISH, "shared"));
    }

    @Test
    void missingKeyPolicies() {
        Bundle bundle = bundleWith("present = Here\n", Locale.ENGLISH);

        assertEquals("absent", bundle.format(Locale.ENGLISH, "absent"), "returns the key by default");

        bundle.setMissingKeyPolicy(MissingKeyPolicy.bracketed());
        assertEquals("⟦absent⟧", bundle.format(Locale.ENGLISH, "absent"));
        assertEquals("⟦present.title⟧", bundle.formatAttribute(Locale.ENGLISH, "present", "title", Map.of()));

        bundle.setMissingKeyPolicy(MissingKeyPolicy.throwing());
        assertThrows(IllegalStateException.class, () -> bundle.format(Locale.ENGLISH, "absent"));
        assertEquals("Here", bundle.format(Locale.ENGLISH, "present"));

        bundle.setMissingKeyPolicy(null);
        assertEquals("absent", bundle.format(Locale.ENGLISH, "absent"));
    }

    @Test
    void textRendersWithBundleAndLocalizer() {
        Bundle bundle = bundleWith("hello = Hello, { $name }!\n", Locale.ENGLISH);
        add(bundle, "hello = Привет, { $name }!\n", Locale.of("ru"));
        Text text = Text.of("hello", Args.of("name", "Billy"));

        assertEquals("Hello, Billy!", text.render(bundle, Locale.ENGLISH));
        assertEquals("Привет, Billy!", bundle.format(Locale.of("ru"), text));
        assertEquals("Привет, Billy!", text.render(bundle.localizer(Locale.of("ru"))));
        assertEquals(Map.of(), Text.of("hello", null).args());
        assertThrows(NullPointerException.class, () -> Text.of(null));
    }

    @Test
    void textKeepsASnapshotOfItsArguments() {
        Bundle bundle = bundleWith("hello = Hello, { $name }!\n", Locale.ENGLISH);
        Map<String, Object> args = Bundle.args("name", "Billy");
        Text text = Text.of("hello", args);

        args.put("name", "Changed");

        assertEquals("Hello, Billy!", text.render(bundle, Locale.ENGLISH));
        assertThrows(UnsupportedOperationException.class, () -> text.args().put("name", "x"));
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("name", null);
        assertTrue(Text.of("hello", withNull).args().containsKey("name"), "null values are allowed");
    }

    @Test
    void localizerHasFollowsFallbackChain() {
        Bundle bundle = bundleWith("only-en = English\n", Locale.ENGLISH);
        add(bundle, "only-ru = Только\n", Locale.of("ru"));

        assertTrue(bundle.localizer(Locale.of("ru")).has("only-en"));
        assertTrue(bundle.localizer(Locale.of("ru")).has("only-ru"));
        assertFalse(bundle.localizer(Locale.ENGLISH).has("only-ru"));
    }

    @Test
    void argsBuilderSupportsManyAndConditionalPairs() {
        Map<String, Object> args = Args.builder()
                .put("a", 1).put("b", 2).put("c", 3).put("d", 4)
                .put("e", 5).put("f", 6).put("g", 7)
                .putIf(false, "skipped", 0)
                .putIf(true, "h", null)
                .build();

        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g", "h"), new ArrayList<>(args.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> args.put("i", 9));
    }
}
