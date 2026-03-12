package org.xcore.plugin.service.network;

import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RedisStreamRouter {

    public record Route(String streamKey, String eventType, long ttlMillis) {
    }

    public Route route(Object event, String defaultServer) {
        if (event instanceof SocketEvents.MessageEvent) {
            return new Route("xcore:evt:chat:message", "chat.message", 60000L);
        }
        if (event instanceof SocketEvents.ServerActionEvent) {
            return new Route("xcore:evt:server:action", "server.action", 60000L);
        }
        if (event instanceof SocketEvents.PlayerJoinLeaveEvent) {
            return new Route("xcore:evt:player:joinleave", "player.join_leave", 60000L);
        }
        if (event instanceof SocketEvents.GlobalChatEvent) {
            return new Route("xcore:evt:chat:global", "chat.global", 60000L);
        }
        if (event instanceof SocketEvents.DiscordMessageEvent) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:cmd:discord-message:" + server, "chat.discord_ingress", 60000L);
        }
        if (event instanceof SocketEvents.PrivateMessageEvent) {
            return new Route("xcore:evt:chat:private", "chat.private", 60000L);
        }
        if (event instanceof SocketEvents.AdminRequestEvent) {
            return new Route("xcore:evt:admin:request", "admin.request", 120000L);
        }
        if (event instanceof BanData) {
            return new Route("xcore:evt:moderation:ban", "moderation.ban", 120000L);
        }
        if (event instanceof MuteData) {
            return new Route("xcore:evt:moderation:mute", "moderation.mute", 120000L);
        }
        if (event instanceof SocketEvents.AdminRequestConfirmEvent) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:cmd:admin-confirm:" + server, "admin.confirm", 120000L);
        }
        if (event instanceof SocketEvents.KickBannedPlayer) {
            return new Route("xcore:cmd:kick-banned:" + defaultServer, "moderation.kick_banned", 120000L);
        }
        if (event instanceof SocketEvents.PlayerCustomNicknameChanged) {
            return new Route("xcore:cmd:player-custom-nickname:" + defaultServer, "player.custom_nickname", 120000L);
        }
        if (event instanceof SocketEvents.PlayerActiveBadgeChanged) {
            return new Route("xcore:cmd:player-active-badge:" + defaultServer, "player.active_badge", 120000L);
        }
        if (event instanceof SocketEvents.PlayerBadgeInventoryChanged) {
            return new Route("xcore:cmd:player-badge-inventory:" + defaultServer, "player.badge_inventory", 120000L);
        }
        if (event instanceof SocketEvents.PlayerPasswordReset) {
            return new Route("xcore:cmd:player-password-reset:" + defaultServer, "player.password_reset", 120000L);
        }
        if (event instanceof SocketEvents.DiscordLinkCodeCreatedEvent) {
            return new Route("xcore:evt:discord:link-code", "discord.link_code_created", 120000L);
        }
        if (event instanceof SocketEvents.DiscordLinkConfirmEvent) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:cmd:discord-link-confirm:" + server, "discord.link_confirm", 120000L);
        }
        if (event instanceof SocketEvents.DiscordUnlinkEvent) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:cmd:discord-unlink:" + server, "discord.unlink", 120000L);
        }
        if (event instanceof SocketEvents.DiscordLinkStatusChangedEvent) {
            return new Route("xcore:evt:discord:link-status", "discord.link_status_changed", 120000L);
        }
        if (event instanceof SocketEvents.ReloadPlayerDataCache) {
            return new Route("xcore:cmd:reload-cache:" + defaultServer, "cache.reload_player_data", 120000L);
        }
        if (event instanceof SocketEvents.LoadMapsV2) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:cmd:maps-load:" + server, "maps.load", 300000L);
        }
        if (event instanceof SocketEvents.ExecuteCommand) {
            return new Route("xcore:cmd:execute-command:broadcast", "server.execute_command", 120000L);
        }
        if (event instanceof SocketEvents.PardonPlayer) {
            return new Route("xcore:cmd:pardon-player:" + defaultServer, "moderation.pardon", 120000L);
        }
        if (event instanceof SocketEvents.RemoveAdmin) {
            return new Route("xcore:cmd:remove-admin:" + defaultServer, "admin.remove", 120000L);
        }
        if (event instanceof SocketEvents.MapsListRequest) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:rpc:req:" + server, "maps.list", 10000L);
        }
        if (event instanceof SocketEvents.MapRemoveRequest) {
            var server = extractServer(event, defaultServer);
            return new Route("xcore:rpc:req:" + server, "maps.remove", 10000L);
        }

        var eventType = "event." + event.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return new Route("xcore:evt:raw", eventType, 60000L);
    }

    public List<String> subscribeStreamsFor(Class<?> type, String defaultServer) {
        var streams = new ArrayList<String>();

        if (type == SocketEvents.MessageEvent.class) {
            streams.add("xcore:evt:chat:message");
            return streams;
        }
        if (type == SocketEvents.ServerActionEvent.class) {
            streams.add("xcore:evt:server:action");
            return streams;
        }
        if (type == SocketEvents.PlayerJoinLeaveEvent.class) {
            streams.add("xcore:evt:player:joinleave");
            return streams;
        }
        if (type == SocketEvents.GlobalChatEvent.class) {
            streams.add("xcore:evt:chat:global");
            return streams;
        }
        if (type == SocketEvents.DiscordMessageEvent.class) {
            streams.add("xcore:cmd:discord-message:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.PrivateMessageEvent.class) {
            streams.add("xcore:evt:chat:private");
            return streams;
        }
        if (type == SocketEvents.AdminRequestEvent.class) {
            streams.add("xcore:evt:admin:request");
            return streams;
        }
        if (type == BanData.class) {
            streams.add("xcore:evt:moderation:ban");
            return streams;
        }
        if (type == MuteData.class) {
            streams.add("xcore:evt:moderation:mute");
            return streams;
        }
        if (type == SocketEvents.AdminRequestConfirmEvent.class) {
            streams.add("xcore:cmd:admin-confirm:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.KickBannedPlayer.class) {
            streams.add("xcore:cmd:kick-banned:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.PlayerCustomNicknameChanged.class) {
            streams.add("xcore:cmd:player-custom-nickname:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.PlayerActiveBadgeChanged.class) {
            streams.add("xcore:cmd:player-active-badge:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.PlayerBadgeInventoryChanged.class) {
            streams.add("xcore:cmd:player-badge-inventory:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.PlayerPasswordReset.class) {
            streams.add("xcore:cmd:player-password-reset:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.DiscordLinkCodeCreatedEvent.class) {
            streams.add("xcore:evt:discord:link-code");
            return streams;
        }
        if (type == SocketEvents.DiscordLinkConfirmEvent.class) {
            streams.add("xcore:cmd:discord-link-confirm:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.DiscordUnlinkEvent.class) {
            streams.add("xcore:cmd:discord-unlink:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.DiscordLinkStatusChangedEvent.class) {
            streams.add("xcore:evt:discord:link-status");
            return streams;
        }
        if (type == SocketEvents.ReloadPlayerDataCache.class) {
            streams.add("xcore:cmd:reload-cache:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.LoadMapsV2.class) {
            streams.add("xcore:cmd:maps-load:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.ExecuteCommand.class) {
            streams.add("xcore:cmd:execute-command:broadcast");
            return streams;
        }
        if (type == SocketEvents.PardonPlayer.class) {
            streams.add("xcore:cmd:pardon-player:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.RemoveAdmin.class) {
            streams.add("xcore:cmd:remove-admin:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.MapsListRequest.class) {
            streams.add("xcore:rpc:req:" + defaultServer);
            return streams;
        }
        if (type == SocketEvents.MapRemoveRequest.class) {
            streams.add("xcore:rpc:req:" + defaultServer);
            return streams;
        }

        return streams;
    }

    public boolean isReadOnlyType(Class<?> type) {
        return type == SocketEvents.MessageEvent.class
                || type == SocketEvents.ServerActionEvent.class
                || type == SocketEvents.PlayerJoinLeaveEvent.class
                || type == SocketEvents.GlobalChatEvent.class
                || type == SocketEvents.DiscordMessageEvent.class
                || type == SocketEvents.PrivateMessageEvent.class
                || type == SocketEvents.AdminRequestEvent.class
                || type == SocketEvents.DiscordLinkCodeCreatedEvent.class
                || type == SocketEvents.DiscordLinkStatusChangedEvent.class
                || type == BanData.class
                || type == MuteData.class;
    }

    public boolean isMutatingType(Class<?> type) {
        return type == SocketEvents.AdminRequestConfirmEvent.class
                || type == SocketEvents.KickBannedPlayer.class
                || type == SocketEvents.PlayerCustomNicknameChanged.class
                || type == SocketEvents.PlayerActiveBadgeChanged.class
                || type == SocketEvents.PlayerBadgeInventoryChanged.class
                || type == SocketEvents.PlayerPasswordReset.class
                || type == SocketEvents.DiscordLinkConfirmEvent.class
                || type == SocketEvents.DiscordUnlinkEvent.class
                || type == SocketEvents.ReloadPlayerDataCache.class
                || type == SocketEvents.LoadMapsV2.class
                || type == SocketEvents.ExecuteCommand.class
                || type == SocketEvents.PardonPlayer.class
                || type == SocketEvents.RemoveAdmin.class;
    }

    public boolean isRpcRequestType(Class<?> type) {
        return type == SocketEvents.MapsListRequest.class
                || type == SocketEvents.MapRemoveRequest.class;
    }

    public Class<? extends SocketEvents.Response> responseTypeForRequest(Class<?> type) {
        if (type == SocketEvents.MapsListRequest.class) {
            return SocketEvents.MapsListResponse.class;
        }
        if (type == SocketEvents.MapRemoveRequest.class) {
            return SocketEvents.MapRemoveResponse.class;
        }
        return null;
    }

    public String rpcTypeForRequestClass(Class<?> type) {
        if (type == SocketEvents.MapsListRequest.class) {
            return "maps.list";
        }
        if (type == SocketEvents.MapRemoveRequest.class) {
            return "maps.remove";
        }
        return null;
    }

    private String extractServer(Object event, String defaultServer) {
        try {
            Method method = event.getClass().getMethod("server");
            Object value = method.invoke(event);
            if (value instanceof String server && !server.isBlank()) {
                return server;
            }
        } catch (Exception ignored) {
        }

        try {
            Field field = event.getClass().getField("server");
            Object value = field.get(event);
            if (value instanceof String server && !server.isBlank()) {
                return server;
            }
        } catch (Exception ignored) {
        }

        return defaultServer;
    }
}
