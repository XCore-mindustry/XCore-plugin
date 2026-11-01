package org.xcore.plugin.integration;

import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.game.Team;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.CommandEntry;
import org.incendo.cloud.help.result.IndexCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WithHeadlessWorld
class FooClientIntegrationTest {

    private CloudService cloudService;
    private MindustryCommandManager<XCoreSender> clientManager;
    private HelpHandler<XCoreSender> helpHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
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
        new FooClientIntegration(() -> cloudService).init();

        assertThat(Vars.netServer.getPacketHandlers("fooCheck")).isNotEmpty();
        assertThat(Vars.netServer.getPacketHandlers("fooTransmission")).isNotEmpty();
    }

    @Test
    @DisplayName("fooCheck packet handler responds with version 2.0, transmission enabled, and commandList")
    void fooCheck_respondsWithVersionAndCommandList(HeadlessWorld world) {
        var index = emptyIndex();
        when(helpHandler.queryRootIndex(any())).thenReturn(index);
        new FooClientIntegration(() -> cloudService).init();
        MockPlayer player = world.addPlayer("FooUser", Team.sharded);

        player.sendServerPacket("fooCheck", "");

        assertThat(player.clientPackets("fooCheck")).containsExactly("2.0");
        assertThat(player.clientPackets("fooTransmissionEnabled")).containsExactly("true");
        assertThat(player.clientPackets("commandList")).hasSize(1);
    }

    @Test
    @DisplayName("fooTransmission relays payload to other players with sender id prefix")
    void fooTransmission_relaysToOtherPlayers(HeadlessWorld world) {
        new FooClientIntegration(() -> cloudService).init();
        MockPlayer sender = world.addPlayer("Sender", Team.sharded);
        MockPlayer recipient = world.addPlayer("Recipient", Team.sharded);

        sender.sendServerPacket("fooTransmission", "ping_data");

        assertThat(recipient.clientPackets("fooTransmission")).containsExactly(sender.id() + " ping_data");
        assertThat(sender.clientPackets("fooTransmission")).isEmpty();
    }

    @Test
    @DisplayName("fooTransmission enforces 20 packets/sec rate limit per player")
    void fooTransmission_enforcesRateLimit(HeadlessWorld world) {
        new FooClientIntegration(() -> cloudService).init();
        MockPlayer sender = world.addPlayer("Sender", Team.sharded);
        MockPlayer recipient = world.addPlayer("Recipient", Team.sharded);

        for (int i = 0; i < 25; i++) {
            sender.sendServerPacket("fooTransmission", "spam_" + i);
        }

        // Exactly 20 should have been forwarded, 5 dropped
        assertThat(recipient.clientPackets("fooTransmission")).hasSize(20);
    }

    @SuppressWarnings("unchecked")
    private static IndexCommandResult<XCoreSender> emptyIndex() {
        IndexCommandResult<XCoreSender> index = mock(IndexCommandResult.class);
        when(index.entries()).thenReturn(List.of());
        return index;
    }
}
