package org.xcore.plugin.event.handler;

import arc.util.Time;
import arc.Core;
import arc.Settings;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionHandlerTest {

    private NetServer previousNetServer;
    private Administration admins;
    private Settings previousSettings;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        previousSettings = Core.settings;
        admins = mock(Administration.class);
        NetServer netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;
        Core.settings = mock(Settings.class);
        when(Core.settings.getString("servername", "Server")).thenReturn("Server");
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
        Core.settings = previousSettings;
    }

    @Test
    @DisplayName("onPlayerJoin persists nickname with changed ip and revokes unconfirmed admin")
    void onPlayerJoin_persistsNicknameWithChangedIp_andRevokesUnconfirmedAdmin() {
        SessionService sessionService = mock(SessionService.class);
        AdminDataRepository adminDataRepository = mock(AdminDataRepository.class);
        NetworkService networkService = mock(NetworkService.class);
        VoteService voteService = mock(VoteService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";
        GlobalConfig globalConfig = new GlobalConfig();

        ConnectionHandler handler = new ConnectionHandler(
                sessionService,
                adminDataRepository,
                networkService,
                config,
                globalConfig,
                voteService,
                privateMessageService,
                playerDisplayService,
                discordAdminAccessService
        );

        Player player = Player.create();
        player.name = "[red]Renamed[]";
        player.admin = true;
        player.con = new DummyNetConnection("2.2.2.2");
        player.con.uuid = "uuid-1";
        player.con.usid = "usid-1";
        player.con.player = player;

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.timesJoined = 5;
        when(admins.getInfo("uuid-1")).thenReturn(info);
        when(admins.isAdmin("uuid-1", "usid-1")).thenReturn(false);
        when(privateMessageService.countUnread("uuid-1")).thenReturn(0L);

        PlayerData data = PlayerData.builder()
                .uuid("uuid-1")
                .pid(7)
                .ip("1.1.1.1")
                .nickname("OldName")
                .admin(true)
                .build();
        data.exists = true;

        Session session = mock(Session.class);
        session.data = data;
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        when(sessionService.registerLogin(player)).thenReturn(session);

        try (MockedStatic<Time> time = org.mockito.Mockito.mockStatic(Time.class);
             MockedStatic<Call> call = org.mockito.Mockito.mockStatic(Call.class)) {
            handler.onPlayerJoin(new EventType.PlayerJoin(player));
        }

        verify(discordAdminAccessService).deactivateRuntimeAdmin(player, "uuid-1");
        verify(sessionService).updateConnectionData(session, "2.2.2.2", "[#00000000][red]Renamed[]");
        verify(localization).send(eq("error-ip-changed"), anyMap());
        verify(playerDisplayService).refresh(session);
        verify(networkService).post(any(SocketEvents.PlayerJoinLeaveEvent.class));
        verify(sessionService, never()).persistPlayer(session);
    }

    private static final class DummyNetConnection extends NetConnection {

        private DummyNetConnection(String address) {
            super(address);
            this.lastReceivedClientSnapshot = 0;
        }

        @Override
        public void send(Object object, boolean reliable) {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }
}
