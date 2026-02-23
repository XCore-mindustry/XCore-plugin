package org.xcore.plugin.ui;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.ui.Menus;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;

import java.util.List;

@Singleton
public class MenuService {

    private final Provider<SessionService> sessionService;

    private int globalMenuId;
    private int globalTextId;

    @Inject
    public MenuService(Provider<SessionService> sessionService) {
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void init() {
        this.globalMenuId = Menus.registerMenu((player, option) -> {
            Session session = sessionService.get().get(player.uuid());
            if (session == null || session.data == null) return;
            if (option >= 0 && option < session.actions.size()) {
                session.actions.get(option).run();
            }
        });

        this.globalTextId = Menus.registerTextInput((player, text) -> {
            Session session = sessionService.get().get(player.uuid());
            if (session == null || session.data == null) return;
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

    public MenuBuilder builder(String uuid) {
        return new MenuBuilder(this, sessionService.get().get(uuid));
    }

    public MenuBuilder builder(Session session) {
        return new MenuBuilder(this, session);
    }

    public String[][] convertListToArray(List<List<String>> rows) {
        return rows.stream().map(innerList -> innerList.toArray(new String[0])).toArray(String[][]::new);
    }
}