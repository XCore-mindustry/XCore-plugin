package org.xcore.plugin.ui.route;

import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuFlow;

public interface RoutedMenuFlow<TState> extends MenuFlow<TState> {

    String routeId();

    TState createState(Session session, MenuRoute route, TState currentState);
}
