package org.xcore.plugin.ui.menu.event;

import mindustry.gen.Iconc;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.database.repository.EventDataRepository.Listing;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.EventEditorService.Draft;
import org.xcore.plugin.service.EventEditorService.Editor;
import org.xcore.plugin.service.EventEditorService.MapChoice;
import org.xcore.plugin.ui.kit.Accent;

import java.util.List;
import java.util.Locale;

/**
 * Everything the events menu shows: the screen that is open and what each screen holds.
 *
 * @param details  the event on screen; {@code null} unless one is open
 * @param editing  the event being written; kept while its map or a time is picked
 * @param feedback what the last action came to, shown as a band; empty for nothing
 * @param success  whether {@code feedback} is good news
 */
public record EventUiModel(
        View view,
        Viewer viewer,
        Catalog catalog,
        VoteState vote,
        @Nullable Details details,
        @Nullable Editing editing,
        String feedback,
        boolean success
) {

    public EventUiModel {
        feedback = feedback == null ? "" : feedback;
    }

    public enum View {
        LIST, DETAILS, EDITOR, MAPS, TIME
    }

    /**
     * What the player may do. The buttons are drawn by it; a press is checked again against
     * the session, since what a menu drew is not proof of anything.
     *
     * @param id the player's own id, {@code null} for one not stored yet
     */
    public record Viewer(@Nullable ObjectId id, boolean stopEvents, boolean cancelVotes, boolean forceVotes,
                         boolean createMajor, boolean editOthers) {

        public static final Viewer GUEST = new Viewer(null, false, false, false, false, false);

        public Editor editor() {
            return new Editor(id, createMajor, editOthers);
        }
    }

    /** A tab of the list. */
    public enum Tab {
        UPCOMING(Listing.UPCOMING, Iconc.play, Accent.BLUE),
        MAJOR(Listing.MAJOR, Iconc.star, Accent.GOLD),
        PAST(Listing.PAST, Iconc.ok, Accent.GRAY),
        MINE(Listing.MINE, Iconc.pencil, Accent.PURPLE);

        private final Listing listing;
        private final char glyph;
        private final Accent accent;

        Tab(Listing listing, char glyph, Accent accent) {
            this.listing = listing;
            this.glyph = glyph;
            this.accent = accent;
        }

        public Listing listing() {
            return listing;
        }

        public char glyph() {
            return glyph;
        }

        public Accent accent() {
            return accent;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String key() {
            return "event-ui-tab-" + id();
        }

        public static Tab of(String id) {
            for (Tab tab : values()) {
                if (tab.id().equals(id)) return tab;
            }
            return null;
        }
    }

    /**
     * A page of the list.
     *
     * @param total  how many events the tab holds; negative until the first page is read
     * @param active the event that is running, {@code null} for none
     */
    public record Catalog(Tab tab, int page, int totalPages, int total, List<EventData> events,
                          @Nullable EventData active) {

        public Catalog {
            events = List.copyOf(events);
            page = Math.max(1, page);
            totalPages = Math.max(1, totalPages);
        }

        /** A tab nothing was read for yet. */
        public static Catalog unread(Tab tab) {
            return new Catalog(tab, 1, 1, -1, List.of(), null);
        }

        public boolean loaded() {
            return total >= 0;
        }
    }

    /**
     * The votes of the server as they were when the menu last looked.
     *
     * @param running whether a vote of any kind is on: only one runs at a time
     * @param eventId the event voted for, {@code null} when the vote is for something else
     */
    public record VoteState(boolean running, @Nullable String eventId, String eventName, int votes, int required) {

        public static final VoteState NONE = new VoteState(false, null, "", 0, 0);

        public boolean forEvent() {
            return running && eventId != null;
        }

        public boolean isFor(EventData event) {
            return forEvent() && event != null && event.id != null && eventId.equals(event.id.toHexString());
        }
    }

    /** What an admin was asked to press once more. */
    public enum Confirm {
        NONE, FORCE, STOP
    }

    /**
     * An event on screen.
     *
     * @param map            the stored map, {@code null} when the event has none or it is gone
     * @param author         the author's name, empty when unknown
     * @param ownVote        the viewer's vote: liked, disliked, or {@code null} for none
     * @param preview        the texture the client draws the map from; {@code null} until it is there
     * @param previewLoading whether a preview may still arrive
     */
    public record Details(EventData event, @Nullable MapData map, String author, @Nullable Boolean ownVote,
                          @Nullable String preview, boolean previewLoading, Confirm confirm) {

        public Details {
            author = author == null ? "" : author;
        }

        public String id() {
            return event.id == null ? "" : event.id.toHexString();
        }

        public Details withOwnVote(Boolean vote) {
            return new Details(event, map, author, vote, preview, previewLoading, confirm);
        }

        public Details withPreview(@Nullable String region, boolean loading) {
            return new Details(event, map, author, ownVote, region, loading, confirm);
        }

        public Details withConfirm(Confirm confirm) {
            return new Details(event, map, author, ownVote, preview, previewLoading, confirm);
        }
    }

    /** The time of an event the time screen sets. */
    public enum TimeField {
        START, END;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * An event being written.
     *
     * @param maps      the maps to pick from, read when the editor opened
     * @param mapsQuery what the list of maps is narrowed to
     * @param field     the time being set on the time screen
     * @param picked    the time that screen shows; it becomes the draft's only when confirmed
     */
    public record Editing(Draft draft, List<MapChoice> maps, String mapsQuery, int mapsPage,
                          TimeField field, long picked) {

        public Editing {
            maps = List.copyOf(maps);
            mapsQuery = mapsQuery == null ? "" : mapsQuery;
            mapsPage = Math.max(1, mapsPage);
        }

        public static Editing of(Draft draft, List<MapChoice> maps) {
            return new Editing(draft, maps, "", 1, TimeField.START, 0L);
        }

        public Editing withDraft(Draft draft) {
            return new Editing(draft, maps, mapsQuery, mapsPage, field, picked);
        }

        public Editing withMaps(String query, int page) {
            return new Editing(draft, maps, query, page, field, picked);
        }

        public Editing withPicked(TimeField field, long picked) {
            return new Editing(draft, maps, mapsQuery, mapsPage, field, picked);
        }

        /** The maps whose name or author has {@link #mapsQuery()} in it. */
        public List<MapChoice> found() {
            String query = mapsQuery.strip().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) return maps;
            return maps.stream()
                    .filter(map -> map.name().toLowerCase(Locale.ROOT).contains(query)
                            || map.author().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }
    }

    /** The menu as it opens: an empty list that the first page then fills. */
    public static EventUiModel opening(Viewer viewer, Tab tab) {
        return new EventUiModel(View.LIST, viewer, Catalog.unread(tab), VoteState.NONE, null, null, "", false);
    }

    public EventUiModel withView(View view) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, "", false);
    }

    public EventUiModel withCatalog(Catalog catalog) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, feedback, success);
    }

    public EventUiModel withVote(VoteState vote) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, feedback, success);
    }

    public EventUiModel withDetails(@Nullable Details details) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, feedback, success);
    }

    public EventUiModel withEditing(@Nullable Editing editing) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, feedback, success);
    }

    public EventUiModel withFeedback(String feedback, boolean success) {
        return new EventUiModel(view, viewer, catalog, vote, details, editing, feedback, success);
    }

    public EventUiModel withoutFeedback() {
        return feedback.isEmpty() ? this : withFeedback("", false);
    }
}
