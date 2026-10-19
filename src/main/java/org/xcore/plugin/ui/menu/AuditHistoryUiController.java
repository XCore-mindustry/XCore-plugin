package org.xcore.plugin.ui.menu;

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
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.service.moderation.DefaultAuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Kit.MARGIN;
import static org.xcore.plugin.ui.kit.Texts.locale;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * Reactive Elm/MVI controller for Mindustry server-side Audit History inspection (/audit).
 * Replaces legacy AuditHistoryMenu and AuditHistoryFlows with responsive mobile/desktop cards,
 * in-dialog record inspection, and seamless session stack navigation.
 */
public class AuditHistoryUiController implements UiController<AuditHistoryUiController.AuditHistoryModel, AuditHistoryUiController.AuditHistoryEvent> {

    public static final SlotKey<Object> SLOT_HEADER     = SlotKey.of("slot_audit_header");
    public static final SlotKey<Object> SLOT_TABS       = SlotKey.of("slot_audit_tabs");
    public static final SlotKey<Object> SLOT_LIST       = SlotKey.of("slot_audit_list");
    public static final SlotKey<Object> SLOT_PAGINATION = SlotKey.of("slot_audit_pagination");

    public static final int RECORDS_PER_PAGE = 8;
    private static final float STRIPE = 4f;
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

