package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.service.moderation.DefaultAuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.ospx.flubundle.Bundle.args;

/**
 * Reactive Elm/MVI controller for Mindustry server-side Audit History inspection (/audit).
 * Replaces legacy AuditHistoryMenu and AuditHistoryFlows with responsive mobile/desktop cards,
 * in-dialog record inspection, and seamless session stack navigation.
 */
public class AuditHistoryUiController implements UiController<AuditHistoryUiController.AuditHistoryModel, AuditHistoryUiController.AuditHistoryEvent> {

    public static final SlotKey<Object> SLOT_HEADER     = SlotKey.of("slot_audit_header");
    public static final SlotKey<Object> SLOT_TABS       = SlotKey.of("slot_audit_tabs");
    public static final SlotKey<Object> SLOT_LIST       = SlotKey.of("slot_audit_list");
    public static final SlotKey<Object> SLOT_DETAILS    = SlotKey.of("slot_audit_details");
    public static final SlotKey<Object> SLOT_PAGINATION = SlotKey.of("slot_audit_pagination");

    public static final int RECORDS_PER_PAGE = 8;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    public enum AuditViewMode {
        TARGET,
        ACTOR
    }

    public enum ActionFilter {
        ALL(null),
        BANS(Set.of(AuditAction.BAN, AuditAction.UNBAN)),
        MUTES(Set.of(AuditAction.MUTE, AuditAction.UNMUTE)),
        WARNS(Set.of(AuditAction.WARN, AuditAction.KICK)),
        OTHER(Set.of(AuditAction.NOTE, AuditAction.QUARANTINE, AuditAction.UNQUARANTINE, AuditAction.MERGE));

        private final Set<AuditAction> actions;

        ActionFilter(Set<AuditAction> actions) {
            this.actions = actions;
        }

        public boolean matches(AuditAction action) {
            return actions == null || (action != null && actions.contains(action));
        }
    }

    public enum ViewScreen {
        LIST,
        DETAILS
    }

