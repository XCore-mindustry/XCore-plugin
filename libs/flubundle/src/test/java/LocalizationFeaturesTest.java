import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.functions.ColorFunction;
import com.ospx.flubundle.functions.DurationFunction;
import com.ospx.flubundle.functions.StripFunction;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalizationFeaturesTest {

    private Bundle createBundleWithFtl(String ftl, Locale locale) {
        Bundle bundle = new Bundle();
        Fi temp = Fi.tempFile("test_bundle.ftl");
        temp.writeString(ftl);
        bundle.addSource(temp, locale);
        return bundle;
    }

    @Test
    void testStandardFunctionsCaseAndCount() {
        String ftl = """
                upper = { CASE($text, style: "upper") }
                lower = { CASE($text, style: "lower") }
                list-count = { COUNT($items) }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.ENGLISH);

        assertEquals("HELLO WORLD", bundle.format(Locale.ENGLISH, "upper", Map.of("text", "Hello World")));
        assertEquals("hello world", bundle.format(Locale.ENGLISH, "lower", Map.of("text", "Hello World")));
        assertEquals("3", bundle.format(Locale.ENGLISH, "list-count", Map.of("items", List.of("a", "b", "c"))));
    }

    @Test
    void testStripFunction() {
        String ftl = """
                clean = Clean: { STRIP($name) }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.ENGLISH);

        assertEquals("Clean: Alice", bundle.format(Locale.ENGLISH, "clean", Map.of("name", "[scarlet]Alice[]")));
        assertEquals("Clean: Bob", bundle.format(Locale.ENGLISH, "clean", Map.of("name", "[#ff0000]Bob[white]")));
    }

    @Test
    void testColorFunction() {
        String ftl = """
                colored = { COLOR($name, color: "orange") }
                default-color = { COLOR($name) }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.ENGLISH);

        assertEquals("[orange]Alice[]", bundle.format(Locale.ENGLISH, "colored", Map.of("name", "Alice")));
        assertEquals("[accent]Bob[]", bundle.format(Locale.ENGLISH, "default-color", Map.of("name", "Bob")));
    }

    @Test
    void testDurationFunctionTimerStyle() {
        String ftl = """
                timer = { DURATION($time, style: "timer") }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.ENGLISH);

        assertEquals("00:00", bundle.format(Locale.ENGLISH, "timer", Map.of("time", 0)));
        assertEquals("00:05", bundle.format(Locale.ENGLISH, "timer", Map.of("time", 5)));
        assertEquals("01:35", bundle.format(Locale.ENGLISH, "timer", Map.of("time", 95)));
        assertEquals("01:01:05", bundle.format(Locale.ENGLISH, "timer", Map.of("time", 3665)));
        assertEquals("1:01:00:00", bundle.format(Locale.ENGLISH, "timer", Map.of("time", 90000)));
        assertEquals("-01:35", bundle.format(Locale.ENGLISH, "timer", Map.of("time", -95)));
    }

    @Test
    void testDurationFunctionCompactStyle() {
        String ftl = """
                compact = { DURATION($time) }
                """;

        Bundle bundleEn = createBundleWithFtl(ftl, Locale.ENGLISH);
        assertEquals("0s", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 0)));
        assertEquals("5s", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 5)));
        assertEquals("1m", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 60)));
        assertEquals("1m 35s", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 95)));
        assertEquals("1h 1m 5s", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 3665)));
        assertEquals("1d 1h", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", 90000)));
        assertEquals("-1m 5s", bundleEn.format(Locale.ENGLISH, "compact", Map.of("time", -65)));

        Bundle bundleRu = createBundleWithFtl(ftl, Locale.of("ru"));
        assertEquals("0с", bundleRu.format(Locale.of("ru"), "compact", Map.of("time", 0)));
        assertEquals("1м 35с", bundleRu.format(Locale.of("ru"), "compact", Map.of("time", 95)));
        assertEquals("1ч 1м 5с", bundleRu.format(Locale.of("ru"), "compact", Map.of("time", 3665)));
        assertEquals("1д 1ч", bundleRu.format(Locale.of("ru"), "compact", Map.of("time", 90000)));

        Bundle bundleUk = createBundleWithFtl(ftl, Locale.of("uk"));
        assertEquals("1хв 35с", bundleUk.format(Locale.of("uk"), "compact", Map.of("time", 95)));
        assertEquals("1г 1хв 5с", bundleUk.format(Locale.of("uk"), "compact", Map.of("time", 3665)));
        assertEquals("1д 1г", bundleUk.format(Locale.of("uk"), "compact", Map.of("time", 90000)));
    }

    @Test
    void testDurationFunctionFullStyle() {
        String ftl = """
                full = { DURATION($time, style: "full") }
                full-max2 = { DURATION($time, style: "full", maxUnits: 2) }
                full-colored = { DURATION($time, style: "full", colored: "true", maxUnits: 2) }
                """;

        Bundle bundleRu = createBundleWithFtl(ftl, Locale.of("ru"));
        assertEquals("1 день 1 час", bundleRu.format(Locale.of("ru"), "full-max2", Map.of("time", 90000)));
        assertEquals("[white]1[lightgray] день [white]1[lightgray] час", bundleRu.format(Locale.of("ru"), "full-colored", Map.of("time", 90000)));
        assertEquals("1 час 1 минута 5 секунд", bundleRu.format(Locale.of("ru"), "full", Map.of("time", 3665)));
        assertEquals("21 день", bundleRu.format(Locale.of("ru"), "full", Map.of("time", 21 * 86400)));
        assertEquals("22 дня", bundleRu.format(Locale.of("ru"), "full", Map.of("time", 22 * 86400)));
        assertEquals("25 дней", bundleRu.format(Locale.of("ru"), "full", Map.of("time", 25 * 86400)));

        Bundle bundleEn = createBundleWithFtl(ftl, Locale.ENGLISH);
        assertEquals("1 day 1 hour", bundleEn.format(Locale.ENGLISH, "full-max2", Map.of("time", 90000)));
        assertEquals("[white]1[lightgray] day [white]1[lightgray] hour", bundleEn.format(Locale.ENGLISH, "full-colored", Map.of("time", 90000)));
        assertEquals("2 days", bundleEn.format(Locale.ENGLISH, "full", Map.of("time", 2 * 86400)));

        Bundle bundleUk = createBundleWithFtl(ftl, Locale.of("uk"));
        assertEquals("1 день 1 година", bundleUk.format(Locale.of("uk"), "full-max2", Map.of("time", 90000)));
        assertEquals("[white]1[lightgray] день [white]1[lightgray] година", bundleUk.format(Locale.of("uk"), "full-colored", Map.of("time", 90000)));
        assertEquals("2 дні", bundleUk.format(Locale.of("uk"), "full", Map.of("time", 2 * 86400)));
        assertEquals("5 днів", bundleUk.format(Locale.of("uk"), "full", Map.of("time", 5 * 86400)));
    }

    @Test
    void testDurationUnits() {
        String ftl = """
                from-millis = { DURATION($time, unit: "millis") }
                from-minutes = { DURATION($time, unit: "minutes") }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.ENGLISH);

        assertEquals("5s", bundle.format(Locale.ENGLISH, "from-millis", Map.of("time", 5900)));
        assertEquals("2m", bundle.format(Locale.ENGLISH, "from-minutes", Map.of("time", 2)));
    }

    record CustomVal(String val) {}

    @Test
    void testRegistrationLifecycleFrozenAfterSourceAdded() {
        Bundle bundle = new Bundle();
        bundle.registerFormatterExact(CustomVal.class, (item, scope) -> "CUSTOM:" + item.val());

        Fi temp = Fi.tempFile("lifecycle_test.ftl");
        temp.writeString("key = Value: { $val }\n");
        bundle.addSource(temp, Locale.ENGLISH);

        assertEquals("Value: CUSTOM:hello", bundle.format(Locale.ENGLISH, "key", Map.of("val", new CustomVal("hello"))));

        assertThrows(IllegalStateException.class, () ->
                bundle.registerFormatterExact(Long.class, (val, scope) -> "LONG:" + val));
    }

    @Test
    void testRegistrationLifecycleFrozenAfterFormat() {
        Bundle bundle = new Bundle();
        bundle.format(Locale.ENGLISH, "any-key", Map.of());

        assertThrows(IllegalStateException.class, () ->
                bundle.registerFormatterExact(Double.class, (val, scope) -> "D:" + val));
    }

    @Test
    void testSlavicPluralization() {
        String ftl = """
                apples = { $count ->
                    [one] { $count } яблоко
                    [few] { $count } яблока
                    [many] { $count } яблок
                   *[other] { $count } яблока
                }
                """;
        Bundle bundle = createBundleWithFtl(ftl, Locale.of("ru"));

        assertEquals("1 яблоко", bundle.format(Locale.of("ru"), "apples", Map.of("count", 1)));
        assertEquals("2 яблока", bundle.format(Locale.of("ru"), "apples", Map.of("count", 2)));
        assertEquals("4 яблока", bundle.format(Locale.of("ru"), "apples", Map.of("count", 4)));
        assertEquals("5 яблок", bundle.format(Locale.of("ru"), "apples", Map.of("count", 5)));
        assertEquals("11 яблок", bundle.format(Locale.of("ru"), "apples", Map.of("count", 11)));
        assertEquals("21 яблоко", bundle.format(Locale.of("ru"), "apples", Map.of("count", 21)));
        assertEquals("22 яблока", bundle.format(Locale.of("ru"), "apples", Map.of("count", 22)));
        assertEquals("25 яблок", bundle.format(Locale.of("ru"), "apples", Map.of("count", 25)));
    }
}