    public record AuditHistoryModel(
            String viewerUuid,
            boolean isViewerAdmin,
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
                    viewerUuid, isViewerAdmin,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    newMode, ActionFilter.ALL, ViewScreen.LIST,
                    List.of(), null, null, false, new ArrayDeque<>(), 1, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withActionFilter(ActionFilter filter) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin,
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
                    viewerUuid, isViewerAdmin,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.LIST,
                    newRecords, current, next, hasMore, newBackStack, newPageIndex, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withDetails(String auditId, AuditRecord record) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.DETAILS,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, false,
                    auditId, record, ""
            );
        }

        public AuditHistoryModel withBackToList() {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin,
                    targetUuid, targetPid, targetNickname, targetDiscordId, targetColorHex,
                    mode, actionFilter, ViewScreen.LIST,
                    records, currentCursor, nextCursor, hasNext, cursorBackStack, pageIndex, false,
                    null, null, ""
            );
        }

        public AuditHistoryModel withFeedback(String message) {
            return new AuditHistoryModel(
                    viewerUuid, isViewerAdmin,
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
        boolean isAdmin = session != null && session.has(PermissionNodes.MODERATION_AUDIT_OTHERS);
        String viewerUuid = session != null && session.data != null ? session.data.uuid : "";

        String targetColor = PlayerSettingsUiController.resolvePlayerColorHex(session, targetData);

        var emptyModel = new AuditHistoryModel(
                viewerUuid, isAdmin,
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
                if (!model.isViewerAdmin() || newMode == model.mode()
                        || session == null || !session.has(PermissionNodes.MODERATION_AUDIT_OTHERS)) {
                    yield UpdateResult.of(model);
                }
                AuditHistoryModel switched = model.withMode(newMode);
                AuditHistoryModel loaded = loadPage(switched, null, 1, new ArrayDeque<>());
                yield UpdateResult.patch(loaded, Screen.slots(SLOT_HEADER, SLOT_TABS, SLOT_LIST, SLOT_PAGINATION));
            }

            case AuditHistoryEvent.SelectFilter(var filter) -> {
                AuditHistoryModel updated = model.withActionFilter(filter);
                yield UpdateResult.patch(updated, Screen.slots(SLOT_TABS, SLOT_LIST));
            }

            case AuditHistoryEvent.NextPage() -> {
                if (!model.hasNext() || model.nextCursor() == null) {
                    yield UpdateResult.of(model);
                }
                Deque<AuditCursor> newStack = new ArrayDeque<>(model.cursorBackStack());
                newStack.addLast(model.currentCursor());
                AuditHistoryModel loaded = loadPage(model, model.nextCursor(), model.pageIndex() + 1, newStack);
                yield UpdateResult.patch(loaded, Screen.slots(SLOT_LIST, SLOT_PAGINATION));
            }

            case AuditHistoryEvent.PrevPage() -> {
                if (model.cursorBackStack().isEmpty()) {
                    yield UpdateResult.of(model);
                }
                Deque<AuditCursor> newStack = new ArrayDeque<>(model.cursorBackStack());
                AuditCursor prevCursor = newStack.pollLast();
                AuditHistoryModel loaded = loadPage(model, prevCursor, Math.max(1, model.pageIndex() - 1), newStack);
                yield UpdateResult.patch(loaded, Screen.slots(SLOT_LIST, SLOT_PAGINATION));
            }

            case AuditHistoryEvent.Refresh() -> {
                AuditHistoryModel refreshed = loadPage(model, model.currentCursor(), model.pageIndex(), model.cursorBackStack());
                yield UpdateResult.patch(refreshed, Screen.slots(SLOT_LIST, SLOT_PAGINATION));
            }

            case AuditHistoryEvent.InspectRecord(String auditId) -> {
                if (auditId == null || auditId.isBlank() || auditService == null) {
                    yield UpdateResult.of(model);
                }
                AuditRecord record = auditService.findByAuditId(auditId).orElse(null);
                if (record == null) {
                    String err = t(session, "error-processing-request");
                    yield UpdateResult.patch(model.withFeedback("[scarlet]" + Iconc.warning + " " + err + "[]"), Screen.slots(SLOT_HEADER));
                }
                yield UpdateResult.rerender(model.withDetails(auditId, record));
            }

            case AuditHistoryEvent.BackToList() -> {
                yield UpdateResult.rerender(model.withBackToList());
            }

            case AuditHistoryEvent.CopyAuditId(String auditId) -> {
                if (session != null && session.player != null && auditId != null) {
                    session.player.sendMessage("[accent]" + t(session, "audit-menu-field-id") + ": [white]" + auditId);
                }
                String msg = t(session, "audit-menu-copy-id-success", args("auditId", auditId));
                yield UpdateResult.patch(model.withFeedback("[lime]" + Iconc.ok + " " + msg + "[]"), Screen.slots(SLOT_HEADER));
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
        return Screen.each(screen -> window(model, screen));
    }

    /** The whole menu as it is on a screen of the given class. */
    VNode window(AuditHistoryModel model, Screen screen) {
        Localization local = locale(session);
        return model.screen() == ViewScreen.DETAILS ? details(model, screen, local) : list(model, screen, local);
    }

    // ------------------------------------------------------------------ list

    private VNode list(AuditHistoryModel model, Screen screen, Localization local) {
        float width = screen.width();
        return Kit.window(window -> {
            window.slot(screen.slot(SLOT_HEADER).path(), slot -> slot.add(header(model, width, local))).row();
            window.slot(screen.slot(SLOT_TABS).path(), slot -> tabs(slot, model, width, local)).row();
            window.slot(screen.slot(SLOT_LIST).path(), slot -> slot.add(records(model, screen, local))).row();
            window.slot(screen.slot(SLOT_PAGINATION).path(), slot -> slot.add(Kit.pager(width,
                    t(local, "audit-menu-page", args("page", model.pageIndex())),
                    model.cursorBackStack().isEmpty() ? null : "action:page:prev",
                    model.hasNext() && model.nextCursor() != null ? "action:page:next" : null,
                    "action:refresh"))).row();

            // Back leads to the menu this one was opened from; with none, the dialog's own button closes it.
            if (session != null && session.hasHistory()) {
                window.add(Ui.table(bar -> {
                    bar.layout(l -> l.padTop(GAP));
                    bar.add(Kit.actions(width, List.of(
                            new Kit.Action(Iconc.left + " " + t(local, "back"), "action:back"))));
                })).row();
            }
        });
    }

    /** Whose history this is; under the name, what the last action came to, or what to do here. */
    private VNode header(AuditHistoryModel model, float width, Localization local) {
        float text = width - 2f * MARGIN;
        String name = TextWidth.fit("[accent]" + Iconc.list + "[] [#" + model.targetColorHex() + "]"
                + TextWidth.escape(model.targetNickname()) + "[] [gray]#" + model.targetPid() + "[]", text);

        boolean sanctions = model.mode() == AuditViewMode.TARGET;
        String second;
        if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
            second = model.feedbackMessage();
        } else if (model.screen() == ViewScreen.DETAILS) {
            second = sanctions
                    ? "[#" + Accent.ORANGE.color() + "]" + Iconc.warning + " " + t(local, "audit-menu-tab-sanctions") + "[]"
                    : "[#" + Accent.GOLD.color() + "]" + Iconc.admin + " " + t(local, "audit-menu-tab-actions") + "[]";
        } else {
            second = "[lightgray]" + (sanctions
                    ? t(local, "audit-menu-history-hint")
                    : t(local, "audit-menu-actions-hint")) + "[]";
        }
        return Kit.header(width, name + "\n" + second);
    }

    /** Whose records are shown (an admin's choice), and which kinds of them. */
    private void tabs(Ui.TableBuilder slot, AuditHistoryModel model, float width, Localization local) {
        boolean sanctions = model.mode() == AuditViewMode.TARGET;
        if (model.isViewerAdmin()) {
            slot.add(Kit.tabs(width, "audit_mode", List.of(
                    new Kit.Tab(Iconc.warning, t(local, "audit-menu-tab-sanctions"),
                            "action:mode:TARGET", Accent.ORANGE, sanctions),
                    new Kit.Tab(Iconc.admin, t(local, "audit-menu-tab-actions"),
                            "action:mode:ACTOR", Accent.GOLD, !sanctions)))).row();
        }
        slot.add(Kit.line(width, sanctions ? Accent.ORANGE : Accent.GOLD)).row();

        List<Kit.Option> filters = new ArrayList<>();
        for (ActionFilter filter : ActionFilter.values()) {
            filters.add(new Kit.Option(resolveFilterLabel(filter, local), "action:filter:" + filter.name(),
                    model.actionFilter() == filter));
        }
        Kit.options(slot, width, "audit_filter", filters);
    }

    private static String resolveFilterLabel(ActionFilter filter, Localization local) {
        return switch (filter) {
            case ALL -> t(local, "audit-menu-filter-all");
            case BANS -> t(local, "audit-menu-filter-bans");
            case MUTES -> t(local, "audit-menu-filter-mutes");
            case WARNS -> t(local, "audit-menu-filter-warns");
            case OTHER -> t(local, "audit-menu-filter-other");
        };
    }

    private VNode records(AuditHistoryModel model, Screen screen, Localization local) {
        List<AuditRecordSummary> filtered = model.records().stream()
                .filter(r -> model.actionFilter().matches(r.action()))
                .toList();

        return Kit.pane(screen, body -> {
            if (filtered.isEmpty()) {
                body.add(Kit.note(screen.cards(), model.mode() == AuditViewMode.TARGET
                        ? t(local, "audit-menu-history-empty")
                        : t(local, "audit-menu-actions-empty"))).row();
                return;
            }
            List<VNode> rows = new ArrayList<>();
            for (AuditRecordSummary item : filtered) {
                Accent accent = accent(item.action());
                rows.add(Kit.row("action:inspect:" + item.auditId(), screen.card(), false, true, (row, inner) -> {
                    float text = inner - STRIPE - GAP;
                    row.add(Ui.image("whiteui", l -> l.width(STRIPE).growY().padRight(GAP).color(accent.color())));
                    row.add(Kit.text(rowText(item, model.mode(), text, local), text));
                }));
            }
            body.add(Kit.columns(screen, rows)).row();
        });
    }

    /**
     * A record in four lines: what was done and whether it still holds, by whom (or to whom, in
     * a staff member's log), why, and when. A date beside the name would leave a small phone
     * some eight letters of it.
     */
    static String rowText(AuditRecordSummary item, AuditViewMode mode, float width, Localization local) {
        Accent accent = accent(item.action());
        String status = status(item.action(), item.expiresAt(), local);
        String what = "[#" + accent.color() + "]" + actionIcon(item.action()) + " " + formatActionName(item.action(), local) + "[]"
                + (status.isEmpty() ? "" : "  " + status);

        boolean sanctions = mode == AuditViewMode.TARGET;
        String who = sanctions ? item.actorName() : item.targetName();
        String whom = "[gray]" + (sanctions ? Iconc.admin : Iconc.players) + "[] [white]" + TextWidth.escape(who) + "[]";
        String when = "[gray]" + (item.createdAtEpochMs() <= 0
                ? "-" : DATE_TIME_FORMAT.format(Instant.ofEpochMilli(item.createdAtEpochMs()))) + "[]";

        String reason = item.reason() == null || item.reason().isBlank()
                ? t(local, "audit-menu-reason-unspecified")
                : item.reason().trim().replaceAll("\\s+", " ");

        return TextWidth.fit(what, width) + "\n" + TextWidth.fit(whom, width) + "\n"
                + TextWidth.fit("[lightgray]" + TextWidth.escape(reason) + "[]", width) + "\n" + when;
    }

    // ------------------------------------------------------------------ details

    private VNode details(AuditHistoryModel model, Screen screen, Localization local) {
        float width = screen.width();
        AuditRecord rec = model.inspectedRecord();
        return Kit.window(window -> {
            window.slot(screen.slot(SLOT_HEADER).path(), slot -> slot.add(header(model, width, local))).row();
            window.add(Kit.line(width, rec == null ? Accent.GRAY : accent(rec.action))).row();

            Kit.body(window, screen, body -> {
                if (rec == null) {
                    body.add(Kit.note(screen.cards(), t(local, "audit-menu-details-unavailable"))).row();
                    return;
                }
                body.add(Kit.columns(screen,
                        List.of(eventCard(rec, screen.card(), local), reasonCard(rec, screen.card(), local)),
                        List.of(timeCard(rec, screen.card(), local), idCard(rec, screen.card(), local)))).row();
            });

            List<Kit.Action> actions = new ArrayList<>();
            actions.add(new Kit.Action(Iconc.left + " " + t(local, "audit-menu-btn-back"), "action:back_to_list"));
            if (rec != null) {
                actions.add(new Kit.Action(Iconc.copy + " " + t(local, "audit-menu-btn-copy-id"), "action:copy_id:" + rec.auditId));
            }
            window.add(Ui.table(bar -> {
                bar.layout(l -> l.padTop(GAP));
                bar.add(Kit.actions(width, actions));
            })).row();
        });
    }

    private VNode eventCard(AuditRecord rec, float width, Localization local) {
        Accent accent = accent(rec.action);
        return Kit.card(width, accent, actionIcon(rec.action) + " " + formatActionName(rec.action, local), (content, inner) -> {
            List<String> lines = new ArrayList<>();
            Instant expiresAt = rec.details != null ? rec.details.expiresAt : null;
            String status = status(rec.action, expiresAt, local);
            if (!status.isEmpty()) lines.add(status);

            String target = rec.target != null && rec.target.nameSnapshot != null
                    ? rec.target.nameSnapshot : t(local, "audit-menu-unknown-target");
            String actor = rec.actor != null && rec.actor.nameSnapshot != null
                    ? rec.actor.nameSnapshot : t(local, "audit-menu-unknown-actor");
            lines.add(field(local, "audit-menu-field-target") + " [white]" + TextWidth.escape(target) + "[]");
            lines.add(field(local, "audit-menu-field-actor") + " [white]" + TextWidth.escape(actor) + "[]"
                    + (rec.actor != null && rec.actor.type != null ? " [gray](" + rec.actor.type + ")[]" : ""));
            if (rec.origin != null && rec.origin.serverId != null && !rec.origin.serverId.isBlank()) {
                lines.add(field(local, "audit-menu-field-server") + " [sky]" + TextWidth.escape(rec.origin.serverId) + "[]");
            }
            content.add(Kit.text(String.join("\n", lines), inner)).row();
        });
    }

    private VNode reasonCard(AuditRecord rec, float width, Localization local) {
        return Kit.card(width, Accent.GRAY, Iconc.chat + " " + t(local, "audit-menu-field-reason"), (content, inner) -> {
            String reason = rec.reason != null && !rec.reason.isBlank()
                    ? rec.reason : t(local, "audit-menu-reason-unspecified");
            content.add(Kit.text("[white]" + TextWidth.escape(reason) + "[]", inner)).row();
        });
    }

    private VNode timeCard(AuditRecord rec, float width, Localization local) {
        return Kit.card(width, Accent.BLUE, Iconc.refresh + " " + t(local, "audit-menu-section-time"), (content, inner) -> {
            List<String> lines = new ArrayList<>();
            lines.add(field(local, "audit-menu-field-occurred") + " [white]"
                    + (rec.occurredAt != null ? DATE_TIME_FORMAT.format(rec.occurredAt) : "-") + "[]");
            if (rec.details != null && rec.details.durationMs != null) {
                long minutes = Math.max(1L, rec.details.durationMs / 60000L);
                lines.add(field(local, "audit-menu-field-duration") + " [white]"
                        + PlayerProfileUiController.formatDuration((int) minutes, local) + "[]");
            } else if (rec.action == AuditAction.BAN || rec.action == AuditAction.MUTE) {
                lines.add(field(local, "audit-menu-field-duration") + " [scarlet]"
                        + t(local, "audit-menu-duration-permanent") + "[]");
            }
            if (rec.details != null && rec.details.expiresAt != null) {
                lines.add(field(local, "audit-menu-field-expires") + " [white]"
                        + DATE_TIME_FORMAT.format(rec.details.expiresAt) + "[]");
            }
            content.add(Kit.text(String.join("\n", lines), inner)).row();
        });
    }

    private VNode idCard(AuditRecord rec, float width, Localization local) {
        return Kit.card(width, (content, inner) ->
                content.add(Kit.text("[gray]" + t(local, "audit-menu-field-id") + "[]\n[lightgray]"
                        + lines(rec.auditId, inner) + "[]", inner)).row());
    }

    // --- Helper Formatters ---

    /** The name of a field of a record, as it stands before its value. */
    private static String field(Localization local, String key) {
        return "[gray]" + t(local, key) + ":[]";
    }

    /** An id is one long word, which no label wraps: it is cut into lines that fit {@code width}. */
    static String lines(String id, float width) {
        if (id == null) return "";
        StringBuilder all = new StringBuilder();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (line.length() > 0 && TextWidth.of(TextWidth.escape(line.toString() + c)) > width) {
                all.append(TextWidth.escape(line.toString())).append('\n');
                line.setLength(0);
            }
            line.append(c);
        }
        return all.append(TextWidth.escape(line.toString())).toString();
    }

    private static String formatActionName(AuditAction action, Localization local) {
        if (action == null) return t(local, "unknown");
        if (local != null) {
            String key = "audit-menu-action-" + action.name().toLowerCase();
            String translated = local.t(key);
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return translated;
            }
        }
        return action.name();
    }

    private static Accent accent(AuditAction action) {
        if (action == null) return Accent.GRAY;
        return switch (action) {
            case BAN -> Accent.RED;
            case MUTE -> Accent.ORANGE;
            case WARN -> Accent.GOLD;
            case QUARANTINE -> Accent.PURPLE;
            case UNBAN, UNMUTE -> Accent.GREEN;
            case UNQUARANTINE -> Accent.TEAL;
            case KICK, NOTE, MERGE -> Accent.GRAY;
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

    /** Whether a ban or a mute still holds; nothing for the actions that do not last. */
    private static String status(AuditAction action, Instant expiresAt, Localization local) {
        if (action != AuditAction.BAN && action != AuditAction.MUTE) return "";
        if (expiresAt == null) {
            return "[scarlet]" + t(local, "audit-menu-status-permanent") + "[]";
        }
        if (Instant.now().isBefore(expiresAt)) {
            return "[scarlet]" + t(local, "audit-menu-status-active") + "[]";
        }
        return "[gray]" + t(local, "audit-menu-status-expired") + "[]";
    }
}
