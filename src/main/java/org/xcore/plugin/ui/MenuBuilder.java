package org.xcore.plugin.ui;

import org.xcore.plugin.common.StatusEnum;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuAction;
import org.xcore.plugin.ui.flow.MenuMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ospx.flubundle.Bundle.args;


public class MenuBuilder {
    public final MenuService service;
    public final Session session;
    public final Localization localization;

    public String title = "";
    public String content = "";
    public final List<List<String>> rows = new ArrayList<>();
    public final List<String> row = new ArrayList<>();
    public MenuMode mode = MenuMode.NORMAL;

    public MenuBuilder(MenuService service, Session session) {
        this.service = service;
        this.session = session;
        this.localization = session.locale();
    }

    public MenuBuilder title(String key) {
        this.title = localization.format(key);
        return this;
    }

    public MenuBuilder title(String key, Map<String, Object> args) {
        this.title = localization.format(key, args);
        return this;
    }

    public MenuBuilder title(String rawTitle, boolean raw) {
        this.title = rawTitle;
        return this;
    }

    public MenuBuilder content(String key) {
        this.content = localization.format(key, args());
        return this;
    }

    public MenuBuilder content(String key, Map<String, Object> args) {
        this.content = localization.format(key, args);
        return this;
    }

    public MenuBuilder rawContent(String content) {
        this.content = content;
        return this;
    }

    public MenuBuilder addRowText(String buttonText, Runnable action) {
        rows.add(List.of(session.add(buttonText, action)));
        return this;
    }

    public MenuBuilder addRowText(String btn1, Runnable action1, String btn2, Runnable action2) {
        rows.add(List.of(session.add(btn1, action1), session.add(btn2, action2)));
        return this;
    }

    public MenuBuilder addRowText(List<MenuBuilder.ButtonDef> buttons) {
        String[] row = buttons.stream()
                .map(b -> session.add(b.text, b.action))
                .toArray(String[]::new);
        rows.add(List.of(row));
        return this;
    }

    public MenuBuilder addRow(String buttonText, Runnable action) {
        return addRowText(buttonText, action);
    }

    public MenuBuilder addRow(String btn1, Runnable action1, String btn2, Runnable action2) {
        return addRowText(btn1, action1, btn2, action2);
    }

    public MenuBuilder addRow(List<MenuBuilder.ButtonDef> buttons) {
        return addRowText(buttons);
    }

    public MenuBuilder addRowKey(String buttonKey, Runnable action) {
        rows.add(List.of(session.add(localization.t(buttonKey), action)));
        return this;
    }

    public MenuBuilder addRowKey(String btn1Key, Runnable action1, String btn2Key, Runnable action2) {
        rows.add(List.of(session.add(localization.t(btn1Key), action1), session.add(localization.t(btn2Key), action2)));
        return this;
    }

    public MenuBuilder addRowKey(List<MenuBuilder.ButtonDef> buttons) {
        String[] row = buttons.stream()
                .map(b -> session.add(localization.t(b.text), b.action))
                .toArray(String[]::new);
        rows.add(List.of(row));
        return this;
    }

    public MenuBuilder addLocalRow(String buttonText, Runnable action) {
        return addRowKey(buttonText, action);
    }

    public MenuBuilder addLocalRow(String btn1, Runnable action1, String btn2, Runnable action2) {
        return addRowKey(btn1, action1, btn2, action2);
    }

    public MenuBuilder addLocalRow(List<MenuBuilder.ButtonDef> buttons) {
        return addRowKey(buttons);
    }

    public MenuBuilder addButtonText(String buttonText, Runnable action) {
        row.add(session.add(buttonText, action));
        return this;
    }

    public MenuBuilder addButtonKey(String buttonKey, Runnable action) {
        row.add(session.add(localization.t(buttonKey), action));
        return this;
    }

    public MenuBuilder addButtonKey(String buttonKey, Runnable action, Map<String, Object> args) {
        row.add(session.add(localization.t(buttonKey, args), action));
        return this;
    }

    public MenuBuilder add(String buttonText, Runnable action) {
        return addButtonText(buttonText, action);
    }


    public MenuBuilder addLocal(String buttonText, Runnable action) {
        return addButtonKey(buttonText, action);
    }

