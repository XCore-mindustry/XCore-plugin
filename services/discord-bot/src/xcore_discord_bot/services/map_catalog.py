from __future__ import annotations

import time

from ..rpc.mindustry_rpc import MindustryRpcClient

# How long a server's map list is reused before it is asked again.
TTL_SECONDS = 60
LIST_TIMEOUT_MS = 3000


class MapCatalog:
    """The maps installed on each server, as last listed; kept for autocomplete."""

    def __init__(self, rpc: MindustryRpcClient) -> None:
        self._rpc = rpc
        self._lists: dict[str, tuple[float, list[dict[str, str]]]] = {}

    async def maps(self, server: str) -> list[dict[str, str]]:
        """The server's maps, listed again once the last list is a minute old.

        A server that does not answer gets its last list, or none.
        """
        now = time.monotonic()
        cached = self._lists.get(server)
        if cached is not None and now - cached[0] < TTL_SECONDS:
            return cached[1]
        try:
            maps = await self._rpc.list_maps(server=server, timeout_ms=LIST_TIMEOUT_MS)
        except TimeoutError:
            return cached[1] if cached is not None else []
        self._lists[server] = (now, maps)
        return maps

    def forget(self, server: str) -> None:
        """Drops the server's list, so the next read asks the server again."""
        self._lists.pop(server, None)
