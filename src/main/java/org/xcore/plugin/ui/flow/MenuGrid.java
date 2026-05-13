package org.xcore.plugin.ui.flow;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;

import java.util.ArrayList;
import java.util.List;

public class MenuGrid {
    private final List<List<MenuButton>> rows = new ArrayList<>();

    public MenuGrid row(MenuButton... buttons) {
        rows.add(List.of(buttons));
        return this;
    }

    public MenuGrid rowIf(boolean condition, MenuButton... buttons) {
        if (condition) {
            rows.add(List.of(buttons));
        }
        return this;
    }

    public MenuGrid pagination(int currentPage, int totalPages, String prevAction, String nextAction, Localization loc) {
        var buttons = new ArrayList<MenuButton>();
        if (currentPage > 1) {
            buttons.add(MenuButton.of(loc.t("previous"), prevAction));
        }
        if (currentPage < totalPages) {
            buttons.add(MenuButton.of(loc.t("next"), nextAction));
        }
        if (!buttons.isEmpty()) {
            rows.add(List.copyOf(buttons));
        }
        return this;
    }

    public MenuGrid defaultNavigation(Session session, Localization loc) {
        var buttons = new ArrayList<MenuButton>();
        if (session.canGoBack()) {
            buttons.add(MenuButton.of(loc.t("back"), "back"));
        }
        buttons.add(MenuButton.of(loc.t("close"), "close"));
        rows.add(List.copyOf(buttons));
        return this;
    }

    public List<List<MenuButton>> build() {
        return List.copyOf(rows);
    }
}
