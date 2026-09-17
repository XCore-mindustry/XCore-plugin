package org.xcore.plugin.ui.menu.map;

import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.map.MapPreviewService;
import org.xcore.plugin.service.map.MapVoteObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

/**
 * Modern reactive controller for Map browsing, inspection, texture streaming,
 * and live RTV voting in Mindustry v160.
 */
public class MapUiController implements UiController<MapUiModel, MapUiEvent> {

    public static final SlotKey<Object> SLOT_MAP_TABLE = SlotKey.of("slot_map_table");
    public static final SlotKey<Object> SLOT_PREVIEW = SlotKey.of("slot_preview");
    public static final SlotKey<Object> SLOT_REPUTATION = SlotKey.of("slot_reputation");
    public static final SlotKey<Object> SLOT_RTV = SlotKey.of("slot_rtv");

    public static final int MAPS_PER_PAGE = 10;

    private final MapService mapService;
    private final MapDataRepository mapDataRepository;
    private final MapPreviewService previewService;
    private final MapVoteObserverService observerService;
    private final Session session;
    private final org.xcore.plugin.service.map.MapContentHashService hashService;
    private final org.xcore.plugin.service.map.MapSummaryCache summaryCache;

    public MapUiController(MapService mapService,
                           MapDataRepository mapDataRepository,
                           MapPreviewService previewService,
                           MapVoteObserverService observerService,
                           Session session) {
        this(mapService, mapDataRepository, previewService, observerService, session, null);
    }

    public MapUiController(MapService mapService,
                           MapDataRepository mapDataRepository,
                           MapPreviewService previewService,
                           MapVoteObserverService observerService,
                           Session session,
                           org.xcore.plugin.service.map.MapContentHashService hashService) {
        this(mapService, mapDataRepository, previewService, observerService, session, hashService,
                mapDataRepository == null ? null : new org.xcore.plugin.service.map.MapSummaryCache(mapDataRepository));
    }

    public MapUiController(MapService mapService, MapDataRepository mapDataRepository,
                           MapPreviewService previewService, MapVoteObserverService observerService,
                           Session session, org.xcore.plugin.service.map.MapContentHashService hashService,
                           org.xcore.plugin.service.map.MapSummaryCache summaryCache) {
        this.summaryCache = summaryCache;
        this.hashService = hashService;
        this.mapService = mapService;
        this.mapDataRepository = mapDataRepository;
        this.previewService = previewService;
        this.observerService = observerService;
        this.session = session;
    }

    @Override
    public MapUiModel initialModel(Object context) {
        throw new UnsupportedOperationException("Open via MapMenu.open* methods with pre-computed initial model");
    }

