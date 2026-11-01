package org.xcore.plugin.ui.menu.help;

import java.util.List;

public record HelpCommandItem(
        String name,
        HelpCategory category,
        String primarySyntax,
        List<String> syntaxes,
        List<String> aliases,
        String rawDescription,
        List<ArgumentInfo> arguments,
        boolean isAdminOnly,
        boolean hasNoRequiredArgs
) {
    public record ArgumentInfo(
            String name,
            boolean required,
            String description
    ) {}
}
