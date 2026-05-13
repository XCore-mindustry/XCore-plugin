package org.xcore.plugin.ui.route;

import java.util.Map;

public record MenuRoute(String id, Map<String, String> params) {

    public MenuRoute {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Route id cannot be blank");
        }
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public MenuRoute(String id) {
        this(id, Map.of());
    }

    public static MenuRoute of(String id) {
        return new MenuRoute(id);
    }

    public MenuRoute withParam(String key, String value) {
        var nextParams = new java.util.LinkedHashMap<>(params);
        nextParams.put(key, value);
        return new MenuRoute(id, nextParams);
    }

    public String param(String key) {
        return params.get(key);
    }

    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    public int intParam(String key, int fallback) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
