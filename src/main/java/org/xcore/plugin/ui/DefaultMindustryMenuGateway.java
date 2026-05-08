package org.xcore.plugin.ui;

import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;

@Singleton
public class DefaultMindustryMenuGateway implements MindustryMenuGateway {

    @Override
    public void menu(Player player, int menuId, String title, String content, String[][] buttons) {
        if (player == null || player.con == null) return;
        Call.menu(player.con, menuId, title, content, buttons);
    }

    @Override
    public void followUpMenu(Player player, int menuId, String title, String content, String[][] buttons) {
        if (player == null || player.con == null) return;
        Call.followUpMenu(player.con, menuId, title, content, buttons);
    }

    @Override
    public void hideFollowUpMenu(Player player, int menuId) {
        if (player == null || player.con == null) return;
        Call.hideFollowUpMenu(player.con, menuId);
    }

    @Override
    public void textInput(Player player, int textInputId, String title, String content, int length, String def, boolean numeric) {
        if (player == null || player.con == null) return;
        Call.textInput(player.con, textInputId, title, content, length, def, numeric);
    }

    @Override
    public void openUri(Player player, String uri) {
        if (player == null || player.con == null) return;
        Call.openURI(player.con, uri);
    }

    @Override
    public void copyToClipboard(Player player, String text) {
        if (player == null || player.con == null) return;
        Call.copyToClipboard(player.con, text);
    }
}
