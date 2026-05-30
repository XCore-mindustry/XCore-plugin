package org.xcore.plugin.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders structured config DTOs as section-based, human-readable TOML.
 *
 * <p>Jackson's TOML writer currently emits dotted root keys like
 * {@code server.name = "event"}. That is technically valid TOML but hard for
 * operators to edit. This helper converts normalized config DTOs into a nested
 * map view and writes standard table sections such as {@code [server]} and
 * {@code [transport.redis]}.</p>
 */
public final class HumanReadableTomlWriter {
    private static final ObjectMapper MAP_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private HumanReadableTomlWriter() {
    }

    public static String write(Object config) {
        Objects.requireNonNull(config, "config must not be null");

        LinkedHashMap<String, Object> map = MAP_MAPPER.convertValue(config, MAP_TYPE);
        StringBuilder builder = new StringBuilder();
        appendSection(builder, null, map);
        return builder.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static void appendSection(StringBuilder builder, String sectionName, Map<String, Object> map) {
        LinkedHashMap<String, Object> scalars = new LinkedHashMap<>();
        LinkedHashMap<String, Map<String, Object>> tables = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> nested) {
                tables.put(entry.getKey(), (Map<String, Object>) nested);
            } else {
                scalars.put(entry.getKey(), value);
            }
        }

        if (sectionName != null && !scalars.isEmpty()) {
            builder.append('[').append(sectionName).append(']').append("\n");
        }

        for (Map.Entry<String, Object> entry : scalars.entrySet()) {
            builder.append(entry.getKey())
                    .append(" = ")
                    .append(formatValue(entry.getValue()))
                    .append("\n");
        }

        if ((sectionName != null && !scalars.isEmpty() && !tables.isEmpty())
                || (sectionName == null && !scalars.isEmpty() && !tables.isEmpty())) {
            builder.append("\n");
        }

        boolean firstNested = true;
        for (Map.Entry<String, Map<String, Object>> entry : tables.entrySet()) {
            String nestedSection = sectionName == null ? entry.getKey() : sectionName + "." + entry.getKey();
            StringBuilder nestedBuilder = new StringBuilder();
            appendSection(nestedBuilder, nestedSection, entry.getValue());
            String renderedNested = nestedBuilder.toString().trim();
            if (renderedNested.isEmpty()) {
                continue;
            }
            if (!firstNested) {
                builder.append("\n");
            }
            builder.append(renderedNested).append("\n");
            firstNested = false;
        }
    }

    @SuppressWarnings("unchecked")
    private static String formatValue(Object value) {
        return switch (value) {
            case String string -> '"' + escapeString(string) + '"';
            case Number number -> number.toString();
            case Boolean bool -> bool.toString();
            case List<?> list -> formatList(list);
            case Iterable<?> iterable -> formatList(iterableToList(iterable));
            default -> '"' + escapeString(String.valueOf(value)) + '"';
        };
    }

    private static String formatList(List<?> list) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(formatValue(list.get(i)));
        }
        return builder.append(']').toString();
    }

    private static List<Object> iterableToList(Iterable<?> iterable) {
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(value -> (Object) value)
                .toList();
    }

    private static String escapeString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
