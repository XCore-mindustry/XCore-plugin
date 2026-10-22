package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.cases.*;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.plugin.ui.kit.TextWidth;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Texts.t;

/** Immutable report wizard. Transitions are pure; submission is a queued, guarded effect. */
public class ReportUiController implements UiController<ReportUiController.Model, ReportUiController.Event> {
    public static final int PAGE_SIZE = 8;
    public static final int COMMENT_LIMIT = 300;
    public enum Stage { SELECT, REASON, CONFIRM, SENDING, RESULT, CLOSED }

    public record Model(List<ReportTarget> targets, List<String> colors, List<ReportReason> reasons,
                        long now, Stage stage, int page, int selected, ReportReason reason,
                        String comment, String validation, ReportResult result) {
        public Model {
            targets = List.copyOf(targets);
            colors = List.copyOf(colors);
            reasons = List.copyOf(reasons);
            comment = limit(comment);
        }

        public static Model selection(List<ReportTarget> targets, List<String> colors,
                                      List<ReportReason> reasons, long now) {
            return new Model(targets, colors, reasons, now, Stage.SELECT, 1, -1, null, "", "", null);
        }

        public int pages() { return Math.max(1, (targets.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
        public ReportTarget target() { return selected >= 0 && selected < targets.size() ? targets.get(selected) : null; }
        public Model pick(int index) {
            return new Model(targets, colors, reasons, now, Stage.REASON, page, index, null, "", "", null);
        }
        public Model completed(ReportResult result) { return change(Stage.RESULT, page, reason, comment, "", result); }
        Model change(Stage stage, int page, ReportReason reason, String comment, String validation, ReportResult result) {
            return new Model(targets, colors, reasons, now, stage, page, selected, reason, comment, validation, result);
        }
    }

    public sealed interface Event {
        record Pick(int index) implements Event {}
        record Page(int page) implements Event {}
        record Reason(ReportReason reason, String comment) implements Event {}
        record Next(String comment) implements Event {}
        record Back(String comment) implements Event {}
        record Send() implements Event {}
        record Completed(ReportResult result) implements Event {}
        record Close() implements Event {}
    }

    private final ReportService reports;
    private final SessionService sessions;
    private final Async async;
    private final Session session;
    private final Player player;
    private final NetConnection connection;
    private final String reporter;

    public ReportUiController(ReportService reports, SessionService sessions, Async async, Session session) {
        this.reports = reports;
        this.sessions = sessions;
        this.async = async;
        this.session = session;
        player = session.player;
        connection = player.con;
        reporter = session.data.uuid;
    }

    /** The state calculation is independently testable and never touches game state or performs I/O. */
    public static UpdateResult<Model> transition(Model model, Event event) {
        if (model.stage() == Stage.CLOSED) return UpdateResult.of(model);
        if (event instanceof Event.Close) return UpdateResult.close(
                model.change(Stage.CLOSED, model.page(), model.reason(), model.comment(), "", model.result()));
        return switch (event) {
            case Event.Pick(int index) -> model.stage() == Stage.SELECT && index >= 0 && index < model.targets().size()
                    && index / PAGE_SIZE + 1 == model.page()
                    ? UpdateResult.rerender(model.pick(index)) : UpdateResult.of(model);
            case Event.Page(int page) -> model.stage() == Stage.SELECT && page >= 1 && page <= model.pages()
                    ? UpdateResult.rerender(model.change(Stage.SELECT, page, model.reason(), model.comment(), "", null))
                    : UpdateResult.of(model);
            case Event.Reason(ReportReason reason, String comment) -> model.stage() == Stage.REASON && model.reasons().contains(reason)
                    ? UpdateResult.rerender(model.change(Stage.REASON, model.page(), reason,
                    comment == null ? model.comment() : comment, "", null)) : UpdateResult.of(model);
            case Event.Next(String comment) -> {
                if (model.stage() != Stage.REASON) yield UpdateResult.of(model);
                String value = limit(comment == null ? model.comment() : comment);
                if (model.reason() == null) yield UpdateResult.rerender(model.change(Stage.REASON, model.page(), null,
                        value, "report-menu-pick-reason", null));
                if (model.reason() == ReportReason.OTHER && value.isBlank()) yield UpdateResult.rerender(
                        model.change(Stage.REASON, model.page(), model.reason(), value, "report-menu-comment-required", null));
                yield UpdateResult.rerender(model.change(Stage.CONFIRM, model.page(), model.reason(), value, "", null));
            }
            case Event.Back(String comment) -> switch (model.stage()) {
                case REASON -> UpdateResult.rerender(model.change(Stage.SELECT, model.page(), model.reason(),
                        comment == null ? model.comment() : comment, "", null));
                case CONFIRM -> UpdateResult.rerender(model.change(Stage.REASON, model.page(), model.reason(), model.comment(), "", null));
                default -> UpdateResult.of(model);
            };
            case Event.Send() -> model.stage() == Stage.CONFIRM && model.target() != null && model.reason() != null
                    && (model.reason() != ReportReason.OTHER || !model.comment().isBlank())
                    ? UpdateResult.rerender(model.change(Stage.SENDING, model.page(), model.reason(), model.comment(), "", null))
                    : UpdateResult.of(model);
            case Event.Completed(ReportResult result) -> model.stage() == Stage.SENDING
                    ? UpdateResult.rerender(model.completed(result == null ? new ReportResult.Failed() : result))
                    : UpdateResult.of(model);
            case Event.Close() -> throw new AssertionError();
        };
    }

    @Override public UpdateResult<Model> update(Model model, Event event, ControllerContext context) {
        var update = transition(model, event);
        if (model.stage() == Stage.CONFIRM && update.model().stage() == Stage.SENDING && context != null) {
            long version = session.uiVersion();
            // Posting lets UiSession install SENDING first, including for an immediately completed refusal.
            context.post(() -> submit(update.model(), version));
        }
        return update;
    }

    private boolean current(Model sending, long version) {
        return session.player == player && player.con == connection && Async.isPlayerOnline(player)
                && session.data != null && Objects.equals(session.data.uuid, reporter)
                && sessions.getActive(player) == session && session.uiVersion() == version
                && session.activeUiSession() != null && session.activeUiSession().model() == sending;
    }

    private void submit(Model sending, long version) {
        if (!current(sending, version)) return;
        try {
            async.onMain(reports.submit(session, new ReportDraft(reporter, sending.target(), sending.reason(), sending.comment())),
                    (result, error) -> deliver(sending, version, error == null ? result : new ReportResult.Failed()));
        } catch (RuntimeException error) {
            deliver(sending, version, new ReportResult.Failed());
        }
    }

    @SuppressWarnings("unchecked")
    private void deliver(Model sending, long version, ReportResult result) {
        if (current(sending, version)) {
            ((UiSession<Model, Event>) session.activeUiSession()).dispatch(new Event.Completed(result));
        }
    }

    @Override public Event parseEvent(MenuResult result) {
        if (result == null || result.wasCancelled()) return null;
        String action = result.result;
        String comment = result.values != null && result.values.containsKey("field_comment")
                ? result.getString("field_comment", "") : null;
        try {
            if (action.startsWith("action:pick:")) return new Event.Pick(Integer.parseInt(action.substring(12)));
            if (action.startsWith("action:page:")) return new Event.Page(Integer.parseInt(action.substring(12)));
            if (action.startsWith("action:reason:")) {
                String code = action.substring(14);
                for (ReportReason reason : ReportReason.values()) if (reason.code().equals(code)) return new Event.Reason(reason, comment);
                return null;
            }
        } catch (NumberFormatException ignored) { return null; }
        return switch (action) {
            case "action:next" -> new Event.Next(comment);
            case "action:back" -> new Event.Back(comment);
            case "action:send" -> new Event.Send();
            case "action:close" -> new Event.Close();
            default -> null;
        };
    }

    @Override public VNode render(Model model) { return Screen.compact(screen -> window(model, screen)); }

    VNode window(Model model, Screen screen) {
        // The desktop uses the same single column as portrait.
        Screen compact = screen.columns() > 1 ? Screen.NARROW : screen;
        float width = compact.width();
        return Kit.window(window -> {
            window.add(Kit.header(width, t(session, "report-menu-title"))).row();
            window.add(Kit.line(width, Accent.GOLD)).row();
            Kit.body(window, compact, body -> {
                switch (model.stage()) {
                    case SELECT -> selection(body, width, model);
                    case REASON -> reasons(body, width, model);
                    case CONFIRM -> {
                        body.add(targetCard(model, width)).row();
                        body.add(Kit.card(width, Accent.GOLD, t(session, "report-menu-confirm-title"), (content, inner) -> {
                            content.add(Kit.text(t(session, model.reason().bundleKey()), inner)).row();
                            if (!model.comment().isBlank()) content.add(Kit.text(
                                    TextWidth.breakWords(TextWidth.escape(model.comment()), inner), inner)).row();
                        })).row();
                        body.add(Kit.note(width, t(session, "report-menu-confirm-note"))).row();
                        body.add(Kit.actions(width, List.of(action("back"), action("send")))).row();
                    }
                    case SENDING -> {
                        body.add(targetCard(model, width)).row();
                        body.add(Kit.note(width, t(session, "map-ui-loading"))).row();
                        body.add(Kit.actions(width, List.of(action("close")))).row();
                    }
                    case RESULT -> {
                        body.add(Kit.feedback(width, resultText(model.result()), model.result() instanceof ReportResult.Sent)).row();
                        body.add(Kit.actions(width, List.of(action("close")))).row();
                    }
                    case CLOSED -> { }
                }
            });
        });
    }

    private void selection(Ui.TableBuilder body, float width, Model model) {
        body.add(Kit.text(t(session, "report-menu-pick-player"), width)).row();
        int from = (model.page() - 1) * PAGE_SIZE;
        int until = Math.min(from + PAGE_SIZE, model.targets().size());
        boolean recentHeading = false;
        if (model.targets().isEmpty()) body.add(Kit.note(width, t(session, "report-menu-empty"))).row();
        for (int i = from; i < until; i++) {
            var target = model.targets().get(i);
            if (!target.online() && !recentHeading) {
                body.add(Kit.text(t(session, "report-menu-left-recently"), width)).row();
                recentHeading = true;
            }
            int index = i;
            body.add(Kit.row("action:pick:" + index, width, false, true, (row, inner) -> {
                row.layout(l -> l.minHeight(Kit.FIELD_HEIGHT));
                String color = index < model.colors().size() ? model.colors().get(index) : "b0b0b0";
                row.add(Kit.text(identity(target, inner, color), inner)).row();
                if (!target.online()) row.add(Kit.text(leftAgo(model, target), inner)).row();
            })).row();
        }
        if (model.pages() > 1) body.add(Kit.pager(width, model.page() + " / " + model.pages(),
                model.page() > 1 ? "action:page:" + (model.page() - 1) : null,
                model.page() < model.pages() ? "action:page:" + (model.page() + 1) : null, null)).row();
        body.add(Kit.note(width, t(session, "report-menu-staff-hint"))).row();
        body.add(Kit.actions(width, List.of(action("close")))).row();
    }

    private void reasons(Ui.TableBuilder body, float width, Model model) {
        body.add(targetCard(model, width)).row();
        body.add(Kit.card(width, Accent.GOLD, t(session, "report-menu-pick-reason"), (content, inner) -> {
            Kit.options(content, inner, inner, "reason", model.reasons().stream().map(reason -> new Kit.Option(
                    t(session, reason.bundleKey()), "action:reason:" + reason.code(), reason == model.reason())).toList());
            content.add(Kit.text(t(session, "report-menu-comment"), inner)).row();
            content.field("field_comment", field -> field.value(model.comment()).maxLength(COMMENT_LIMIT)
                    .hint(Strings.stripColors(t(session, "report-menu-comment")))
                    .layout(l -> l.width(inner).height(Kit.FIELD_HEIGHT).padBottom(Kit.GAP))).row();
            if (!model.validation().isEmpty()) content.add(Kit.feedback(inner, t(session, model.validation()), false)).row();
        })).row();
        body.add(Kit.actions(width, List.of(action("back"), action("next")))).row();
    }

    private VNode targetCard(Model model, float width) {
        return Kit.card(width, (content, inner) -> {
            String color = model.selected() >= 0 && model.selected() < model.colors().size()
                    ? model.colors().get(model.selected()) : "b0b0b0";
            content.add(Kit.text(identity(model.target(), inner, color), inner)).row();
            if (!model.target().online()) content.add(Kit.text(t(session, "report-menu-left-recently") + " — "
                    + leftAgo(model, model.target()), inner)).row();
        });
    }

    private String leftAgo(Model model, ReportTarget target) {
        long elapsed = model.now() <= target.leftAt() ? 0 : Math.subtractExact(model.now(), target.leftAt());
        return t(session, "report-menu-left-ago", args("minutes", elapsed / 60_000));
    }

    private static String identity(ReportTarget target, float width, String color) {
        String pid = PlayerPids.isAssigned(target.pid()) ? " [gray]#" + target.pid() + "[]" : "";
        return "[#" + color + "]" + TextWidth.fit(TextWidth.escape(target.name()), width - TextWidth.of(pid)) + "[]" + pid;
    }

    private Kit.Action action(String name) { return new Kit.Action(t(session, "report-menu-" + name), "action:" + name); }

    String resultText(ReportResult result) {
        return switch (result) {
            case ReportResult.Sent ignored -> t(session, "report-result-sent");
            case ReportResult.Cooldown(long seconds) -> t(session, "report-result-cooldown", args("seconds", seconds));
            case ReportResult.SameTarget ignored -> t(session, "report-result-same-target");
            case ReportResult.DailyLimit ignored -> t(session, "report-result-daily-limit");
            case ReportResult.InvalidTarget ignored -> t(session, "report-result-invalid-target");
            case ReportResult.InsufficientPlaytime(long minutes) -> t(session, "report-result-playtime", args("minutes", minutes));
            case ReportResult.Disabled ignored -> t(session, "report-result-disabled");
            case ReportResult.Failed ignored -> t(session, "report-result-failed");
        };
    }

    private static String limit(String text) {
        if (text == null) return "";
        if (text.length() <= COMMENT_LIMIT) return text;
        int end = Character.isHighSurrogate(text.charAt(COMMENT_LIMIT - 1)) ? COMMENT_LIMIT - 1 : COMMENT_LIMIT;
        return text.substring(0, end);
    }
}
