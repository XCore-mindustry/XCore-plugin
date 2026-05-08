package org.xcore.plugin.ui.flow;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;

public class MenuRenderContext<TState> {
    private final MenuService menuService;
    private final Session session;
    private final MenuFlow<TState> flow;
    private final TState state;

    public MenuRenderContext(MenuService menuService, Session session, MenuFlow<TState> flow, TState state) {
        this.menuService = menuService;
        this.session = session;
        this.flow = flow;
        this.state = state;
    }

    public Session session() {
        return session;
    }

    public TState state() {
        return state;
    }

    public Localization locale() {
        return session.locale();
    }

    public void render() {
        menuService.renderFlow(session, flow);
    }

    public void openPrompt(MenuPrompt prompt) {
        menuService.openPrompt(session, flow, state, prompt);
    }

    public void close() {
        menuService.close(session);
    }
}
