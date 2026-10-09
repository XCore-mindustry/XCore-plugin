package org.xcore.plugin.ui.menu;

import arc.util.Log;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.match.MatchPage;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchRecord;
import org.xcore.plugin.rating.view.LadderProgress;
import org.xcore.plugin.rating.view.MatchPresenter;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Texts.locale;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * The player's own rated matches ({@code /matches}): a tab per ladder, the matches of a page as
 * rows that open the match, and the match itself with every participant and why it counted.
 * Laid out once per {@link Screen}; turning a page patches the list and the pager.
 *
 * <p>The buttons carry positions in the model, never a player's UUID: what a button sends is
 * written into the dialog the client receives.</p>
 */
public class MatchHistoryUiController implements UiController<MatchHistoryUiController.Model, MatchHistoryUiController.Event> {

    public static final SlotKey<Object> SLOT_LIST = SlotKey.of("slot_matches_list");
    public static final SlotKey<Object> SLOT_PAGER = SlotKey.of("slot_matches_pager");

    public static final int MATCHES_PER_PAGE = 10;
    public static final int PARTICIPANTS_PER_PAGE = 12;
    static final String FIRST_PAGE = "__first__";

    private static final float STRIPE = 4f;
    /** The delta and the rating after it, right of a row. */
    private static final float VALUE_WIDTH = 60f;
    /** Columns of a row on a wide screen, where a match takes one line. */
    private static final float OUTCOME_WIDTH = 210f;
    private static final float RATINGS_WIDTH = 130f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter DATE_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final String WIN = "98d982";
    private static final String LOSS = "ff8f8f";
    private static final String NEUTRAL = "3b4252";

    /** A ladder as a tab. */
    public record LadderTab(String id, String name, char icon) {
    }

    /**
     * One page of matches to load.
     *
     * @param cursor    {@code null} for the first page
     * @param backStack cursors of the pages before this one
     * @param full      whether what belongs to the ladder rather than to the page is read too
     */
    public record Query(String ladderId, int page, @Nullable String cursor, Deque<String> backStack, boolean full) {
        public Query {
            page = Math.max(1, page);
            backStack = backStack == null ? new ArrayDeque<>() : new ArrayDeque<>(backStack);
        }

        /** The first page of a ladder, with everything about it. */
        public static Query first(String ladderId) {
            return new Query(ladderId, 1, null, null, true);
        }
    }

    /**
     * What is read once per ladder rather than per page.
     *
     * @param progress     the owner's standing, {@code null} when it could not be read
     * @param historyStart when the ladder's history begins, {@code null} while it holds nothing
     * @param recent       the owner's latest matches, for the form line
     */
    public record Summary(long total, @Nullable LadderProgress progress, @Nullable Instant historyStart,
                          int currentSeason, List<MatchRecord> recent) {
        public Summary {
            recent = List.copyOf(recent);
        }

        static final Summary EMPTY = new Summary(0, null, null, Ladder.FIRST_SEASON, List.of());
    }

    /** What storage returned for a {@link Query}; {@code summary} is {@code null} when it was not asked for. */
    public record Data(Query query, MatchPage page, @Nullable Summary summary) {
    }

    /**
     * @param ownerUuid        whose matches these are: always the viewer's own
     * @param open             the match on screen, {@code null} while the list is
     * @param participantsPage page of the open match's participants
     */
    public record Model(
            String ownerUuid,
            String ownerName,
            List<LadderTab> ladders,
            String ladderId,
            Summary summary,
            List<MatchRecord> matches,
            int page,
            @Nullable String cursor,
            @Nullable String nextCursor,
            Deque<String> backStack,
            @Nullable MatchRecord open,
            int participantsPage,
            String feedback
    ) {
        public Model {
            ladders = List.copyOf(ladders);
            matches = List.copyOf(matches);
            backStack = new ArrayDeque<>(backStack);
            feedback = feedback == null ? "" : feedback;
        }

        public int totalPages() {
            return Math.max(1, (int) Math.ceil((double) summary.total() / MATCHES_PER_PAGE));
        }

        Model withFeedback(String message) {
            return new Model(ownerUuid, ownerName, ladders, ladderId, summary, matches, page, cursor, nextCursor,
                    backStack, open, participantsPage, message);
        }

        Model withOpen(@Nullable MatchRecord match, int participants) {
            return new Model(ownerUuid, ownerName, ladders, ladderId, summary, matches, page, cursor, nextCursor,
                    backStack, match, participants, "");
        }
    }

