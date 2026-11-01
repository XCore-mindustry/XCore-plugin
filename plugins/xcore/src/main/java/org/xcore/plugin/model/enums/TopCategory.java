package org.xcore.plugin.model.enums;

public enum TopCategory {
    PLAYTIME("top-menu-category-playtime"),
    HEXED("top-menu-category-hexed");

    private final String bundleKey;

    TopCategory(String bundleKey) {
        this.bundleKey = bundleKey;
    }

    public String bundleKey() {
        return bundleKey;
    }
}
