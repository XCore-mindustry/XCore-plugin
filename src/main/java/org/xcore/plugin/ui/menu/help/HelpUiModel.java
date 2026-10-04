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
 *
 * @param page the page of the selected category's commands on screen, counted from 0
 */
public record HelpUiModel(
        ViewMode mode,
        HelpCategory selectedCategory,
        List<HelpCommandItem> allCommands,
        String selectedCommandName,
        int page
) {
    /**
     * Commands on a page. The dialog travels as one packet with a copy of the list per class of
     * screens, so a list of every command at once would not fit it.
     */
    public static final int PAGE_SIZE = 12;

    public enum ViewMode {
        LIST,
        DETAILS
    }

    public HelpUiModel {
        allCommands = allCommands == null ? List.of() : List.copyOf(allCommands);
        int count = (int) allCommands.stream()
                .filter(cmd -> selectedCategory == HelpCategory.ALL || cmd.category() == selectedCategory)
                .count();
        page = Math.clamp(page, 0, Math.max(0, (count - 1) / PAGE_SIZE));
    }

    public HelpUiModel(ViewMode mode, HelpCategory selectedCategory, List<HelpCommandItem> allCommands,
                       String selectedCommandName) {
        this(mode, selectedCategory, allCommands, selectedCommandName, 0);
    }

    public List<HelpCommandItem> filteredCommands() {
        return allCommands.stream()
                .filter(cmd -> selectedCategory == HelpCategory.ALL || cmd.category() == selectedCategory)
                .toList();
    }

    public int pages() {
        return Math.max(1, (filteredCommands().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The commands of the page on screen. */
    public List<HelpCommandItem> pageCommands() {
        List<HelpCommandItem> commands = filteredCommands();
        int from = Math.min(commands.size(), page * PAGE_SIZE);
        return commands.subList(from, Math.min(commands.size(), from + PAGE_SIZE));
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
        return new HelpUiModel(ViewMode.LIST, cat, allCommands, null, 0);
    }

    public HelpUiModel withPage(int page) {
        return new HelpUiModel(ViewMode.LIST, selectedCategory, allCommands, null, page);
    }

    /** The page stays, so going back from a command returns to where it was picked. */
    public HelpUiModel withDetails(String commandName) {
        return new HelpUiModel(ViewMode.DETAILS, selectedCategory, allCommands, commandName, page);
    }

    public HelpUiModel withList() {
        return new HelpUiModel(ViewMode.LIST, selectedCategory, allCommands, null, page);
    }
}
