package org.xcore.plugin.ui;

import mindustry.gen.Player;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import org.xcore.testkit.ui.DeterministicUiLoop;

/** Sends built menus into a {@link DeterministicUiLoop} instead of a connection; classic menus go nowhere. */
public final class LoopMenuGateway implements MindustryMenuGateway {
    private final DeterministicUiLoop loop;

    public LoopMenuGateway(DeterministicUiLoop loop) {
        this.loop = loop;
    }

    @Override public void menu(Player p, int id, String t, String c, String[][] b) {}
    @Override public void followUpMenu(Player p, int id, String t, String c, String[][] b) {}
    @Override public void hideFollowUpMenu(Player p, int id) {}
    @Override public void textInput(Player p, int id, String t, String c, int l, String d, boolean n) {}
    @Override public void openUri(Player p, String u) {}
    @Override public void copyToClipboard(Player p, String t) {}

    @Override
    public void menuBuilder(Player player, int menuId, long token, String title,
                            boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui) {
        loop.sendShow(menuId, token, hideExisting, ui);
    }

    @Override
    public void menuBuilderUpdate(Player player, int menuId, String tableId, NodeBuilder<?> ui) {
        loop.sendUpdate(menuId, tableId, ui);
    }

    @Override
    public void hideMenuBuilder(Player player, int menuId) {
        loop.sendHide(menuId);
    }
}
