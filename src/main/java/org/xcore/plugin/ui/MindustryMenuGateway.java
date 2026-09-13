package org.xcore.plugin.ui;

import mindustry.gen.Player;
import mindustry.ui.builder.UiBuilder.NodeBuilder;

public interface MindustryMenuGateway {
    void menu(Player player, int menuId, String title, String content, String[][] buttons);

    void followUpMenu(Player player, int menuId, String title, String content, String[][] buttons);

    void hideFollowUpMenu(Player player, int menuId);

    void textInput(Player player, int textInputId, String title, String content, int length, String def, boolean numeric);

    void openUri(Player player, String uri);

    void copyToClipboard(Player player, String text);

    void menuBuilder(Player player, int menuId, long token, String title, boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui);

    void menuBuilderUpdate(Player player, int menuId, String tableId, NodeBuilder<?> ui);

    void hideMenuBuilder(Player player, int menuId);
}
