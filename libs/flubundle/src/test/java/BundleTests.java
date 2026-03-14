import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BundleTests {

    private Bundle bundle;

    @BeforeEach
    void setUp() {
        bundle = new Bundle();
        bundle.addSource(new Fi("src/test/resources/bundles"));
    }

    @Test
    void mergesMultipleResourcesForSameLocale() {
        assertEquals("Hello, Billy!", bundle.format(Locale.of("en"), "hello-user",
                Map.of("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(Locale.of("en"), "hello-user2",
                Map.of("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(Locale.of("en"), "hello-user",
                Bundle.args("userName", "Billy")));

        assertEquals("Hello, Billy!", bundle.format(Locale.of("en"), "hello-num-user",
                Bundle.numArgs("Billy")));
    }

    @Test
    void exposesAvailableLocales() {
        assertEquals(3, bundle.getAvailableLocales().size);
    }

    @Test
    void normalizesLocaleCodesBeforeResolving() {
        assertEquals(Locale.of("en", "US"), bundle.resolveLocale("EN-us"));
        assertEquals(Locale.of("en", "US"), bundle.resolveLocale("en_US"));
        assertEquals(Locale.of("en", "US"), bundle.resolveLocale(Locale.of("en", "us")));
    }

    @Test
    void resolvesAliasesBeforeLookup() {
        bundle.addLocaleAlias("uk", "uk_UA");

        assertEquals(Locale.of("uk", "UA"), bundle.resolveLocale("uk"));
        assertEquals("Pryvit!", bundle.format(Locale.of("uk"), "shared-message", Map.of()));
    }

    @Test
    void fallsBackFromRegionalLocaleToLanguageAndDefaultLocale() {
        assertEquals("English only value", bundle.format(Locale.of("en", "US"), "english-only", Map.of()));
        assertEquals("Default fallback value", bundle.format(Locale.of("fr", "FR"), "fallback-only", Map.of()));
    }

    @Test
    void usesDefaultValueFactoryAfterFallbackChainMiss() {
        assertEquals("missing-key", bundle.format(Locale.of("fr", "FR"), "missing-key", Map.of()));
        assertEquals("default text", bundle.format(Locale.of("fr", "FR"), "missing-key", "default text", Map.of()));
    }

    @Test
    void strictFormattingPreservesPreviousFailureMode() {
        assertThrows(RuntimeException.class,
                () -> bundle.formatStrict(Locale.of("fr", "FR"), "shared-message", Map.of()));
    }

    @Test
    void localizerCanBeSnapshotOrDynamic() {
        Localizer snapshot = bundle.localizer(Locale.of("en", "US"));
        assertEquals("American English message", snapshot.format("shared-message", Map.of()));

        AtomicReference<Locale> locale = new AtomicReference<>(Locale.of("en", "US"));
        Localizer dynamic = bundle.localizer((java.util.function.Supplier<Locale>) locale::get);
        assertEquals("American English message", dynamic.format("shared-message", Map.of()));

        locale.set(Locale.of("uk", "UA"));
        assertEquals("Pryvit!", dynamic.format("shared-message", Map.of()));
    }
}
