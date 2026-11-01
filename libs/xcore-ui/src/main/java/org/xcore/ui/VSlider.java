package org.xcore.ui;

/**
 * Interactive slider with an optional formatted value label.
 * The v160 client renders a {@code Stack(slider, label)} automatically.
 */
public record VSlider(
        String id, UiLayout layout,
        float min, float max, float step, Float defaultValue,
        String style, boolean disabled
) implements VNode {

    public VSlider {
        if (id != null) VNode.requireSafeId(id, "slider");
        if (step <= 0f) {
            throw new IllegalArgumentException("slider step must be > 0: " + step);
        }
        if (min >= max) {
            throw new IllegalArgumentException("slider min must be < max: " + min + " >= " + max);
        }
        if (defaultValue != null && (defaultValue < min || defaultValue > max)) {
            throw new IllegalArgumentException("slider defaultValue out of range: " + defaultValue);
        }
    }

    @Override
    public Kind kind() {
        return Kind.SLIDER;
    }
}
