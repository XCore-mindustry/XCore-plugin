package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import jakarta.inject.Singleton;
import org.xcore.plugin.cases.ReportResult;
import org.xcore.plugin.cases.ReportService;
import org.xcore.plugin.cases.ReportTarget;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;

import java.util.List;
import java.util.Locale;

/** Game-thread entry point; candidates are always obtained from the shared eligibility service. */
@Singleton
public class ReportMenu {
    private final ReportService reports;
    private final MenuService menus;
    private final SessionService sessions;
    private final Async async;

    public ReportMenu(ReportService reports, MenuService menus, SessionService sessions, Async async) {
        this.reports = reports;
        this.menus = menus;
        this.sessions = sessions;
        this.async = async;
    }

    public void open(Session session, String target) {
        if (session == null || session.data == null || !Async.isPlayerOnline(session.player)
                || sessions.getActive(session.player) != session) return;
        session.clear();
        session.nextUiVersion();
        var controller = new ReportUiController(reports, sessions, async, session);
        boolean enabled = reports.enabled();
        List<ReportTarget> candidates = enabled ? reports.targets(session) : List.of();
        List<String> colors = candidates.stream().map(candidate -> {
            Session online = candidate.online() ? sessions.getActive(candidate.uuid()) : null;
            return online != null && online.player != null ? online.player.team().color.toString() : "b0b0b0";
        }).toList();
        var model = ReportUiController.Model.selection(candidates, colors, reports.reasons(), System.currentTimeMillis());
        if (!enabled) {
            model = model.completed(new ReportResult.Disabled());
        } else if (target != null && !target.isBlank()) {
            List<Integer> matches = matching(candidates, target);
            if (matches.size() == 1) model = model.pick(matches.getFirst());
            else if (matches.isEmpty()) model = model.completed(new ReportResult.InvalidTarget());
            // Ambiguity stays on selection: the player chooses, rather than us guessing.
        }
        menus.openUi(session, controller, model, true);
    }

    /** Resolves signed PIDs and names only inside the service's eligible snapshot. */
    static List<Integer> matching(List<ReportTarget> targets, String query) {
        String plain = Strings.stripColors(query).strip();
        if (plain.isEmpty()) return List.of();
        // PlayerPids accepts the usual negative spelling; an explicit positive sign is also a signed PID.
        Integer pid = PlayerPids.parse(plain.matches("#?\\+\\d+") ? plain.replace("+", "") : plain);
        if (plain.startsWith("#") || pid != null) {
            return java.util.stream.IntStream.range(0, targets.size())
                    .filter(i -> pid != null && pid.equals(PlayerPids.orNull(targets.get(i).pid())))
                    .boxed().toList();
        }
        String name = plain.toLowerCase(Locale.ROOT);
        List<Integer> exact = java.util.stream.IntStream.range(0, targets.size())
                .filter(i -> Strings.stripColors(targets.get(i).name()).equalsIgnoreCase(plain)).boxed().toList();
        return exact.isEmpty() ? java.util.stream.IntStream.range(0, targets.size())
                .filter(i -> Strings.stripColors(targets.get(i).name()).toLowerCase(Locale.ROOT).contains(name))
                .boxed().toList() : exact;
    }
}