    public MenuBuilder addLocal(String buttonText, Runnable action, Map<String, Object> args) {
        return addButtonKey(buttonText, action, args);
    }

    public MenuBuilder start() {
        row.clear();
        return this;
    }

    public MenuBuilder end() {
        if (!row.isEmpty()) {
            rows.add(new ArrayList<>(row));
            row.clear();
        }
        return this;
    }

    public MenuBuilder addNavigationRow() {
        start();

        if (session.hasHistory() || session.hasRouteHistory()) {
            row.add(session.add(localization.format("back", args()), () -> {
                service.goBack(session);
            }));
        }

        row.add(session.add(localization.format("close", args()), () -> service.close(session)));

        end();
        return this;
    }

    public MenuBuilder addStatusButton(String buttonText, Runnable action) {
        start();
        StatusEnum buttonStatus = session.sortStatus.getOrDefault(buttonText, StatusEnum.Neutral);

        Runnable lambda = () -> {
            session.setNextStatus(buttonText);
            if (action != null) action.run();
        };

        if (buttonStatus == StatusEnum.Neutral) {
            row.add(session.add(localization.format(buttonText + "-neutral", args()), lambda));
        } else if (buttonStatus == StatusEnum.Active) {
            row.add(session.add(localization.format(buttonText + "-active", args()), lambda));
        } else if (buttonStatus == StatusEnum.Inactive) {
            row.add(session.add(localization.format(buttonText + "-inactive", args()), lambda));
        }
        end();
        return this;
    }

    public MenuBuilder followUp() {
        this.mode = MenuMode.FOLLOW_UP;
        return this;
    }

    public boolean showFollowUp() {
        this.mode = MenuMode.FOLLOW_UP;
        return show();
    }

    public boolean show() {
        if (!row.isEmpty()) {
            end();
        }

        List<MenuAction> actions = session.actions.stream()
                .map(a -> (MenuAction) new MenuAction.CallbackAction(a))
                .collect(Collectors.toList());

        service.show(session, title, content, rows, actions, mode);
        return true;
    }

    public boolean show(int menuId) {
        if (!row.isEmpty()) {
            end();
        }

        if (menuId == service.getMenuId()) {
            return show();
        }

        service.showRaw(session, menuId, title, content, rows);
        return true;
    }

    public void clear() {
        row.clear();
    }

    public void clearRows() {
        rows.clear();
        row.clear();
    }

    public MenuBuilder ifAddButtonText(boolean bool, String buttonText, Runnable action) {
        if (bool) {
            row.add(session.add(buttonText, action));
        }
        return this;
    }

    public MenuBuilder ifAddButtonKey(boolean bool, String buttonKey, Runnable action) {
        if (bool) {
            row.add(session.add(localization.t(buttonKey), action));
        }
        return this;
    }

    public MenuBuilder ifAddButtonKey(boolean bool, String buttonKey, Runnable action, Map<String, Object> args) {
        if (bool) {
            row.add(session.add(localization.t(buttonKey, args), action));
        }
        return this;
    }

    public MenuBuilder ifAdd(boolean bool, String buttonText, Runnable action) {
        return ifAddButtonText(bool, buttonText, action);
    }

    public MenuBuilder ifAddLocal(boolean bool, String buttonText, Runnable action) {
        return ifAddButtonKey(bool, buttonText, action);
    }

    public MenuBuilder ifAddLocal(boolean bool, String buttonText, Runnable action, Map<String, Object> args) {
        return ifAddButtonKey(bool, buttonText, action, args);
    }

    public MenuBuilder ifElseAdd(boolean bool, String buttonText1, Runnable action1, String buttonText2, Runnable action2) {
        if (bool) {
            row.add(session.add(buttonText1, action1));
        } else {
            row.add(session.add(buttonText2, action2));
        }

        return this;
    }

    public MenuBuilder apply(java.util.function.Consumer<MenuBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public <T> MenuBuilder addForEach(Iterable<T> items, java.util.function.BiConsumer<MenuBuilder, T> action) {
        for (T item : items) {
            action.accept(this, item);
        }
        return this;
    }

    public record ButtonDef(String text, Runnable action) {}
}
