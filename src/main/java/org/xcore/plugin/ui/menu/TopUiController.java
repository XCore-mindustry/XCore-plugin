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
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.responsive.DialogMetrics;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

/**
 * Modern reactive Elm/MVI controller for the Leaderboard / Top Menu (/top, /lb) in Mindustry v160.
 * Replaces legacy TopFlows.java with in-dialog adaptive category tabs, zero-aim full-row touch targets,
 * sticky self-rank indicator, and zero-flicker slot patching.
 */
public class TopUiController implements UiController<TopUiController.TopModel, TopUiController.TopEvent> {

    public static final SlotKey<Object> SLOT_ENTRIES = SlotKey.of("slot_top_entries");
    public static final SlotKey<Object> SLOT_SELF_RANK = SlotKey.of("slot_top_self_rank");
    public static final SlotKey<Object> SLOT_PAGINATION = SlotKey.of("slot_top_pagination");

    public static final int PLAYERS_PER_PAGE = 10;
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
     * dialog needs to adapt is expressed in the tree and resolved by the client, either through
     * {@link org.xcore.ui.responsive.Responsive} conditions or {@code growX() + maxWidth} caps.
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
                        ? UpdateResult.patch(loaded, SLOT_ENTRIES, SLOT_SELF_RANK, SLOT_PAGINATION)
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

    /** Caps only; the client resolves the actual width and body height. */
    private DialogMetrics metrics() {
        return DialogMetrics.standard();
    }

    @Override
    public VNode render(TopModel model) {
        DialogMetrics metrics = metrics();
        Localization local = locale();
        String currentCatName = model.categories().stream()
                .filter(c -> Objects.equals(c.id(), model.selectedCategoryId()))
                .findFirst()
                .map(CategoryTab::displayName)
                .orElse(model.selectedCategoryId());

        return Ui.table(root -> {
            root.background("pane");
            root.margin(8f);
            root.layout(l -> l.growX().maxWidth(metrics.maxDialogWidth()).pad(4f));

            // 1. Dialog Header: Star Icon + Full Title on row 1, Count on row 2, and Close Button on the right
            root.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(4f));

                h.add(Ui.table(titleCol -> {
                    titleCol.layout(l -> l.growX().align("left"));
                    String titleText = local != null
                            ? local.t("top-menu-title", args("category", currentCatName))
                            : "Top Players: " + currentCatName;
                    titleCol.label(Text.raw("[gold]" + Iconc.star + "[] [white]" + titleText + "[]"),
                            l -> l.align("left").growX()).row();

                    if (model.totalEntries() != null && model.totalEntries() > 0) {
                        String totalText = local != null
                                ? local.t("top-menu-total-count", args("count", model.totalEntries()))
                                : model.totalEntries() + " players";
                        titleCol.label(Text.raw("[lightgray]" + totalText + "[]"),
                                l -> l.align("left").padTop(2f));
                    }
                }));

                h.button(Text.raw(" [scarlet]" + Iconc.cancel + "[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(32f)));
            })).row();

            root.image("whiteui", l -> l.growX().height(2f).padBottom(4f).color("3b4252")).row();

            // 2. Category Tabs
            root.add(Ui.table(tabs -> {
                // WrapTable picks the tab columns from the width the client actually gives it, so the
                // same tab set fits a narrow phone and a wide desktop without a server-side guess
                tabs.wrap();
                tabs.layout(l -> l.growX().padBottom(4f));

                for (CategoryTab cat : model.categories()) {
                    boolean active = Objects.equals(cat.id(), model.selectedCategoryId());
                    String label = (active ? "[accent]" : "[lightgray]") + cat.iconGlyph() + " " + cat.displayName() + "[]";

                    tabs.button(Text.raw(label), "action:tab:" + cat.id(), b -> b
                            .style(active ? "togglet" : "cleart")
                            .checked(active)
                            .layout(l -> l.height(34f).pad(2f).growX().uniform()));
                }
            })).row();

            // 2b. Scope switcher, for a category with several leaderboards (rating seasons)
            if (model.scopes().size() > 1) {
                root.add(Ui.table(bar -> renderScopeSwitcher(bar, model, local))).row();
            }

            root.image("whiteui", l -> l.growX().height(2f).padTop(2f).padBottom(2f).color("3b4252")).row();

            // 3. Dynamic Entries Slot (Scrollable Player Rows)
            root.slot(SLOT_ENTRIES.path(), slot -> {
                slot.layout(l -> l.growX());
                slot.pane(pane -> {
                    pane.layout(l -> l.growX().growY().maxHeight(metrics.maxBodyHeight()));
                    pane.table(list -> renderEntriesList(list, model, metrics, local, currentCatName));
                });
            }).row();

            root.image("whiteui", l -> l.growX().height(2f).padTop(2f).padBottom(2f).color("3b4252")).row();

            // 4. Dynamic Sticky Self-Rank Slot
            root.slot(SLOT_SELF_RANK.path(), slot -> {
                slot.layout(l -> l.growX());
                renderSelfRankCard(slot, model, metrics, local);
            }).row();

            // Feedback Message if any
            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                root.image("whiteui", l -> l.growX().height(2f).padTop(2f).padBottom(2f).color("454545")).row();
                root.add(Ui.table(fb -> {
                    fb.layout(l -> l.growX().padTop(2f).padBottom(2f));
                    fb.label(Text.raw(model.feedbackMessage()), l -> l.align("center").growX());
                })).row();
            }