    @Override
    public UpdateResult<MapUiModel> update(MapUiModel model, MapUiEvent event, ControllerContext ctx) {
        return switch (event) {
            case MapUiEvent.DetailsReady(var mapId, var data) -> {
                if (model.mode() != MapUiModel.ViewMode.DETAILS || !Objects.equals(mapId, model.selectedMapId())) {
                    yield UpdateResult.of(model);
                }
                yield UpdateResult.rerender(loadDetailsModel(model, mapId, data));
            }
            case MapUiEvent.DetailsFailed(var mapId) -> {
                if (Objects.equals(mapId, model.selectedMapId()) && session != null) {
                    session.locale().send("error-map-not-found");
                }
                yield UpdateResult.of(model);
            }
            case MapUiEvent.SummariesReady() -> {
                cachedMapSummaries = null;
                yield model.mode() == MapUiModel.ViewMode.BROWSER
                        ? UpdateResult.patch(filterAndPaginate(model, model.page()), SLOT_MAP_TABLE)
                        : UpdateResult.of(model);
            }
            // --- Browser Search & Pagination ---
            case MapUiEvent.SearchChanged(var query) -> {
                MapUiModel updated = filterAndPaginate(model.withSearchQuery(query), 1);
                yield UpdateResult.patch(updated, SLOT_MAP_TABLE);
            }
            case MapUiEvent.NextPage() -> {
                MapUiModel updated = filterAndPaginate(model, model.page() + 1);
                yield UpdateResult.patch(updated, SLOT_MAP_TABLE);
            }
            case MapUiEvent.PrevPage() -> {
                MapUiModel updated = filterAndPaginate(model, model.page() - 1);
                yield UpdateResult.patch(updated, SLOT_MAP_TABLE);
            }
            case MapUiEvent.ChangePage(int newPage) -> {
                MapUiModel updated = filterAndPaginate(model, newPage);
                yield UpdateResult.patch(updated, SLOT_MAP_TABLE);
            }

            // --- Navigation: Browser -> Details ---
            case MapUiEvent.OpenMapDetails(var mapId) -> {
                MapUiModel details = loadDetailsModel(model, mapId);
                if (ctx != null) ctx.post(() -> requestDetailsAsync(mapId));
                else requestDetailsAsync(mapId);
                String selectedId = details.selectedMapId();
                if (observerService != null) {
                    observerService.registerViewing(model.playerUuid(), selectedId);
                }
                requestPreviewAsync(session, selectedId);
                yield UpdateResult.rerender(details);
            }

            // --- Navigation: Details -> Browser ---
            case MapUiEvent.BackToBrowser() -> {
                if (observerService != null) {
                    observerService.unregisterViewing(model.playerUuid());
                }
                MapUiModel browser = filterAndPaginate(model.withMode(MapUiModel.ViewMode.BROWSER), model.page());
                yield UpdateResult.rerender(browser);
            }

            // --- Async Preview Callbacks ---
            case MapUiEvent.PreviewReady(var mapId, var region) -> {
                if (!isSameMap(mapId, model.selectedMapId())) yield UpdateResult.of(model);
                yield UpdateResult.patch(model.withPreview(region, false), SLOT_PREVIEW);
            }
            case MapUiEvent.PreviewFailed(var mapId) -> {
                if (!isSameMap(mapId, model.selectedMapId())) yield UpdateResult.of(model);
                yield UpdateResult.patch(model.withPreview(null, false), SLOT_PREVIEW);
            }

            // --- Optimistic Reputation (Like / Dislike) ---
            case MapUiEvent.ToggleReputation(boolean like) -> {
                Boolean current = model.playerVote();
                boolean isRevoking = (current != null && current == like);
                Boolean next = isRevoking ? null : like;

                int likeDelta = 0;
                int dislikeDelta = 0;
                if (current == null) {
                    if (like) likeDelta = 1; else dislikeDelta = 1;
                } else if (isRevoking) {
                    if (like) likeDelta = -1; else dislikeDelta = -1;
                } else {
                    if (like) { likeDelta = 1; dislikeDelta = -1; }
                    else { likeDelta = -1; dislikeDelta = 1; }
                }

                int newLikes = Math.max(0, model.likes() + likeDelta);
                int newDislikes = Math.max(0, model.dislikes() + dislikeDelta);
                int newRep = model.reputation() + (likeDelta - dislikeDelta);
                int newApproval = (newLikes + newDislikes == 0) ? 0 : (int) Math.round((double) newLikes * 100 / (newLikes + newDislikes));

                MapUiModel updated = model.withReputation(next, newRep, newLikes, newDislikes, newApproval);

                // Async persistence to MongoDB and session data
                MapData mapData = model.resolvedDetails();
                if (mapData != null && session != null && session.player != null && mapService != null) {
                    mapService.handleReputation(session.player, like, mapData);
                }

                cachedMapSummaries = null;

                yield UpdateResult.patch(updated, SLOT_REPUTATION);
            }

            // --- Live RTV Vote Observer Callbacks ---
            case MapUiEvent.RtvVoteUpdated(var mapId, int votes, int req, int remainingSec) -> {
                if (!isSameMap(mapId, model.selectedMapId())) yield UpdateResult.of(model);
                yield UpdateResult.patch(model.withRtvStatus(true, votes, req, remainingSec), SLOT_RTV);
            }
            case MapUiEvent.RtvVoteEnded() ->
                UpdateResult.patch(model.withRtvStatus(false, 0, 0, 0), SLOT_RTV);

            // --- Player Trigger RTV ---
            case MapUiEvent.TriggerRtv() -> {
                Map mindustryMap = findMindustryMap(model.selectedMapId(), model.resolvedDetails());
                if (mindustryMap != null && session != null && session.player != null && mapService != null) {
                    mapService.startRtvSession(session.player, mindustryMap, true, false);
                }
                yield UpdateResult.patch(model.withRtvStatus(true, 1, 1, 30), SLOT_RTV);
            }

            // --- Admin Force RTV (2-Click Confirmed) ---
            case MapUiEvent.AdminForceRtvClick() -> {
                if (!model.isAdmin()) yield UpdateResult.of(model);
                long now = System.currentTimeMillis();
                if (model.adminForceConfirming() && now < model.adminConfirmExpireMillis()) {
                    Map mindustryMap = findMindustryMap(model.selectedMapId(), model.resolvedDetails());
                    if (mindustryMap != null && session != null && session.player != null && mapService != null) {
                        mapService.startRtvSession(session.player, mindustryMap, true, true);
                    }
                    if (ctx != null) ctx.close();
                    yield UpdateResult.close(model);
                } else {
                    MapUiModel confirming = model.withAdminConfirm(true, now + 3000L);
                    yield UpdateResult.patch(confirming, SLOT_RTV);
                }
            }
            case MapUiEvent.AdminCancelForceRtv() ->
                UpdateResult.patch(model.withAdminConfirm(false, 0L), SLOT_RTV);

            // --- Close ---
            case MapUiEvent.Close() -> {
                if (observerService != null) {
                    observerService.unregisterViewing(model.playerUuid());
                }
                if (ctx != null) ctx.close();
                yield UpdateResult.close(model);
            }
        };
    }

