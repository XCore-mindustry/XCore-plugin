package org.xcore.plugin.ui.flow;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

public class MenuRenderContext<TState> {
    private final MenuService menuService;
    private final Session session;
    private final MenuFlow<TState> flow;
    private final TState state;
    private final MenuRoute route;

    public MenuRenderContext(MenuService menuService, Session session, MenuFlow<TState> flow, TState state) {
        this(menuService, session, flow, state, null);
    }

    public MenuRenderContext(MenuService menuService, Session session, MenuFlow<TState> flow, TState state, MenuRoute route) {
        this.menuService = menuService;
        this.session = session;
        this.flow = flow;
        this.state = state;
        this.route = route;
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

    public boolean hasRoute() {
        return route != null;
    }

    public MenuRoute route() {
        return route;
    }

    public void render() {
        menuService.renderFlow(session, flow, state, route);
    }

    public void openRoute(MenuRoute route) {
        menuService.openRoute(session, route);
    }

    public boolean goBack() {
        return menuService.goBack(session);
    }

    public void openPrompt(MenuPrompt prompt) {
        menuService.openPrompt(session, flow, state, prompt);
    }

    public void close() {
        menuService.close(session);
    }
}
