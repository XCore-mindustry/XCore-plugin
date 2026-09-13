package org.xcore.ui;

/**
 * Immutable layout description mapped onto {@code Cell} properties of the parent table.
 * {@code UiLayout.DEFAULT} means "no cell constraints" (Mindustry default behavior).
 *
 * <p>Instances are created fluently via {@code Ui.layout(...)} or derived from existing
 * ones with {@link #mutate()}, which returns a builder pre-filled with current values.
 */
public record UiLayout(
        Float width, Float height, Float minWidth, Float maxWidth, Float minHeight, Float maxHeight,
        Float size,
        Float pad, Float padTop, Float padLeft, Float padBottom, Float padRight,
        Boolean grow, Boolean growX, Boolean growY,
        Boolean fill, Boolean fillX, Boolean fillY,
        Boolean expand, Boolean expandX, Boolean expandY,
        Boolean uniform, Boolean uniformX, Boolean uniformY,
        Integer colspan,
        String align, String color,
        Boolean disabled
) {

    public static final UiLayout DEFAULT = new UiLayout(
            null, null, null, null, null, null,
            null,
            null, null, null, null, null,
            null, null, null,
            null, null, null,
            null, null, null,
            null, null, null,
            null, null, null, null);

    public UiLayout {
        validate("width", width, 0f, false);
        validate("height", height, 0f, false);
        validate("minWidth", minWidth, 0f, false);
        validate("maxWidth", maxWidth, 0f, false);
        validate("minHeight", minHeight, 0f, false);
        validate("maxHeight", maxHeight, 0f, false);
        validate("size", size, 0f, false);
        validate("pad", pad, 0f, true);
        validate("padTop", padTop, 0f, true);
        validate("padLeft", padLeft, 0f, true);
        validate("padBottom", padBottom, 0f, true);
        validate("padRight", padRight, 0f, true);
        if (colspan != null && colspan < 1) {
            throw new IllegalArgumentException("colspan must be >= 1: " + colspan);
        }
    }

    private static void validate(String name, Float value, float min, boolean allowZero) {
        if (value == null) return;
        if (value.isNaN() || value.isInfinite() || (value < min) || (!allowZero && value <= 0f)) {
            throw new IllegalArgumentException("UiLayout." + name + " is invalid: " + value);
        }
    }

    public boolean isDefault() {
        return this.equals(DEFAULT);
    }

    /** Returns a mutable builder initialized with this layout's values. */
    public Builder mutate() {
        return new Builder(this);
    }

    /** Fluent builder for {@link UiLayout}; obtain via {@code Ui.layout()} or {@link #mutate()}. */
    public static final class Builder {
        private Float width, height, minWidth, maxWidth, minHeight, maxHeight;
        private Float size;
        private Float pad, padTop, padLeft, padBottom, padRight;
        private Boolean grow, growX, growY;
        private Boolean fill, fillX, fillY;
        private Boolean expand, expandX, expandY;
        private Boolean uniform, uniformX, uniformY;
        private Integer colspan;
        private String align, color;
        private Boolean disabled;

        Builder() {
        }

        Builder(UiLayout from) {
            this.width = from.width; this.height = from.height;
            this.minWidth = from.minWidth; this.maxWidth = from.maxWidth;
            this.minHeight = from.minHeight; this.maxHeight = from.maxHeight;
            this.size = from.size;
            this.pad = from.pad; this.padTop = from.padTop; this.padLeft = from.padLeft;
            this.padBottom = from.padBottom; this.padRight = from.padRight;
            this.grow = from.grow; this.growX = from.growX; this.growY = from.growY;
            this.fill = from.fill; this.fillX = from.fillX; this.fillY = from.fillY;
            this.expand = from.expand; this.expandX = from.expandX; this.expandY = from.expandY;
            this.uniform = from.uniform; this.uniformX = from.uniformX; this.uniformY = from.uniformY;
            this.colspan = from.colspan; this.align = from.align; this.color = from.color;
            this.disabled = from.disabled;
        }

        public Builder width(float w) { this.width = w; return this; }
        public Builder height(float h) { this.height = h; return this; }
        public Builder minWidth(float w) { this.minWidth = w; return this; }
        public Builder maxWidth(float w) { this.maxWidth = w; return this; }
        public Builder minHeight(float h) { this.minHeight = h; return this; }
        public Builder maxHeight(float h) { this.maxHeight = h; return this; }
        public Builder size(float s) { this.size = s; return this; }
        public Builder pad(float p) { this.pad = p; return this; }
        public Builder padTop(float p) { this.padTop = p; return this; }
        public Builder padLeft(float p) { this.padLeft = p; return this; }
        public Builder padBottom(float p) { this.padBottom = p; return this; }
        public Builder padRight(float p) { this.padRight = p; return this; }
        public Builder grow() { this.grow = true; return this; }
        public Builder growX() { this.growX = true; return this; }
        public Builder growY() { this.growY = true; return this; }
        public Builder fill() { this.fill = true; return this; }
        public Builder fillX() { this.fillX = true; return this; }
        public Builder fillY() { this.fillY = true; return this; }
        public Builder expand() { this.expand = true; return this; }
        public Builder expandX() { this.expandX = true; return this; }
        public Builder expandY() { this.expandY = true; return this; }
        public Builder uniform() { this.uniform = true; return this; }
        public Builder uniformX() { this.uniformX = true; return this; }
        public Builder uniformY() { this.uniformY = true; return this; }
        public Builder colspan(int c) { this.colspan = c; return this; }
        public Builder align(String align) { this.align = align; return this; }
        public Builder color(String hex) { this.color = hex; return this; }
        public Builder disabled() { this.disabled = true; return this; }
        public Builder disabled(boolean d) { this.disabled = d; return this; }

        public UiLayout build() {
            return new UiLayout(width, height, minWidth, maxWidth, minHeight, maxHeight, size,
                    pad, padTop, padLeft, padBottom, padRight,
                    grow, growX, growY, fill, fillX, fillY,
                    expand, expandX, expandY, uniform, uniformX, uniformY,
                    colspan, align, color, disabled);
        }

        public UiLayout buildAndReturn() {
            return build();
        }
    }
}
