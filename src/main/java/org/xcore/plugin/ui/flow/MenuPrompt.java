package org.xcore.plugin.ui.flow;

public record MenuPrompt(String promptId, String title, String content, int length, String defaultValue, boolean numeric) {
}
