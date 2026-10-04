package org.xcore.plugin.ui.menu;

import arc.util.Log;
import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.integration.top.TopScope;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
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
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;

/**
 * The leaderboard ({@code /top}, {@code /lb}): a tab per category, the players of a page as rows
 * that open a profile, the viewer's own place under the list, laid out once per {@link Screen}.
 * Turning a page patches the list, the place and the pager and leaves the rest of the window.
 */
public class TopUiController implements UiController<TopUiController.TopModel, TopUiController.TopEvent> {

    public static final SlotKey<Object> SLOT_ENTRIES = SlotKey.of("slot_top_entries");
    public static final SlotKey<Object> SLOT_SELF_RANK = SlotKey.of("slot_top_self_rank");
    public static final SlotKey<Object> SLOT_PAGINATION = SlotKey.of("slot_top_pagination");

    public static final int PLAYERS_PER_PAGE = 10;
    /** Letters of a nickname kept before it is measured against the row; no nickname needs more. */
    private static final int NICKNAME_BUDGET = 64;
    public static final String FIRST_PAGE_CURSOR_TOKEN = "__first__";

    public record CategoryTab(
            String id,
            String displayName,
            String iconGlyph,
            int priority
    ) {}

    /**
     * Leaderboard view state.
     *
     * <p>No device or orientation flags live here. The server cannot know either one —
     * {@code ConnectPacket} carries only {@code mobile}, and the camera dimensions in
     * {@code clientSnapshot} describe the world view rather than the screen — so anything the
     * dialog needs to adapt is expressed in the tree and resolved by the client (see {@link Screen}).
     *
     * @param scopes          the selected category's leaderboards, empty when it has only one
     * @param selectedScopeId the scope on screen, {@code null} when the category has only one
     */
    public record TopModel(
            String viewerUuid,
            String selectedCategoryId,
            List<CategoryTab> categories,
            List<TopScope> scopes,
            String selectedScopeId,
            int currentPage,
            int totalPages,
            Long totalEntries,
            Integer selfRank,
            String selfPrimaryValue,
            List<LeaderboardEntry> entries,
            boolean hasNext,
            String currentCursor,
            String nextCursor,
            Deque<String> cursorBackStack,
            String feedbackMessage
    ) {
        public TopModel withFeedback(String message) {
            return new TopModel(
                    viewerUuid, selectedCategoryId, categories, scopes, selectedScopeId,
                    currentPage, totalPages, totalEntries,
                    selfRank, selfPrimaryValue,
                    entries, hasNext,
                    currentCursor, nextCursor,
                    cursorBackStack, message
            );
        }
    }

    /**
     * One leaderboard page to load.
     *
     * @param scopeId    {@code null} for the category's current scope
     * @param cursor     {@code null} for the first page
     * @param backStack  cursors of the pages before this one
     * @param withScopes whether the category's scopes must be (re)read as well
     */
    public record TopQuery(String categoryId, String scopeId, int page, String cursor, Deque<String> backStack,
                           boolean withScopes) {
        public TopQuery {
            page = Math.max(1, page);
            backStack = backStack == null ? new ArrayDeque<>() : new ArrayDeque<>(backStack);
        }
    }

    /**
     * What storage returned for a {@link TopQuery}.
     *
     * @param scopes {@code null} when the query did not ask for them
     */
    public record TopData(TopQuery query, List<TopScope> scopes, LeaderboardPage page) {
    }

    public sealed interface TopEvent {
        record SelectCategory(String categoryId) implements TopEvent {}
        record SelectScope(String scopeId) implements TopEvent {}
        record NextPage() implements TopEvent {}
        record PrevPage() implements TopEvent {}
        record Refresh() implements TopEvent {}
        record InspectPlayer(String playerUuid) implements TopEvent {}
        /** A page requested by an earlier event has arrived from storage. */
        record Loaded(TopData data) implements TopEvent {}
        record Close() implements TopEvent {}
    }

    private final TopMenu topMenu;
    private final TopCategoryRegistry categoryRegistry;
    private final PlayerMenu playerMenu;
    private final SessionService sessionService;
    private final Async async;
    private final Session session;
    /** Counts the page loads this dialog started; only the latest one may change it. */
    private long loadGeneration;

