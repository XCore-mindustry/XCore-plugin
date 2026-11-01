package org.xcore.plugin.ui.flow;

import java.util.List;

public record MenuScreen(MenuMode mode, String title, String content, List<List<MenuButton>> rows) {

    public MenuScreen {
        rows = rows == null
                ? List.of()
                : rows.stream()
                .map(r -> r == null ? List.<MenuButton>of() : List.copyOf(r))
                .toList();
    }

    public static MenuScreen normal(String title, String content, List<List<MenuButton>> rows) {
        return new MenuScreen(MenuMode.NORMAL, title, content, rows);
    }

    public static MenuScreen followUp(String title, String content, List<List<MenuButton>> rows) {
        return new MenuScreen(MenuMode.FOLLOW_UP, title, content, rows);
    }

    public List<List<String>> toTextRows() {
        return rows.stream()
                .map(row -> row.stream().map(MenuButton::text).toList())
                .toList();
    }

    public List<MenuAction> toActions() {
        return rows.stream()
                .flatMap(row -> row.stream().map(b -> (MenuAction) new MenuAction.NamedAction(b.actionId(), null)))
                .toList();
    }

    public String actionIdAt(int option) {
        int idx = 0;
        for (var row : rows) {
            for (var btn : row) {
                if (idx == option) {
                    return btn.actionId();
                }
                idx++;
            }
        }
        return null;
    }

    public int actionCount() {
        return rows.stream().mapToInt(List::size).sum();
    }
}
