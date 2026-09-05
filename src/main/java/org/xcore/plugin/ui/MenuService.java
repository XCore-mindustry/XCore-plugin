package org.xcore.plugin.ui;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.ui.Menus;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.ActiveMenuPrompt;
import org.xcore.plugin.ui.flow.ActiveMenuScreen;
import org.xcore.plugin.ui.flow.MenuAction;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuFlow;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
public class MenuService {

    private final Provider<SessionService> sessionService;
    private final MindustryMenuGateway gateway;
    private final Map<String, RoutedMenuFlow<?>> routedFlows = new HashMap<>();
    private final List<MenuLifecycleListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private int globalMenuId;
    private int globalTextId;

    public interface MenuLifecycleListener {
        default void onMenuOpened(Session session) {}
        default void onMenuClosed(Session session) {}
    }

    public MenuService(Provider<SessionService> sessionService, MindustryMenuGateway gateway) {
        this.sessionService = sessionService;
        this.gateway = gateway;
    }

    @PostConstruct
    public void init() {
        this.globalMenuId = Menus.registerMenu((player, option) -> {
            if (player == null) return;
            Session session = sessionService.get().get(player.uuid());
            onMenuOption(session, option);
        });

        this.globalTextId = Menus.registerTextInput((player, text) -> {
            if (player == null) return;
            Session session = sessionService.get().get(player.uuid());
            onTextInput(session, text);
        });
    }

    public int getMenuId() {
        return globalMenuId;
    }

    public int getTextId() {
        return globalTextId;
    }