    public sealed interface Event {
        record SelectLadder(String ladderId) implements Event {}
        record NextPage() implements Event {}
        record PrevPage() implements Event {}
        record Refresh() implements Event {}
        /** A row of the list: a position on the page. */
        record OpenMatch(int index) implements Event {}
        record MatchLoaded(MatchRecord match) implements Event {}
        record ParticipantsPage(int page) implements Event {}
        record BackToList() implements Event {}
        /** A participant of the open match: a position in {@link #participants(MatchRecord)}. */
        record InspectPlayer(int index) implements Event {}
        /** A page requested by an earlier event has arrived from storage. */
        record Loaded(Data data) implements Event {}
        record LoadFailed() implements Event {}
        /** Back to the menu this one was opened from. */
        record Back() implements Event {}
        record Close() implements Event {}
    }

    private final MatchHistoryMenu menu;
    private final Async async;
    private final Session session;
    private final Clock clock;
    /** Counts the loads this dialog started; only the latest one may change it. */
    private long loadGeneration;

    public MatchHistoryUiController(MatchHistoryMenu menu, Async async, Session session, Clock clock) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.async = async;
        this.session = session;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    // =========================================================================
    // Loading
    // =========================================================================

    /** The query that opens the menu; an unknown or blank ladder means the first tab. */
    public Query query(@Nullable String ladderId) {
        List<LadderTab> tabs = menu.tabs(locale(session));
        String chosen = tabs.stream().map(LadderTab::id).filter(id -> id.equals(ladderId)).findFirst()
                .orElse(tabs.isEmpty() ? "" : tabs.getFirst().id());
        return Query.first(chosen);
    }

    /** Blocking. Reads {@code query} for the player {@code uuid}; touches nothing of the game thread. */
    public Data fetch(Query query, String uuid) {
        Ladder ladder = menu.ladder(query.ladderId());
        if (ladder == null) {
            return new Data(query, MatchPage.empty(), query.full() ? Summary.EMPTY : null);
        }
        MatchPage page = ladder.matches(uuid, MATCHES_PER_PAGE, query.cursor());
        Summary summary = null;
        if (query.full()) {
            LadderProgress progress = null;
            try {
                progress = menu.progress(ladder, uuid);
            } catch (RuntimeException e) {
                // The header goes without the standing; the matches are what was asked for.
                Log.err("Failed to read the standing of " + uuid + " on ladder " + ladder.id(), e);
            }
            summary = new Summary(ladder.matchCount(uuid), progress, ladder.historyStart().orElse(null),
                    ladder.currentSeason(), query.page() == 1 ? page.matches() : List.of());
        }
        return new Data(query, page, summary);
    }

    /** The model that opens the menu on {@code data}. */
    public Model model(Data data) {
        String owner = session != null && session.data != null ? session.data.uuid : "";
        String name = session != null && session.data != null ? displayName(session.data.customNickname,
                session.data.nickname) : "";
        Model blank = new Model(owner, name, menu.tabs(locale(session)), data.query().ladderId(), Summary.EMPTY,
                List.of(), 1, null, null, new ArrayDeque<>(), null, 1, "");
        return apply(blank, data);
    }

    private static String displayName(@Nullable String custom, @Nullable String nickname) {
        if (custom != null && !custom.isBlank()) return custom;
        return nickname != null ? nickname : "";
    }

    /** {@code current} showing what {@code data} holds. */
    private static Model apply(Model current, Data data) {
        Query query = data.query();
        Summary summary = data.summary() != null ? data.summary() : current.summary();
        return new Model(current.ownerUuid(), current.ownerName(), current.ladders(), query.ladderId(), summary,
                data.page().matches(), query.page(), query.cursor(), data.page().nextCursor(), query.backStack(),
                null, 1, "");
    }

