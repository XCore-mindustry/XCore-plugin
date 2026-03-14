package com.ospx.flubundle;

import mindustry.gen.Call;
import mindustry.gen.Player;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class BundleContext {

    private final Player player;
    private final Localizer localizer;

    BundleContext(Player player, Localizer localizer) {
        this.player = player;
        this.localizer = Objects.requireNonNull(localizer, "localizer");
    }

    public Player player() {
        return player;
    }

    public Localizer localizer() {
        return localizer;
    }

    public String format(String id) {
        return localizer.format(id);
    }

    public String format(String id, Map<String, Object> args) {
        return localizer.format(id, args);
    }

    public void send(String id) {
        send(id, Collections.emptyMap());
    }

    public void send(String id, Map<String, Object> args) {
        requirePlayer();
        player.sendMessage(localizer.format(id, args));
    }

    public void infoMessage(String id, Map<String, Object> args) {
        requirePlayer();
        Call.infoMessage(player.con, localizer.format(id, args));
    }

    public void announce(String id, Map<String, Object> args) {
        requirePlayer();
        Call.announce(player.con, localizer.format(id, args));
    }

    public void toast(int icon, String id, Map<String, Object> args) {
        requirePlayer();
        Call.warningToast(player.con, icon, localizer.format(id, args));
    }

    public void setHud(String id, Map<String, Object> args) {
        requirePlayer();
        Call.setHudText(player.con, localizer.format(id, args));
    }

    public void label(float duration, float x, float y, String id, Map<String, Object> args) {
        requirePlayer();
        Call.label(player.con, localizer.format(id, args), duration, x, y);
    }

    public void popup(float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args) {
        requirePlayer();
        Call.infoPopup(player.con, localizer.format(id, args), duration, align, top, left, bottom, right);
    }

    public void kick(String id, Map<String, Object> args) {
        requirePlayer();
        Call.kick(player.con, localizer.format(id, args));
    }

    private void requirePlayer() {
        if (player == null) {
            throw new IllegalStateException("BundleContext requires a player for transport operations");
        }
    }
}
