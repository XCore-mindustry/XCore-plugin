"""What the cases core needs from the Redis transport, and nothing more.

The real implementation needs ``xcore-protocol`` 0.15.0 and belongs to the transport package.
Until it is installed with ``CaseService.set_transport`` the defaults below are used: every
flow keeps working, only without live evidence, deep map checks and instant in-game notices.
Argument names follow the protocol fields of the design spec (6.2).
"""

from __future__ import annotations

from typing import Any, Protocol


class CaseTransport(Protocol):
    async def publish_case_notice(
        self, *, notice_id: str, player_uuid: str, server: str
    ) -> None:
        """``cases.notice.command``: tell ``server`` that a ``case_notices`` row is waiting."""

    async def rpc_case_evidence(
        self,
        *,
        server: str,
        player_uuid: str,
        chat_lines: int = 30,
        window_seconds: int = 600,
    ) -> dict[str, Any] | None:
        """``cases.evidence.request``: the response payload, or ``None`` without a transport.

        Raises the existing RPC errors (``NoLiveServerError``, ``TimeoutError``, ...).
        """

    async def rpc_inspect_map(
        self,
        *,
        url: str,
        file_name: str,
        profile: str | None = None,
        server: str | None = None,
    ) -> dict[str, Any] | None:
        """``maps.inspect.request`` on ``server`` or on any live server; the response payload,
        or ``None`` without a transport. Raises the existing RPC errors."""


class NoCaseTransport:
    async def publish_case_notice(
        self, *, notice_id: str, player_uuid: str, server: str
    ) -> None:
        pass

    async def rpc_case_evidence(
        self,
        *,
        server: str,
        player_uuid: str,
        chat_lines: int = 30,
        window_seconds: int = 600,
    ) -> dict[str, Any] | None:
        return None

    async def rpc_inspect_map(
        self,
        *,
        url: str,
        file_name: str,
        profile: str | None = None,
        server: str | None = None,
    ) -> dict[str, Any] | None:
        return None
