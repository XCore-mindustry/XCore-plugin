from __future__ import annotations

from .redis_client import RedisMetricsClient
from .settings import Settings


class SnapshotDiscovery:
    def __init__(self, settings: Settings, redis_client: RedisMetricsClient) -> None:
        self._settings = settings
        self._redis_client = redis_client
        self._keys: tuple[str, ...] = ()

    def current_keys(self) -> tuple[str, ...]:
        return self._keys

    async def discover_once(self) -> bool:
        cursor = 0
        discovered: list[str] = []
        seen: set[str] = set()

        try:
            while True:
                cursor, keys = await self._redis_client.scan_snapshot_keys(cursor)
                for key in keys:
                    if key in seen:
                        continue
                    seen.add(key)
                    discovered.append(key)
                    if len(discovered) >= self._settings.max_servers:
                        self._keys = tuple(sorted(discovered))
                        return True
                if cursor == 0:
                    break
        except Exception:
            return False

        self._keys = tuple(sorted(discovered))
        return True
