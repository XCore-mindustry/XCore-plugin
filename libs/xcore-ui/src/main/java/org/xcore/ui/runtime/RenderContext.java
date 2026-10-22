package org.xcore.ui.runtime;

import org.xcore.ui.LocalizerResolver;

import java.util.Map;

/**
 * What a view may know besides its model: the language of the player it is drawn for.
 *
 * <p>{@link org.xcore.ui.Text#t} resolves lazily, but a view that measures text to fit it in a
 * place needs the words themselves; these resolve them now, with the session's resolver.
 */
public interface RenderContext {

    LocalizerResolver resolver();

    default String t(String key) {
        return resolver().format(key);
    }

    default String t(String key, Map<String, Object> args) {
        return resolver().format(key, args);
    }

    static RenderContext of(LocalizerResolver resolver) {
        LocalizerResolver r = resolver != null ? resolver : LocalizerResolver.getDefault();
        return () -> r;
    }
}