    /**
     * Loads {@code query} off the game thread and feeds the result back as an event. The
     * dialog keeps showing {@code model} meanwhile; a result that arrives after the dialog moved
     * on, or after the player asked for something newer, is dropped.
     */
    private UpdateResult<Model> load(Model model, Query query) {
        String owner = model.ownerUuid();
        long generation = ++loadGeneration;
        async.supply(() -> fetch(query, owner)).thenMain((data, error) -> {
            if (error != null) {
                Log.err("Failed to load the matches of " + owner + " on ladder " + query.ladderId(), error);
            }
            deliver(model, generation, error == null ? new Event.Loaded(data) : new Event.LoadFailed());
        });
        return UpdateResult.of(model);
    }

    private UpdateResult<Model> loadMatch(Model model, MatchRecord row) {
        long generation = ++loadGeneration;
        async.supply(() -> {
            Ladder ladder = menu.ladder(row.ladder());
            return ladder == null ? null : ladder.match(row.matchId()).orElse(null);
        }).thenMain((match, error) -> {
            if (error != null) {
                Log.err("Failed to load match " + row.id(), error);
            }
            deliver(model, generation, match != null ? new Event.MatchLoaded(match) : new Event.LoadFailed());
        });
        return UpdateResult.of(model);
    }

    private void deliver(Model model, long generation, Event event) {
        if (generation != loadGeneration || session == null) {
            return; // Superseded: applying it would make the newer request look stale.
        }
        var active = session.activeUiSession();
        if (active != null && active.model() == model) {
            @SuppressWarnings("unchecked")
            var ui = (UiSession<Model, Event>) active;
            ui.dispatch(event);
        }
    }

    // =========================================================================
    // Events
    // =========================================================================

    @Override
    public Model initialModel(Object context) {
        return model(fetch(query(context instanceof String ladderId ? ladderId : null),
                session != null && session.data != null ? session.data.uuid : ""));
    }

    @Override
    public UpdateResult<Model> update(Model model, Event event, ControllerContext ctx) {
        return switch (event) {
            case Event.SelectLadder(String ladderId) -> {
                boolean known = model.ladders().stream().anyMatch(tab -> tab.id().equals(ladderId));
                if (!known || ladderId.equals(model.ladderId())) {
                    yield UpdateResult.of(model);
                }
                yield load(model, Query.first(ladderId));
            }
            case Event.NextPage() -> {
                if (model.open() != null || model.nextCursor() == null) {
                    yield UpdateResult.of(model);
                }
                Deque<String> stack = new ArrayDeque<>(model.backStack());
                stack.addLast(model.cursor() == null ? FIRST_PAGE : model.cursor());
                yield load(model, new Query(model.ladderId(), model.page() + 1, model.nextCursor(), stack, false));
            }
            case Event.PrevPage() -> {
                if (model.open() != null || model.backStack().isEmpty()) {
                    yield UpdateResult.of(model);
                }
                Deque<String> stack = new ArrayDeque<>(model.backStack());
                String previous = stack.pollLast();
                yield load(model, new Query(model.ladderId(), model.page() - 1,
                        FIRST_PAGE.equals(previous) ? null : previous, stack, false));
            }
            case Event.Refresh() -> load(model, Query.first(model.ladderId()));
            case Event.OpenMatch(int index) -> {
                if (index < 0 || index >= model.matches().size()) {
                    yield UpdateResult.of(model);
                }
                yield loadMatch(model, model.matches().get(index));
            }
            case Event.MatchLoaded(MatchRecord match) -> UpdateResult.rerender(model.withOpen(match, 1));
            case Event.ParticipantsPage(int page) -> {
                if (model.open() == null) {
                    yield UpdateResult.of(model);
                }
                int pages = participantPages(model.open());
                yield UpdateResult.rerender(model.withOpen(model.open(), Math.clamp(page, 1, pages)));
            }
            case Event.BackToList() -> UpdateResult.rerender(model.withOpen(null, 1));
            case Event.InspectPlayer(int index) -> {
                MatchRecord open = model.open();
                List<MatchParticipant> participants = open == null ? List.of() : participants(open);
                if (index < 0 || index >= participants.size() || session == null) {
                    yield UpdateResult.of(model);
                }
                menu.inspect(session, participants.get(index).uuid(), () -> menu.reopen(session, this, model));
                yield UpdateResult.of(model);
            }
            case Event.Loaded(Data data) -> {
                Model loaded = apply(model, data);
                // Turning a page changes the list only; anything else may change the header as well.
                boolean pageOnly = data.summary() == null && model.open() == null
                        && Objects.equals(model.ladderId(), loaded.ladderId());
                yield pageOnly
                        ? UpdateResult.patch(loaded, Screen.slots(SLOT_LIST, SLOT_PAGER))
                        : UpdateResult.rerender(loaded);
            }
            case Event.LoadFailed() -> UpdateResult.rerender(model.withFeedback(
                    "[scarlet]" + Iconc.warning + "[] " + t(locale(session), "match-history-load-failed")));
            case Event.Back() -> {
                Runnable previous = session != null && session.hasHistory() ? session.popHistory() : null;
                if (previous == null) {
                    yield UpdateResult.of(model);
                }
                if (ctx != null) {
                    ctx.post(previous);
                } else {
                    previous.run();
                }
                yield UpdateResult.close(model);
            }
            case Event.Close() -> UpdateResult.close(model);
        };
    }

