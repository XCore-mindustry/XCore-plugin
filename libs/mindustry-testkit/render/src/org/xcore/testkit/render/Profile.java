package org.xcore.testkit.render;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A screen the client is started with. The framebuffer and the UI scale are fixed before the
 * fonts load, so a client renders one profile; the UI viewport is the framebuffer divided by the
 * scale. Profiles model viewport geometry, not a particular device.
 */
public enum Profile {
    /** A small phone held upright: 480×800 units. */
    PHONE(480, 800, 1f, true),
    /** A 1080p phone at the default Android scale: 540×1200 units. */
    ANDROID1080(1080, 2400, 2f, true),
    /** A desktop: 1920×1080 units. */
    PC(1920, 1080, 1f, false),
    /** A small phone held sideways: 800×480 units. */
    LANDSCAPE(800, 480, 1f, true);

    public final int width;
    public final int height;
    public final float scale;
    public final boolean mobile;

    Profile(int width, int height, float scale, boolean mobile) {
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.mobile = mobile;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Profile of(String id) {
        for (Profile profile : values()) {
            if (profile.id().equals(id)) return profile;
        }
        throw new IllegalArgumentException("Unknown profile " + id + "; one of " + ids());
    }

    public static String ids() {
        return Arrays.stream(values()).map(Profile::id).collect(Collectors.joining(", "));
    }
}
