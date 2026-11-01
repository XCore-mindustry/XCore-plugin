package org.xcore.plugin.ui.menu.help;

import mindustry.gen.Iconc;

public enum HelpCategory {
    ALL("help-cat-all", Iconc.grid, "ffd37f"),
    GENERAL("help-cat-general", Iconc.info, "8be9fd"),
    GAME("help-cat-game", Iconc.planet, "50fa7b"),
    SOCIAL("help-cat-social", Iconc.chat, "bd93f9"),
    VOTES("help-cat-votes", Iconc.refresh, "ffb86c"),
    ADMIN("help-cat-admin", Iconc.hammer, "ff5555");

    private final String bundleKey;
    private final char icon;
    private final String colorHex;

    HelpCategory(String bundleKey, char icon, String colorHex) {
        this.bundleKey = bundleKey;
        this.icon = icon;
        this.colorHex = colorHex;
    }

    public String bundleKey() {
        return bundleKey;
    }

    public char icon() {
        return icon;
    }

    public String colorHex() {
        return colorHex;
    }
}
