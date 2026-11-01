package org.xcore.plugin.config;

/**
 * Implemented by configuration POJOs whose fields need repair after binding
 * (null guards, range fixes, derived defaults). {@link PluginConfigLoader}
 * invokes {@link #normalize()} automatically after loading.
 */
public interface SelfNormalizing {

    void normalize();
}
