from __future__ import annotations

from collections.abc import Sequence
from datetime import datetime
from typing import Protocol, runtime_checkable

from .dto import PlayerRecord


@runtime_checkable
class StoreService(Protocol):
    async def autocomplete_players(
        self,
        query: str,
        *,
        limit: int,
    ) -> Sequence[PlayerRecord]: ...


@runtime_checkable
class PlayerLookupService(Protocol):
    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None: ...

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None: ...

    async def now_utc(self) -> datetime: ...


@runtime_checkable
class BusService(Protocol):
    async def get_cached_maps(self, server: str) -> list[dict[str, str]]: ...


@runtime_checkable
class ConsumerRecoveryService(Protocol):
    async def reconnect_bus(self) -> None: ...
