package com.ospx.flubundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A message that has not been rendered yet: a bundle key plus its arguments.
 *
 * <p>Services can return a {@code Text} and leave it to the caller to decide who receives it and
 * in which locale. Delivering one {@code Text} to many players renders it once per locale.
 *
 * <pre>{@code
 * Text text = Text.of("votekick-fail", Args.of("target", target.coloredName()));
 * messenger.all().send(text);
 * String english = text.render(bundle, Locale.ENGLISH);
 * }</pre>
 *
 * @param key  bundle message id
 * @param args message arguments; stored as an unmodifiable copy, never {@code null}
 */
public record Text(String key, Map<String, Object> args) {

    public Text {
        Objects.requireNonNull(key, "key");
        // Rendering happens later, so keep a snapshot the caller cannot change in the meantime.
        args = args == null || args.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    public static Text of(String key) {
        return new Text(key, Collections.emptyMap());
    }

    public static Text of(String key, Map<String, Object> args) {
        return new Text(key, args);
    }

    public String render(Bundle bundle, Locale locale) {
        return bundle.format(locale, key, args);
    }

    public String render(Localizer localizer) {
        return localizer.format(key, args);
    }
}