    public void addListener(MenuLifecycleListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(MenuLifecycleListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public boolean isMenuOpen(Session session) {
        return session != null && session.hasActiveMenu();
    }

    private void notifyMenuOpened(Session session) {
        if (session == null) return;
        for (MenuLifecycleListener listener : listeners) {
            try {
                listener.onMenuOpened(session);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyMenuClosed(Session session) {
        if (session == null) return;
        for (MenuLifecycleListener listener : listeners) {
            try {
                listener.onMenuClosed(session);
            } catch (Throwable ignored) {
            }
        }
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

    public void show(Session session, String title, String content, List<List<String>> rows, List<MenuAction> actions, MenuMode mode) {
        if (session == null || session.player == null || session.player.con == null) return;

        long version = session.nextUiVersion();
        var screen = ActiveMenuScreen.create(version, mode, actions);
        session.setActiveScreen(screen);
        notifyMenuOpened(session);

        String[][] buttons = convertListToArray(rows);
        if (mode == MenuMode.FOLLOW_UP) {
            gateway.followUpMenu(session.player, globalMenuId, title, content, buttons);
        } else {
            gateway.menu(session.player, globalMenuId, title, content, buttons);
        }
    }

    public <TState> void show(Session session, MenuScreen screen, MenuFlow<TState> flow, TState state) {
        show(session, screen, flow, state, null);
    }

    public <TState> void show(Session session, MenuScreen screen, MenuFlow<TState> flow, TState state, MenuRoute route) {
        if (session == null || session.player == null || session.player.con == null) return;

        long version = session.nextUiVersion();
        List<MenuAction> actions = screen.toActions();
        List<String> actionIds = screen.rows().stream()
                .flatMap(row -> row.stream().map(MenuButton::actionId))
                .toList();
        var active = ActiveMenuScreen.create(version, screen.mode(), actions, flow, state, actionIds, route);
        session.setActiveScreen(active);
        notifyMenuOpened(session);

        String[][] buttons = convertListToArray(screen.toTextRows());
        if (screen.mode() == MenuMode.FOLLOW_UP) {
            gateway.followUpMenu(session.player, globalMenuId, screen.title(), screen.content(), buttons);
        } else {
            gateway.menu(session.player, globalMenuId, screen.title(), screen.content(), buttons);
        }
    }

    public <TState> void renderFlow(Session session, MenuFlow<TState> flow) {
        TState state = session.getDraft(flow.stateType());
        renderFlow(session, flow, state, null);
    }

    public <TState> void renderFlow(Session session, MenuFlow<TState> flow, TState state, MenuRoute route) {
        var context = new MenuRenderContext<>(this, session, flow, state, route);
        MenuScreen screen = flow.render(context);
        show(session, screen, flow, state, route);
    }

    public void registerRoute(RoutedMenuFlow<?> flow) {
        RoutedMenuFlow<?> previous = routedFlows.putIfAbsent(flow.routeId(), flow);
        if (previous != null) {
            throw new IllegalStateException("Duplicate menu route id: " + flow.routeId());
        }
    }

    public void renderRoute(Session session, MenuRoute route) {
        if (session == null || route == null) return;

        RoutedMenuFlow<?> flow = routedFlows.get(route.id());
        if (flow == null) {
            throw new IllegalArgumentException("Unknown menu route: " + route.id());
        }

        renderResolvedRoute(session, route, flow);
    }

    public void openRoute(Session session, MenuRoute route) {
        if (session == null || route == null) return;

        var activeScreen = session.activeScreen();
        if (activeScreen != null && activeScreen.hasRoute()) {
            session.pushRouteHistory(activeScreen.route());
        }

        renderRoute(session, route);
    }

    public boolean goBack(Session session) {
        if (session == null) {
            return false;
        }

        var activeScreen = session.activeScreen();
        if (session.hasRouteHistory() && (activeScreen == null || activeScreen.hasRoute())) {
            if (activeScreen != null && activeScreen.mode() == MenuMode.FOLLOW_UP && session.player != null) {
                gateway.hideFollowUpMenu(session.player, globalMenuId);
            }
            renderRoute(session, session.popRouteHistory());
            return true;
        }

        Runnable previousMenu = session.popHistory();
        if (previousMenu != null) {
            if (activeScreen != null && activeScreen.mode() == MenuMode.FOLLOW_UP && session.player != null) {
                gateway.hideFollowUpMenu(session.player, globalMenuId);
            }
            session.clearActivePrompt();
            session.textHandler = null;
            session.actions.clear();
            if (session.activeScreen() == activeScreen) {
                session.clearActiveScreen();
            }
            previousMenu.run();
            return true;
        }

        return false;
    }

    public void hideFollowUp(Session session) {
        if (session == null || session.player == null) return;
        var screen = session.activeScreen();
        if (screen != null && screen.mode() == MenuMode.FOLLOW_UP) {
            gateway.hideFollowUpMenu(session.player, globalMenuId);
        }
        if (session.activeScreen() == screen) {
            session.clearActiveScreen();
        }
        notifyMenuClosed(session);
    }

    public void close(Session session) {
        if (session == null) return;
        var screen = session.activeScreen();

        session.clearActivePrompt();
        session.textHandler = null;
        session.actions.clear();

        if (screen != null && screen.mode() == MenuMode.FOLLOW_UP) {
            if (session.player != null) {
                gateway.hideFollowUpMenu(session.player, globalMenuId);
            }
        }
        if (screen != null && screen.hasFlow()) {
            dispatchFlowClose(screen, session);
        }
        if (session.activeScreen() == screen) {
            session.clearActiveScreen();
        }
        notifyMenuClosed(session);
    }

    public void openUri(Session session, String uri) {
        if (session == null || session.player == null || session.player.con == null) return;
        gateway.openUri(session.player, uri);
    }

    public void copyToClipboard(Session session, String text) {
        if (session == null || session.player == null || session.player.con == null) return;
        gateway.copyToClipboard(session.player, text);
    }

    void showRaw(Session session, int menuId, String title, String content, List<List<String>> rows) {
        if (session == null || session.player == null || session.player.con == null) return;
        gateway.menu(session.player, menuId, title, content, convertListToArray(rows));
    }

    public void openTextPrompt(Session session, String title, String content, int length, String def, boolean numeric, Consumer<String> onSubmit, Runnable onCancel) {
        if (session == null || session.player == null || session.player.con == null) return;

        long version = session.nextUiVersion();
        var prompt = ActiveMenuPrompt.create(version, globalTextId, onSubmit, onCancel);
        session.setActivePrompt(prompt);
        notifyMenuOpened(session);

        gateway.textInput(session.player, globalTextId, title, content, length, def, numeric);
    }

    public void openPrompt(Session session, MenuPrompt prompt, Consumer<String> onSubmit, Runnable onCancel) {
        openTextPrompt(session, prompt.title(), prompt.content(), prompt.length(), prompt.defaultValue(), prompt.numeric(), onSubmit, onCancel);
    }

    public <TState> void openPrompt(Session session, MenuFlow<TState> flow, TState state, MenuPrompt prompt) {
        if (session == null || session.player == null || session.player.con == null) return;

        long version = session.nextUiVersion();
        MenuRoute route = session.activeScreen() != null ? session.activeScreen().route() : null;
        var active = ActiveMenuPrompt.create(version, globalTextId, null, null, flow, state, prompt.promptId(), route);
        session.setActivePrompt(active);
        notifyMenuOpened(session);

        gateway.textInput(session.player, globalTextId, prompt.title(), prompt.content(), prompt.length(), prompt.defaultValue(), prompt.numeric());
    }

    // Package-private for testing
    void onMenuOption(Session session, int option) {
        if (session == null || session.data == null) return;

        var screen = session.activeScreen();
        if (screen != null) {
            if (option == -1) {
                session.clearActivePrompt();
                session.textHandler = null;
                session.actions.clear();

                if (screen.mode() == MenuMode.FOLLOW_UP) {
                    gateway.hideFollowUpMenu(session.player, globalMenuId);
                }
                if (screen.hasFlow()) {
                    dispatchFlowClose(screen, session);
                }
                if (session.activeScreen() == screen) {
                    session.clearActiveScreen();
                }
                notifyMenuClosed(session);
                return;
            }
            if (option >= 0 && option < screen.actionCount()) {
                if (screen.hasFlow()) {
                    dispatchFlowAction(screen, session, option);
                } else {
                    screen.runAction(option);
                }
                if (session.activeScreen() == screen && screen.mode() != MenuMode.FOLLOW_UP) {
                    session.clearActiveScreen();
                    notifyMenuClosed(session);
                }
            }
            return;
        }

        if (option >= 0 && option < session.actions.size()) {
            session.actions.get(option).run();
        }
    }

    // Package-private for testing
    void onTextInput(Session session, String text) {
        if (session == null || session.data == null) return;

        var prompt = session.activePrompt();
        if (prompt != null) {
            session.clearActivePrompt();
            notifyMenuClosed(session);
            if (prompt.hasFlow()) {
                dispatchFlowPrompt(prompt, session, text);
            } else {
                if (text == null) {
                    prompt.cancel();
                } else {
                    prompt.submit(text);
                }
            }
            return;
        }

        if (text == null) {
            session.textHandler = null;
        } else if (session.textHandler != null) {
            var handler = session.textHandler;
            session.textHandler = null;
            handler.accept(text);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchFlowClose(ActiveMenuScreen screen, Session session) {
        var flow = (MenuFlow<Object>) screen.flow();
        var state = screen.state();
        var context = new MenuRenderContext<>(this, session, flow, state, screen.route());
        flow.onClose(context);
    }

    @SuppressWarnings("unchecked")
    private void dispatchFlowAction(ActiveMenuScreen screen, Session session, int option) {
        var flow = (MenuFlow<Object>) screen.flow();
        var state = screen.state();
        var context = new MenuRenderContext<>(this, session, flow, state, screen.route());
        String actionId = screen.actionIdAt(option);
        if (actionId != null) {
            flow.onAction(context, actionId);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchFlowPrompt(ActiveMenuPrompt prompt, Session session, String text) {
        var flow = (MenuFlow<Object>) prompt.flow();
        var state = prompt.state();
        var context = new MenuRenderContext<>(this, session, flow, state, prompt.route());
        if (text == null) {
            flow.onPromptCancel(context, prompt.promptIdString());
        } else {
            flow.onPromptSubmit(context, prompt.promptIdString(), text);
        }
    }

    @SuppressWarnings("unchecked")
    private <TState> void renderResolvedRoute(Session session, MenuRoute route, RoutedMenuFlow<?> routedFlow) {
        RoutedMenuFlow<TState> flow = (RoutedMenuFlow<TState>) routedFlow;
        TState currentState = session.getDraft(flow.stateType());
        TState state = flow.createState(session, route, currentState);
        if (state != null) {
            session.setDraft(flow.stateType(), state);
        }

        var context = new MenuRenderContext<>(this, session, flow, state, route);
        MenuScreen screen = flow.render(context);
        show(session, screen, flow, state, route);
    }
}
