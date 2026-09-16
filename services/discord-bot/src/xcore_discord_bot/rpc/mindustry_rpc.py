from __future__ import annotations

from typing import Any

from ..redis_bus import RedisBus


class MindustryRpcClient:
    def __init__(self, bus: RedisBus) -> None:
        self._bus = bus

    async def rpc_subnet_rules_command(self, **kwargs: Any) -> Any:
        return await self._bus.rpc_subnet_rules_command(**kwargs)

    async def rpc_subnet_rules_list(self, target_server: str, timeout_ms: int) -> Any:
        return await self._bus.rpc_subnet_rules_list(target_server, timeout_ms)

    async def rpc_subnet_rules_check(
        self, target_server: str, ip: str, timeout_ms: int
    ) -> Any:
        return await self._bus.rpc_subnet_rules_check(target_server, ip, timeout_ms)

    async def rpc_maps_list(
        self, *, server: str, timeout_ms: int
    ) -> list[dict[str, str]]:
        return await self._bus.rpc_maps_list(server=server, timeout_ms=timeout_ms)

    async def claim_idempotency(self, key: str, *, ttl_seconds: int = 600) -> bool:
        return await self._bus.claim_idempotency(key, ttl_seconds=ttl_seconds)

    async def rpc_remove_map(
        self,
        *,
        server: str,
        file_name: str,
        timeout_ms: int,
    ) -> str:
        return await self._bus.rpc_remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=timeout_ms,
        )

    async def publish_maps_load(
        self,
        *,
        server: str,
        files: list[dict[str, str]],
    ) -> None:
        await self._bus.publish_maps_load(server=server, files=files)
