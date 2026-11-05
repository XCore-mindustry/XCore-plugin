package org.xcore.testkit.ui;

/**
 * The screen a {@link HeadlessMenuClient} builds dialogs for, in the client's UI units
 * ({@code Core.scene.getWidth() / Scl.scl(1f)}). It decides the {@code condition} of each node the
 * way {@code UiTreeBuilder.evalCondition} does: a node whose condition fails is not built at all.
 *
 * @param width  the scene width in UI units
 * @param height the scene height in UI units
 */
public record ClientScreen(float width, float height) {

    /** A 1080p desktop at the default UI scale. */
    public static final ClientScreen DESKTOP = new ClientScreen(1920f, 1080f);
    /** A phone held upright. */
    public static final ClientScreen PHONE_PORTRAIT = new ClientScreen(400f, 860f);
    /** A phone held sideways. */
    public static final ClientScreen PHONE_LANDSCAPE = new ClientScreen(860f, 400f);

    public ClientScreen {
        if (!(width > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("a screen needs a positive size: " + width + "x" + height);
        }
    }

    public boolean portrait() {
        return height > width;
    }

    /**
     * Whether the client builds a node with {@code condition}. Mirrors
     * {@code UiTreeBuilder.evalCondition}: {@code portrait}, {@code landscape}, and
     * {@code width|height <op> <number>}; anything it cannot read counts as true.
     */
    public boolean matches(String condition) {
        String cond = condition.trim();
        if (cond.equals("portrait")) return portrait();
        if (cond.equals("landscape")) return !portrait();
        String[] parts = cond.split("\\s+");
        if (parts.length != 3) return true;
        float dim = switch (parts[0]) {
            case "width" -> width;
            case "height" -> height;
            default -> 0f;
        };
        float num;
        try {
            num = Float.parseFloat(parts[2]);
        } catch (NumberFormatException e) {
            return true;
        }
        return switch (parts[1]) {
            case ">=" -> dim >= num;
            case ">" -> dim > num;
            case "<=" -> dim <= num;
            case "<" -> dim < num;
            default -> true;
        };
    }
}
