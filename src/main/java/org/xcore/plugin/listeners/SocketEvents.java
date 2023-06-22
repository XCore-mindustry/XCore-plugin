package org.xcore.plugin.listeners;

import arc.func.Cons;
import arc.util.Http;
import arc.util.Timer;
import arc.util.Log;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.net.Packets;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.commands.DiscordCommands;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import com.ospx.sock.EventBus.Subscription;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

public class SocketEvents {
    public static void init() {
        if (SockCommunicator.isSocketServer()) {
            Bot.connect();
            DiscordCommands.init();

            SockCommunicator.onEvent(MessageEvent.class,
                    e -> Bot.sendMessageEvent(e.authorName(), e.message(), e.server()));
            SockCommunicator.onEvent(ServerActionEvent.class,
                    e -> Bot.getServerLogChannel(e.server()).createMessage(e.message()).subscribe());
            SockCommunicator.onEvent(PlayerJoinLeaveEvent.class,
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName(), e.server(), e.join()));
            SockCommunicator.onEvent(AdminRequestEvent.class, e -> Bot.sendAdminRequestEvent(e.pid(), e.server()));
            SockCommunicator.onEvent(BanData.class, Bot::sendBan);

            Timer.schedule(() -> {
                var datas = database.getPlayerDataExecutor().getAdmins();
                Bot.sendAdminPlayTimeMessage(datas);

                for (PlayerData data : datas) {
                    data.playTime = 0;
                    data.save();
                    SockCommunicator.sendEvent(new SocketEvents.SyncPlayerData(data));
                }
            }, Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT))
                    .toSeconds(), 60 * 60 * 24);

        } else {
            SockCommunicator.onEvent(DiscordMessageEvent.class, e -> {
                if (!e.server().equals(config.server))
                    return;

                XcorePlugin.sendMessageFromDiscord(e.authorName(), e.message());
            });
        }

        SockCommunicator.onEvent(MapsListRequest.class, e -> {
            if (!e.server.equals(config.server))
                return;

            SockCommunicator.sendEvent(new MapsListResponse(e.id, maps.customMaps().map(Map::plainName).toArray(String.class)));
        });

        SockCommunicator.onEvent(MapRemoveRequest.class, e -> {
            if (!e.server.equals(config.server))
                return;

            var map = maps.byName(e.mapName);
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            SockCommunicator.sendEvent(new MapRemoveResponse(e.id, map == null ?
                    "net" :
                    "da"
            ));
        });

        SockCommunicator.onEvent(AdminRequestConfirmEvent.class, e -> {
            if (!e.server.equals(config.server))
                return;

            var info = Find.playerInfo(e.uuid);
            var player = Find.playerByUuid(e.uuid);

            if (info == null)
                return;
            if (player != null) {
                player.admin = true;
                send(player, "commands.login.confirmed");
            }

            netServer.admins.adminPlayer(e.uuid, info.adminUsid);
        });

        SockCommunicator.onEvent(KickBannedPlayer.class, e -> Groups.player
                .each(p -> p.uuid().equals(e.uuid) || p.ip().equals(e.ip), p -> p.kick(Packets.KickReason.banned)));

        SockCommunicator.onEvent(SyncPlayerData.class, e -> {
            if (database.cachedPlayerData.containsKey(e.data().uuid))
                database.setCached(e.data());
        });

        SockCommunicator.onEvent(LoadMaps.class, e -> {
            if (!config.server.equals(e.server))
                return;

            AtomicInteger counter = new AtomicInteger();
            for (String url : e.urls) {
                Http.get(url)
                        .error(Log::err)
                        .submit(result -> {
                            var split = url.split("/");
                            var fileName = split[split.length - 1];
                            customMapDirectory.child(fileName).writeBytes(result.getResult());

                            if (counter.incrementAndGet() == e.urls.length) {
                                maps.reload();
                            }
                        });
            }
        });

        SockCommunicator.sendEvent(new ServerActionEvent("Server loaded", config.server));
    }

    public record MessageEvent(String authorName, String message, String server) {
    }

    public record ServerActionEvent(String message, String server) {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) {
    }

    public record AdminRequestEvent(int pid, String server) {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) {
    }

    public record KickBannedPlayer(String uuid, String ip) {
    }

    public record SyncPlayerData(PlayerData data) {
    }

    public record LoadMaps(String[] urls, String server) {
    }

    public sealed interface ExpiringRequest<T extends ExpiringResponse> permits MapsListRequest, MapRemoveRequest {
        String id();
        String server();

        default void send(Class<T> type, Cons<T> listener, Runnable expired) {
            var subscription = new Subscription[1];

            subscription[0] = SockCommunicator.onEvent(type, event -> {
                if (event.id().equals(id())) {
                    subscription[0].unsubscribe();
                    listener.get(event);
                }
            }).expireAfter(3000, expired);

            SockCommunicator.sendEvent(this);
        }
    }

    public record MapsListRequest(String id, String server) implements ExpiringRequest<MapsListResponse> {
        public MapsListRequest(String server) {
            this(UUID.randomUUID().toString(), server);
        }
    }

    public record MapRemoveRequest(String id, String mapName, String server) implements ExpiringRequest<MapRemoveResponse> {
        public MapRemoveRequest(String mapName, String server) {
            this(UUID.randomUUID().toString(), mapName, server);
        }
    }

    public sealed interface ExpiringResponse permits MapRemoveResponse, MapsListResponse {
        String id();
    }

    public record MapsListResponse(String id, String[] maps) implements ExpiringResponse { }
    public record MapRemoveResponse(String id, String result) implements ExpiringResponse { }
}