    public TopUiController(TopMenu topMenu,
                           TopCategoryRegistry categoryRegistry,
                           PlayerMenu playerMenu,
                           SessionService sessionService,
                           Async async,
                           Session session) {
        this.topMenu = topMenu;
        this.categoryRegistry = categoryRegistry;
        this.playerMenu = playerMenu;
        this.sessionService = sessionService;
        this.async = async;
        this.session = session;
    }

    /** Blocking: loads the first page on the calling thread. Use {@link TopMenu#openTopUi} from the game thread. */
    @Override
    public TopModel initialModel(Object context) {
        return createInitialModel(context instanceof String categoryId ? categoryId : null, null, 1, null, null);
    }

    /** Blocking. The same as {@link #query}, {@link #fetch} and {@link #model} in one go. */
    public TopModel createInitialModel(String categoryId, String scopeId, int page, String cursor, Deque<String> backStack) {
        return model(fetch(query(categoryId, scopeId, page, cursor, backStack), viewer()));
    }

    /** The query that opens the menu; a blank category means the first tab. */
    public TopQuery query(String categoryId, String scopeId, int page, String cursor, Deque<String> backStack) {
        String category = categoryId;
        if (category == null || category.isBlank()) {
            List<CategoryTab> tabs = resolveCategoryTabs(locale());
            category = tabs.isEmpty() ? "PLAYTIME" : tabs.getFirst().id();
        }
        return new TopQuery(category, scopeId, page, cursor, backStack, true);
    }

