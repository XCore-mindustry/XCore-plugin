package org.xcore.plugin.ui.kit;

/**
 * The colour of a topic: tabs, title bands and lines of one topic share it, so they read as one
 * group.
 *
 * @param color the colour of text and thin lines
 * @param band  a dark shade of it, for the surfaces that text sits on
 */
public record Accent(String color, String band) {

    public static final Accent GOLD = new Accent("ffd37f", "4d4026");
    public static final Accent BLUE = new Accent("7fc8ff", "243c4f");
    public static final Accent GREEN = new Accent("98d982", "2c4227");
    public static final Accent PURPLE = new Accent("d79bff", "402c4f");
    public static final Accent RED = new Accent("ff8f8f", "4f2a2a");
    public static final Accent ORANGE = new Accent("ffb074", "4f3524");
    public static final Accent TEAL = new Accent("7fe0d0", "24443f");
    public static final Accent GRAY = new Accent("c9ced9", "353a48");

    private static final Accent[] CYCLE = {GOLD, BLUE, GREEN, PURPLE, ORANGE, TEAL, RED};

    /** An accent for the {@code index}-th of several topics that have no colour of their own. */
    public static Accent at(int index) {
        return CYCLE[Math.floorMod(index, CYCLE.length)];
    }

    /** An accent of the colour {@code hex} ({@code rrggbb}); its band is that colour, darkened. */
    public static Accent of(String hex) {
        try {
            int rgb = Integer.parseInt(hex, 16);
            int r = shade(rgb >> 16 & 0xff), g = shade(rgb >> 8 & 0xff), b = shade(rgb & 0xff);
            return new Accent(hex, String.format("%02x%02x%02x", r, g, b));
        } catch (RuntimeException e) {
            return GRAY;
        }
    }

    /** A third of the channel over the dark of a card, so any colour gives a readable band. */
    private static int shade(int channel) {
        return Math.round(channel * 0.24f + 18f);
    }
}
