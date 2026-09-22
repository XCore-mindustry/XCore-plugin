package org.xcore.plugin.ui.flow;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MenuGrid {
    private static final List<List<MenuButton>> EMPTY = List.of();

    private final List<List<MenuButton>> rows = new ArrayList<>();

    public MenuGrid row(MenuButton... buttons) {
        rows.add(List.of(buttons));
        return this;
    }

    public MenuGrid row(Collection<MenuButton> buttons) {
        if (buttons != null && !buttons.isEmpty()) {
            rows.add(List.copyOf(buttons));
        }
        return this;
    }

    public MenuGrid rowIf(boolean condition, MenuButton... buttons) {
        if (condition) {
            rows.add(List.of(buttons));
        }
        return this;
    }

    public MenuGrid pagination(int currentPage, int totalPages, Localization loc) {
        return pagination(currentPage, totalPages, "previous", "next", loc);
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

    public MenuGrid defaultNavigation(Session session) {
        return defaultNavigation(session, session.locale());
    }

    public MenuGrid defaultNavigation(MenuRenderContext<?> context) {
        return defaultNavigation(context.session(), context.locale());
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

    public static List<List<MenuButton>> onlyClose(Localization loc) {
        return List.of(List.of(MenuButton.of(loc.t("close"), "close")));
    }

    public static List<List<MenuButton>> onlyBack(Localization loc) {
        return List.of(List.of(MenuButton.of(loc.t("back"), "back")));
    }

    public static List<List<MenuButton>> empty() {
        return EMPTY;
    }
}
