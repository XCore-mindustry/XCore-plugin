package com.ospx.flubundle.mindustry;

import arc.util.Align;

/**
 * Placement of an info popup ({@code Call.infoPopup}).
 *
 * <pre>{@code
 * messenger.to(player).popup("wave-countdown", args, Popup.at(Align.top).duration(5f).margin(80, 0, 0, 0));
 * }</pre>
 *
 * @param duration seconds the popup stays on screen
 * @param align    an {@link Align} constant
 */
public record Popup(float duration, int align, int top, int left, int bottom, int right) {

    public static final float DEFAULT_DURATION = 3f;

    public static Popup at(int align) {
        return new Popup(DEFAULT_DURATION, align, 0, 0, 0, 0);
    }

    public static Popup center() {
        return at(Align.center);
    }

    public Popup duration(float seconds) {
        return new Popup(seconds, align, top, left, bottom, right);
    }

    public Popup margin(int top, int left, int bottom, int right) {
        return new Popup(duration, align, top, left, bottom, right);
    }
}