    @Override
    public VNode render(MapUiModel model) {
        return Ui.table(root -> {
            root.background("pane");
            root.margin(14f);
            root.layout(l -> l.width(520f).pad(6f));

            if (model.mode() == MapUiModel.ViewMode.BROWSER) {
                renderBrowser(root, model);
            } else {
                renderDetails(root, model);
            }
        });
    }

    // ==================================================================
    // View 1: Browser View
    // ==================================================================

    private void renderBrowser(Ui.TableBuilder root, MapUiModel model) {
        // 1. Header with Title and 3px Accent underline
        root.add(Ui.table(h -> {
            h.layout(l -> l.width(520f).padBottom(4f));
            h.label(Text.t("commands-maps-title"), l -> l.align("center").growX());
            String totalText = "[gray] (" + model.totalMapsCount() + ")[]";
            h.label(Text.raw(totalText), l -> l.align("center").padLeft(4f));
        })).row();
        root.image("whiteui", l -> l.width(520f).height(3f).padBottom(10f).color("ffd37f")).row();

        // 2. Search Field
        root.add(Ui.table(s -> {
            s.layout(l -> l.width(520f).padBottom(8f));
            s.field("field_search", f -> f
                    .value(model.searchQuery())
                    .hint(Text.t("map-ui-search-hint"))
                    .enter("action:search")
                    .layout(l -> l.growX()));
        })).row();

        root.image("whiteui", l -> l.width(520f).height(2f).padBottom(8f).color("454545")).row();

        // 3. Tabular Map Table Slot
        root.slot(SLOT_MAP_TABLE.path(), listSlot -> {
            listSlot.layout(l -> l.width(520f));

            if (model.displayedMaps().isEmpty()) {
                listSlot.add(Ui.table(empty -> {
                    empty.layout(l -> l.growX().height(180f));
                    empty.label(Text.t("map-ui-no-maps-found"), l -> l.align("center"));
                })).row();
            } else {
                for (MapUiModel.MapSummary map : model.displayedMaps()) {
                    listSlot.add(Ui.table(row -> {
                        row.layout(l -> l.growX().height(36f).padBottom(2f));
                        row.button(buildMapRowText(map), "action:select_map:" + map.id(), b -> b
                                .style("cleart")
                                .layout(l -> l.growX().height(34f)));
                    })).row();
                }
            }

            // Pagination Row
            listSlot.image("whiteui", l -> l.growX().height(2f).padTop(6f).padBottom(8f).color("454545")).row();
            listSlot.add(Ui.table(pag -> {
                pag.layout(l -> l.growX().height(32f));
                pag.button(Text.t("map-ui-prev"), "action:page:prev", b -> {
                    b.style("cleart");
                    if (model.page() <= 1) b.disabled();
                    b.layout(l -> l.width(80f).height(28f));
                });

                pag.label(Text.t("map-ui-page-info", args("page", model.page(), "total", model.totalPages())),
                        l -> l.growX().align("center"));

                pag.button(Text.t("map-ui-next"), "action:page:next", b -> {
                    b.style("cleart");
                    if (model.page() >= model.totalPages()) b.disabled();
                    b.layout(l -> l.width(80f).height(28f));
                });
            })).row();
        }).row();

        // 4. Footer Close
        root.image("whiteui", l -> l.width(520f).height(2f).padTop(6f).padBottom(6f).color("454545")).row();
        root.button(Text.join(Text.raw("[accent]"), Text.t("close")), "action:close", b -> b
                .style("cleart")
                .layout(l -> l.width(520f).fillX().height(38f)));
    }

    private Text buildMapRowText(MapUiModel.MapSummary map) {
        String prefix = map.isCurrent() ? "[gold]* [accent]" : "[white]";
        String rep = (map.likes() > 0 || map.dislikes() > 0)
                ? "  [green]+" + map.likes() + "[] [scarlet]-" + map.dislikes() + "[]"
                : "";
        return Text.join(
                Text.raw(prefix + map.name() + "[] [gray]"),
                Text.join(
                        Text.t("map-ui-by", args("author", map.author())),
                        Text.raw(" [darkgray]| [gray]" + map.width() + "x" + map.height() + "[]" + rep)
                )
        );
    }

    // ==================================================================
    // View 2: Details View
    // ==================================================================

