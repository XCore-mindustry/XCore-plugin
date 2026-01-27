package org.xcore.plugin;

import arc.Core;
import arc.net.Server;
import arc.util.*;
import io.avaje.inject.BeanScope;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Administration.ActionType;
import mindustry.net.ArcNetProvider;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.infra.CommandRegistrar;
import org.xcore.plugin.listeners.NetEventService;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.common.BuildInfo;
import org.xcore.plugin.modules.common.PluginState;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.maps.SmartMapSelector;
import org.xcore.plugin.utils.FindService;
import org.xcore.plugin.utils.models.PlayerData;

import java.nio.ByteBuffer;
import java.time.Duration;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static org.xcore.plugin.utils.Utils.writeString;

public class XcorePlugin extends Plugin {
    private BeanScope beanScope;

    private CommandRegistrar commandRegistrar;
    private NetEventService netEvents;
    private DatabaseService database;
    private Config config;
    private GlobalConfig globalConfig;
    private BundleService bundleService;
    private FindService find;
    private BuildInfo buildInfo;
    private PluginState pluginState;
    private SmartMapSelector mapSelector;

    public static void info(String text, Object... values) {
        Log.infoTag("XCore", Strings.format(text, values));
    }

    public static void err(String text, Object... values) {
        Log.errTag("XCore", Strings.format(text, values));
    }

    public static void discord(String text, Object... values) {
        Log.infoTag("Discord", Strings.format(text, values));
    }

    public static void sendMessageFromDiscord(String authorName, String message) {
        discord("@: @", authorName, message);
        Call.sendMessage(Strings.format("[blue][Discord][] @: @", authorName, message));
    }

    @Override
    public void init() {
        beanScope = BeanScope.builder()
                .classLoader(getClass().getClassLoader())
                .build();

        commandRegistrar = beanScope.get(CommandRegistrar.class);
        netEvents = beanScope.get(NetEventService.class);
        database = beanScope.get(DatabaseService.class);
        config = beanScope.get(Config.class);
        globalConfig = beanScope.get(GlobalConfig.class);
        bundleService = beanScope.get(BundleService.class);
        find = beanScope.get(FindService.class);
        buildInfo = beanScope.get(BuildInfo.class);
        pluginState = beanScope.get(PluginState.class);
        mapSelector = beanScope.get(SmartMapSelector.class);

        Reflect.set(Vars.maps, "shuffler", mapSelector);

        try {
            database.checkMapDecay();
        } catch (Exception e) {
            Log.err("Failed to check map decay on init", e);
        }
        Timer.schedule(() -> {
            try {
                database.checkMapDecay();
            } catch (Exception e) {
                Log.err("Failed to check map decay", e);
            }
        }, 60 * 60, 60 * 60);

        try {
            var myMod = Vars.mods.getMod(getClass());
            if (myMod != null && myMod.meta != null) {
                buildInfo.setVersion(myMod.meta.version);
            }
        } catch (Exception e) {
            Log.err("Failed to load plugin version", e);
        }

        Vars.netServer.admins.addActionFilter(action -> {
            if (action.type == ActionType.depositItem) {
                PlayerData playerData = database.getCached(action.player.uuid());
                if (System.nanoTime() - playerData.lastUnload < 1_000_000_000)
                    return false;
                playerData.lastUnload = System.nanoTime();
            }
            return true;
        });

        ArcNetProvider provider = Reflect.get(Vars.net, "provider");
        Server server = Reflect.get(provider, "server");

        server.setConnectFilter(netEvents::connectFilter);

        final String[] footer = {""};
        server.setDiscoveryHandler((address, handler) -> {
            String name = Administration.Config.serverName.string();
            String description = Administration.Config.desc.string().equals("off")
                    ? footer[0]
                    : Administration.Config.desc.string() + footer[0];

            String map = state.map.name();

            ByteBuffer buffer = ByteBuffer.allocate(500);

            writeString(buffer, name, 100);
            writeString(buffer, map, 64);

            buffer.putInt(Core.settings.getInt("totalPlayers", Groups.player.size()));
            buffer.putInt(state.wave);
            buffer.putInt(Version.build);
            writeString(buffer, Version.type);

            buffer.put((byte) state.rules.mode().ordinal());
            buffer.putInt(config.playerLimit > 0 ? config.getNoAdminPlayerLimit() : 0);

            writeString(buffer, description, 200);
            if (state.rules.modeName != null) {
                writeString(buffer, state.rules.modeName, 50);
            }

            buffer.position(0);
            handler.respond(buffer);
        });
        netServer.admins.addChatFilter(netEvents::chat);

        Vars.net.handleServer(AdminRequestCallPacket.class, netEvents::adminRequest);
        Vars.net.handleServer(Packets.Connect.class, netEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, netEvents::connectPacket);

        Timer.schedule(() -> {
            footer[0] = config.gameStartedTimer
                    ? "\n[green]Game started [accent]" + Duration.ofMillis(Time.millis() - pluginState.gameStartTime).toMinutes() + "[] minutes ago."
                    : "";
            database.cachedPlayerData.each((uuid, data) -> {
                var player = find.playerByUuid(uuid);

                if (player == null) return;

                data.totalPlayTime++;

                if (data.totalPlayTime == globalConfig.minPlayTimeForVotekick) {
                    bundleService.send(player, "notification-votekick-playtime",
                            args("votekickPlayTime", globalConfig.minPlayTimeForVotekick));
                } else if (data.totalPlayTime == globalConfig.minPlayTimeForGlobalChat) {
                    bundleService.send(player, "notification-global-chat-playtime",
                            args("globalChatPlayTime", globalConfig.minPlayTimeForGlobalChat));
                }

                database.getPlayerDataRepository().save(data);
            });
        }, 0, 60);

        commandRegistrar.registerClient(netServer.clientCommands);
        commandRegistrar.registerServer(ServerControl.instance.handler);
    }
}