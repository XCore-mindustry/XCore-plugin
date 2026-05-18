package org.xcore.plugin.event.net.connect;

import arc.Core;
import arc.Settings;
import arc.graphics.Color;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.core.World;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.logic.LExecutor;
import mindustry.logic.LVar;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerConnectionBootstrapTest {

    private NetServer previousNetServer;
    private Settings previousSettings;
    private GameState previousState;
    private World previousWorld;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        previousSettings = Core.settings;
        previousState = Vars.state;
        previousWorld = Vars.world;

        Core.settings = mock(Settings.class);
        Vars.state = new GameState();
        Vars.state.rules = new Rules();
        Vars.world = new World();
        Vars.world.resize(1, 1).fill();
        Team.sharded.data().players.clear();
    }

    @AfterEach
    void tearDown() {
        Team.sharded.data().players.clear();
        Vars.netServer = previousNetServer;
        Core.settings = previousSettings;
        Vars.state = previousState;
        Vars.world = previousWorld;
    }

    @Test
    @DisplayName("sanitizeLogicSeqVariables clears Seq object vars in logic builds")
    void sanitizeLogicSeqVariables_clearsSeqObjectVarsInLogicBuilds() {
        LVar seqVar = new LVar("seq");
        seqVar.setobj(new Seq<>());
        LVar stringVar = new LVar("text");
        stringVar.setobj("kept");
        LVar numberVar = new LVar("number");
        numberVar.setnum(5);

        LogicBlock.LogicBuild logicBuild = mock(LogicBlock.LogicBuild.class);
        logicBuild.executor = new LExecutor();
        logicBuild.executor.vars = new LVar[]{seqVar, stringVar, numberVar};

        Tile tile = Vars.world.tile(0, 0);
        tile.build = logicBuild;

        int sanitized = PlayerConnectionBootstrap.sanitizeLogicSeqVariables();

        assertThat(sanitized).isEqualTo(1);
        assertThat(seqVar.objval).isNull();
        assertThat(stringVar.objval).isEqualTo("kept");
        assertThat(numberVar.isobj).isFalse();
    }

    @Test
    @DisplayName("bootstrap retries world send after sanitizing Seq logic vars")
    void bootstrap_retriesWorldSendAfterSanitizingSeqLogicVars() {
        Administration admins = mock(Administration.class);
        NetServer netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        when(admins.getInfo("uuid-1")).thenReturn(info);
        when(admins.isAdmin("uuid-1", "usid-1")).thenReturn(false);
        when(netServer.assignTeam(any())).thenReturn(Team.sharded);
        doThrow(new IllegalArgumentException("Unknown object type: class arc.struct.Seq"))
                .doNothing()
                .when(netServer).sendWorldData(any());

        LVar seqVar = new LVar("seq");
        seqVar.setobj(new Seq<>());
        LogicBlock.LogicBuild logicBuild = mock(LogicBlock.LogicBuild.class);
        logicBuild.executor = new LExecutor();
        logicBuild.executor.vars = new LVar[]{seqVar};
        Vars.world.tile(0, 0).build = logicBuild;

        PlayerConnectionBootstrap bootstrap = new PlayerConnectionBootstrap();

        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.uuid = "uuid-1";
        packet.usid = "usid-1";
        packet.name = "Tester";
        packet.color = Color.white.rgba();
        packet.locale = "en";

        DummyNetConnection connection = new DummyNetConnection("1.2.3.4");

        bootstrap.bootstrap(connection, packet);

        verify(netServer, times(2)).sendWorldData(any());
        assertThat(seqVar.objval).isNull();
        assertThat(connection.player).isNotNull();
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
