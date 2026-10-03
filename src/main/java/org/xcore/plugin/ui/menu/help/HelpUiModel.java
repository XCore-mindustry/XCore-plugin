package org.xcore.plugin.ui.menu.help;

import java.util.List;
import java.util.Optional;

/**
 * View state for the command browser.
 *
 * <p>No device or orientation flags live here. The server cannot know either — {@code ConnectPacket}
 * carries only {@code mobile}, and the camera dimensions in {@code clientSnapshot} describe the world
 * view rather than the screen — so anything the dialog needs to adapt is expressed in the tree and
 * resolved by the client.
 */
public record HelpUiModel(
        ViewMode mode,
        HelpCategory selectedCategory,
        List<HelpCommandItem> allCommands,
        String selectedCommandName
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
        return new HelpUiModel(ViewMode.LIST, cat, allCommands, null);
    }

    public HelpUiModel withDetails(String commandName) {
        return new HelpUiModel(ViewMode.DETAILS, selectedCategory, allCommands, commandName);
    }

    public HelpUiModel withList() {
        return new HelpUiModel(ViewMode.LIST, selectedCategory, allCommands, null);
    }
}