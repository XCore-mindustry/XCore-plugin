package org.xcore.ui;

/**
 * Static image bound to an atlas region or a server-streamed texture
 * ({@code net-<hash>} names produced by {@link TextureRegistry}).
 */
public record VImage(String id, UiLayout layout, String region, String placeholder, String scaling) implements VNode {

    public VImage {
        if (id != null) VNode.requireSafeId(id, "image");
        if (region == null || region.isEmpty()) {
            throw new IllegalArgumentException("image region must not be null or empty");
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
