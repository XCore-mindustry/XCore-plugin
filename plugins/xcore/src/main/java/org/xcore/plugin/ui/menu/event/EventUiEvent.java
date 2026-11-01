package org.xcore.plugin.ui.menu.event;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.service.EventEditorService.SaveResult;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Details;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;

/**
 * What the player pressed in the events menu, and what storage answered.
 *
 * <p>A press names a position in the model, never an id: what a button sends is written into
 * the dialog the client receives.
 */
public sealed interface EventUiEvent {

    // --- The list ---
    record SelectTab(Tab tab) implements EventUiEvent {}
    record NextPage() implements EventUiEvent {}
    record PrevPage() implements EventUiEvent {}
    record Refresh() implements EventUiEvent {}
    /** A row of the list: a position on the page. */
    record OpenEvent(int index) implements EventUiEvent {}
    /** The event that is running. */
    record OpenActive() implements EventUiEvent {}
    /** The event being voted for. */
    record OpenVoted() implements EventUiEvent {}
    record Create() implements EventUiEvent {}

    // --- An event ---
    record BackToList() implements EventUiEvent {}
    record Rate(boolean like) implements EventUiEvent {}
    record StartVote() implements EventUiEvent {}
    /** Starts the event without a vote; asked twice. */
    record ForceVote() implements EventUiEvent {}
    /** Ends the running event; asked twice. */
    record StopEvent() implements EventUiEvent {}
    record StopVote() implements EventUiEvent {}
    /** The answer "no" to what {@link ForceVote} or {@link StopEvent} asked. */
    record CancelConfirm() implements EventUiEvent {}
    record OpenMap() implements EventUiEvent {}
    record Edit() implements EventUiEvent {}

    // --- The editor ---

    /** What the text fields held when a button of the editor was pressed. */
    record Fields(String name, String description) {
    }

    enum EditorAction {
        PICK_MAP, PICK_START, PICK_END, TOGGLE_TEMPORARY, TOGGLE_MAJOR, SAVE
    }

    /** @param fields {@code null} when the press came without the fields */
    record EditorPress(@Nullable Fields fields, EditorAction action) implements EventUiEvent {}
    record CancelEdit() implements EventUiEvent {}

    // --- The maps of the editor ---
    record SearchMaps(String query) implements EventUiEvent {}
    record MapsPage(int delta) implements EventUiEvent {}
    /** A map of the list: a position among those found. */
    record ChooseMap(int index) implements EventUiEvent {}
    record BackToEditor() implements EventUiEvent {}

    // --- A time of the editor ---
    record TimeStep(EventTime.Step step) implements EventUiEvent {}
    record TimeShortcut(EventTime.Shortcut shortcut) implements EventUiEvent {}
    record ClearTime() implements EventUiEvent {}
    record ApplyTime() implements EventUiEvent {}

    // --- What storage answered ---
    record ListLoaded(Catalog catalog) implements EventUiEvent {}
    /** @param notice what to tell the player along with the event; empty for nothing */
    record DetailsLoaded(Details details, String notice) implements EventUiEvent {}
    record LoadFailed() implements EventUiEvent {}
    /** What was on screen is done with; {@code catalog} is the list after that, {@code notice} what to say above it. */
    record Returned(Catalog catalog, String notice) implements EventUiEvent {}
    record Saved(SaveResult result) implements EventUiEvent {}
    record PreviewReady(String eventId, String region) implements EventUiEvent {}
    record PreviewFailed(String eventId) implements EventUiEvent {}

    // --- The window ---
    /** Back to the menu this one was opened from. */
    record Back() implements EventUiEvent {}
    record Close() implements EventUiEvent {}
}
