package org.xcore.ui.responsive;

import java.util.Objects;

/**
 * Upper bounds for a server-driven dialog, resolved by the client.
 *
 * <p>The truncation helpers hold for any layout. The caps are what {@link DialogScaffold} lays out
 * with, and share its limits: see there, and {@link Variants} for a layout that fits a phone.
 *
 * <p>These are caps, not sizes. Mindustry ships literal floats for {@code width}/{@code maxWidth},
 * and a cell only honours {@code maxWidth} when it also fills
 * ({@code growX() + maxWidth(n)} resolves to {@code min(available, n)}). Emitting a cap instead of
 * a resolved width is what lets one tree serve every device: a desktop takes the cap, a phone takes
 * whatever it actually has, and the server never has to guess a viewport.
 *
 * <p>Do not add an {@code isMobile}/{@code isPortrait} field here. The server cannot know either
 * one — {@code ConnectPacket} carries only {@code mobile}, and the camera dimensions in
 * {@code clientSnapshot} describe the world view, not the screen. Genuine structural differences
 * belong in {@link Responsive#portrait}/{@link Responsive#landscape}, which the client evaluates.
 */
public record DialogMetrics(
        float maxDialogWidth,
        float maxBodyHeight,
        int textBudget
) {
    /** Desktop cap: matches the width vanilla {@code BaseDialog} content is comfortable at. */
    public static final float DIALOG_WIDTH = 760f;

    /** Body cap on a screen with room to spare; the client squeezes this on short screens. */
    public static final float BODY_HEIGHT = 460f;

    /**
     * Characters of a short identifier (nickname, map or server name) before truncation.
     *
     * <p>Conservative on purpose: {@code UiKey} has no {@code ellipsis}, so labels wrap instead of
     * clipping and a long value pushes its row tall rather than overflowing sideways.
     */
    public static final int TEXT_BUDGET = 44;

    /**
     * Characters of free-form prose (ban reasons, audit explanations) before truncation.
     *
     * <p>Kept separate from {@link #TEXT_BUDGET} because the two are different classes of content:
     * an identifier that overflows is a cosmetic glitch, while a truncated reason loses the
     * administrative context that makes the audit log useful. Collapsing both into one budget
     * silently shrinks prose to identifier length.
     */
    public static final int REASON_BUDGET = 72;

    public DialogMetrics {
        if (maxDialogWidth <= 0f) throw new IllegalArgumentException("maxDialogWidth must be > 0");
        if (maxBodyHeight <= 0f) throw new IllegalArgumentException("maxBodyHeight must be > 0");
        if (textBudget <= 0) throw new IllegalArgumentException("textBudget must be > 0");
    }

    /** Caps sized for the framework defaults. */
    public static DialogMetrics standard() {
        return new DialogMetrics(DIALOG_WIDTH, BODY_HEIGHT, TEXT_BUDGET);
    }

    public static DialogMetrics of(float maxDialogWidth, float maxBodyHeight) {
        return new DialogMetrics(maxDialogWidth, maxBodyHeight, TEXT_BUDGET);
    }

    public DialogMetrics withDialogWidth(float width) {
        return new DialogMetrics(width, maxBodyHeight, textBudget);
    }

    public DialogMetrics withBodyHeight(float height) {
        return new DialogMetrics(maxDialogWidth, height, textBudget);
    }

    public DialogMetrics withTextBudget(int budget) {
        return new DialogMetrics(maxDialogWidth, maxBodyHeight, budget);
    }

    /** Caps a card at the dialog cap minus the scaffold's own horizontal inset. */
    public float maxCardWidth() {
        return maxDialogWidth - cardInset();
    }

    private static float cardInset() {
        return 40f;
    }

    /** Truncates untrusted text to {@link #textBudget()} without splitting a surrogate pair. */
    public String truncate(String text) {
        return truncate(text, textBudget);
    }

    /** Truncates untrusted prose to {@link #reasonBudget()} without splitting a surrogate pair. */
    public String truncateReason(String text) {
        return truncate(text, REASON_BUDGET);
    }

    /** Truncates to an explicit budget without splitting a surrogate pair. */
    public String truncate(String text, int budget) {
        if (text == null || text.isEmpty()) return "";
        String flat = text.replace('\n', ' ');
        if (flat.length() <= budget) return flat;

        int end = budget - 1;
        if (end > 0 && Character.isHighSurrogate(flat.charAt(end - 1))) end--;
        return flat.substring(0, end) + "…";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DialogMetrics other
                && Float.compare(other.maxDialogWidth, maxDialogWidth) == 0
                && Float.compare(other.maxBodyHeight, maxBodyHeight) == 0
                && other.textBudget == textBudget;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxDialogWidth, maxBodyHeight, textBudget);
    }
}