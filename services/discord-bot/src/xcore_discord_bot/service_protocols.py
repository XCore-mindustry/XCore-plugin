from __future__ import annotations

from collections.abc import Mapping, Sequence
from datetime import datetime
from typing import Protocol, runtime_checkable


@runtime_checkable
class StoreService(Protocol):
    async def autocomplete_players(
        self,
        query: str,
        *,
        limit: int,
    ) -> Sequence[dict[str, object]]: ...


@runtime_checkable
class PlayerLookupService(Protocol):
    async def find_player_by_pid(self, pid: int) -> Mapping[str, object] | None: ...

    async def find_player_by_uuid(self, uuid: str) -> Mapping[str, object] | None: ...

    async def now_utc(self) -> datetime: ...


@runtime_checkable
class BusService(Protocol):
    async def get_cached_maps(self, server: str) -> list[dict[str, str]]: ...


@runtime_checkable
class ConsumerRecoveryService(Protocol):
    async def reconnect_bus(self) -> None: ...