    @Override
    public Event parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new Event.Close();
        }
        String res = result.result.trim();
        return switch (res) {
            case "action:page:next" -> new Event.NextPage();
            case "action:page:prev" -> new Event.PrevPage();
            case "action:refresh" -> new Event.Refresh();
            case "action:list" -> new Event.BackToList();
            case "action:back" -> new Event.Back();
            default -> {
                if (res.startsWith("action:ladder:")) {
                    yield new Event.SelectLadder(res.substring("action:ladder:".length()));
                }
                Integer number = null;
                String kind = null;
                for (String prefix : List.of("action:match:", "action:player:", "action:players:")) {
                    if (res.startsWith(prefix)) {
                        kind = prefix;
                        try {
                            number = Integer.parseInt(res.substring(prefix.length()));
                        } catch (NumberFormatException ignored) {
                            // Not a button of this dialog.
                        }
                    }
                }
                if (number == null) {
                    yield new Event.Close();
                }
                yield switch (kind) {
                    case "action:match:" -> new Event.OpenMatch(number);
                    case "action:player:" -> new Event.InspectPlayer(number);
                    default -> new Event.ParticipantsPage(number);
                };
            }
        };
    }

    /** The participants of a match in the order they are shown: by team and place, best first. */
    public static List<MatchParticipant> participants(MatchRecord match) {
        Comparator<MatchParticipant> order = Comparator.comparingInt(MatchParticipant::placement)
                .thenComparing(p -> p.team() == null ? 0 : p.team())
                .thenComparing(MatchParticipant::counted, Comparator.reverseOrder())
                .thenComparing(MatchParticipant::ratingAfter, Comparator.reverseOrder())
                .thenComparing(MatchParticipant::uuid);
        return match.participants().stream().sorted(order).toList();
    }

    private static int participantPages(MatchRecord match) {
        return Math.max(1, (match.participants().size() + PARTICIPANTS_PER_PAGE - 1) / PARTICIPANTS_PER_PAGE);
    }

    // =========================================================================
    // View
    // =========================================================================

    @Override
    public VNode render(Model model) {
        return Screen.each(screen -> window(model, screen));
    }

    /** The menu laid out for one class of screens. */
    VNode window(Model model, Screen screen) {
        Localization local = locale(session);
        return model.open() != null ? matchWindow(model, model.open(), screen, local) : listWindow(model, screen, local);
    }

    private MatchPresenter presenter(String ladderId) {
        return menu.presenter(ladderId);
    }

    private String ladderName(Model model, String ladderId) {
        return model.ladders().stream().filter(tab -> tab.id().equals(ladderId)).findFirst()
                .map(LadderTab::name).orElse(ladderId);
    }

    private Accent accent(Model model) {
        for (int i = 0; i < model.ladders().size(); i++) {
            if (model.ladders().get(i).id().equals(model.ladderId())) {
                return Accent.at(i);
            }
        }
        return Accent.GOLD;
    }

    // ------------------------------------------------------------------ list

    private VNode listWindow(Model model, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.list + "[] [white]" + t(local, "match-history-title")
                    + "[]\n" + TextWidth.fit(owner(model, local), inner))).row();

            List<Kit.Tab> tabs = new ArrayList<>();
            for (int i = 0; i < model.ladders().size(); i++) {
                LadderTab tab = model.ladders().get(i);
                tabs.add(new Kit.Tab(tab.icon(), tab.name(), "action:ladder:" + tab.id(), Accent.at(i),
                        tab.id().equals(model.ladderId())));
            }
            window.add(Kit.tabs(width, "match_ladders", tabs)).row();
            window.add(Kit.line(width, accent(model))).row();

            String form = form(model, width - 2f * Kit.BAND_MARGIN, local);
            if (!form.isEmpty()) {
                window.add(Ui.table(band -> {
                    band.layout(l -> l.padBottom(GAP));
                    band.add(Kit.band(width, Kit.HEADER, Kit.BAND_MARGIN, form));
                })).row();
            }

            // The parts a turned page changes are slots, so the rest of the window stays as it is.
            window.slot(screen.slot(SLOT_LIST).path(), slot ->
                    slot.add(Kit.pane(screen, list -> rows(list, model, screen, local)))).row();

            if (!model.feedback().isBlank()) {
                window.add(Ui.table(feedback -> {
                    feedback.layout(l -> l.padTop(GAP));
                    feedback.add(Kit.feedback(width, model.feedback(), false));
                })).row();
            }

            window.slot(screen.slot(SLOT_PAGER).path(), slot -> slot.add(Kit.pager(width,
                    model.page() + " / " + Math.max(model.page(), model.totalPages()),
                    model.backStack().isEmpty() ? null : "action:page:prev",
                    model.nextCursor() == null ? null : "action:page:next",
                    "action:refresh"))).row();

            if (session != null && session.hasHistory()) {
                window.add(Ui.table(bar -> {
                    bar.layout(l -> l.padTop(GAP));
                    bar.add(Kit.actions(width, List.of(new Kit.Action(
                            "[lightgray]" + Iconc.left + "[] " + t(local, "back"), "action:back"))));
                })).row();
            }
        });
    }

    /** "ospx · ◆ Titanium 1642 · #14 this season" */
    private String owner(Model model, Localization local) {
        StringBuilder line = new StringBuilder("[lightgray]").append(model.ownerName()).append("[]");
        LadderProgress progress = model.summary().progress();
        if (progress != null) {
            LadderStanding standing = progress.standing();
            RatingLeague league = standing.league();
            line.append(" [gray]·[] ").append(league.icon()).append(" [white]")
                    .append(t(local, league.localizationKey())).append("[] [accent]").append(standing.rating())
                    .append("[]");
            if (progress.rank() != null) {
                line.append(" [gray]·[] ").append(t(local, "match-history-rank", args("rank", progress.rank())));
            }
        }
        return line.toString();
    }

    /**
     * "Last 10: ●●●●●  +38" over the latest matches, newest first. A screen too narrow for the
     * words keeps the dots and the sum.
     */
    private String form(Model model, float width, Localization local) {
        List<MatchRecord> recent = model.summary().recent();
        if (recent.isEmpty()) {
            return "";
        }
        StringBuilder dots = new StringBuilder();
        int sum = 0;
        for (MatchRecord match : recent) {
            MatchParticipant own = own(match, model);
            if (own == null) continue;
            dots.append("[#").append(color(match, own)).append("]●[]");
            sum += counts(match, own) ? own.delta() : 0;
        }
        String figures = dots + "  " + delta(sum, true);
        String full = "[lightgray]" + t(local, "match-history-form", args("count", recent.size())) + "[]  " + figures;
        return TextWidth.of(full) <= width ? full : TextWidth.fit(figures, width);
    }

    private void rows(Ui.TableBuilder list, Model model, Screen screen, Localization local) {
        float width = screen.cards();
        if (model.matches().isEmpty()) {
            list.add(Kit.note(width, empty(model, local))).row();
            return;
        }
        int season = model.summary().currentSeason();
        for (int i = 0; i < model.matches().size(); i++) {
            MatchRecord match = model.matches().get(i);
            if (match.season() != season) {
                season = match.season();
                list.add(seasonBand(width, season, local)).row();
            }
            list.add(row(model, i, match, screen, local)).row();
        }
    }

    private String empty(Model model, Localization local) {
        Instant start = model.summary().historyStart();
        String text = t(local, "match-history-empty");
        return start == null ? text
                : text + " " + t(local, "match-history-since", args("date", DATE_YEAR.format(start.atZone(ZoneOffset.UTC))));
    }

    /** Between matches of different seasons: after a soft reset the ratings on either side jump. */
    private static VNode seasonBand(float width, int season, Localization local) {
        String title = "[lightgray]" + t(local, "season-title", args("number", season)) + "[]";
        return Ui.table(band -> {
            band.layout(l -> l.padBottom(Kit.TAB_GAP));
            band.add(Kit.band(width, Kit.INSET, Kit.BAND_MARGIN, title));
        });
    }

    /**
     * A match of the list; pressing it opens the match. A wide screen keeps the match on one
     * line, a phone gives it two.
     */
    private VNode row(Model model, int index, MatchRecord match, Screen screen, Localization local) {
        MatchParticipant own = own(match, model);
        MatchPresenter presenter = presenter(match.ladder());
        String outcome = own != null ? presenter.outcome(match, own, local) : "";
        String when = when(match.endedAt(), local);
        String map = match.map() != null && !match.map().isBlank() ? TextWidth.escape(match.map()) : "";
        String stripe = own != null ? color(match, own) : NEUTRAL;
        String value = own != null ? delta(own.delta(), counts(match, own)) : "";
        String after = own != null ? "[lightgray]" + own.ratingAfter() + "[]" : "";

        return Kit.row("action:match:" + index, screen.cards(), false, true, (row, inner) -> {
            float rest = inner - STRIPE - GAP - GAP - VALUE_WIDTH;
            row.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP).color(stripe));
            if (screen.columns() > 1) {
                float place = rest - OUTCOME_WIDTH - GAP - RATINGS_WIDTH - GAP;
                String details = "[lightgray]" + when + (map.isEmpty() ? "" : " · " + map) + " · "
                        + PlayerProfileUiController.formatDuration(minutes(match.duration()), local) + "[]";
                String ratings = own == null ? "" : "[lightgray]" + own.ratingBefore() + " [gray]" + Iconc.right
                        + "[] " + own.ratingAfter() + "[]";
                row.label(Text.raw(TextWidth.fit(outcome, OUTCOME_WIDTH)), l -> l.width(OUTCOME_WIDTH).padRight(GAP));
                row.label(Text.raw(TextWidth.fit(details, place)), l -> l.width(place).padRight(GAP));
                row.label(Text.raw(TextWidth.fit(ratings, RATINGS_WIDTH)), l -> l.width(RATINGS_WIDTH).padRight(GAP));
                row.add(Kit.right(value, VALUE_WIDTH));
            } else {
                String details = "[lightgray]" + when + (map.isEmpty() ? "" : " · " + map) + "[]";
                row.add(Ui.labelWrap(Text.raw(TextWidth.fit(outcome, rest) + "\n" + TextWidth.fit(details, rest)),
                        l -> l.width(rest).padRight(GAP)));
                row.add(Kit.right(value + "\n" + after, VALUE_WIDTH));
            }
        });
    }

    // ------------------------------------------------------------------ match

    private VNode matchWindow(Model model, MatchRecord match, Screen screen, Localization local) {
        float width = screen.width();
        float inner = width - 2f * Kit.MARGIN;
        MatchPresenter presenter = presenter(match.ladder());
        List<MatchParticipant> participants = participants(match);
        int pages = participantPages(match);
        int page = Math.clamp(model.participantsPage(), 1, pages);

        return Kit.window(window -> {
            String title = "[accent]" + presenter.icon() + "[] [white]" + ladderName(model, match.ladder())
                    + (match.map() != null && !match.map().isBlank() ? " · " + TextWidth.escape(match.map()) : "")
                    + "[]";
            List<String> facts = new ArrayList<>();
            facts.add(DATE_TIME.format(match.endedAt()));
            facts.add(PlayerProfileUiController.formatDuration(minutes(match.duration()), local));
            facts.add(t(local, "season-title", args("number", match.season())));
            String finish = presenter.finish(match, local);
            if (!finish.isBlank()) facts.add(finish);
            window.add(Kit.header(width, TextWidth.fit(title, inner) + "\n[lightgray]"
                    + String.join(" · ", facts) + "[]")).row();
            window.add(Kit.line(width, accent(model))).row();

            Kit.body(window, screen, body -> {
                List<VNode> left = new ArrayList<>();
                List<VNode> right = new ArrayList<>();
                MatchParticipant own = own(match, model);
                if (own != null) {
                    left.add(resultCard(match, own, screen.card(), presenter, local));
                }
                int from = (page - 1) * PARTICIPANTS_PER_PAGE;
                int to = Math.min(participants.size(), from + PARTICIPANTS_PER_PAGE);
                List<VNode> cards = participantCards(model, match, participants, from, to, screen.card(),
                        presenter, local);
                // Without a result of the viewer's own the participants take both columns.
                if (left.isEmpty()) {
                    body.add(Kit.columns(screen, cards)).row();
                } else {
                    right.addAll(cards);
                    body.add(Kit.columns(screen, left, right)).row();
                }
            });

            if (pages > 1) {
                window.add(Kit.pager(width, page + " / " + pages,
                        page > 1 ? "action:players:" + (page - 1) : null,
                        page < pages ? "action:players:" + (page + 1) : null, null)).row();
            }
            if (!model.feedback().isBlank()) {
                window.add(Ui.table(feedback -> {
                    feedback.layout(l -> l.padTop(GAP));
                    feedback.add(Kit.feedback(width, model.feedback(), false));
                })).row();
            }
            window.add(Ui.table(bar -> {
                bar.layout(l -> l.padTop(GAP));
                bar.add(Kit.actions(width, List.of(new Kit.Action(
                        "[lightgray]" + Iconc.left + "[] " + t(local, "match-history-back-to-list"), "action:list"))));
            })).row();
        });
    }

    /** What the match came to for the viewer: the outcome, the rating, the league and why it counted. */
    private VNode resultCard(MatchRecord match, MatchParticipant own, float width, MatchPresenter presenter,
                             Localization local) {
        Accent accent = Accent.of(color(match, own));
        String title = Iconc.players + " " + t(local, "match-history-your-result");
        return Kit.card(width, accent, title, (content, inner) -> {
            List<String> lines = new ArrayList<>();
            lines.add(presenter.outcome(match, own, local));
            lines.add("[white]" + own.ratingBefore() + " [gray]" + Iconc.right + "[] " + own.ratingAfter() + "[]  "
                    + delta(own.delta(), counts(match, own)));
            RatingLeague before = RatingLeague.fromRating(own.ratingBefore());
            RatingLeague after = RatingLeague.fromRating(own.ratingAfter());
            if (before != after) {
                lines.add(before.icon() + " " + t(local, before.localizationKey()) + " [gray]" + Iconc.right + "[] "
                        + after.icon() + " [white]" + t(local, after.localizationKey()) + "[]");
            }
            lines.add("[lightgray]" + presenter.reason(match, own, local) + "[]");
            for (String line : lines) {
                content.add(Kit.text(line, inner)).row();
            }
        });
    }

    /** The participants from {@code from} to {@code to}: a card per team, or one card of places. */
    private List<VNode> participantCards(Model model, MatchRecord match, List<MatchParticipant> participants,
                                         int from, int to, float width, MatchPresenter presenter,
                                         Localization local) {
        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = from; i < to; i++) {
            MatchParticipant participant = participants.get(i);
            int key = match.teams() && participant.team() != null ? participant.team() : Integer.MIN_VALUE;
            groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(i);
        }
        List<VNode> cards = new ArrayList<>();
        for (var group : groups.entrySet()) {
            String title;
            Accent accent;
            if (group.getKey() == Integer.MIN_VALUE) {
                title = Iconc.players + " " + t(local, "match-history-places");
                accent = Accent.GRAY;
            } else {
                int team = group.getKey();
                MatchParticipant first = participants.get(group.getValue().getFirst());
                title = t(local, "match-history-team-title", args(
                        "place", first.placement(),
                        "team", presenter.team(team, local),
                        "average", average(match, team)));
                accent = Accent.of(presenter.teamColor(team));
            }
            cards.add(Kit.card(width, accent, title, (content, inner) -> {
                for (int index : group.getValue()) {
                    content.add(participantRow(model, match, participants.get(index), index, inner,
                            presenter, local)).row();
                }
            }));
        }
        return cards;
    }

    /** Average rating the team went in with, over the members the match counted for. */
    private static int average(MatchRecord match, int team) {
        return (int) Math.round(match.participants().stream()
                .filter(p -> Objects.equals(p.team(), team) && p.counted())
                .mapToInt(MatchParticipant::ratingBefore)
                .average()
                .orElseGet(() -> match.participants().stream()
                        .filter(p -> Objects.equals(p.team(), team))
                        .mapToInt(MatchParticipant::ratingBefore).average().orElse(0)));
    }

    /** A participant; pressing the row opens their profile. */
    private VNode participantRow(Model model, MatchRecord match, MatchParticipant participant, int index,
                                 float width, MatchPresenter presenter, Localization local) {
        boolean own = participant.uuid().equals(model.ownerUuid());
        String place = match.teams() ? "" : "[gray]#" + participant.placement() + "[] ";
        String name = place + participantName(participant, own ? "[accent]" : "[white]", local);
        String ratings = participant.counted()
                ? "[lightgray]" + participant.ratingBefore() + " [gray]" + Iconc.right + "[] " + participant.ratingAfter() + "[]"
                : "[gray]" + participant.ratingAfter() + "[]";
        String figures = presenter.figures(participant, local);
        String second = figures.isBlank() ? ratings : ratings + " [gray]·[] [lightgray]" + figures + "[]";
        String value = delta(participant.delta(), counts(match, participant));

        return Kit.row("action:player:" + index, width, own, true, (row, inner) -> {
            float rest = inner - GAP - VALUE_WIDTH;
            row.add(Ui.labelWrap(Text.raw(TextWidth.fit(name, rest) + "\n" + TextWidth.fit(second, rest)),
                    l -> l.width(rest).padRight(GAP)));
            row.add(Kit.right(value, VALUE_WIDTH));
        });
    }

    /** The name a participant had in the match. A name that brings its own colours keeps them. */
    private static String participantName(MatchParticipant participant, String color, Localization local) {
        String name = participant.name().replace('\n', ' ').trim();
        if (name.isEmpty()) {
            name = t(local, "player-menu-player");
        }
        return (name.startsWith("[") ? "" : color) + name + "[]";
    }

    // ------------------------------------------------------------------ pieces

    private static @Nullable MatchParticipant own(MatchRecord match, Model model) {
        return match.participant(model.ownerUuid()).orElse(null);
    }

    /** Whether the match changed the participant's rating, so that a delta is worth showing. */
    private static boolean counts(MatchRecord match, MatchParticipant participant) {
        return match.rated() && participant.counted();
    }

    /** Green for a gain, red for a loss, grey for a match that changed nothing. */
    private static String color(MatchRecord match, MatchParticipant participant) {
        if (!counts(match, participant) || participant.delta() == 0) {
            return participant.win() && counts(match, participant) ? WIN : NEUTRAL;
        }
        return participant.delta() > 0 ? WIN : LOSS;
    }

    /** "+14", "-11", or a dash for a match that did not count. */
    static String delta(int delta, boolean counted) {
        if (!counted) return "[gray]—[]";
        if (delta > 0) return "[lime]+" + delta + "[]";
        if (delta < 0) return "[scarlet]" + delta + "[]";
        return "[lightgray]0[]";
    }

    private static int minutes(Duration duration) {
        return (int) Math.max(1, (duration.getSeconds() + 59) / 60);
    }

    /** "just now", "5 min ago", "3 h ago", "yesterday", then the date: players are in many time zones. */
    String when(Instant ended, Localization local) {
        Instant now = clock.instant();
        long minutes = Math.max(0, Duration.between(ended, now).toMinutes());
        if (minutes < 1) return t(local, "match-history-time-now");
        if (minutes < 60) return t(local, "match-history-time-minutes", args("count", minutes));
        if (minutes < 24 * 60) return t(local, "match-history-time-hours", args("count", minutes / 60));
        if (minutes < 48 * 60) return t(local, "match-history-time-yesterday");
        ZoneId zone = ZoneOffset.UTC;
        boolean thisYear = ended.atZone(zone).getYear() == now.atZone(zone).getYear();
        return (thisYear ? DATE : DATE_YEAR).format(ended.atZone(zone));
    }
}
