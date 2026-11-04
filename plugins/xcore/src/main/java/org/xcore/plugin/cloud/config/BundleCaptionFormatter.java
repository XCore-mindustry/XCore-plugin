package org.xcore.plugin.cloud.config;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Markup;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionFormatter;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders Cloud captions from the FluBundle, in the recipient's language and with the caption
 * variables as real message arguments, so plural selectors and {@code NUMBER()} work on them.
 *
 * <ul>
 *     <li>A caption whose bundle key ({@link CloudCaptionConfigurer#bundleKey}) exists is the
 *     bundle message.</li>
 *     <li>Otherwise it is the caption's own text (the English default of Cloud or
 *     cloud-mindustry) with Cloud's {@code <variable>} placeholders.</li>
 *     <li>Variables carry what the sender typed ({@code input}, {@code flag}, ...), so text values are
 *     escaped and cannot recolor the message. Counts and bounds ({@link #NUMERIC_VARIABLES}) are
 *     passed as numbers.</li>
 * </ul>
 */
public final class BundleCaptionFormatter implements CaptionFormatter<XCoreSender, String> {

    /** Caption variables Cloud and cloud-mindustry fill with numbers. */
    static final Set<String> NUMERIC_VARIABLES = Set.of("count", "limit", "min", "max", "page", "pages", "next");

    private final Bundle bundle;
    private final CaptionFormatter<XCoreSender, String> fallback = CaptionFormatter.placeholderReplacing();

    public BundleCaptionFormatter(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public String formatCaption(Caption captionKey, XCoreSender recipient, String caption,
                                List<CaptionVariable> variables) {
        String key = CloudCaptionConfigurer.bundleKey(captionKey);
        // The recipient's locale chain, as format() uses it: a key only another locale defines would
        // otherwise render as the missing-key text.
        if (bundle.has(recipient.locale(), key)) {
            return bundle.format(recipient.locale(), key, arguments(variables));
        }

        List<CaptionVariable> escaped = new ArrayList<>(variables.size());
        for (CaptionVariable variable : variables) {
            escaped.add(CaptionVariable.of(variable.key(), Markup.escape(variable.value())));
        }
        return fallback.formatCaption(captionKey, recipient, caption, escaped);
    }

    static Map<String, Object> arguments(List<CaptionVariable> variables) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (CaptionVariable variable : variables) {
            arguments.put(variable.key(), value(variable));
        }
        return arguments;
    }

    private static Object value(CaptionVariable variable) {
        String value = variable.value();
        if (value != null && NUMERIC_VARIABLES.contains(variable.key())) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException notWhole) {
                try {
                    return Double.parseDouble(value.trim());
                } catch (NumberFormatException notNumber) {
                    // fall through: rendered as text
                }
            }
        }
        return Markup.escape(value);
    }
}
