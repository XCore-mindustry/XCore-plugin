package org.xcore.plugin.ui.menu.event;

import mindustry.gen.Player;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.service.EventEditorService.SaveResult;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.menu.event.EventUiModel.Catalog;
import org.xcore.plugin.ui.menu.event.EventUiModel.Tab;
import org.xcore.plugin.ui.menu.event.EventUiModel.Viewer;
import org.xcore.plugin.ui.menu.event.EventUiModel.VoteState;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * What the events menu asks of the server. The calls marked blocking read or write storage and
 * are made off the game thread; the rest belong to the game thread.
 */
public interface EventUiBackend {

    /**
     * An event as storage has it.
     *
     * @param map    {@code null} when the event has no map or its record is gone
     * @param author the author's name, empty when unknown
     */
    record Stored(EventData event, @Nullable MapData map, String author) {
    }

    /** Blocking. A page of {@code tab} as {@code viewer} sees it, with the event that is running. */
    Catalog catalog(Tab tab, int page, Viewer viewer);

    /** Blocking. {@code null} when there is no such event any more. */
    @Nullable Stored event(ObjectId id);

    /** Blocking. */
    SaveResult save(Draft draft, Editor editor, String fallbackName);

    /** Blocking. Ends the event that is running. */
    void finishActiveEvent();

    VoteState votes();

    /** The event being voted for, {@code null} when no such vote is on. */
    @Nullable EventData votedEvent();

    /** Starts the vote for {@code event}; {@code forced} starts the event itself. */
    void startVote(Player player, EventData event, boolean forced);

    /** Ends the vote for an event, if one is on. */
    void stopVote();

    /** Counts the player's vote on {@code event}; says whether it changed anything. */
    boolean rate(Player player, boolean like, EventData event);

    /** The maps an event can be given, as the server has them now. */
    List<MapChoice> maps();

    int mapsPerPage();

    /** Opens the menu of {@code map} in place of this one. */
    void openMap(Session session, MapData map);

    /** The texture of the map's picture if the client already has it, {@code null} otherwise. */
    @Nullable String cachedPreview(MapData map);

    /** Sends the map's picture to the player; {@code done} gets its texture, or an error. */
    void requestPreview(Player player, MapData map, BiConsumer<String, Throwable> done);
}
