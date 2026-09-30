package org.xcore.plugin.ui.menu.help;

public sealed interface HelpUiEvent {
    record SelectCategory(HelpCategory category) implements HelpUiEvent {}
    record SelectCommand(String commandName) implements HelpUiEvent {}
    record BackToList() implements HelpUiEvent {}
    record ExecuteCommand(String syntax) implements HelpUiEvent {}
    record CopyCommand(String syntax) implements HelpUiEvent {}
    record Close() implements HelpUiEvent {}
}