    public record UiMetrics(
            float dialogWidth,
            float contentWidth,
            float cardWidth,
            float cardContentWidth,
            float paneMaxHeight,
            int maxReasonLength,
            boolean isMobile
    ) {
        public static UiMetrics of(boolean isMobile) {
            if (isMobile) {
                float dw = 680f;
                float pad = 10f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 18f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 520f, 40, true);
            } else {
                float dw = 740f;
                float pad = 12f;
                float cw = dw - pad * 2f;
                float cardW = cw - 26f;
                float cardInnerW = cardW - 22f;
                return new UiMetrics(dw, cw, cardW, cardInnerW, 500f, 65, false);
            }
        }
    }

    public record AuditHistoryModel(
            String viewerUuid,
            boolean isViewerAdmin,
            boolean isMobile,
            String targetUuid,
            int targetPid,
            String targetNickname,
            String targetDiscordId,
            String targetColorHex,
            AuditViewMode mode,
            ActionFilter actionFilter,
            ViewScreen screen,
            List<AuditRecordSummary> records,
            AuditCursor currentCursor,
            AuditCursor nextCursor,
            boolean hasNext,
            Deque<AuditCursor> cursorBackStack,
            int pageIndex,
            boolean isLoading,
            String inspectedAuditId,
            AuditRecord inspectedRecord,
            String feedbackMessage
    ) {
        public AuditHistoryModel withMode(AuditViewMode newMode) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    newMode, ActionFilter.ALL, ViewScreen.LIST,
                    List.of(), null, null, false, new ArrayDeque<>(), 1, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withActionFilter(ActionFilter filter) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, filter, screen,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, isLoading,
                    inspectedAuditId, inspectedRecord, ""
            );
        }

        public AuditHistoryModel withPage(List<AuditRecordSummary> newRecords, AuditCursor current,
                                          AuditCursor next, boolean hasMore,
                                          Deque<AuditCursor> newBackStack, int newPageIndex) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.LIST,
                    newRecords, current, next, hasMore, newBackStack, newPageIndex, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withDetails(String auditId, AuditRecord record) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.DETAILS,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, false,
                    auditId, record, ""
            );
        }

        public AuditHistoryModel withBackToList() {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.LIST,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withFeedback(String message) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin, isMobile,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, screen,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, isLoading,
                    inspectedAuditId, inspectedRecord, message
            );
        }
    }

    public sealed interface AuditHistoryEvent {
        record SelectMode(AuditViewMode mode) implements AuditHistoryEvent {}
        record SelectFilter(ActionFilter filter) implements AuditHistoryEvent {}
        record NextPage() implements AuditHistoryEvent {}
        record PrevPage() implements AuditHistoryEvent {}
        record Refresh() implements AuditHistoryEvent {}
        record InspectRecord(String auditId) implements AuditHistoryEvent {}
        record BackToList() implements AuditHistoryEvent {}
        record CopyAuditId(String auditId) implements AuditHistoryEvent {}
        record Back() implements AuditHistoryEvent {}
        record Close() implements AuditHistoryEvent {}
    }

    private final AuditHistoryMenu menu;
    private final AuditService auditService;
    private final PlayerDisplayService playerDisplayService;
    private final SessionService sessionService;
    private final Async async;
    private final Session session;
    private final PlayerData targetData;

    public AuditHistoryUiController(AuditHistoryMenu menu,
                                    AuditService auditService,
                                    PlayerDisplayService playerDisplayService,
                                    SessionService sessionService,
                                    Async async,
                                    Session session,
                                    PlayerData targetData) {
        this.menu = menu;
        this.auditService = auditService;
        this.playerDisplayService = playerDisplayService;
        this.sessionService = sessionService;
        this.async = async;
        this.session = session;
        this.targetData = targetData;
    }

    public AuditHistoryUiController(AuditHistoryMenu menu,
                                    AuditService auditService,
                                    SessionService sessionService,
                                    Session session,
                                    PlayerData targetData) {
        this(menu, auditService, null, sessionService, null, session, targetData);
    }

    public AuditHistoryModel createInitialModel(AuditViewMode mode) {
        boolean isMobile = session != null && session.player != null && session.player.con != null && session.player.con.mobile;
        boolean isAdmin = session != null && session.player != null && session.player.admin;
        String viewerUuid = session != null && session.data != null ? session.data.uuid : "";

        String targetColor = PlayerSettingsUiController.resolvePlayerColorHex(session, targetData);

        var emptyModel = new AuditHistoryModel(
                viewerUuid, isAdmin, isMobile,
                targetData != null ? targetData.uuid : "",
                targetData != null ? targetData.pid : 0,
                targetData != null && targetData.nickname != null ? targetData.nickname : "",
                targetData != null && targetData.discordId != null ? targetData.discordId : "",
                targetColor,
                mode != null ? mode : AuditViewMode.TARGET,
                ActionFilter.ALL,
                ViewScreen.LIST,
                List.of(), null, null, false, new ArrayDeque<>(), 1, false,
                null, null, ""
        );

        return loadPage(emptyModel, null, 1, new ArrayDeque<>());
    }

    private AuditHistoryModel loadPage(AuditHistoryModel current, AuditCursor cursor, int pageIdx, Deque<AuditCursor> backStack) {
        if (auditService == null) return current;

        Slice<AuditRecordSummary> slice;
        if (current.mode() == AuditViewMode.TARGET) {
            slice = auditService.findSummaryByTargetUuid(current.targetUuid(), cursor, RECORDS_PER_PAGE);
        } else {
            List<String> lookupIds = (current.targetDiscordId() != null && !current.targetDiscordId().isBlank())
                    ? List.of(current.targetDiscordId(), current.targetNickname())
                    : List.of(current.targetNickname());

            if (auditService instanceof DefaultAuditService defaultService) {
                slice = defaultService.findSummaryByActor(AuditActorType.PLAYER_ADMIN, lookupIds, cursor, RECORDS_PER_PAGE);
            } else {
                slice = auditService.findSummaryByActor(AuditActorType.PLAYER_ADMIN, lookupIds.getFirst(), cursor, RECORDS_PER_PAGE);
            }
        }

        List<AuditRecordSummary> items = slice != null && slice.items() != null ? slice.items() : List.of();
        boolean hasMore = slice != null && slice.hasNext();
        AuditCursor nextCursor = slice != null ? slice.nextCursor() : null;

        return current.withPage(items, cursor, nextCursor, hasMore, backStack, pageIdx);
    }

    @Override
    public AuditHistoryModel initialModel(Object context) {
        AuditViewMode mode = context instanceof AuditViewMode m ? m : AuditViewMode.TARGET;
        return createInitialModel(mode);
    }

    @Override
    public UpdateResult<AuditHistoryModel> update(AuditHistoryModel model, AuditHistoryEvent event, ControllerContext ctx) {
        return switch (event) {
            case AuditHistoryEvent.SelectMode(var newMode) -> {
                if (!model.isViewerAdmin() || newMode == model.mode()) {
                    yield UpdateResult.of(model);
                }
                AuditHistoryModel switched = model.withMode(newMode);
                AuditHistoryModel loaded = loadPage(switched, null, 1, new ArrayDeque<>());
                yield UpdateResult.patch(loaded, SLOT_HEADER, SLOT_TABS, SLOT_LIST, SLOT_PAGINATION);
            }

            case AuditHistoryEvent.SelectFilter(var filter) -> {
                AuditHistoryModel updated = model.withActionFilter(filter);
                yield UpdateResult.patch(updated, SLOT_TABS, SLOT_LIST);
            }

            case AuditHistoryEvent.NextPage() -> {
                if (!model.hasNext() || model.nextCursor() == null) {
                    yield UpdateResult.of(model);
                }
                Deque<AuditCursor> newStack = new ArrayDeque<>(model.cursorBackStack());
                newStack.addLast(model.currentCursor());
                AuditHistoryModel loaded = loadPage(model, model.nextCursor(), model.pageIndex() + 1, newStack);
                yield UpdateResult.patch(loaded, SLOT_LIST, SLOT_PAGINATION);
            }

            case AuditHistoryEvent.PrevPage() -> {
                if (model.cursorBackStack().isEmpty()) {
                    yield UpdateResult.of(model);
                }
                Deque<AuditCursor> newStack = new ArrayDeque<>(model.cursorBackStack());
                AuditCursor prevCursor = newStack.pollLast();
                AuditHistoryModel loaded = loadPage(model, prevCursor, Math.max(1, model.pageIndex() - 1), newStack);
                yield UpdateResult.patch(loaded, SLOT_LIST, SLOT_PAGINATION);
            }

            case AuditHistoryEvent.Refresh() -> {
                AuditHistoryModel refreshed = loadPage(model, model.currentCursor(), model.pageIndex(), model.cursorBackStack());
                yield UpdateResult.patch(refreshed, SLOT_LIST, SLOT_PAGINATION);
            }

            case AuditHistoryEvent.InspectRecord(String auditId) -> {
                if (auditId == null || auditId.isBlank() || auditService == null) {
                    yield UpdateResult.of(model);
                }
                AuditRecord record = auditService.findByAuditId(auditId).orElse(null);
                if (record == null) {
                    Localization local = session != null ? session.locale() : null;
                    String err = local != null ? local.t("error-processing-request") : "Record not found";
                    yield UpdateResult.patch(model.withFeedback("[scarlet]" + Iconc.warning + " " + err + "[]"), SLOT_HEADER);
                }
                yield UpdateResult.rerender(model.withDetails(auditId, record));
            }

            case AuditHistoryEvent.BackToList() -> {
                yield UpdateResult.rerender(model.withBackToList());
            }

            case AuditHistoryEvent.CopyAuditId(String auditId) -> {
                if (session != null && session.player != null && auditId != null) {
                    session.player.sendMessage("[accent]Audit ID: [white]" + auditId);
                }
                Localization local = session != null ? session.locale() : null;
                String msg = local != null
                        ? local.t("audit-menu-copy-id-success", args("auditId", auditId))
                        : "Audit ID sent to chat: " + auditId;
                yield UpdateResult.patch(model.withFeedback("[lime]" + Iconc.ok + " " + msg + "[]"), SLOT_HEADER);
            }

            case AuditHistoryEvent.Back() -> {
                // If in details dossier, step back to list screen
                if (model.screen() == ViewScreen.DETAILS) {
                    yield UpdateResult.rerender(model.withBackToList());
                }

                // If on list screen, return to previous UI screen via session stack
                if (session != null && session.hasHistory()) {
                    Runnable prev = session.popHistory();
                    if (prev != null) {
                        if (ctx != null) {
                            ctx.post(prev);
                        } else {
                            prev.run();
                        }
                        yield UpdateResult.close(model);
                    }
                }
                yield UpdateResult.close(model);
            }

            case AuditHistoryEvent.Close() -> UpdateResult.close(model);
        };
    }

    @Override
    public AuditHistoryEvent parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled() || result.result == null) {
            return new AuditHistoryEvent.Close();
        }
        String res = result.result.trim();
        if ("action:close".equals(res)) return new AuditHistoryEvent.Close();
        if ("action:back".equals(res)) return new AuditHistoryEvent.Back();
        if ("action:back_to_list".equals(res)) return new AuditHistoryEvent.BackToList();
        if ("action:page:next".equals(res)) return new AuditHistoryEvent.NextPage();
        if ("action:page:prev".equals(res)) return new AuditHistoryEvent.PrevPage();
        if ("action:refresh".equals(res)) return new AuditHistoryEvent.Refresh();

        if (res.startsWith("action:mode:")) {
            try {
                return new AuditHistoryEvent.SelectMode(
                        AuditViewMode.valueOf(res.substring("action:mode:".length()))
                );
            } catch (Exception ignored) {}
        }
        if (res.startsWith("action:filter:")) {
            try {
                return new AuditHistoryEvent.SelectFilter(
                        ActionFilter.valueOf(res.substring("action:filter:".length()))
                );
            } catch (Exception ignored) {}
        }
        if (res.startsWith("action:inspect:")) {
            return new AuditHistoryEvent.InspectRecord(res.substring("action:inspect:".length()));
        }
        if (res.startsWith("action:copy_id:")) {
            return new AuditHistoryEvent.CopyAuditId(res.substring("action:copy_id:".length()));
        }

        return new AuditHistoryEvent.Close();
    }

    // =========================================================================
    // View Rendering
    // =========================================================================

    @Override
    public VNode render(AuditHistoryModel model) {
        UiMetrics metrics = UiMetrics.of(model.isMobile());
        Localization local = session != null ? session.locale() : null;

        return Ui.table(root -> {
            root.background("pane");
            root.margin(model.isMobile() ? 8f : 12f);
            root.layout(l -> l.width(metrics.dialogWidth()).pad(4f));

            // Slot 1: Header (Target Nickname, Mode, Breadcrumbs, Close [X])
            root.slot(SLOT_HEADER.path(), h -> renderHeader(h, model, metrics, local)).row();
            root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padBottom(4f).color("ffd37f")).row();

            if (model.screen() == ViewScreen.DETAILS) {
                // In-Dialog Inspection Dossier
                root.slot(SLOT_DETAILS.path(), d -> renderDetailsView(d, model, metrics, local)).row();
            } else {
                // Slot 2: Tabs (Mode Tabs + Action Filter Buttons)
                root.slot(SLOT_TABS.path(), t -> renderTabs(t, model, metrics, local)).row();

                // Slot 3: Scrollable Card List
                root.slot(SLOT_LIST.path(), l -> renderCardList(l, model, metrics, local)).row();

                root.image("whiteui", l -> l.width(metrics.contentWidth()).height(2f).padTop(4f).padBottom(4f).color("3b4252")).row();

                // Slot 4: Pagination Footer Bar
                root.slot(SLOT_PAGINATION.path(), p -> renderPagination(p, model, metrics, local)).row();
            }
        });
    }

    private void renderHeader(Ui.TableBuilder h, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        h.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));

        h.add(Ui.table(left -> {
            left.layout(l -> l.width(metrics.contentWidth() - 40f).align("left"));

            String nameFormatted = "[#" + model.targetColorHex() + "]" + escapeMarkup(model.targetNickname()) + "[]";
            String modeBadge = model.mode() == AuditViewMode.TARGET
                    ? "[orange]" + Iconc.warning + " " + (local != null ? local.t("audit-menu-tab-sanctions") : "Sanctions Dossier") + "[]"
                    : "[accent]" + Iconc.admin + " " + (local != null ? local.t("audit-menu-tab-actions") : "Staff Actions Log") + "[]";

            left.label(Text.raw(nameFormatted + " [gray]#" + model.targetPid() + "[] " + modeBadge),
                    l -> l.align("left").growX()).row();

            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                left.label(Text.raw(model.feedbackMessage()), l -> l.align("left")).row();
            } else {
                String sub = model.mode() == AuditViewMode.TARGET
                        ? (local != null ? local.t("audit-menu-history-hint") : "Sanctions and interventions against player")
                        : (local != null ? local.t("audit-menu-actions-hint") : "Administrative enforcement actions performed by staff member");
                left.label(Text.raw("[darkgray]" + sub + "[]"), l -> l.align("left")).row();
            }
        }));

        // Close Button
        h.button(Text.raw("[scarlet]" + Iconc.cancel + "[]"), "action:close",
                b -> b.style("cleart").layout(l -> l.size(34f)));
    }

    private void renderTabs(Ui.TableBuilder t, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        t.layout(l -> l.width(metrics.contentWidth()).padBottom(6f));

        // 1. Mode Tabs (Only switchable if viewer is admin)
        if (model.isViewerAdmin()) {
            t.add(Ui.table(m -> {
                m.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));

                boolean isTarget = model.mode() == AuditViewMode.TARGET;
                String targetStyle = isTarget ? "default" : "cleart";
                String targetText = local != null ? local.t("audit-menu-tab-sanctions") : "Sanctions Received";
                String targetLabel = (isTarget ? "[accent]" : "[gray]") + Iconc.warning + " " + targetText + "[]";
                m.button(Text.raw(targetLabel), "action:mode:TARGET",
                        b -> b.style(targetStyle).layout(l -> l.uniform().growX().height(32f).padRight(4f)));

                String actorStyle = !isTarget ? "default" : "cleart";
                String actorText = local != null ? local.t("audit-menu-tab-actions") : "Staff Actions Taken";
                String actorLabel = (!isTarget ? "[accent]" : "[gray]") + Iconc.admin + " " + actorText + "[]";
                m.button(Text.raw(actorLabel), "action:mode:ACTOR",
                        b -> b.style(actorStyle).layout(l -> l.uniform().growX().height(32f)));
            })).row();
        }

        // 2. Action Filter Chips (ALL, BANS, MUTES, WARNS, OTHER)
        t.add(Ui.table(f -> {
            f.layout(l -> l.width(metrics.contentWidth()).align("left"));
            for (var filter : ActionFilter.values()) {
                boolean active = model.actionFilter() == filter;
                String style = active ? "default" : "cleart";
                String color = active ? "[white]" : "[darkgray]";
                String label = resolveFilterLabel(filter, local);
                f.button(Text.raw(color + label + "[]"), "action:filter:" + filter.name(),
                        b -> b.style(style).layout(l -> l.height(28f).padRight(4f)));
            }
        })).row();
    }

    private static String resolveFilterLabel(ActionFilter filter, Localization local) {
        if (local == null) return filter.name();
        return switch (filter) {
            case ALL -> local.t("audit-menu-filter-all");
            case BANS -> local.t("audit-menu-filter-bans");
            case MUTES -> local.t("audit-menu-filter-mutes");
            case WARNS -> local.t("audit-menu-filter-warns");
            case OTHER -> local.t("audit-menu-filter-other");
        };
    }

    private void renderCardList(Ui.TableBuilder l, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        l.layout(lout -> lout.width(metrics.contentWidth()).padBottom(4f));

        List<AuditRecordSummary> filtered = model.records().stream()
                .filter(r -> model.actionFilter().matches(r.action()))
                .toList();

        if (filtered.isEmpty()) {
            l.add(Ui.table(empty -> {
                empty.background("button");
                empty.margin(14f);
                empty.layout(lay -> lay.width(metrics.cardWidth()).height(140f).align("center"));
                String emptyMsg = model.mode() == AuditViewMode.TARGET
                        ? (local != null ? local.t("audit-menu-history-empty") : "No audit entries found for this player yet.")
                        : (local != null ? local.t("audit-menu-actions-empty") : "No audit actions found for this player yet.");
                empty.label(Text.raw("[gray]" + Iconc.info + " " + emptyMsg + "[]"),
                        lay -> lay.align("center")).row();
            })).row();
            return;
        }

        l.pane(scroll -> {
            scroll.layout(p -> p.width(metrics.contentWidth()).maxHeight(metrics.paneMaxHeight()));
            scroll.table(list -> {
                list.layout(lay -> lay.growX());
                for (var item : filtered) {
                    renderAuditCard(list, item, model, metrics, local);
                }
            });
        });
    }

    private void renderAuditCard(Ui.TableBuilder list, AuditRecordSummary item, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        String color = actionColor(item.action());
        char icon = actionIcon(item.action());

        list.add(Ui.table(card -> {
            card.background("button");
            card.margin(model.isMobile() ? 6f : 8f);
            card.layout(l -> l.width(metrics.cardWidth()).padBottom(4f));

            // Left vertical severity accent stripe
            card.image("whiteui", l -> l.width(4f).growY().padRight(8f).color(color));

            // Card Body
            card.add(Ui.table(body -> {
                body.layout(l -> l.width(metrics.cardContentWidth()).align("left"));

                // Row 1: Action Badge + Subject + Timestamp + Active Status
                body.add(Ui.table(r1 -> {
                    r1.layout(l -> l.growX());
                    String actionName = formatActionName(item.action(), local);
                    String badge = "[" + color + "]" + icon + " " + actionName + "[]";
                    String subject = model.mode() == AuditViewMode.TARGET
                            ? "[gray]by[] [white]" + escapeMarkup(item.actorName()) + "[]"
                            : "[gray]on[] [white]" + escapeMarkup(item.targetName()) + "[]";
                    String time = "[darkgray]• " + formatTimestamp(item.createdAtEpochMs()) + "[]";
                    String status = formatStatusPill(item, local);

                    r1.label(Text.raw(badge + "  " + subject + " " + time), l -> l.align("left").growX());
                    if (!status.isEmpty()) {
                        r1.label(Text.raw(status), l -> l.align("right"));
                    }
                })).row();

                // Row 2: Reason Excerpt
                String reasonExcerpt = summarizeReason(item.reason(), metrics.maxReasonLength(), local);
                body.label(Text.raw("[lightgray]" + escapeMarkup(reasonExcerpt) + "[]"),
                        l -> l.align("left").growX().padTop(2f).padBottom(3f)).row();

                // Row 3: Duration / ID + Click to Inspect Button
                body.add(Ui.table(r3 -> {
                    r3.layout(l -> l.growX());
                    String durationInfo = formatDurationInfo(item, local);
                    r3.label(Text.raw(durationInfo), l -> l.align("left").growX());

                    String btnText = local != null ? local.t("audit-menu-btn-details") : "Details >";
                    r3.button(Text.raw("[accent]" + Iconc.zoom + " " + btnText + "[]"), "action:inspect:" + item.auditId(),
                            b -> b.style("cleart").layout(l -> l.height(24f)));
                })).row();
            }));
        })).row();
    }

    private void renderDetailsView(Ui.TableBuilder d, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        d.layout(l -> l.width(metrics.contentWidth()).padBottom(4f));
        AuditRecord rec = model.inspectedRecord();
        if (rec == null) {
            d.label(Text.raw("[scarlet]Record details unavailable.[]")).row();
            return;
        }

        String color = actionColor(rec.action);
        char icon = actionIcon(rec.action);

        d.add(Ui.table(card -> {
            card.background("button");
            card.margin(10f);
            card.layout(l -> l.width(metrics.cardWidth()).padBottom(6f));

            // Section 1: Header & Status
            card.add(Ui.table(top -> {
                top.layout(l -> l.growX().padBottom(6f));
                String actionName = formatActionName(rec.action, local);
                String title = (local != null ? local.t("audit-menu-details-title") : "Audit details") + ": [" + color + "]" + icon + " " + actionName + "[]";
                top.label(Text.raw(title), l -> l.align("left").growX());

                boolean active = isRecordActive(rec);
                String activeTag = active
                        ? "[scarlet]" + (local != null ? local.t("audit-menu-status-active") : "ACTIVE") + "[]"
                        : "[darkgray]" + (local != null ? local.t("audit-menu-status-expired") : "EXPIRED") + "[]";
                top.label(Text.raw(activeTag), l -> l.align("right"));
            })).row();
            card.image("whiteui", l -> l.growX().height(1f).padBottom(6f).color("3b4252")).row();

            // Section 2: Target & Actor Information
            card.add(Ui.table(info -> {
                info.layout(l -> l.growX().padBottom(4f));
                String targetName = rec.target != null && rec.target.nameSnapshot != null ? rec.target.nameSnapshot : "Unknown";
                String actorName = rec.actor != null && rec.actor.nameSnapshot != null ? rec.actor.nameSnapshot : "Unknown";
                info.label(Text.raw("[gray]Target:[] [white]" + escapeMarkup(targetName) + "[]"), l -> l.align("left")).row();
                info.label(Text.raw("[gray]Actor:[]  [white]" + escapeMarkup(actorName) + "[] [accent](" + (rec.actor != null ? rec.actor.type : "UNKNOWN") + ")[]"), l -> l.align("left")).row();
                if (rec.origin != null && rec.origin.serverId != null && !rec.origin.serverId.isBlank()) {
                    info.label(Text.raw("[gray]Server:[] [sky]" + rec.origin.serverId + "[]"), l -> l.align("left")).row();
                }
            })).row();

            // Section 3: Reason (Multi-line safe)
            card.add(Ui.table(reasonBox -> {
                reasonBox.background("pane");
                reasonBox.margin(6f);
                reasonBox.layout(l -> l.growX().padBottom(6f));
                String reasonStr = rec.reason != null && !rec.reason.isBlank() ? rec.reason : (local != null ? local.t("audit-menu-reason-unspecified") : "Not specified");
                reasonBox.label(Text.raw("[white]Reason: " + escapeMarkup(reasonStr) + "[]"),
                        l -> l.align("left").growX()).row();
            })).row();

            // Section 4: Timing & Duration
            card.add(Ui.table(timing -> {
                timing.layout(l -> l.growX().padBottom(6f));
                String occurred = rec.occurredAt != null ? DATE_TIME_FORMAT.format(rec.occurredAt) : "-";
                timing.label(Text.raw("[gray]Occurred:[] [white]" + occurred + "[]"), l -> l.align("left")).row();

                if (rec.details != null && rec.details.durationMs != null) {
                    long minutes = Math.max(1L, rec.details.durationMs / 60000L);
                    String durationStr = PlayerProfileUiController.formatDuration((int) minutes, local);
                    timing.label(Text.raw("[gray]Duration:[] [white]" + durationStr + "[]"), l -> l.align("left")).row();
                } else {
                    String perm = local != null ? local.t("audit-menu-duration-permanent") : "Permanent";
                    timing.label(Text.raw("[gray]Duration:[] [scarlet]" + perm + "[]"), l -> l.align("left")).row();
                }
                if (rec.details != null && rec.details.expiresAt != null) {
                    timing.label(Text.raw("[gray]Expires:[]  [scarlet]" + DATE_TIME_FORMAT.format(rec.details.expiresAt) + "[]"), l -> l.align("left")).row();
                }
            })).row();

            // Section 5: Metadata Footer (Audit ID)
            card.add(Ui.table(meta -> {
                meta.layout(l -> l.growX());
                meta.label(Text.raw("[darkgray]Audit ID: " + rec.auditId + "[]"), l -> l.align("left").growX());
                String copyLabel = local != null ? local.t("audit-menu-btn-copy-id") : "Copy ID";
                meta.button(Text.raw("[lightgray]" + Iconc.copy + " " + copyLabel + "[]"), "action:copy_id:" + rec.auditId,
                        b -> b.style("cleart").layout(l -> l.height(24f)));
            })).row();
        })).row();

        // Details Navigation Footer
        d.add(Ui.table(foot -> {
            foot.layout(l -> l.width(metrics.cardWidth()).padTop(4f));
            String backText = local != null ? local.t("audit-menu-btn-back") : "Back to History";
            foot.button(Text.raw("[accent]" + Iconc.left + " " + backText + "[]"), "action:back_to_list",
                    b -> b.style("default").layout(l -> l.growX().height(36f)));
        })).row();
    }

    private void renderPagination(Ui.TableBuilder p, AuditHistoryModel model, UiMetrics metrics, Localization local) {
        p.layout(l -> l.width(metrics.contentWidth()).padTop(4f));

        // Prev Button
        boolean hasPrev = !model.cursorBackStack().isEmpty();
        String prevStyle = hasPrev ? "default" : "cleart";
        String prevText = local != null ? local.t("previous") : "Prev";
        String prevLabel = (hasPrev ? "[accent]" : "[gray]") + Iconc.left + " " + prevText + "[]";
        p.button(Text.raw(prevLabel), "action:page:prev", b -> b.style(prevStyle).layout(l -> l.width(100f).height(32f).padRight(8f)));

        // Page Indicator
        p.label(Text.raw("[white]Page " + model.pageIndex() + "[]"), l -> l.align("center").growX());

        // Next Button
        boolean hasNext = model.hasNext() && model.nextCursor() != null;
        String nextStyle = hasNext ? "default" : "cleart";
        String nextText = local != null ? local.t("next") : "Next";
        String nextLabel = (hasNext ? "[accent]" : "[gray]") + nextText + " " + Iconc.right + "[]";
        p.button(Text.raw(nextLabel), "action:page:next", b -> b.style(nextStyle).layout(l -> l.width(100f).height(32f).padLeft(8f)));

        // Refresh Button
        p.button(Text.raw("[gray]" + Iconc.refresh + "[]"), "action:refresh",
                b -> b.style("cleart").layout(l -> l.size(32f).padLeft(6f)));

        // Return to Profile / Back Button
        String backBtnText = local != null ? local.t("back") : "Back";
        p.button(Text.raw("[accent]" + Iconc.left + " " + backBtnText + "[]"), "action:back",
                b -> b.style("default").layout(l -> l.height(32f).padLeft(6f)));
    }

    // --- Helper Formatters ---

    private static String formatActionName(AuditAction action, Localization local) {
        if (action == null) return "UNKNOWN";
        if (local != null) {
            String key = "audit-menu-action-" + action.name().toLowerCase();
            String translated = local.t(key);
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return action.name();
    }

    private static String actionColor(AuditAction action) {
        if (action == null) return "gray";
        return switch (action) {
            case BAN -> "scarlet";
            case UNBAN -> "lime";
            case MUTE -> "orange";
            case UNMUTE -> "lime";
            case WARN -> "yellow";
            case KICK -> "lightgray";
            case QUARANTINE -> "purple";
            case UNQUARANTINE -> "cyan";
            case NOTE, MERGE -> "gray";
        };
    }

    private static char actionIcon(AuditAction action) {
        if (action == null) return Iconc.info;
        return switch (action) {
            case BAN -> Iconc.lock;
            case UNBAN -> Iconc.ok;
            case MUTE -> Iconc.chat;
            case UNMUTE -> Iconc.ok;
            case WARN -> Iconc.warning;
            case KICK -> Iconc.cancel;
            case QUARANTINE -> Iconc.defense;
            case UNQUARANTINE -> Iconc.ok;
            case NOTE, MERGE -> Iconc.list;
        };
    }

    private static String formatStatusPill(AuditRecordSummary item, Localization local) {
        if (item.action() != AuditAction.BAN && item.action() != AuditAction.MUTE) return "";
        if (item.expiresAt() == null) {
            String perm = local != null ? local.t("audit-menu-status-permanent") : "PERMANENT";
            return "[scarlet][" + perm + "][]";
        }
        Instant now = Instant.now();
        if (now.isBefore(item.expiresAt())) {
            String active = local != null ? local.t("audit-menu-status-active") : "ACTIVE";
            return "[scarlet][" + active + "][]";
        }
        String expired = local != null ? local.t("audit-menu-status-expired") : "EXPIRED";
        return "[darkgray][" + expired + "][]";
    }

    private static boolean isRecordActive(AuditRecord rec) {
        if (rec == null) return false;
        if (rec.action != AuditAction.BAN && rec.action != AuditAction.MUTE) return false;
        if (rec.details == null || rec.details.expiresAt == null) return true; // Permanent
        return Instant.now().isBefore(rec.details.expiresAt);
    }

    private static String formatDurationInfo(AuditRecordSummary item, Localization local) {
        if (item.durationMs() == null && item.expiresAt() == null) {
            return "[darkgray]ID: " + truncate(item.auditId(), 12) + "[]";
        }
        if (item.expiresAt() == null) {
            String perm = local != null ? local.t("audit-menu-duration-permanent") : "Permanent";
            return "[darkgray]" + perm + " | ID: " + truncate(item.auditId(), 12) + "[]";
        }
        return "[darkgray]Expires: " + DATE_TIME_FORMAT.format(item.expiresAt()) + "[]";
    }

    private static String summarizeReason(String reason, int maxLen, Localization local) {
        if (reason == null || reason.isBlank()) {
            return local != null ? local.t("audit-menu-reason-unspecified") : "Not specified";
        }
        String clean = reason.replace('\n', ' ').trim();
        return truncate(clean, maxLen);
    }

    private static String formatTimestamp(long millis) {
        if (millis <= 0) return "-";
        return DATE_TIME_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private static String escapeMarkup(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace("[", "[[");
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }
}
