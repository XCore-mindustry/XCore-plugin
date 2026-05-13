package org.xcore.plugin.ui.flow;

public record MenuPromptContext<T>(MenuRenderContext<T> renderContext, String text) {}
