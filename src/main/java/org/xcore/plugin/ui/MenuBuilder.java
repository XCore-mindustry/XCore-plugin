package org.xcore.plugin.ui;

import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;


public class MenuBuilder {
    public final MenuService service;
    public final MenuSession session;
    public final XCoreSender sender;

    public String title = "";
    public String content = "";
    public final List<List<String>> rows = new ArrayList<>();
    public final List<String> row = new ArrayList<>();

    public MenuBuilder(MenuService service, MenuSession session) {
        this.service = service;
        this.session = session;
        this.sender = session.sender;
    }

    public MenuBuilder title(String key) {
        if (sender == null) return this;
        this.title = sender.format(key);
        return this;
    }

    public MenuBuilder title(String rawTitle, boolean raw) {
        this.title = rawTitle;
        return this;
    }

    public MenuBuilder content(String key) {
        if (sender == null) return this;
        this.content = sender.format(key, args());
        return this;
    }

    public MenuBuilder content(String key, Map<String, Object> args) {
        if (sender == null) return this;
        this.content = sender.format(key, args);
        return this;
    }

    public MenuBuilder rawContent(String content) {
        this.content = content;
        return this;
    }

    public MenuBuilder addRow(String buttonText, Runnable action) {
        rows.add(List.of(session.add(buttonText, action)));
        return this;
    }

    public MenuBuilder addRow(String btn1, Runnable action1, String btn2, Runnable action2) {
        rows.add(List.of(session.add(btn1, action1), session.add(btn2, action2)));
        return this;
    }

    public MenuBuilder addRow(List<MenuBuilder.ButtonDef> buttons) {
        String[] row = buttons.stream()
                .map(b -> session.add(b.text, b.action))
                .toArray(String[]::new);
        rows.add(List.of(row));
        return this;
    }

    public MenuBuilder add(String buttonText, Runnable action) {
        row.add(session.add(buttonText, action));
        return this;
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
        if (sender == null) return this;

        start();

        if (session.hasHistory()) {
            row.add(session.add(sender.format("back", args()), () -> {
                Runnable previousMenu = session.popHistory();
                if (previousMenu != null) previousMenu.run();
            }));
        }

        row.add(session.add(sender.format("close", args()), () -> {
            service.clear(sender.player().uuid());
        }));

        end();
        return this;
    }

    public MenuBuilder addStatusButton(String buttonText, Runnable action) {
        if (sender == null) return this;

        start();
        StatusEnum buttonStatus = session.sortStatus.getOrDefault(buttonText, StatusEnum.Neutral);

        Runnable lambda = () -> {
            session.setNextStatus(buttonText);
            if (action != null) action.run();
        };

        if (buttonStatus == StatusEnum.Neutral) {
            row.add(session.add(sender.format(buttonText + "-neutral", args()), lambda));
        } else if (buttonStatus == StatusEnum.Active) {
            row.add(session.add(sender.format(buttonText + "-active", args()), lambda));
        } else if (buttonStatus == StatusEnum.Inactive) {
            row.add(session.add(sender.format(buttonText + "-inactive", args()), lambda));
        }
        end();
        return this;
    }

    public boolean show() {
        return show(service.getMenuId());
    }

    public boolean show(int menuId) {
        if (sender == null) return false;

        if (!row.isEmpty()) {
            end();
        }

        Call.menu(sender.player().con, menuId, title, content, service.convertListToArray(rows));
        return true;
    }

    public void clear() {
        row.clear();
    }

    public void clearRows() {
        rows.clear();
        row.clear();
    }

    public MenuBuilder ifAdd(boolean bool, String buttonText, Runnable action) {
        if (bool) {
            row.add(session.add(buttonText, action));
        }
        return this;
    }

    public MenuBuilder ifElseAdd(boolean bool, String buttonText1, Runnable action1, String buttonText2, Runnable action2) {
        if (bool) {
            row.add(session.add(buttonText1, action1));
        } else {
            row.add(session.add(buttonText2, action2));
        }

        return this;
    }

    public record ButtonDef(String text, Runnable action) {}
}