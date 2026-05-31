package org.xcore.plugin.metrics;

import java.util.Objects;

public final class Tags {
    private static final Tags EMPTY = new Tags(new String[0]);

    private final String[] entries;

    private Tags(String[] entries) {
        this.entries = entries;
    }

    public static Tags empty() {
        return EMPTY;
    }

    public static Tags of(String key, String value) {
        return ofPairs(key, value);
    }

    public static Tags of(String key1, String value1, String key2, String value2) {
        return ofPairs(key1, value1, key2, value2);
    }

    private static Tags ofPairs(String... entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        if ((entries.length & 1) != 0) {
            throw new IllegalArgumentException("Metric tags must contain key/value pairs");
        }
        String[] normalized = new String[entries.length];
        for (int i = 0; i < entries.length; i += 2) {
            String key = entries[i];
            String value = entries[i + 1];
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Metric tag key must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("Metric tag value must not be null");
            }
            normalized[i] = key;
            normalized[i + 1] = value;
        }
        return normalized.length == 0 ? EMPTY : new Tags(normalized);
    }

    int size() {
        return entries.length / 2;
    }

    String nameAt(int index) {
        return entries[index * 2];
    }

    String valueAt(int index) {
        return entries[index * 2 + 1];
    }

    boolean contains(String name) {
        for (int i = 0; i < size(); i++) {
            if (nameAt(i).equals(name)) {
                return true;
            }
        }
        return false;
    }

    String value(String name) {
        for (int i = 0; i < size(); i++) {
            if (nameAt(i).equals(name)) {
                return valueAt(i);
            }
        }
        throw new IllegalArgumentException("Missing tag value for label: " + name);
    }
}
