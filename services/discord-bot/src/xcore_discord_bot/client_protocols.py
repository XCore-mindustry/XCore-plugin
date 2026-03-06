from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol, runtime_checkable

from .settings import Settings


@runtime_checkable
class SupportsSettings(Protocol):
    @property
    def settings(self) -> Settings: ...


@runtime_checkable
class SupportsPlayerAutocomplete(Protocol):
    async def autocomplete_players(
        self,
        query: str,
        *,
        limit: int,
    ) -> Sequence[dict[str, object]]: ...


@runtime_checkable
class SupportsCachedMaps(Protocol):
    async def get_cached_maps(self, server: str) -> list[dict[str, str]]: ...