    private void renderDetails(Ui.TableBuilder root, MapUiModel model) {
        // 1. Header with Map Name and Accent line
        root.add(Ui.table(h -> {
            h.layout(l -> l.width(520f).padBottom(4f));
            h.label(Text.raw("[accent]" + model.mapName() + "[]"), l -> l.align("center").growX()).row();
            h.label(Text.join(
                    Text.t("map-ui-by", args("author", model.mapAuthor())),
                    Text.join(
                            Text.raw(" [darkgray]| [gray]"),
                            Text.t("map-ui-mode", args("mode", model.gamemodeName()))
                    )
            ), l -> l.align("center").growX());
        })).row();
        root.image("whiteui", l -> l.width(520f).height(3f).padBottom(10f).color("ffd37f")).row();

        // 2. 2-Column Hero: 150x150 Preview (SLOT_PREVIEW) + Metadata
        root.add(Ui.table(hero -> {
            hero.layout(l -> l.width(520f).padBottom(8f));

            // Left Column: SLOT_PREVIEW
            hero.slot(SLOT_PREVIEW.path(), pSlot -> {
                pSlot.layout(l -> l.size(150f).padRight(14f));
                if (model.previewTextureRegion() != null) {
                    pSlot.image(model.previewTextureRegion(), l -> l.size(150f));
                } else if (model.previewLoading()) {
                    pSlot.add(Ui.table(loading -> {
                        loading.background("button");
                        loading.layout(l -> l.size(150f));
                        loading.label(Text.t("map-ui-loading"), l -> l.align("center"));
                    }));
                } else {
                    pSlot.add(Ui.table(err -> {
                        err.background("button");
                        err.layout(l -> l.size(150f));
                        err.label(Text.t("map-ui-no-preview"), l -> l.align("center"));
                    }));
                }
            });

            // Right Column: Identity metadata
            hero.add(Ui.table(meta -> {
                meta.layout(l -> l.growX().align("left"));
                meta.label(Text.t("map-ui-dimensions", args("width", model.width(), "height", model.height())), l -> l.align("left").padBottom(2f)).row();
                meta.label(Text.t("map-ui-total-plays", args("played", model.playedTimes(), "playedYear", model.playedTimesYear())), l -> l.align("left").padBottom(2f)).row();
                meta.label(Text.t("map-ui-last-played", args("lastPlayed", model.lastPlayedFormatted())), l -> l.align("left").padBottom(2f)).row();
                if (model.mapDescription() == null || model.mapDescription().isBlank()) {
                    meta.label(Text.t("map-ui-no-description"), l -> l.align("left").growX().padTop(2f));
                } else {
                    meta.label(Text.t("map-ui-description", args("description", model.mapDescription())), l -> l.align("left").growX().padTop(2f));
                }
            }));
        })).row();

        root.image("whiteui", l -> l.width(520f).height(2f).padBottom(8f).color("454545")).row();

        // 3. 3-Column Telemetry Matrix
        root.add(Ui.table(matrix -> {
            matrix.layout(l -> l.width(520f).padBottom(8f));

            // Col 1: Duration
            matrix.add(Ui.table(c1 -> {
                c1.layout(l -> l.uniform().growX().align("left"));
                c1.label(Text.t("map-ui-col-duration"), l -> l.padBottom(2f)).row();
                c1.label(Text.t("map-ui-duration-min", args("value", model.minGameTime()))).row();
                c1.label(Text.t("map-ui-duration-avg", args("value", model.avgGameTime()))).row();
                c1.label(Text.t("map-ui-duration-max", args("value", model.maxGameTime())));
            }));

            // Col 2: Popularity
            matrix.add(Ui.table(c2 -> {
                c2.layout(l -> l.uniform().growX().align("left"));
                c2.label(Text.t("map-ui-col-popularity"), l -> l.padBottom(2f)).row();
                c2.label(Text.t("map-ui-popularity-score", args("value", model.reputation()))).row();
                c2.label(Text.t("map-ui-popularity-pop", args("value", String.format("%.1f", model.popularity())))).row();
                c2.label(Text.t("map-ui-popularity-interest", args("value", String.format("%.1f", model.interest()))));
            }));

            // Col 3: Community
            matrix.add(Ui.table(c3 -> {
                c3.layout(l -> l.uniform().growX().align("left"));
                c3.label(Text.t("map-ui-col-community"), l -> l.padBottom(2f)).row();
                c3.label(Text.t("map-ui-community-approval", args("rate", model.approvalRatePercent()))).row();
                c3.label(Text.t("map-ui-community-likes", args("value", model.likes()))).row();
                c3.label(Text.t("map-ui-community-dislikes", args("value", model.dislikes())));
            }));
        })).row();

        root.image("whiteui", l -> l.width(520f).height(2f).padBottom(8f).color("454545")).row();

        // 4. Interactive Reputation Slot (SLOT_REPUTATION)
        root.slot(SLOT_REPUTATION.path(), rSlot -> {
            rSlot.layout(l -> l.width(520f).padBottom(8f));
            rSlot.add(Ui.table(votes -> {
                votes.layout(l -> l.growX());
                boolean isLiked = Boolean.TRUE.equals(model.playerVote());
                boolean isDisliked = Boolean.FALSE.equals(model.playerVote());

                Text likeText = isLiked
                        ? Text.t("map-ui-btn-liked", args("count", model.likes()))
                        : Text.t("map-ui-btn-like", args("count", model.likes()));
                Text dislikeText = isDisliked
                        ? Text.t("map-ui-btn-disliked", args("count", model.dislikes()))
                        : Text.t("map-ui-btn-dislike", args("count", model.dislikes()));

                votes.button(likeText, "action:like", b -> b
                        .style("cleart")
                        .layout(l -> l.uniform().growX().height(32f).padRight(6f)));

                votes.button(dislikeText, "action:dislike", b -> b
                        .style("cleart")
                        .layout(l -> l.uniform().growX().height(32f)));
            }));
        }).row();

        root.image("whiteui", l -> l.width(520f).height(2f).padBottom(8f).color("454545")).row();

        // 5. Live RTV Slot (SLOT_RTV)
        root.slot(SLOT_RTV.path(), rtvSlot -> {
            rtvSlot.layout(l -> l.width(520f).padBottom(8f));

            if (model.rtvActive()) {
                rtvSlot.label(Text.t("map-ui-rtv-active-status", args(
                        "votes", model.rtvVotes(),
                        "required", model.rtvVotesRequired(),
                        "seconds", model.rtvRemainingSeconds()
                )), l -> l.align("center").padBottom(4f)).row();
                rtvSlot.button(Text.t("map-ui-rtv-vote-yes"), "action:rtv", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().fillX().height(38f))).row();
            } else {
                rtvSlot.button(Text.t("map-ui-rtv-start"), "action:rtv", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().fillX().height(40f))).row();
            }

            // Admin Force RTV button (2-click confirmed)
            if (model.isAdmin()) {
                Text adminText = model.adminForceConfirming()
                        ? Text.t("map-ui-admin-rtv-confirm")
                        : Text.t("map-ui-admin-rtv");
                rtvSlot.button(adminText, "action:admin_rtv", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().fillX().height(28f).padTop(4f)));
            }
        }).row();

        // 6. Footer Navigation
        root.image("whiteui", l -> l.width(520f).height(2f).padBottom(6f).color("454545")).row();
        root.button(Text.join(Text.raw("[accent]"), Text.t("map-maps-back")), "action:back_to_list", b -> b
                .style("cleart")
                .layout(l -> l.width(520f).fillX().height(38f)));
    }

    // ==================================================================
    // Event Parsing
    // ==================================================================

    @Override
    public MapUiEvent parseEvent(MenuResult result) {
        if (result == null || result.result == null) return new MapUiEvent.Close();

        String res = result.result;
        if ("action:search".equals(res)) {
            String q = result.values != null && result.values.get("field_search") instanceof String s ? s : "";
            return new MapUiEvent.SearchChanged(q);
        }
        if ("action:page:prev".equals(res)) return new MapUiEvent.PrevPage();
        if ("action:page:next".equals(res)) return new MapUiEvent.NextPage();

        if (res.startsWith("action:select_map:")) {
            return new MapUiEvent.OpenMapDetails(res.substring("action:select_map:".length()));
        }
        if ("action:back_to_list".equals(res)) return new MapUiEvent.BackToBrowser();
        if ("action:like".equals(res)) return new MapUiEvent.ToggleReputation(true);
        if ("action:dislike".equals(res)) return new MapUiEvent.ToggleReputation(false);
        if ("action:rtv".equals(res)) return new MapUiEvent.TriggerRtv();
        if ("action:admin_rtv".equals(res)) return new MapUiEvent.AdminForceRtvClick();
        if ("action:admin_rtv_cancel".equals(res)) return new MapUiEvent.AdminCancelForceRtv();
        if ("action:close".equals(res)) return new MapUiEvent.Close();

        return null;
    }

    // ==================================================================
    // State Builders & Helpers
    // ==================================================================

    /**
     * Pure function mapping state and event to side-effect commands (MVI architecture).
     */
    public static List<MapUiCmd> evaluateCommands(MapUiState state, MapUiEvent event, long sessionToken) {
        return switch (event) {
            case MapUiEvent.SearchChanged(var query) -> List.of(new MapUiCmd.LoadAllSummaries(query, 1));
            case MapUiEvent.ChangePage(int newPage) -> {
                String q = (state instanceof MapUiState.Browser b) ? b.searchQuery() : "";
                yield List.of(new MapUiCmd.LoadAllSummaries(q, newPage));
            }
            case MapUiEvent.NextPage() -> {
                if (state instanceof MapUiState.Browser b) {
                    yield List.of(new MapUiCmd.LoadAllSummaries(b.searchQuery(), b.page() + 1));
                }
                yield List.of();
            }
            case MapUiEvent.PrevPage() -> {
                if (state instanceof MapUiState.Browser b) {
                    yield List.of(new MapUiCmd.LoadAllSummaries(b.searchQuery(), b.page() - 1));
                }
                yield List.of();
            }
            case MapUiEvent.OpenMapDetails(var mapId) -> List.of(
                    new MapUiCmd.LoadMapDetails(mapId),
                    new MapUiCmd.RequestPreview(mapId),
                    new MapUiCmd.SubscribeRtv(mapId, sessionToken)
            );
            case MapUiEvent.BackToBrowser() -> List.of(new MapUiCmd.UnsubscribeRtv(sessionToken));
            case MapUiEvent.ToggleReputation(boolean like) -> {
                String mapId = (state instanceof MapUiState.Details d) ? d.mapId() : "";
                Boolean currentVote = (state instanceof MapUiState.Details d && d.reputation() != null)
                        ? d.reputation().playerVote() : null;
                boolean isRevoking = (currentVote != null && currentVote == like);
                yield List.of(new MapUiCmd.PersistReputationVote(mapId, like, isRevoking));
            }
            case MapUiEvent.TriggerRtv() -> {
                String mapId = (state instanceof MapUiState.Details d) ? d.mapId() : "";
                yield List.of(new MapUiCmd.TriggerRtv(mapId, false));
            }
            case MapUiEvent.AdminForceRtvClick() -> {
                String mapId = (state instanceof MapUiState.Details d) ? d.mapId() : "";
                yield List.of(new MapUiCmd.TriggerRtv(mapId, true));
            }
            case MapUiEvent.Close() -> List.of(
                    new MapUiCmd.UnsubscribeRtv(sessionToken),
                    new MapUiCmd.CloseSession()
            );
            default -> List.of();
        };
    }

    public MapUiModel createInitialBrowserModel(Session session, int initialPage) {
        String uuid = session.player != null ? session.player.uuid() : "";
        boolean admin = session.player != null && session.player.admin;

        MapUiModel base = new MapUiModel(
                MapUiModel.ViewMode.BROWSER,
                uuid,
                admin,
                "",
                initialPage,
                1,
                List.of(),
                0,
                "", "", "", "", 0, 0, "", false,
                0, 0, "", "", "", "",
                0, 0.0, 0.0, 0, 0, 0, null,
                false, null,
                false, 0, 0, 0,
                false, 0L
        );
        return filterAndPaginate(base, initialPage);
    }

    public MapUiModel createInitialDetailsModel(Session session, MapData mapData) {
        String uuid = session != null && session.player != null ? session.player.uuid() : "";
        boolean admin = session != null && session.player != null && session.player.admin;

        Map mindustryMap = mapData != null && mapService != null ? mapService.findPersistedMap(mapData) : null;
        String mapId = mindustryMap != null
                ? (mindustryMap.file != null ? mindustryMap.file.name() : mindustryMap.plainName())
                : (mapData != null && mapData.id != null ? mapData.id.toHexString() : "");

        MapUiModel base = new MapUiModel(
                MapUiModel.ViewMode.DETAILS,
                uuid,
                admin,
                "",
                1,
                1,
                List.of(),
                0,
                mapId,
                "", "", "", 0, 0, "", false,
                0, 0, "", "", "", "",
                0, 0.0, 0.0, 0, 0, 0, null,
                false, null,
                false, 0, 0, 0,
                false, 0L,
                mapData
        );
        return loadDetailsModel(base, mapId, mapData);
    }

    public MapUiModel filterAndPaginate(MapUiModel model, int targetPage) {
        List<MapUiModel.MapSummary> all = loadAllMapSummaries();
        String q = model.searchQuery() == null ? "" : model.searchQuery().trim().toLowerCase();

        List<MapUiModel.MapSummary> filtered = all.stream()
                .filter(m -> q.isEmpty()
                        || m.name().toLowerCase().contains(q)
                        || m.author().toLowerCase().contains(q))
                .toList();

        int pageSize = mapsPerPage();
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / pageSize));
        int page = Math.clamp(targetPage, 1, totalPages);

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());
        List<MapUiModel.MapSummary> pageItems = fromIndex < filtered.size()
                ? filtered.subList(fromIndex, toIndex)
                : List.of();

        return model.withPagination(page, totalPages, pageItems, all.size());
    }

    /** Called after UiSession installation; late results never enter another dialog. */
    public void requestSummariesAsync() {
        if (summaryCache == null || session == null || session.activeUiSession() == null) return;
        var target = session.activeUiSession();
        summaryCache.refresh().whenComplete((rows, error) ->
                org.xcore.plugin.concurrent.MainThreadDispatcher.mindustry().execute(() -> {
                    if (session.activeUiSession() != target) return;
                    if (error != null) {
                        org.xcore.plugin.common.PLog.warn("Map summaries refresh failed: @", error.toString());
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    var typed = (org.xcore.ui.runtime.UiSession<MapUiModel, MapUiEvent>) target;
                    typed.dispatch(new MapUiEvent.SummariesReady());
                }));
    }

    public int mapsPerPage() {
        if (session != null && session.secretsConfig != null && session.secretsConfig.pagination != null) {
            return Math.max(1, session.secretsConfig.pagination.mapsPerPage);
        }
        return MAPS_PER_PAGE;
    }

    private List<MapUiModel.MapSummary> cachedMapSummaries;

    private List<MapUiModel.MapSummary> loadAllMapSummaries() {
        if (cachedMapSummaries != null) {
            return cachedMapSummaries;
        }

        List<MapUiModel.MapSummary> list = new ArrayList<>();
        if (mapService == null) return list;
        var available = mapService.getAvailableMaps();
        if (available == null) return list;
        String currentMapFile = state.map != null && state.map.file != null ? state.map.file.name() : "";

        // Single batch fetch of all map stats from repository
        var allData = summaryCache != null ? summaryCache.snapshot() : List.<org.xcore.plugin.service.map.MapSummaryCache.Summary>of();

        for (Map m : available) {
            String id = m.file != null ? m.file.name() : m.plainName();
            String mode = state.rules != null ? state.rules.mode().name() : "survival";
            var matches = allData.stream().filter(row -> mode.equals(row.mode())
                    && row.fileName() != null && id.equalsIgnoreCase(row.fileName())).toList();
            if (matches.isEmpty()) {
                matches = allData.stream().filter(row -> mode.equals(row.mode())
                        && m.plainName().equalsIgnoreCase(arc.util.Strings.stripColors(row.name() == null ? "" : row.name()))
                        && m.plainAuthor().equalsIgnoreCase(arc.util.Strings.stripColors(row.author() == null ? "" : row.author()))).toList();
            }
            var data = matches.size() == 1 ? matches.getFirst() : null;
            boolean isCur = m.file != null && m.file.name().equalsIgnoreCase(currentMapFile);
            int likes = data != null ? data.likes() : 0;
            int dislikes = data != null ? data.dislikes() : 0;
            list.add(new MapUiModel.MapSummary(
                    id,
                    m.plainName(),
                    m.author(),
                    m.width,
                    m.height,
                    likes,
                    dislikes,
                    isCur
            ));
        }
        this.cachedMapSummaries = list;
        return list;
    }

    private MapUiModel loadDetailsModel(MapUiModel current, String mapId) {
        return loadDetailsModel(current, mapId, null);
    }

    private MapUiModel loadDetailsModel(MapUiModel current, String mapId, MapData preloadedData) {
        MapData data = preloadedData != null ? preloadedData : current.resolvedDetails();
        Map mindustryMap = findMindustryMap(mapId, data);
        if (mindustryMap == null && data != null && mapService != null) {
            mindustryMap = mapService.findPersistedMap(data);
        }

        String name = data != null ? data.name : (mindustryMap != null ? mindustryMap.plainName() : "Unknown");
        String author = data != null ? data.author : (mindustryMap != null ? mindustryMap.author() : "Unknown");
        String desc = mindustryMap != null ? mindustryMap.description() : "";
        int w = mindustryMap != null ? mindustryMap.width : 0;
        int h = mindustryMap != null ? mindustryMap.height : 0;
        String rawMode = (state.rules != null && state.rules.mode() != null)
                ? state.rules.mode().name()
                : (data != null && data.gameMode != null && !data.gameMode.isBlank() ? data.gameMode : "survival");
        String mode = rawMode;
        if (session != null && session.locale() != null) {
            String modeKey = "gamemode-" + rawMode.toLowerCase();
            String localizedMode = session.locale().t(modeKey);
            if (localizedMode != null && !localizedMode.equals(modeKey)) {
                mode = localizedMode;
            }
        }
        boolean isCur = mindustryMap != null && state.map != null && Objects.equals(mindustryMap.file, state.map.file);

        long plays = data != null ? data.playedTimes : 0L;
        long playsYear = data != null ? data.playedTimesYear : 0L;
        String last = (data == null || data.playedTimes == 0)
                ? (session != null && session.locale() != null ? session.locale().t("never") : "Never")
                : formatDuration((int) ((System.currentTimeMillis() - data.lastPlayedTime) / 60000));

        String minTime = data != null ? formatDuration((int) (data.minimumGameTime / 60000)) : "-";
        String avgTime = data != null ? formatDuration((int) (data.averageGameTime / 60000)) : "-";
        String maxTime = data != null ? formatDuration((int) (data.maximumGameTime / 60000)) : "-";

        int rep = data != null ? data.reputation : 0;
        double pop = data != null ? data.popularity : 0.0;
        double interest = data != null ? data.interest : 0.0;
        int likes = data != null ? data.like : 0;
        int dislikes = data != null ? data.dislike : 0;
        int approval = (likes + dislikes == 0) ? 0 : (int) Math.round((double) likes * 100 / (likes + dislikes));

        Boolean vote = (session != null && session.data != null && session.data.mapVotes != null && data != null && data.id != null)
                ? session.data.mapVotes.get(data.id.toString()) : null;

        String cachedRegion = previewService != null ? previewService.getCachedRegionName(mindustryMap) : null;
        boolean loading = (cachedRegion == null && mindustryMap != null);

        String canonicalMapId = mindustryMap != null
                ? (mindustryMap.file != null ? mindustryMap.file.name() : mindustryMap.plainName())
                : (data != null && data.id != null ? data.id.toHexString() : mapId);

        return new MapUiModel(
                MapUiModel.ViewMode.DETAILS,
                current.playerUuid(),
                current.isAdmin(),
                current.searchQuery(),
                current.page(),
                current.totalPages(),
                current.displayedMaps(),
                current.totalMapsCount(),
                canonicalMapId,
                name,
                author,
                desc,
                w, h,
                mode,
                isCur,
                plays,
                playsYear,
                last,
                minTime,
                avgTime,
                maxTime,
                rep,
                pop,
                interest,
                likes,
                dislikes,
                approval,
                vote,
                loading,
                cachedRegion,
                false, 0, 0, 0,
                false, 0L,
                data
        );
    }

    public void requestPreviewAsync(Session session, String mapId) {
        if (session == null || session.player == null || previewService == null) return;
        Map mindustryMap = findMindustryMap(mapId);
        if (mindustryMap == null) return;

        var target = session.activeUiSession();
        java.util.function.BiConsumer<String, Throwable> deliver = (region, error) ->
                org.xcore.plugin.concurrent.MainThreadDispatcher.mindustry().execute(() -> {
                    if (target == null || session.activeUiSession() != target) return;
                    @SuppressWarnings("unchecked")
                    var typed = (org.xcore.ui.runtime.UiSession<?, MapUiEvent>) target;
                    typed.dispatch(region != null ? new MapUiEvent.PreviewReady(mapId, region)
                            : new MapUiEvent.PreviewFailed(mapId));
                });
        String cached = previewService.getCachedRegionName(mindustryMap);
        if (cached != null) {
            deliver.accept(cached, null);
        } else {
            previewService.requestPreview(session.player, mindustryMap, deliver);
        }
    }

    private boolean isSameMap(String id1, String id2) {
        if (Objects.equals(id1, id2)) return true;
        if (id1 == null || id2 == null) return false;
        Map m1 = findMindustryMap(id1);
        Map m2 = findMindustryMap(id2);
        if (m1 != null && m2 != null) {
            return Objects.equals(m1.file, m2.file) || m1.plainName().equalsIgnoreCase(m2.plainName());
        }
        return false;
    }

    private void requestDetailsAsync(String mapId) {
        var target = session == null ? null : session.activeUiSession();
        resolveMapData(mapId).whenComplete((data, error) ->
                org.xcore.plugin.concurrent.MainThreadDispatcher.mindustry().execute(() -> {
                    if (target == null || session.activeUiSession() != target) return;
                    @SuppressWarnings("unchecked")
                    var typed = (org.xcore.ui.runtime.UiSession<MapUiModel, MapUiEvent>) target;
                    typed.dispatch(error == null && data != null
                            ? new MapUiEvent.DetailsReady(mapId, data) : new MapUiEvent.DetailsFailed(mapId));
                }));
    }

    private java.util.concurrent.CompletionStage<MapData> resolveMapData(String mapId) {
        if (mapId == null || mapId.isBlank() || mapDataRepository == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (org.bson.types.ObjectId.isValid(mapId)) {
            return mapDataRepository.findByIdAsync(new org.bson.types.ObjectId(mapId));
        }

        Map mindustryMap = findMindustryMap(mapId);
        String mode = (state.rules != null && state.rules.mode() != null)
                ? state.rules.mode().name()
                : "survival";

        if (mindustryMap != null) {
            String fileName = mindustryMap.file != null ? mindustryMap.file.name() : mapId;
            var file = mindustryMap.file;
            return mapDataRepository.findExistingAsync(mindustryMap.plainName(), fileName, mindustryMap.author(), mode)
                    .thenApply(data -> {
                        if (hashService != null && data != null && data.id != null
                                && data.contentHash == null && file != null
                                && fileName.equals(data.fileName) && !mapDataRepository.isReadOnly()) {
                            var id = data.id;
                            hashService.hashAsync(file::read)
                                    .thenCompose(hash -> mapDataRepository.updateMapContentHashAsync(id, fileName, hash))
                                    .whenComplete((updated, error) -> {
                                        if (error != null) org.xcore.plugin.common.PLog.warn("Map hash failed for @: @", fileName, error.toString());
                                    });
                        }
                        return data;
                    });
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    private Map findMindustryMap(String mapId) {
        return findMindustryMap(mapId, null);
    }

    private Map findMindustryMap(String mapId, MapData details) {
        if (mapId == null || mapId.isBlank() || mapService == null) return null;
        Map byFile = mapService.findMapByFileName(mapId);
        if (byFile != null) return byFile;
        Map byName = mapService.findMap(mapId);
        if (byName != null) return byName;
        if (details != null && details.id != null && mapId.equals(details.id.toHexString())) {
            return mapService.findPersistedMap(details);
        }
        return null;
    }

    private String formatDuration(int minutes) {
        if (minutes < 0) return "-";
        if (session != null && session.locale() != null) {
            if (minutes <= 0) {
                return session.locale().t("player-menu-time-minutes", args("value", 0));
            }
            int days = minutes / (60 * 24);
            int hours = (minutes / 60) % 24;
            int mins = minutes % 60;
            StringBuilder result = new StringBuilder();
            if (days > 0) {
                result.append(session.locale().t("player-menu-time-days", args("value", days))).append(" ");
            }
            if (hours > 0) {
                result.append(session.locale().t("player-menu-time-hours", args("value", hours))).append(" ");
            }
            if (mins > 0 || result.isEmpty()) {
                result.append(session.locale().t("player-menu-time-minutes", args("value", mins)));
            }
            return result.toString().trim();
        }
        if (minutes <= 0) return "< 1m";
        if (minutes < 60) return minutes + "m";
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
