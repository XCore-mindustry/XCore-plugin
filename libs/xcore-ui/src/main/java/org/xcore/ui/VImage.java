package org.xcore.ui;

/**
 * Static image bound to an atlas region or a server-streamed texture
 * ({@code net-<hash>} names produced by {@link org.xcore.ui.texture.TextureRegistry}).
 */
public record VImage(String id, UiLayout layout, String region, String placeholder, String scaling) implements VNode {

    public VImage {
        if (id != null) VNode.requireSafeId(id, "image");
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("image region must not be null or empty");
        }
        if (scaling != null) {
            try {
                arc.util.Scaling.valueOf(scaling);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("image scaling is not one of "
                        + java.util.Arrays.toString(arc.util.Scaling.values()) + ": " + scaling, e);
            }
        }
    }

    @Override
    public Kind kind() {
        return Kind.IMAGE;
    }

    public static final String SCALING_FIT = "fit";
    public static final String SCALING_FILL = "fill";
    public static final String SCALING_STRETCH = "stretch";
}
