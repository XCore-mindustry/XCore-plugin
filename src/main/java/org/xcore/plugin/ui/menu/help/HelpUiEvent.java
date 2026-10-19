package org.xcore.plugin.ui.menu.help;

public sealed interface HelpUiEvent {
    record SelectCategory(HelpCategory category) implements HelpUiEvent {}
    record SelectCommand(String commandName) implements HelpUiEvent {}
    /** A page of the list, counted from 0. */
    record OpenPage(int page) implements HelpUiEvent {}
    record Search(String query) implements HelpUiEvent {}
    record BackToList() implements HelpUiEvent {}
    record ExecuteCommand(String syntax) implements HelpUiEvent {}
    record CopyCommand(String syntax) implements HelpUiEvent {}
    record Close() implements HelpUiEvent {}
}