            root.image("whiteui", l -> l.growX().height(2f).padTop(4f).padBottom(4f).color("3b4252")).row();

            // 5. Dynamic Pagination Toolbar Slot
            root.slot(SLOT_PAGINATION.path(), slot -> {
                slot.layout(l -> l.growX());
                renderPaginationBar(slot, model, local);
            }).row();
        });
    }

    /** "◀ Season 3 ▶": scopes run newest first, so the left arrow steps to an older one. */
    private void renderScopeSwitcher(Ui.TableBuilder bar, TopModel model, Localization local) {
        bar.layout(l -> l.growX().padBottom(2f));

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

        scopeStep(bar, older, Iconc.left);

        TopCategoryProvider provider = provider(model.selectedCategoryId());
        String label = selected.id();
        if (provider != null && local != null) {
            try {
                label = provider.formatScope(selected, local);
            } catch (Exception ignored) {
                // The raw scope id is still a usable label.
            }
        }
        bar.label(Text.raw((selected.current() ? "[accent]" : "[lightgray]") + label + "[]"),
                l -> l.align("center").growX().padLeft(6f).padRight(6f));

        scopeStep(bar, newer, Iconc.right);
    }

    private static void scopeStep(Ui.TableBuilder bar, TopScope target, char glyph) {
        String action = target != null ? "action:scope:" + target.id() : "action:scope:";
        bar.button(Text.raw((target != null ? "[accent]" : "[gray]") + glyph + "[]"), action, b -> {
            if (target == null) b.disabled();
            b.style("cleart").layout(l -> l.size(34f));
        });
    }

    private void renderEntriesList(Ui.TableBuilder list, TopModel model, DialogMetrics metrics, Localization local, String categoryDisplayName) {
        list.layout(l -> l.growX());

        if (model.entries().isEmpty()) {
            list.add(Ui.table(empty -> {
                empty.background("button");
                empty.margin(14f);
                empty.layout(l -> l.growX());
                empty.label(Text.raw("[gray]" + Iconc.info + " " + (local != null ? local.t("top-menu-empty", args("category", categoryDisplayName)) : "No entries found.") + "[]"),
                        l -> l.align("center").growX());
            })).row();
            return;
        }

        for (LeaderboardEntry entry : model.entries()) {
            boolean isViewer = Objects.equals(entry.playerUuid(), model.viewerUuid());
            String clickAction = "action:inspect:" + entry.playerUuid();

            list.buttonTable(clickAction, card -> {
                card.layout(l -> l.growX().padBottom(3f));
                card.margin(8f);
                card.style(isViewer ? "togglet" : "default");
                if (isViewer) card.checked(true);

                card.table(inner -> {
                    inner.layout(l -> l.growX());

                    // Left Column: Stripe, Rank, Badges, Admin, Nickname
                    String stripeColor = switch (entry.rank()) {
                        case 1 -> "ffd700";
                        case 2 -> "c0c0c0";
                        case 3 -> "d99058";
                        default -> isViewer ? "ffd37f" : "3b4252";
                    };

                    inner.add(Ui.table(left -> {
                        left.layout(l -> l.align("left").growX());

                        left.image("whiteui", l -> l.width(4f).growY().padRight(8f).color(stripeColor));

                        String rankBadge = formatRankBadge(entry.rank());
                        left.label(Text.raw(rankBadge + " "), l -> l.align("left").padRight(4f));

                        Map<String, String> attrs = entry.attributes() != null ? entry.attributes() : Map.of();
                        if (attrs.containsKey("activeBadge")) {
                            Badge b = Badge.byId(attrs.get("activeBadge"));
                            if (b != null) {
                                String mode = attrs.getOrDefault("badgeColorMode", "default");
                                String colorHex = attrs.get("playerColorHex");
                                left.label(Text.raw(PlayerSettingsUiController.renderBadgeTagExact(b, mode, colorHex) + " "),
                                        l -> l.align("left").padRight(2f));
                            }
                        }

                        if ("true".equalsIgnoreCase(attrs.get("admin"))) {
                            left.label(Text.raw("[scarlet]<" + Iconc.admin + ">[] "), l -> l.align("left").padRight(2f));
                        }

                        String cleanNick = resolveNickname(entry, attrs, metrics.textBudget());
                        String namePrefix = isViewer ? "[lime]● [accent]" : "";
                        left.label(Text.raw(namePrefix + cleanNick + "[]"), l -> l.align("left"));
                    }));

                    // Right Column: Tags, Value, Chevron
                    inner.add(Ui.table(right -> {
                        right.layout(l -> l.align("right"));

                        Map<String, String> attrs = entry.attributes() != null ? entry.attributes() : Map.of();
                        if (attrs.containsKey("leagueIcon")) {
                            right.label(Text.raw(attrs.get("leagueIcon") + " "), l -> l.align("right").padRight(4f));
                        } else if (attrs.containsKey("leagueName")) {
                            String locLeague = attrs.get("leagueName");
                            try {
                                RatingLeague rl = RatingLeague.valueOf(attrs.get("leagueName").toUpperCase());
                                locLeague = local != null ? local.t(rl.localizationKey()) : rl.name();
                            } catch (Exception ignored) {}
                            right.label(Text.raw("[purple][[" + PlayerSettingsUiController.escapeMarkup(locLeague) + "][] "), l -> l.align("right").padRight(6f));
                        }
                        if (attrs.containsKey("rankName")) {
                            String rankName = attrs.get("rankName");
                            String locRank = local != null ? local.t("hexed-ranks-" + rankName) : rankName;
                            right.label(Text.raw("[purple][[" + PlayerSettingsUiController.escapeMarkup(locRank) + "][] "), l -> l.align("right").padRight(6f));
                        }

                        String valColor = entry.rank() <= 3 ? "[gold]" : "[sky]";
                        String formattedVal = formatValue(model.selectedCategoryId(), entry, local);
                        right.label(Text.raw(valColor + formattedVal + "[] "), l -> l.align("right").padRight(6f));

                        right.label(Text.raw("[gray]" + Iconc.players + "[]"), l -> l.align("right"));
                    }));
                });
            });
            list.row();
        }
    }

    private void renderSelfRankCard(Ui.TableBuilder slot, TopModel model, DialogMetrics metrics, Localization local) {
        slot.add(Ui.table(card -> {
            card.background("button");
            card.margin(8f);
            card.layout(l -> l.growX());

            if (model.selfRank() != null) {
                boolean onPage = model.entries().stream().anyMatch(e -> Objects.equals(e.playerUuid(), model.viewerUuid()));
                String rankStr = "[#ffd37f]#" + model.selfRank() + "[]";
                String text = local != null
                        ? local.t("top-menu-self-rank-line", args("rank", rankStr))
                        : "Your rank: " + rankStr;

                card.label(Text.raw("[lime]●[] " + text), l -> l.align("left").growX());

                if (onPage) {
                    String onPageStr = local != null ? local.t("top-menu-on-this-page") : "on this page";
                    card.label(Text.raw("[gray](" + onPageStr + ")[]"), l -> l.align("right"));
                } else if (model.selfPrimaryValue() != null) {
                    card.label(Text.raw("[gray]|[] [sky]" + model.selfPrimaryValue() + "[]"), l -> l.align("right"));
                }
            } else {
                String unrankedStr = local != null
                        ? local.t("top-menu-unranked")
                        : "You are not ranked in this category yet";
                card.label(Text.raw("[gray]" + Iconc.info + " " + unrankedStr + "[]"), l -> l.align("left").growX());
            }
        }));
    }

    private void renderPaginationBar(Ui.TableBuilder slot, TopModel model, Localization local) {
        slot.add(Ui.table(bar -> {
            bar.layout(l -> l.growX().padTop(2f));

            // Prev Button
            boolean canPrev = !model.cursorBackStack().isEmpty() && model.currentPage() > 1;
            String rawPrev = local != null ? local.t("previous") : null;
            if (rawPrev == null || rawPrev.isBlank()) rawPrev = "« Prev";
            String cleanPrev = Strings.stripColors(rawPrev).replace("«", "").trim();
            String prevLabel = (canPrev ? "[accent]« " : "[gray]« ") + cleanPrev + "[]";
            bar.button(Text.raw(prevLabel), "action:page:prev", b -> {
                if (!canPrev) b.disabled();
                b.style("cleart").layout(l -> l.height(34f).padRight(12f));
            });

            // Page Info
            String pageInfo = "[white]" + model.currentPage() + " / " + model.totalPages() + "[]";
            bar.label(Text.raw(pageInfo), l -> l.align("center").growX().padLeft(6f).padRight(6f));

            // Refresh Button
            bar.button(Text.raw("[sky]" + Iconc.refresh + "[]"), "action:refresh", b -> b
                    .style("cleart")
                    .layout(l -> l.size(34f).padRight(8f)));

            // Next Button
            boolean canNext = model.hasNext() && model.nextCursor() != null;
            String rawNext = local != null ? local.t("next") : null;
            if (rawNext == null || rawNext.isBlank()) rawNext = "Next »";
            String cleanNext = Strings.stripColors(rawNext).replace("»", "").trim();
            String nextLabel = (canNext ? "[accent]" : "[gray]") + cleanNext + " »[]";
            bar.button(Text.raw(nextLabel), "action:page:next", b -> {
                if (!canNext) b.disabled();
                b.style("cleart").layout(l -> l.height(34f).padRight(12f));
            });

            // Close Button
            String rawClose = local != null ? local.t("close") : null;
            if (rawClose == null || rawClose.isBlank()) rawClose = "Close";
            String cleanClose = Strings.stripColors(rawClose).trim();
            bar.button(Text.raw("[scarlet]" + cleanClose + "[]"), "action:close", b -> b
                    .style("cleart")
                    .layout(l -> l.height(34f).padLeft(8f)));
        }));
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
