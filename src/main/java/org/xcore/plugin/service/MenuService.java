package org.xcore.plugin.service;

import arc.struct.ObjectMap;
import jakarta.inject.Singleton;
import mindustry.ui.Menus;
import org.xcore.plugin.ui.MenuSession;

@Singleton
public class MenuService {

    private final ObjectMap<String, MenuSession> sessions = new ObjectMap<>();

    private int globalMenuId;
    private int globalTextId;


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

    public int getMenuId() { return globalMenuId; }
    public int getTextId() { return globalTextId; }

    public MenuSession get(String uuid) {
        if (!sessions.containsKey(uuid)) {
            sessions.put(uuid, new MenuSession());
        }
        return sessions.get(uuid);
    }

    public void clear(String uuid) {
        sessions.remove(uuid);
    }
}