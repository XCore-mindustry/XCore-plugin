package org.xcore.plugin.ui.menu.help;

import java.util.List;
import java.util.Optional;

public record HelpUiModel(
        ViewMode mode,
        HelpCategory selectedCategory,
        List<HelpCommandItem> allCommands,
        String selectedCommandName,
        boolean isMobile
) {
    public enum ViewMode {
        LIST,
        DETAILS
    }

    public List<HelpCommandItem> filteredCommands() {
        return allCommands.stream()
                .filter(cmd -> selectedCategory == HelpCategory.ALL || cmd.category() == selectedCategory)
                .toList();
    }

    public int countForCategory(HelpCategory category) {
        if (category == HelpCategory.ALL) return allCommands.size();
        return (int) allCommands.stream().filter(c -> c.category() == category).count();
    }

    public Optional<HelpCommandItem> selectedCommand() {
        if (selectedCommandName == null) return Optional.empty();
        return allCommands.stream()
                .filter(c -> c.name().equalsIgnoreCase(selectedCommandName))
                .findFirst();
    }

    public HelpUiModel withCategory(HelpCategory cat) {
        return new HelpUiModel(ViewMode.LIST, cat, allCommands, null, isMobile);
    }

    public HelpUiModel withDetails(String commandName) {
        return new HelpUiModel(ViewMode.DETAILS, selectedCategory, allCommands, commandName, isMobile);
    }

    public HelpUiModel withList() {
        return new HelpUiModel(ViewMode.LIST, selectedCategory, allCommands, null, isMobile);
    }
}
