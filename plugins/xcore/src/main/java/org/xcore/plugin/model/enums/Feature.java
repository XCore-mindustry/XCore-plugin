package org.xcore.plugin.model.enums;

import java.util.Optional;

public enum Feature {
    RTV("rtv"),
    VNW("vnw");

    private final String key;

    Feature(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Feature> fromKey(String key) {
        if (key == null) return Optional.empty();
        for (Feature f : values()) {
            if (f.key.equalsIgnoreCase(key)) return Optional.of(f);
        }
        return Optional.empty();
    }
}
