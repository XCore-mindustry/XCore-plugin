package com.ospx.flubundle.mindustry;

import com.ospx.flubundle.LocaleCodes;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Team;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Localized names of vanilla Mindustry content and teams, taken from the game's own bundles.
 *
 * <p>A dedicated server loads no game bundles, so {@code block.localizedName} is the internal name
 * there. FluBundle ships the names of blocks, units, items, liquids, status effects, teams, planets,
 * sectors and weather for every language the game is translated to (see the
 * {@code updateMindustryContentNames} Gradle task). {@link com.ospx.flubundle.Bundle} renders any
 * {@link UnlockableContent} or {@link Team} passed as a message argument through this class:
 *
 * <pre>{@code
 * # bundle_ru.ftl
 * core-destroyed = Ядро команды { $team } уничтожено юнитом { $unit }
 *
 * messenger.all().send("core-destroyed", Args.of("team", Team.crux, "unit", UnitTypes.dagger));
 * // ru: Ядро команды Багровые уничтожено юнитом Кинжал
 * }</pre>
 *
 * <p>Lookup per name: the requested locale, its language (or the first regional variant of it, so
 * {@code zh} finds {@code zh_CN}), English, then the content's own {@code localizedName}.
 */
public final class ContentNames {

    private static final String ROOT = "flubundle/mindustry/";
    private static final ContentNames VANILLA = new ContentNames(ContentNames.class.getClassLoader(), ROOT);

    private final ClassLoader loader;
    private final String root;
    private final List<String> codes;
    private final Map<String, Map<String, String>> names = new ConcurrentHashMap<>();
    private final Map<String, List<String>> candidates = new ConcurrentHashMap<>();

    ContentNames(ClassLoader loader, String root) {
        this.loader = loader;
        this.root = root;
        this.codes = readIndex();
    }

    /** Names shipped with FluBundle for the Mindustry version it was built against. */
    public static ContentNames vanilla() {
        return VANILLA;
    }

    /** Locale codes with shipped names, such as {@code en}, {@code ru}, {@code uk_UA}. */
    public List<String> locales() {
        return codes;
    }

    /**
     * @param key a game bundle key such as {@code block.router.name}
     * @return the name in the best matching locale, or {@code null} for an unknown key
     */
    public String get(String key, Locale locale) {
        for (String code : candidates(locale)) {
            String value = names(code).get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** The localized name of a block, unit, item, liquid, status effect, planet, sector or weather. */
    public String name(UnlockableContent content, Locale locale) {
        String name = get(content.getContentType().name() + "." + content.name + ".name", locale);
        if (name != null) {
            return name;
        }
        return content.localizedName != null ? content.localizedName : content.name;
    }

    /** The localized name of a team; teams without a vanilla name keep {@link Team#name}. */
    public String name(Team team, Locale locale) {
        String name = get("team." + team.name + ".name", locale);
        return name != null ? name : team.name;
    }

    private List<String> candidates(Locale locale) {
        String requested = LocaleCodes.normalize(locale);
        return candidates.computeIfAbsent(requested == null ? "" : requested, code -> {
            List<String> result = new ArrayList<>(3);
            if (!code.isEmpty()) {
                String language = code.split("_")[0];
                if (codes.contains(code)) {
                    result.add(code);
                }
                if (codes.contains(language)) {
                    addOnce(result, language);
                } else {
                    for (String available : codes) {
                        if (available.startsWith(language + "_")) {
                            addOnce(result, available);
                            break;
                        }
                    }
                }
            }
            addOnce(result, "en");
            return List.copyOf(result);
        });
    }

    private static void addOnce(List<String> list, String code) {
        if (!list.contains(code)) {
            list.add(code);
        }
    }

    private Map<String, String> names(String code) {
        return names.computeIfAbsent(code, this::load);
    }

    private Map<String, String> load(String code) {
        try (InputStream in = loader.getResourceAsStream(root + "content_" + code + ".properties")) {
            if (in == null) {
                return Collections.emptyMap();
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> result = new HashMap<>(properties.size() * 2);
            properties.forEach((key, value) -> result.put((String) key, (String) value));
            return Collections.unmodifiableMap(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read content names for " + code, e);
        }
    }

    private List<String> readIndex() {
        try (InputStream in = loader.getResourceAsStream(root + "locales")) {
            if (in == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    result.add(line.trim());
                }
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the content name index", e);
        }
    }
}