    /**
     * Reads a page, and the category's scopes when asked to. Blocks on storage and touches
     * nothing viewer-specific, so it runs off the game thread.
     */
    public TopData fetch(TopQuery query, PlayerData viewer) {
        TopCategoryProvider provider = provider(query.categoryId());
        if (provider == null) {
            return new TopData(query, query.withScopes() ? List.of() : null, LeaderboardPage.empty(query.page()));
        }

        List<TopScope> scopes = null;
        if (query.withScopes()) {
            try {
                scopes = provider.scopes();
            } catch (Exception e) {
                Log.warn("Top category @ failed to list its scopes: @", query.categoryId(), e.getMessage());
            }
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        LeaderboardPage page;
        try {
            page = provider.loadPage(new LeaderboardPageRequest(
                    query.categoryId(), query.page(), PLAYERS_PER_PAGE, query.cursor(), viewer, query.scopeId()));
        } catch (Exception e) {
            Log.warn("Top category @ failed to load page @: @", query.categoryId(), query.page(), e.getMessage());
            page = LeaderboardPage.empty(query.page());
        }
        return new TopData(query, scopes, page);
    }

    /** The model that opens the menu on {@code data}. */
    public TopModel model(TopData data) {
        TopModel blank = new TopModel(
                session != null && session.data != null ? session.data.uuid : "",
                data.query().categoryId(), resolveCategoryTabs(locale()), List.of(), null,
                1, 1, 0L, null, null,
                List.of(), false, null, null, new ArrayDeque<>(), ""
        );
        return apply(blank, data);
    }

    /** {@code current} showing what {@code data} holds. */
    private TopModel apply(TopModel current, TopData data) {
        TopQuery query = data.query();
        LeaderboardPage page = data.page();
        boolean sameCategory = Objects.equals(current.selectedCategoryId(), query.categoryId());
        List<TopScope> scopes = data.scopes() != null ? data.scopes() : (sameCategory ? current.scopes() : List.of());

        long total = page.totalEntries() != null ? page.totalEntries() : page.entries().size();
        int pages = page.totalEntries() != null
                ? Math.max(1, (int) Math.ceil((double) page.totalEntries() / PLAYERS_PER_PAGE))
                : (page.hasNext() ? page.currentPage() + 1 : page.currentPage());

        return new TopModel(
                current.viewerUuid(), query.categoryId(), current.categories(),
                scopes, selectedScope(scopes, query.scopeId()),
                page.currentPage(), pages, total,
                page.selfRank(), selfPrimaryValue(query.categoryId(), page),
                page.entries(), page.hasNext(),
                query.cursor(), page.nextCursor(),
                query.backStack(), ""
        );
    }

    /** The scope on screen: the one asked for when it exists, otherwise the category's current one. */
    private static String selectedScope(List<TopScope> scopes, String requested) {
        if (scopes.isEmpty()) {
            return null;
        }
        String current = scopes.getFirst().id();
        for (TopScope scope : scopes) {
            if (scope.id().equals(requested)) {
                return requested;
            }
            if (scope.current()) {
                current = scope.id();
            }
        }
        return current;
    }

    private List<CategoryTab> resolveCategoryTabs(Localization local) {
        if (categoryRegistry == null) {
            return List.of(new CategoryTab("PLAYTIME", "Playtime", String.valueOf(Iconc.refresh), 10));
        }
        return categoryRegistry.all().stream()
                .sorted(Comparator.comparingInt(TopCategoryProvider::priority).reversed())
                .map(p -> new CategoryTab(p.id(), safeDisplayName(p, local), resolveIcon(p.id()), p.priority()))
                .toList();
    }

    /** The viewer's own value in the category, as the category words it. */
    private String selfPrimaryValue(String categoryId, LeaderboardPage page) {
        TopCategoryProvider provider = provider(categoryId);
        if (provider == null || session == null || session.data == null) return null;
        Localization local = locale();

        for (LeaderboardEntry entry : page.entries()) {
            if (Objects.equals(entry.playerUuid(), session.data.uuid)) {
                return provider.formatValue(entry, local);
            }
        }
        if (page.selfPrimaryValue() != null && !page.selfPrimaryValue().isBlank()) {
            return provider.formatValue(page.selfPrimaryValue(), local);
        }
        return null;
    }

    private TopCategoryProvider provider(String categoryId) {
        return categoryRegistry == null ? null : categoryRegistry.resolve(categoryId).orElse(null);
    }

    private Localization locale() {
        return session != null ? session.locale() : null;
    }

    private PlayerData viewer() {
        return session != null ? session.data : null;
    }

    /**
     * Loads {@code query} off the game thread and feeds the result back as a
     * {@link TopEvent.Loaded}. The dialog keeps showing {@code model} meanwhile; a result
     * that arrives after the dialog moved on, or after the player asked for something newer,
     * is dropped.
     */
    private UpdateResult<TopModel> load(TopModel model, TopQuery query) {
        PlayerData viewer = viewer();
        long generation = ++loadGeneration;
        async.supply(() -> fetch(query, viewer)).thenMain((data, error) -> {
            if (generation != loadGeneration) {
                return; // Superseded: applying it would make the newer request look stale.
            }
            if (error != null) {
                Log.err("Failed to load top category " + query.categoryId(), error);
                return;
            }
            var active = session != null ? session.activeUiSession() : null;
            if (active != null && active.model() == model) {
                @SuppressWarnings("unchecked")
                var topSession = (org.xcore.ui.runtime.UiSession<TopModel, TopEvent>) active;
                topSession.dispatch(new TopEvent.Loaded(data));
            }
        });
        return UpdateResult.of(model);
    }

    @Override
    public UpdateResult<TopModel> update(TopModel model, TopEvent event, ControllerContext ctx) {
        return switch (event) {
            case TopEvent.SelectCategory e -> {
                if (Objects.equals(model.selectedCategoryId(), e.categoryId())) {
                    yield UpdateResult.of(model);
                }
                yield load(model, new TopQuery(e.categoryId(), null, 1, null, null, true));
            }
            case TopEvent.SelectScope e -> {
                boolean known = model.scopes().stream().anyMatch(scope -> scope.id().equals(e.scopeId()));
                if (!known || Objects.equals(model.selectedScopeId(), e.scopeId())) {
                    yield UpdateResult.of(model);
                }
                yield load(model, new TopQuery(model.selectedCategoryId(), e.scopeId(), 1, null, null, false));
            }
            case TopEvent.NextPage() -> {
                if (!model.hasNext() || model.nextCursor() == null) {
                    yield UpdateResult.of(model);
                }
                Deque<String> nextStack = new ArrayDeque<>(model.cursorBackStack());
                nextStack.addLast(model.currentCursor() == null ? FIRST_PAGE_CURSOR_TOKEN : model.currentCursor());
                yield load(model, new TopQuery(model.selectedCategoryId(), model.selectedScopeId(),
                        model.currentPage() + 1, model.nextCursor(), nextStack, false));
            }
            case TopEvent.PrevPage() -> {
                if (model.cursorBackStack().isEmpty()) {
                    yield UpdateResult.of(model);
                }
                Deque<String> prevStack = new ArrayDeque<>(model.cursorBackStack());
                String prevCursor = prevStack.pollLast();
                String targetCursor = FIRST_PAGE_CURSOR_TOKEN.equals(prevCursor) ? null : prevCursor;
                yield load(model, new TopQuery(model.selectedCategoryId(), model.selectedScopeId(),
                        Math.max(1, model.currentPage() - 1), targetCursor, prevStack, false));
            }
            case TopEvent.Refresh() -> load(model, new TopQuery(model.selectedCategoryId(), model.selectedScopeId(),
                    model.currentPage(), model.currentCursor(), model.cursorBackStack(), true));
            case TopEvent.Loaded(TopData data) -> {
                TopModel loaded = apply(model, data);
                // Turning a page changes the list only; anything else may change the header as well.
                boolean pageOnly = !data.query().withScopes()
                        && Objects.equals(model.selectedCategoryId(), loaded.selectedCategoryId())
                        && Objects.equals(model.selectedScopeId(), loaded.selectedScopeId());
                yield pageOnly
                        ? UpdateResult.patch(loaded, Screen.slots(SLOT_ENTRIES, SLOT_SELF_RANK, SLOT_PAGINATION))
                        : UpdateResult.rerender(loaded);
            }
            case TopEvent.InspectPlayer e -> {
                if (e.playerUuid() == null || e.playerUuid().isBlank()) {
                    yield UpdateResult.of(model);
                }

                // If inspecting self
                if (session != null && session.data != null && Objects.equals(session.data.uuid, e.playerUuid())) {
                    openProfileWithReturn(model, session.data);
                    yield UpdateResult.of(model);
                }

                // If target is online
                PlayerData onlineTarget = null;
                if (sessionService != null) {
                    Session s = sessionService.get(e.playerUuid());
                    if (s != null && s.data != null) {
                        onlineTarget = s.data;
                    }
                }
                if (onlineTarget != null) {
                    openProfileWithReturn(model, onlineTarget);
                    yield UpdateResult.of(model);
                }

                // If target is offline -> non-blocking async load
                if (async != null && session != null && session.player != null && sessionService != null) {
                    async.onMainForPlayer(session.player,
                            sessionService.getOrLoadFromDbAsync(e.playerUuid()),
                            (player, loaded) -> {
                                var active = session.activeUiSession();
                                if (loaded != null && active != null && active.model() == model) {
                                    openProfileWithReturn(model, loaded);
                                }
                            });
                }
                yield UpdateResult.of(model);
            }
            case TopEvent.Close() -> UpdateResult.close(model);
        };
    }

    private void openProfileWithReturn(TopModel model, PlayerData target) {
        if (session == null || playerMenu == null) return;
        String cat = model.selectedCategoryId();
        String scope = model.selectedScopeId();
        int page = model.currentPage();
        String cursor = model.currentCursor();
        Deque<String> backStack = new ArrayDeque<>(model.cursorBackStack());

        session.pushHistory(() -> {
            if (topMenu != null) {
                topMenu.openTopUi(session, cat, scope, page, cursor, backStack);
            }
        });

        playerMenu.openProfileUi(session, target);
    }

    @Override
    public TopEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new TopEvent.Close();
        }
        String res = result.result.trim();
        if ("action:close".equals(res)) return new TopEvent.Close();
        if ("action:page:next".equals(res)) return new TopEvent.NextPage();
        if ("action:page:prev".equals(res)) return new TopEvent.PrevPage();
        if ("action:refresh".equals(res)) return new TopEvent.Refresh();
        if (res.startsWith("action:tab:")) {
            return new TopEvent.SelectCategory(res.substring("action:tab:".length()));
        }
        if (res.startsWith("action:scope:")) {
            return new TopEvent.SelectScope(res.substring("action:scope:".length()));
        }
        if (res.startsWith("action:inspect:")) {
            return new TopEvent.InspectPlayer(res.substring("action:inspect:".length()));
        }
        return new TopEvent.Close();
    }

    // =========================================================================
    // View Rendering
    // =========================================================================

    /** What the rank takes of a row, and the value on a screen wide enough to keep it on the name's line. */
    private static final float RANK_WIDTH = 60f;
    private static final float VALUE_WIDTH = 250f;
    private static final float STRIPE = 4f;

    @Override
    public VNode render(TopModel model) {
        return Screen.each(screen -> window(model, screen));
    }

    /** The leaderboard laid out for one class of screens. */
    VNode window(TopModel model, Screen screen) {
        Localization local = locale();
        float width = screen.width();
        String categoryName = model.categories().stream()
                .filter(c -> Objects.equals(c.id(), model.selectedCategoryId()))
                .findFirst()
                .map(CategoryTab::displayName)
                .orElse(model.selectedCategoryId());

        List<Kit.Tab> tabs = new ArrayList<>();
        Accent accent = Accent.GOLD;
        for (int i = 0; i < model.categories().size(); i++) {
            CategoryTab category = model.categories().get(i);
            boolean selected = Objects.equals(category.id(), model.selectedCategoryId());
            if (selected) accent = Accent.at(i);
            char glyph = category.iconGlyph() == null || category.iconGlyph().isEmpty()
                    ? 0 : category.iconGlyph().charAt(0);
            tabs.add(new Kit.Tab(glyph, category.displayName(), "action:tab:" + category.id(), Accent.at(i), selected));
        }
        Accent lineAccent = accent;

        return Kit.window(window -> {
            String title = local != null
                    ? local.t("top-menu-title", args("category", categoryName))
                    : "Top Players: " + categoryName;
            String total = "";
            if (model.totalEntries() != null && model.totalEntries() > 0) {
                total = "\n[lightgray]" + (local != null
                        ? local.t("top-menu-total-count", args("count", model.totalEntries()))
                        : model.totalEntries() + " players") + "[]";
            }
            window.add(Kit.header(width, "[gold]" + Iconc.star + "[] [white]" + title + "[]" + total)).row();

            window.add(Kit.tabs(width, "tabs", tabs)).row();
            // A category with several leaderboards (rating seasons) steps through them here.
            if (model.scopes().size() > 1) {
                window.add(scopeSwitcher(model, width, local)).row();
            }
            window.add(Kit.line(width, lineAccent)).row();

            // The parts a turned page changes are slots, so the rest of the window stays as it is.
            window.slot(screen.slot(SLOT_ENTRIES).path(), slot ->
                    slot.add(Kit.pane(screen, list -> entries(list, model, screen, local, categoryName)))).row();
            window.slot(screen.slot(SLOT_SELF_RANK).path(), slot -> {
                slot.layout(l -> l.padTop(GAP));
                slot.add(Kit.band(width, Kit.HEADER, Kit.BAND_MARGIN, selfRank(model, local)));
            }).row();

            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                window.add(Ui.table(feedback -> {
                    feedback.layout(l -> l.padTop(GAP));
                    feedback.add(Kit.feedback(width, model.feedbackMessage(), false));
                })).row();
            }

            window.slot(screen.slot(SLOT_PAGINATION).path(), slot -> {
                boolean canPrev = !model.cursorBackStack().isEmpty() && model.currentPage() > 1;
                boolean canNext = model.hasNext() && model.nextCursor() != null;
                slot.add(Kit.pager(width, model.currentPage() + " / " + model.totalPages(),
                        canPrev ? "action:page:prev" : null, canNext ? "action:page:next" : null, "action:refresh"));
            }).row();
        });
    }

    /** "◀ Season 3 ▶": scopes run newest first, so the left arrow steps to an older one. */
    private VNode scopeSwitcher(TopModel model, float width, Localization local) {
        List<TopScope> scopes = model.scopes();
        int index = 0;
        for (int i = 0; i < scopes.size(); i++) {
            if (scopes.get(i).id().equals(model.selectedScopeId())) {
                index = i;
            }
        }
        TopScope selected = scopes.get(index);
        TopScope older = index + 1 < scopes.size() ? scopes.get(index + 1) : null;
        TopScope newer = index > 0 ? scopes.get(index - 1) : null;

        TopCategoryProvider provider = provider(model.selectedCategoryId());
        String label = selected.id();
        if (provider != null && local != null) {
            try {
                label = provider.formatScope(selected, local);
            } catch (Exception ignored) {
                // The raw scope id is still a usable label.
            }
        }
        return Kit.pager(width, (selected.current() ? "[accent]" : "[lightgray]") + label + "[]",
                older != null ? "action:scope:" + older.id() : null,
                newer != null ? "action:scope:" + newer.id() : null, null);
    }

    private void entries(Ui.TableBuilder list, TopModel model, Screen screen, Localization local,
                         String categoryName) {
        float width = screen.cards();
        if (model.entries().isEmpty()) {
            list.add(Kit.note(width, local != null
                    ? local.t("top-menu-empty", args("category", categoryName))
                    : "No entries found.")).row();
            return;
        }
        for (LeaderboardEntry entry : model.entries()) {
            list.add(entryRow(entry, model, screen, local)).row();
        }
    }

    /**
     * A player of the list; pressing the row opens their profile. A wide screen keeps the value on
     * the name's line, a phone puts it under the name.
     */
    private VNode entryRow(LeaderboardEntry entry, TopModel model, Screen screen, Localization local) {
        boolean isViewer = Objects.equals(entry.playerUuid(), model.viewerUuid());
        Map<String, String> attrs = entry.attributes() != null ? entry.attributes() : Map.of();
        String stripeColor = switch (entry.rank()) {
            case 1 -> "ffd700";
            case 2 -> "c0c0c0";
            case 3 -> "d99058";
            default -> isViewer ? "ffd37f" : "3b4252";
        };

        StringBuilder identity = new StringBuilder();
        if (attrs.containsKey("activeBadge")) {
            Badge badge = Badge.byId(attrs.get("activeBadge"));
            if (badge != null) {
                identity.append(PlayerSettingsUiController.renderBadgeTagExact(badge,
                        attrs.getOrDefault("badgeColorMode", "default"), attrs.get("playerColorHex"))).append(' ');
            }
        }
        if ("true".equalsIgnoreCase(attrs.get("admin"))) {
            identity.append("[scarlet]").append(Iconc.admin).append("[] ");
        }
        identity.append(isViewer ? "[accent]" : "[white]").append(resolveNickname(entry, attrs, NICKNAME_BUDGET));

        StringBuilder value = new StringBuilder();
        if (attrs.containsKey("leagueIcon")) {
            value.append(attrs.get("leagueIcon")).append(' ');
        } else if (attrs.containsKey("leagueName")) {
            String league = attrs.get("leagueName");
            try {
                RatingLeague rl = RatingLeague.valueOf(league.toUpperCase());
                league = local != null ? local.t(rl.localizationKey()) : rl.name();
            } catch (Exception ignored) {
                // An unknown league is shown as it was stored.
            }
            value.append("[purple]").append(TextWidth.escape(league)).append("[] ");
        }
        if (attrs.containsKey("rankName")) {
            String rankName = attrs.get("rankName");
            value.append("[purple]").append(TextWidth.escape(
                    local != null ? local.t("hexed-ranks-" + rankName) : rankName)).append("[] ");
        }
        value.append(entry.rank() <= 3 ? "[gold]" : "[sky]")
                .append(formatValue(model.selectedCategoryId(), entry, local)).append("[]");

        String rank = formatRankBadge(entry.rank());
        float rankWidth = Math.max(RANK_WIDTH, TextWidth.of(rank) + 4f);

        return Kit.row("action:inspect:" + entry.playerUuid(), screen.cards(), isViewer, true, (row, inner) -> {
            float rest = inner - STRIPE - GAP - rankWidth;
            row.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP).color(stripeColor));
            row.label(Text.raw(rank), l -> l.width(rankWidth));
            if (screen.columns() > 1) {
                float name = rest - VALUE_WIDTH - GAP;
                row.label(Text.raw(TextWidth.fit(identity.toString(), name)), l -> l.width(name).padRight(GAP));
                row.add(Kit.right(TextWidth.fit(value.toString(), VALUE_WIDTH), VALUE_WIDTH));
            } else {
                row.add(Kit.text(TextWidth.fit(identity.toString(), rest) + "[]\n"
                        + TextWidth.fit(value.toString(), rest), rest));
            }
        });
    }

    /** Where the viewer stands in the category, whatever page is open. */
    private String selfRank(TopModel model, Localization local) {
        if (model.selfRank() == null) {
            return "[gray]" + Iconc.info + " " + (local != null
                    ? local.t("top-menu-unranked")
                    : "You are not ranked in this category yet") + "[]";
        }
        String rank = "[#ffd37f]#" + model.selfRank() + "[]";
        String line = "[lime]●[] " + (local != null
                ? local.t("top-menu-self-rank-line", args("rank", rank))
                : "Your rank: " + rank);
        boolean onPage = model.entries().stream().anyMatch(e -> Objects.equals(e.playerUuid(), model.viewerUuid()));
        if (onPage) {
            return line + "  [gray](" + (local != null ? local.t("top-menu-on-this-page") : "on this page") + ")[]";
        }
        if (model.selfPrimaryValue() != null) {
            return line + "  [gray]|[] [sky]" + model.selfPrimaryValue() + "[]";
        }
        return line;
    }

    // =========================================================================
    // Formatters & Utilities
    // =========================================================================

    public static String formatRankBadge(int rank) {
        return switch (rank) {
            case 1 -> "[#FFD700]" + Iconc.star + " #1[]";
            case 2 -> "[#C0C0C0]#2[]";
            case 3 -> "[#D99058]#3[]";
            default -> "[gray]#" + rank + "[]";
        };
    }

    private static String resolveIcon(String id) {
        String upper = id != null ? id.toUpperCase() : "";
        if (upper.equals("MINI_PVP") || upper.contains("PVP")) {
            return String.valueOf(Iconc.modePvp);
        }
        if (upper.equals("PLAYTIME") || upper.contains("TIME")) {
            return String.valueOf(Iconc.refresh);
        }
        if (upper.contains("HEXED") || upper.contains("STAR") || upper.contains("ELO")) {
            return String.valueOf(Iconc.star);
        }
        return String.valueOf(Iconc.players);
    }

    private static String safeDisplayName(TopCategoryProvider provider, Localization local) {
        try {
            String name = provider.displayName(local);
            if (name != null && !name.isBlank()) return name;
        } catch (Exception ignored) {}
        return provider.id();
    }

    public static String resolveNickname(LeaderboardEntry entry, Map<String, String> attrs, int maxPlainLen) {
        String raw = attrs != null && attrs.containsKey("customNickname") ? attrs.get("customNickname") : (entry != null ? entry.displayName() : null);
        if (raw == null || raw.isBlank()) {
            return entry != null && entry.rank() > 0 ? "Player #" + entry.rank() : "Player";
        }
        String clean = raw.replace('\n', ' ').trim();

        String stripped = Strings.stripColors(clean).trim();
        if (stripped.isEmpty()) {
            return entry != null && entry.rank() > 0 ? "Player #" + entry.rank() : "Player";
        }

        // If stripped plain length is within limit, preserve original colors and formatting intact
        if (stripped.length() <= maxPlainLen) {
            return clean;
        }

        // Otherwise truncate visible plain text cleanly to avoid broken color tags, overflow, or color bleeding
        return stripped.substring(0, maxPlainLen) + "...";
    }

    private String formatValue(String categoryId, LeaderboardEntry entry, Localization local) {
        if (entry == null) return "-";
        TopCategoryProvider provider = provider(categoryId);
        return provider != null ? provider.formatValue(entry, local) : entry.primaryValue();
    }
}
