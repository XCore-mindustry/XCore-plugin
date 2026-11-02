"""Chat and server activity: chat in both directions, joins and leaves, server actions and heartbeats."""

from __future__ import annotations

from collections.abc import Awaitable, Callable

from ..contracts import (
    ChatGlobalV1,
    ChatMessageV1,
    PlayerJoinLeaveV1,
    ServerActionV1,
    ServerHeartbeatV1,
    parse_chat_message_payload,
    parse_global_chat_payload,
    parse_player_join_leave_payload,
    parse_server_action_payload,
    parse_server_heartbeat_payload,
)
from ..protocol_outbound import build_chat_discord_ingress_command
from ..registry import LiveServerRegistry
from .transport import RedisTransport


class ChatBus:
    def __init__(self, transport: RedisTransport, servers: LiveServerRegistry) -> None:
        self._transport = transport
        # Filled from the servers' heartbeats; a message for every server goes to these.
        self._servers = servers

    async def publish_discord_message(
        self,
        server: str | None,
        author_name: str,
        message: str,
        source_message_id: str | None = None,
    ) -> None:
        target_servers = [server] if server is not None else self._servers.names()
        for target_server in target_servers:
            await self._transport.publish(
                stream=f"xcore:cmd:discord-message:{target_server}",
                event_type="chat.discord-ingress.command",
                ttl_ms=60_000,
                server=target_server,
                payload=build_chat_discord_ingress_command(
                    author_name=author_name,
                    message=message,
                    server=target_server,
                ).to_payload(),
                idempotency_prefix="discord.message",
                idempotency_scope=source_message_id,
            )

    async def consume_game_chat(
        self, callback: Callable[[ChatMessageV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:chat:message",
            group_suffix="discord-chat",
            parse_payload=parse_chat_message_payload,
            callback=callback,
            skip_discord_producer=True,
        )

    async def consume_global_chat(
        self, callback: Callable[[ChatGlobalV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:chat:global",
            group_suffix="discord-global-chat",
            parse_payload=parse_global_chat_payload,
            callback=callback,
        )

    async def consume_server_heartbeats(
        self, callback: Callable[[ServerHeartbeatV1], Awaitable[None]]
    ) -> None:
        async def wrapped(event: ServerHeartbeatV1) -> None:
            self._update_registry_from_heartbeat(event)
            await callback(event)

        await self._transport.consume(
            stream="xcore:evt:server:heartbeat",
            group_suffix="discord-server-heartbeat",
            parse_payload=parse_server_heartbeat_payload,
            callback=wrapped,
        )

    async def consume_player_join_leave(
        self, callback: Callable[[PlayerJoinLeaveV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:player:joinleave",
            group_suffix="discord-join-leave",
            parse_payload=parse_player_join_leave_payload,
            callback=callback,
        )

    async def consume_server_actions(
        self, callback: Callable[[ServerActionV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:server:action",
            group_suffix="discord-server-action",
            parse_payload=parse_server_action_payload,
            callback=callback,
        )

    def _update_registry_from_heartbeat(self, event: ServerHeartbeatV1) -> None:
        self._servers.update_server(
            event.serverName,
            event.discordChannelId,
            event.players,
            event.maxPlayers,
            event.version,
            event.host,
            event.port,
            description=event.description,
            map_name=event.map,
            wave=event.wave,
            mode=event.mode,
            tps=event.tps,
        )
