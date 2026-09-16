from __future__ import annotations

from ..rpc.mindustry_rpc import MindustryRpcClient


class MapService:
    def __init__(self, rpc: MindustryRpcClient) -> None:
        self._rpc = rpc
        self._map_cache: dict[str, tuple[float, list[dict[str, str]]]] = {}

    async def list_maps(
        self, server: str, timeout_ms: int = 5000
    ) -> list[dict[str, str]]:
        return await self._rpc.rpc_maps_list(server=server, timeout_ms=timeout_ms)

    async def remove_map(
        self, *, server: str, file_name: str, timeout_ms: int = 5000
    ) -> str:
        return await self._rpc.rpc_remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=timeout_ms,
        )

    async def publish_maps_load(
        self, *, server: str, files: list[dict[str, str]]
    ) -> None:
        await self._rpc.publish_maps_load(server=server, files=files)
