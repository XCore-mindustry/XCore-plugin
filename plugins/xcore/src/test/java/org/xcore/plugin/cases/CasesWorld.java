package org.xcore.plugin.cases;

import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.integration.blockhistory.BlockHistoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class CasesWorld {
    public final TomlSecretsConfig secrets = new TomlSecretsConfig();
    public final TomlXcoreConfig config = new TomlXcoreConfig();
    public final SessionService sessions = mock(SessionService.class);
    public final NetworkService network = mock(NetworkService.class);
    public final CasesFeature feature = new CasesFeature(secrets, config);
    public final CasesTestLoop loop = new CasesTestLoop();
    public final RecentChatBuffer chat = new RecentChatBuffer(feature, sessions);
    public final RecentPlayers recent = new RecentPlayers(feature, secrets);
    public final BlockHistoryRegistry history = new BlockHistoryRegistry();
    public final ReportRateLimiter limiter = new ReportRateLimiter(secrets);
    public final CaseEvidenceService evidence = new CaseEvidenceService(feature, chat, recent, history);
    public final ReportService reports = new ReportService(feature, secrets, config, sessions, recent, evidence,
            limiter, network, loop.async);

    public CasesWorld() {
        mindustry.gen.Groups.init();
        secrets.cases.reports.enabled = true;
        config.server.name = "survival";
    }

    public Session session(String uuid, int pid, String address) {
        Player player = mock(Player.class);
        when(player.id()).thenReturn(pid);
        when(player.uuid()).thenReturn(uuid);
        when(player.ip()).thenReturn(address);
        when(player.plainName()).thenReturn("Player " + pid);
        when(player.isAdded()).thenReturn(true);
        player.con = mock(NetConnection.class);
        when(player.con.isConnected()).thenReturn(true);
        Session session = mock(Session.class);
        session.player = player;
        session.data = new PlayerData();
        session.data.uuid = uuid;
        session.data.pid = pid;
        session.permissions = new PermissionService();
        session.permissionSet = PermissionSet.EMPTY;
        Localization locale = mock(Localization.class);
        session.localization = locale;
        when(locale.getLocale()).thenReturn(Locale.ENGLISH);
        when(session.locale()).thenReturn(locale);
        when(sessions.getActive(uuid)).thenReturn(session);
        when(sessions.getActive(player)).thenReturn(session);
        when(sessions.get(uuid)).thenReturn(session);
        when(sessions.get(player)).thenReturn(session);
        mindustry.gen.Groups.player.add(player);
        return session;
    }

    public static ReportTarget target(Session session) {
        return new ReportTarget(session.data.uuid, session.data.pid, session.player.plainName(), true, 0);
    }

    public static ReportDraft draft(Session reporter, Session target) {
        return new ReportDraft(reporter.data.uuid, target(target), ReportReason.GRIEFING, "Broken core");
    }
}
