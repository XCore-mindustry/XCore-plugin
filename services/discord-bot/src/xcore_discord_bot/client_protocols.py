from __future__ import annotations

from typing import Protocol, runtime_checkable

from .settings import Settings


@runtime_checkable
class SupportsSettings(Protocol):
    @property
    def settings(self) -> Settings: ...
