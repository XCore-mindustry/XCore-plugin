package org.xcore.plugin.metrics;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class LabelSchema {
    private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final LabelSchema EMPTY = new LabelSchema(List.of());

    private final List<String> names;

    private LabelSchema(List<String> names) {
        this.names = List.copyOf(names);
    }

    public static LabelSchema empty() {
        return EMPTY;
    }

    public static LabelSchema of(String... names) {
        Objects.requireNonNull(names, "names must not be null");
        Set<String> unique = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || !LABEL_NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Label name must be Prometheus-compatible: " + name);
            }
            if ("server".equals(name)) {
                throw new IllegalArgumentException("Metric labels must not include reserved label 'server'");
            }
            if (!unique.add(name)) {
                throw new IllegalArgumentException("Duplicate label name: " + name);
            }
        }
        return unique.isEmpty() ? EMPTY : new LabelSchema(List.copyOf(unique));
    }

    public Map<String, Object> toLabelMap(Tags tags) {
        validate(tags);
        if (names.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> labels = new LinkedHashMap<>();
        for (String name : names) {
            labels.put(name, tags.value(name));
        }
        return labels;
    }

    public void validate(Tags tags) {
        Objects.requireNonNull(tags, "tags must not be null");
        if (tags.size() != names.size()) {
            throw new IllegalArgumentException("Expected " + names.size() + " metric label(s), got " + tags.size());
        }
        for (int i = 0; i < tags.size(); i++) {
            String name = tags.nameAt(i);
            if (!names.contains(name)) {
                throw new IllegalArgumentException("Unexpected metric label: " + name);
            }
        }
        for (String expected : names) {
            if (!tags.contains(expected)) {
                throw new IllegalArgumentException("Missing metric label: " + expected);
            }
        }
    }
}
