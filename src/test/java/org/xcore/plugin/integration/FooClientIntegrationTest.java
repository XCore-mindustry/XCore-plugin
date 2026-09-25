package org.xcore.plugin.integration;

import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.entities.EntityGroup;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.incendo.cloud.help.result.IndexCommandResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FooClientIntegrationTest {

    private NetServer previousNetServer;
    private EntityGroup<Player> previousPlayers;
    private CloudService cloudService;
    private MindustryCommandManager<XCoreSender> clientManager;
    private HelpHandler<XCoreSender> helpHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        previousNetServer = Vars.netServer;
        previousPlayers = Groups.player;
        Groups.player = new EntityGroup<>(Player.class, false, false);

        NetServer netServer = mock(NetServer.class);
        netServer.clientCommands = new CommandHandler("/");
        Vars.netServer = netServer;

        cloudService = mock(CloudService.class);
        clientManager = mock(MindustryCommandManager.class);
        helpHandler = mock(HelpHandler.class);

        when(cloudService.getClientManager()).thenReturn(clientManager);
        when(cloudService.getHelpHandler()).thenReturn(helpHandler);
        when(clientManager.senderMapper()).thenReturn(SenderMapper.create(
                raw -> mock(XCoreSender.class),
                sender -> mock(org.xcore.cloud.mindustry.MindustrySender.class)
        ));
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
        Groups.player = previousPlayers;
    }

    @Test
    @DisplayName("buildCommandListJson formats prefix and resolves Cloud commands with real parameters and aliases")
    @SuppressWarnings("unchecked")
    void buildCommandListJson_formatsCloudCommandsAndAliases() {
        // Setup mock command: /vote <choice> with alias /v
        Command<XCoreSender> voteCmd = mock(Command.class);
        CommandComponent<XCoreSender> voteRoot = mock(CommandComponent.class);
        when(voteRoot.name()).thenReturn("vote");
        when(voteRoot.aliases()).thenReturn(Collections.singleton("v"));
        when(voteCmd.rootComponent()).thenReturn(voteRoot);

        CommandEntry<XCoreSender> voteEntry = mock(CommandEntry.class);
        when(voteEntry.command()).thenReturn(voteCmd);
        when(voteEntry.syntax()).thenReturn("vote <choice>");

        // Setup mock command: /settings (no args)
        Command<XCoreSender> settingsCmd = mock(Command.class);
        CommandComponent<XCoreSender> settingsRoot = mock(CommandComponent.class);
        when(settingsRoot.name()).thenReturn("settings");
        when(settingsRoot.aliases()).thenReturn(Collections.emptySet());
        when(settingsCmd.rootComponent()).thenReturn(settingsRoot);

        CommandEntry<XCoreSender> settingsEntry = mock(CommandEntry.class);
        when(settingsEntry.command()).thenReturn(settingsCmd);
        when(settingsEntry.syntax()).thenReturn("settings");

        // Setup disabled command: /secret
        Command<XCoreSender> secretCmd = mock(Command.class);
        CommandComponent<XCoreSender> secretRoot = mock(CommandComponent.class);
        when(secretRoot.name()).thenReturn("secret");
        when(secretRoot.aliases()).thenReturn(Collections.emptySet());
        when(secretCmd.rootComponent()).thenReturn(secretRoot);

        CommandEntry<XCoreSender> secretEntry = mock(CommandEntry.class);
        when(secretEntry.command()).thenReturn(secretCmd);
        when(secretEntry.syntax()).thenReturn("secret <token>");

        when(cloudService.isCommandDisabled(voteCmd)).thenReturn(false);
        when(cloudService.isCommandDisabled(settingsCmd)).thenReturn(false);
        when(cloudService.isCommandDisabled(secretCmd)).thenReturn(true);

        IndexCommandResult<XCoreSender> indexResult = mock(IndexCommandResult.class);
        when(indexResult.entries()).thenReturn(List.of(voteEntry, settingsEntry, secretEntry));
        when(helpHandler.queryRootIndex(any())).thenReturn(indexResult);

        // Add a legacy command to Vars.netServer.clientCommands
        Vars.netServer.clientCommands.register("w", "<player> <message...>", "whisper", (args, player) -> {});

        FooClientIntegration integration = new FooClientIntegration(() -> cloudService);
        String jsonStr = integration.buildCommandListJson(null);

        Jval json = Jval.read(jsonStr);
        assertThat(json.getString("prefix", "")).isEqualTo("/");

        var cmds = json.get("commands");
        assertThat(cmds.getString("vote", null)).isEqualTo("<choice>");
        assertThat(cmds.getString("v", null)).isEqualTo("<choice>");
        assertThat(cmds.getString("settings", null)).isEqualTo("");
        assertThat(cmds.getString("w", null)).isEqualTo("<player> <message...>");
        assertThat(cmds.has("secret")).isFalse();
    }

    @Test
    @DisplayName("init registers fooCheck and fooTransmission packet handlers on netServer")
    void init_registersPacketHandlers() {
        FooClientIntegration integration = new FooClientIntegration(() -> cloudService);
        integration.init();

        verify(Vars.netServer).addPacketHandler(eq("fooCheck"), any());
        verify(Vars.netServer).addPacketHandler(eq("fooTransmission"), any());
    }

    @Test
    @DisplayName("fooCheck packet handler responds with version 2.0, transmission enabled, and commandList")
    @SuppressWarnings("unchecked")
    void fooCheck_respondsWithVersionAndCommandList() {
        var netServer = Vars.netServer;
        FooClientIntegration integration = new FooClientIntegration(() -> cloudService);
        integration.init();

        ArgumentCaptor<Cons2<Player, String>> captor = ArgumentCaptor.forClass(Cons2.class);
        verify(netServer).addPacketHandler(eq("fooCheck"), captor.capture());

        Player player = Player.create();
        NetConnection con = mock(NetConnection.class);
        player.con = con;
        player.id = 12;
        player.name = "FooUser";

        try (MockedStatic<Call> call = mockStatic(Call.class)) {
            captor.getValue().get(player, "");

            call.verify(() -> Call.clientPacketReliable(con, "fooCheck", "2.0"));
            call.verify(() -> Call.clientPacketReliable(con, "fooTransmissionEnabled", "true"));
            call.verify(() -> Call.clientPacketReliable(eq(con), eq("commandList"), anyString()));
        }
    }

    @Test
    @DisplayName("fooTransmission relays payload to other players with sender id prefix")
    @SuppressWarnings("unchecked")
    void fooTransmission_relaysToOtherPlayers() {
        var netServer = Vars.netServer;
        FooClientIntegration integration = new FooClientIntegration(() -> cloudService);
        integration.init();

        ArgumentCaptor<Cons2<Player, String>> captor = ArgumentCaptor.forClass(Cons2.class);
        verify(netServer).addPacketHandler(eq("fooTransmission"), captor.capture());

        Player sender = Player.create();
        sender.id = 1;
        sender.con = mock(NetConnection.class);

        Player recipient = Player.create();
        recipient.id = 2;
        recipient.con = mock(NetConnection.class);

        mindustry.gen.Groups.player.add(sender);
        mindustry.gen.Groups.player.add(recipient);

        try (MockedStatic<Call> call = mockStatic(Call.class)) {
            captor.getValue().get(sender, "ping_data");

            call.verify(() -> Call.clientPacketReliable(eq(recipient.con), eq("fooTransmission"), eq("1 ping_data")));
            call.verify(() -> Call.clientPacketReliable(eq(sender.con), eq("fooTransmission"), anyString()), never());
        } finally {
            mindustry.gen.Groups.player.remove(sender);
            mindustry.gen.Groups.player.remove(recipient);
        }
    }

    @Test
    @DisplayName("fooTransmission enforces 20 packets/sec rate limit per player")
    @SuppressWarnings("unchecked")
    void fooTransmission_enforcesRateLimit() {
        var netServer = Vars.netServer;
        FooClientIntegration integration = new FooClientIntegration(() -> cloudService);
        integration.init();

        ArgumentCaptor<Cons2<Player, String>> captor = ArgumentCaptor.forClass(Cons2.class);
        verify(netServer).addPacketHandler(eq("fooTransmission"), captor.capture());

        Player sender = Player.create();
        sender.id = 5;
        sender.con = mock(NetConnection.class);

        Player recipient = Player.create();
        recipient.id = 6;
        recipient.con = mock(NetConnection.class);

        mindustry.gen.Groups.player.add(sender);
        mindustry.gen.Groups.player.add(recipient);

        try (MockedStatic<Call> call = mockStatic(Call.class)) {
            for (int i = 0; i < 25; i++) {
                captor.getValue().get(sender, "spam_" + i);
            }

            // Exactly 20 should have been forwarded, 5 dropped
            call.verify(() -> Call.clientPacketReliable(eq(recipient.con), eq("fooTransmission"), anyString()), times(20));
        } finally {
            mindustry.gen.Groups.player.remove(sender);
            mindustry.gen.Groups.player.remove(recipient);
        }
    }
}
