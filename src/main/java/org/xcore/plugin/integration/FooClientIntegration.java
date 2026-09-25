package org.xcore.plugin.integration;

import arc.Events;
import arc.struct.IntMap;
import arc.util.Ratekeeper;
import arc.util.serialization.Jval;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.cloud.mindustry.MindustryCloudCommand;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.common.PLog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side integration with Foo's Client protocol (and compatible client mods).
 * <p>
 * Handles the {@code fooCheck} handshake, enables client transmission forwarding (team pings/chat),
 * and dynamically synchronizes the server's command list (with exact Cloud and legacy parameter signatures)
 * to client-side autocompletion via {@code commandList}.
 */
@Singleton
public class FooClientIntegration {

    public static final String PROTOCOL_VERSION = "2.0";

    private final Provider<CloudService> cloudProvider;
    private final IntMap<Ratekeeper> playerTransmissions = new IntMap<>();

    @Inject
    public FooClientIntegration(Provider<CloudService> cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    @PostConstruct
    public void init() {
        if (Vars.netServer == null) return;

        Vars.netServer.addPacketHandler("fooCheck", (player, content) -> {
            if (player == null || player.con == null) return;
            PLog.info("Player @ (@) synced with Foo's Client protocol", player.plainName(), player.uuid());

            Call.clientPacketReliable(player.con, "fooCheck", PROTOCOL_VERSION);
            Call.clientPacketReliable(player.con, "fooTransmissionEnabled", "true");
            syncCommands(player);
        });

        Vars.netServer.addPacketHandler("fooTransmission", (player, content) -> {
            if (player == null || player.con == null || content == null) return;

            Ratekeeper rate = playerTransmissions.get(player.id);
            if (rate == null) {
                rate = new Ratekeeper();
                playerTransmissions.put(player.id, rate);
            }

            // Cap at 20 packets/sec per player to prevent client flood attacks
            if (!rate.allow(1000, 20)) {
                return;
            }

            String payload = player.id + " " + content;
            if (Groups.player != null) {
                for (Player other : Groups.player) {
                    if (other != player && other.con != null) {
                        Call.clientPacketReliable(other.con, "fooTransmission", payload);
                    }
                }
            }
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            if (event.player != null) {
                playerTransmissions.remove(event.player.id);
            }
        });
    }

    /**
     * Serializes and dispatches the command registry to the player's client via {@code commandList}.
     */
    public void syncCommands(Player player) {
        if (player == null || player.con == null) return;
        String json = buildCommandListJson(player);
        Call.clientPacketReliable(player.con, "commandList", json);
    }

    /**
     * Builds the JSON command payload parsed by Foo's Client {@code CommandCompletion}:
     * <pre>{@code
     * {
     *   "prefix": "/",
     *   "commands": {
     *     "help": "[page]",
     *     "profile": "[player]",
     *     "settings": ""
     *   }
     * }
     * }</pre>
     */
    public String buildCommandListJson(Player player) {
        Map<String, String> commands = collectCommands(player);

        String prefix = (Vars.netServer != null && Vars.netServer.clientCommands != null)
                ? Vars.netServer.clientCommands.getPrefix()
                : "/";
        if (prefix == null || prefix.isEmpty()) {
            prefix = "/";
        }

        Jval json = Jval.newObject();
        json.add("prefix", prefix);

        Jval cmdObj = Jval.newObject();
        commands.forEach(cmdObj::add);
        json.add("commands", cmdObj);

        return json.toString(Jval.Jformat.plain);
    }

    Map<String, String> collectCommands(Player player) {
        Map<String, String> commands = new LinkedHashMap<>();

        CloudService cloudService = cloudProvider != null ? cloudProvider.get() : null;
        if (cloudService != null && cloudService.getClientManager() != null) {
            MindustrySender rawSender = player != null
                    ? new MindustrySender.PlayerSender(player)
                    : new MindustrySender.ConsoleSender();
            XCoreSender sender = cloudService.getClientManager().senderMapper().map(rawSender);

            var helpHandler = cloudService.getHelpHandler();
            if (helpHandler != null) {
                var rootIndex = helpHandler.queryRootIndex(sender);
                if (rootIndex != null && rootIndex.entries() != null) {
                    for (var entry : rootIndex.entries()) {
                        if (cloudService.isCommandDisabled(entry.command())) {
                            continue;
                        }

                        var root = entry.command().rootComponent();
                        String rootName = root.name();
                        String syntax = entry.syntax();
                        String params = extractParams(rootName, syntax);

                        commands.merge(rootName, params, this::mergeParams);
                        for (String alias : root.aliases()) {
                            commands.merge(alias, params, this::mergeParams);
                        }
                    }
                }
            }
        }

        // Add legacy Mindustry commands (excluding Cloud bridge wrappers and disabled commands)
        if (Vars.netServer != null && Vars.netServer.clientCommands != null) {
            for (var cmd : Vars.netServer.clientCommands.getCommandList()) {
                if (cmd instanceof MindustryCloudCommand) continue;
                if (cloudService != null && cloudService.isCommandDisabled(cmd.text)) continue;

                String params = cmd.paramText != null ? cmd.paramText.trim() : "";
                commands.putIfAbsent(cmd.text, params);
            }
        }

        return commands;
    }

    private String extractParams(String rootName, String syntax) {
        if (syntax == null || syntax.isBlank()) return "";
        syntax = syntax.trim();
        if (syntax.startsWith("/")) syntax = syntax.substring(1).trim();
        if (syntax.startsWith(rootName)) {
            return syntax.substring(rootName.length()).trim();
        }
        return syntax;
    }

    private String mergeParams(String existing, String candidate) {
        if (existing == null || existing.isEmpty()) return candidate;
        if (existing.equals("[args...]") && candidate != null && !candidate.isEmpty()) return candidate;
        return existing;
    }
}
