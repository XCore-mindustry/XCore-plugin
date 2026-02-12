package org.xcore.plugin.ui;

import arc.struct.ObjectMap;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MenuService {

    private final BundleService bundle;
    private final GlobalConfig globalConfig;

    private final ObjectMap<String, MenuSession> sessions = new ObjectMap<>();

    private int globalMenuId;
    private int globalTextId;

    @Inject
    public MenuService(BundleService bundle,  GlobalConfig globalConfig) {
        this.bundle = bundle;
        this.globalConfig = globalConfig;
    }

    @PostConstruct
    public void init() {
        this.globalMenuId = Menus.registerMenu((player, option) -> {
            MenuSession session = get(player.uuid());
            if (option >= 0 && option < session.actions.size()) {
                session.actions.get(option).run();
            }
        });

        this.globalTextId = Menus.registerTextInput((player, text) -> {
            MenuSession session = get(player.uuid());
            if (session.textHandler != null && text != null) {
                session.textHandler.accept(text);
            }
        });
    }

    public int getMenuId() {
        return globalMenuId;
    }

    public int getTextId() {
        return globalTextId;
    }

    public MenuSession get(String uuid) {
        if (!sessions.containsKey(uuid)) {
            sessions.put(uuid, new MenuSession(globalConfig));
        }
        return sessions.get(uuid);
    }

    public void clear(String uuid) {
        sessions.remove(uuid);
    }

    public void addNavigationRow(Player player, MenuSession session, List<List<String>> rows) {
        List<String> navRow = new ArrayList<>();

        if (session.hasHistory()) {
            navRow.add(session.add(bundle.format(bundle.locale(player), "back", args()), () -> {
                Runnable previousMenu = session.popHistory();
                if (previousMenu != null) previousMenu.run();
            }));
        }

        navRow.add(session.add(bundle.format(bundle.locale(player), "close", args()), () -> {
            clear(player.uuid());
        }));

        rows.add(navRow);
    }

    public void addStatusButton(Player player, MenuSession session, List<String> row, String key, Runnable runnable) {
        StatusEnum buttonStatus = session.sortStatus.getOrDefault(key, StatusEnum.Neutral);

        Runnable lambda = () -> {
            session.setNextStatus(key);
            if (runnable != null) runnable.run();
        };

        if (buttonStatus == StatusEnum.Neutral) {
            row.add(session.add(bundle.format(bundle.locale(player), key + "-neutral", args()), lambda));
        } else if (buttonStatus == StatusEnum.Active) {
            row.add(session.add(bundle.format(bundle.locale(player), key + "-active", args()), lambda));
        } else if (buttonStatus == StatusEnum.Inactive) {
            row.add(session.add(bundle.format(bundle.locale(player), key + "-inactive", args()), lambda));
        }
    }

    public String[][] convertListToArray(List<List<String>> rows) {
        String[][] result = new String[rows.size()][];

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);

            result[i] = row.toArray(new String[0]);
        }

        return result;
    }
}