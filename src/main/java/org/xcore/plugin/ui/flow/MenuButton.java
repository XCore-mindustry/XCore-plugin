package org.xcore.plugin.ui.flow;

public record MenuButton(String text, String actionId) {
    public static MenuButton of(String text, String actionId) {
        return new MenuButton(text, actionId);
    }
}
